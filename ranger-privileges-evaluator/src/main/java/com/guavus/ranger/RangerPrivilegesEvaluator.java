package com.guavus.ranger;

import com.amazon.opendistroforelasticsearch.security.OpenDistroSecurityPlugin;
import com.amazon.opendistroforelasticsearch.security.auditlog.AuditLog;
import com.amazon.opendistroforelasticsearch.security.configuration.ActionGroupHolder;
import com.amazon.opendistroforelasticsearch.security.configuration.ClusterInfoHolder;
import com.amazon.opendistroforelasticsearch.security.configuration.ConfigurationRepository;
import com.amazon.opendistroforelasticsearch.security.privileges.*;
import com.amazon.opendistroforelasticsearch.security.resolver.IndexResolverReplacer;
import com.amazon.opendistroforelasticsearch.security.securityconf.ConfigModel;
import com.amazon.opendistroforelasticsearch.security.support.ConfigConstants;
import com.amazon.opendistroforelasticsearch.security.support.WildcardMatcher;
import com.amazon.opendistroforelasticsearch.security.user.User;
import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.ranger.audit.provider.MiscUtil;
import org.apache.ranger.authorization.hadoop.config.RangerConfiguration;
import org.apache.ranger.plugin.audit.RangerDefaultAuditHandler;
import org.apache.ranger.plugin.policyengine.RangerAccessRequestImpl;
import org.apache.ranger.plugin.policyengine.RangerAccessResourceImpl;
import org.apache.ranger.plugin.policyengine.RangerAccessResult;
import org.apache.ranger.plugin.service.RangerBasePlugin;
import org.elasticsearch.ElasticsearchSecurityException;
import org.elasticsearch.SpecialPermission;
import org.elasticsearch.action.*;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingRequest;
import org.elasticsearch.action.bulk.BulkAction;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.fieldcaps.FieldCapabilitiesRequest;
import org.elasticsearch.action.get.MultiGetAction;
import org.elasticsearch.action.get.MultiGetRequest;
import org.elasticsearch.action.search.MultiSearchAction;
import org.elasticsearch.action.search.MultiSearchRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.action.termvectors.MultiTermVectorsAction;
import org.elasticsearch.action.termvectors.MultiTermVectorsRequest;
import org.elasticsearch.action.termvectors.TermVectorsRequest;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.metadata.MetaData;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.collect.Tuple;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.reindex.ReindexAction;
import org.elasticsearch.index.reindex.ReindexRequest;
import org.elasticsearch.tasks.Task;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.RemoteClusterAware;
import org.elasticsearch.transport.TransportRequest;
import sun.security.krb5.Config;
import sun.security.krb5.KrbException;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Divyansh Jain
 */

public class RangerPrivilegesEvaluator extends AbstractEvaluator {

    protected final Logger log = LogManager.getLogger(this.getClass());
    private static final Set<String> NULL_SET = Sets.newHashSet((String)null);
    private final static IndicesOptions DEFAULT_INDICES_OPTIONS = IndicesOptions.lenientExpandOpen();
    private static final Set<String> NO_INDICES_SET = Sets.newHashSet("\\",";",",","/","|");
    private static final String CONFIG_FILES_PATH_PREFIX = "/etc/elasticsearch/";

    // access types
    public static final String ACCESS_TYPE_READ = "read";
    public static final String ACCESS_TYPE_WRITE = "write";
    public static final String ACCESS_TYPE_ADMIN = "admin";
    public static final String ACCESS_TYPE_MONITOR = "monitor";

    protected final Logger actionTrace = LogManager.getLogger("opendistro_security_action_trace");
    private final ClusterService clusterService;

    private final IndexNameExpressionResolver resolver;

    // Initialized because used later in opendistro code
    private final AuditLog auditLog;
    private ThreadContext threadContext;
    //private final static IndicesOptions DEFAULT_INDICES_OPTIONS = IndicesOptions.lenientExpandOpen();
    //private final ConfigurationRepository configurationRepository;
    private final ClusterInfoHolder clusterInfoHolder;
    private final SnapshotRestoreEvaluator snapshotRestoreEvaluator;
    private final OpenDistroSecurityIndexAccessEvaluator securityIndexAccessEvaluator;
    private final OpenDistroProtectedIndexAccessEvaluator protectedIndexAccessEvaluator;
    private final TermsAggregationEvaluator termsAggregationEvaluator;
    private final DlsFlsEvaluator dlsFlsEvaluator;
    private final boolean advancedModulesEnabled;

    //private final boolean typeSecurityDisabled = false;
    private final ConfigModel configModel;
    private final IndexResolverReplacer irr;
    private final Map<Class<?>, Method> typeCache = Collections.synchronizedMap(new HashMap<Class<?>, Method>(100));
    private final Map<Class<?>, Method> typesCache = Collections.synchronizedMap(new HashMap<Class<?>, Method>(100));

    private static RangerBasePlugin rangerPlugin = null;
    private String rangerUrl = null;
    private boolean initUGI = false;
    private boolean isInitialised = true;

    @Inject
    public RangerPrivilegesEvaluator(final ClusterService clusterService, final ThreadPool threadPool,
                                     final ConfigurationRepository configurationRepository, final ActionGroupHolder ah, final IndexNameExpressionResolver resolver,
                                     AuditLog auditLog, final Settings settings, final PrivilegesInterceptor privilegesInterceptor, final ClusterInfoHolder clusterInfoHolder,
                                     final IndexResolverReplacer irr, boolean advancedModulesEnabled) {

        super(configurationRepository, privilegesInterceptor);
        log.info("### Loaded Privilege Evaluator : RangerPrivilegesEvaluator");
        this.clusterService = clusterService;
        this.resolver = resolver;
        this.auditLog = auditLog;

        this.threadContext = threadPool.getThreadContext();
        this.clusterInfoHolder = clusterInfoHolder;

        //this.typeSecurityDisabled = settings.getAsBoolean(ConfigConstants.OPENDISTRO_SECURITY_DISABLE_TYPE_SECURITY, false);
        configModel = new ConfigModel(ah);
        configurationRepository.subscribeOnChange("roles", configModel);
        configurationRepository.subscribeOnChange("rolesmapping", this);
        this.irr = irr;

        snapshotRestoreEvaluator = new SnapshotRestoreEvaluator(settings, auditLog);
        securityIndexAccessEvaluator = new OpenDistroSecurityIndexAccessEvaluator(settings, auditLog);
        protectedIndexAccessEvaluator = new OpenDistroProtectedIndexAccessEvaluator(settings, auditLog);
        dlsFlsEvaluator = new DlsFlsEvaluator(settings, threadPool);
        termsAggregationEvaluator = new TermsAggregationEvaluator();
        tenantHolder = new TenantHolder();
        this.advancedModulesEnabled = advancedModulesEnabled;

        configurationRepository.subscribeOnChange("roles", tenantHolder);

        String RANGER_ES_PLUGIN_APP_ID = settings.get(ConfigConstants.OPENDISTRO_AUTH_RANGER_APP_ID);

        if (RANGER_ES_PLUGIN_APP_ID == null) {
            isInitialised = false;
            throw new ElasticsearchSecurityException("Open Distro Ranger plugin enabled but appId config not valid");
        }

        log.info("RANGER_ES_PLUGIN_APP_ID: " + RANGER_ES_PLUGIN_APP_ID);

        try {
            if (!initializeUGI(settings)) {
                log.error("UGI not getting initialized.");
            }
        } catch (Exception e) {
            isInitialised = false;
            log.error(e.getCause());
            e.printStackTrace();
        }

        try {
            configureRangerPlugin(settings);
        } catch (Exception e) {
            isInitialised = false;
            log.error(e.getCause());
            e.printStackTrace();
        }

        if (isInitialised) {
            log.info("RangerPrivilegesEvaluator successfully initialized");
        } else {
            log.error("RangerPrivilegesEvaluator initialization failed");
        }

    }

    public void configureRangerPlugin(Settings settings) {
        log.info("configureRangerPlugin");

        String svcType = settings.get(ConfigConstants.OPENDISTRO_AUTH_RANGER_SERVICE_TYPE, "elasticsearch");
        String appId = settings.get(ConfigConstants.OPENDISTRO_AUTH_RANGER_APP_ID);

        log.debug("svcType : " + svcType);
        log.debug("appId : " + appId);

        try {
            RangerBasePlugin me = rangerPlugin;
            if (me == null) {
                synchronized(RangerPrivilegesEvaluator.class) {
                    me = rangerPlugin;
                    if (me == null) {
                        me = rangerPlugin = new RangerBasePlugin(svcType, appId);
                    }
                }
            }
        } catch (Exception e) {
            throw e;
        }

        log.debug("Security manager");
        try{
            SecurityManager sm = System.getSecurityManager();
            if (sm != null) {
                sm.checkPermission(new SpecialPermission());
            }
        } catch (Exception e) {
            throw e;
        }


        log.debug("call doPrivileged");
        AccessController.doPrivileged(new PrivilegedAction() {
            public Object run() {
                ClassLoader cl = org.apache.ranger.authorization.hadoop.config.RangerConfiguration.class.getClassLoader();
                URL[] urls = ((URLClassLoader)cl).getURLs();
                String pluginPath = null;
                for(URL url: urls){
                    String urlFile = url.getFile();
                    //log.info("urlFile : " + urlFile);
                    int idx = urlFile.indexOf("kafka-clients");
                    if (idx != -1) {
                        pluginPath = urlFile.substring(0, idx);
                    }
                }

                try {
                    Method method = URLClassLoader.class.getDeclaredMethod("addURL", new Class[]{URL.class});
                    method.setAccessible(true);
                    String rangerResourcesPath = pluginPath + "resources/";
                    log.debug("method = " + method);
                    method.invoke(cl, new Object[]{new File(rangerResourcesPath).toURI().toURL()});
                } catch (Exception e) {
                    log.error("Error in adding ranger config files to classpath : " + e.getMessage());
                    e.printStackTrace();
                    if (log.isDebugEnabled()) {
                        e.printStackTrace();
                    }
                }

                return null;
            }
        });

        try {
            log.info("ranger init");
            rangerPlugin.init();
            log.debug("ranger init try done");
        } catch (Throwable e) {
            log.error("Caught exception while ranger init. Please investigate: "
                    + e
                    + Arrays.asList(e.getStackTrace())
                    .stream()
                    .map(Objects::toString)
                    .collect(Collectors.joining("\n"))
            );
        }

        log.debug("end doPrivileged");
        this.rangerUrl = RangerConfiguration.getInstance().get("ranger.plugin.elasticsearch.policy.rest.url");
        log.debug("Ranger uri : " + rangerUrl);
        RangerDefaultAuditHandler auditHandler = new RangerDefaultAuditHandler();
        rangerPlugin.setResultProcessor(auditHandler);
    }

    private boolean validateSettings(String keytabPrincipal, String keytabPath, String krbConf, String hadoopHomeDir,
                                     String coreSiteXmlPath, String hdfsSiteXmlPath) {
        if (Strings.isNullOrEmpty(keytabPrincipal)) {
            log.error("Kerberos principal is empty or null");
            return false;
        } else if (Strings.isNullOrEmpty(keytabPath)) {
            log.error("Keytab Path is empty or null");
            return false;
        } else if (Strings.isNullOrEmpty(krbConf)) {
            log.error("krb5 filepath is empty or null");
            return false;
        }  else if (Strings.isNullOrEmpty(coreSiteXmlPath)) {
            log.error("core-site.xml filepath is empty or null");
            return false;
        } else if (Strings.isNullOrEmpty(hdfsSiteXmlPath)) {
            log.error("hdfs-site.xml filepath is empty or null");
            return false;
        } else if (Strings.isNullOrEmpty(hadoopHomeDir)) {
            log.error("hadoop home directory is empty or null");
            return false;
        }
        return true;
    }

    private boolean initializeUGI(Settings settings) throws Exception {
        if (initUGI) {
            return true;
        }

        String keytabPrincipal = settings.get(ConfigConstants.OPENDISTRO_SECURITY_KERBEROS_UGI_KEYTAB_PRINCIPAL);
        String keytabPath = CONFIG_FILES_PATH_PREFIX.concat(settings.get(ConfigConstants.OPENDISTRO_SECURITY_KERBEROS_UGI_KEYTAB_FILEPATH));
        String krbConf = CONFIG_FILES_PATH_PREFIX.concat(settings.get(ConfigConstants.OPENDISTRO_SECURITY_KERBEROS_KRB5_FILEPATH));
        String hadoopHomeDir = settings.get(ConfigConstants.OPENDISTRO_HADOOP_HOME_DIR);
        String coreSiteXmlPath = settings.get(ConfigConstants.OPENDISTRO_HADOOP_CORE_SITE_XML);
        String hdfsSiteXmlPath = settings.get(ConfigConstants.OPENDISTRO_HADOOP_HDFS_SITE_XML);

        log.info("keytabPath : " + keytabPath);
        log.info ("krbConf : " + krbConf);

        if (!validateSettings(keytabPrincipal, keytabPath, krbConf, hadoopHomeDir, coreSiteXmlPath, hdfsSiteXmlPath))
            return false;

        log.debug("validated settings");
        log.debug("hadoop home dir doPrivileged");
        AccessController.doPrivileged(new PrivilegedAction() {
            public Object run() {
                System.setProperty("java.security.krb5.conf", krbConf);
                System.setProperty("kerberos.client.enabled","true");
                System.setProperty("hadoop.home.dir",hadoopHomeDir);
                System.setProperty("sun.security.util.debug enable", "true");
                System.setProperty("sun.security.krb5.debug", "true");
                System.setProperty("hadoop.security.authentication","kerberos");
                //System.setProperty("fs.hdfs.impl", org.apache.hadoop.hdfs.DistributedFileSystem.class.getName());
                try {
                    Config.refresh();
                } catch (KrbException e) {
                    e.printStackTrace();
                    log.debug(e.getCause());
                }
                return null;
            }
        });

        log.debug("hadoop home dir doPrivileged done");

        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new SpecialPermission());
        }

        log.info("getSecurityManager");

            initUGI = AccessController.doPrivileged(new PrivilegedAction<Boolean>() {
                public Boolean run() {
                    try {
                        log.debug("loginUserFromKeytab");
                        org.apache.hadoop.conf.Configuration conf = new  org.apache.hadoop.conf.Configuration();
                        conf.addResource(new Path(coreSiteXmlPath));
                        conf.addResource(new Path(hdfsSiteXmlPath));
                        conf.set("fs.hdfs.impl",org.apache.hadoop.hdfs.DistributedFileSystem.class.getName());
                        conf.set("fs.file.impl", org.apache.hadoop.fs.LocalFileSystem.class.getName());
                        log.info(conf);
                        UserGroupInformation.setConfiguration(conf);
                        UserGroupInformation ugi = UserGroupInformation.loginUserFromKeytabAndReturnUGI(keytabPrincipal, keytabPath);
                        MiscUtil.setUGILoginUser(ugi, null);

                        log.debug("isSecurityEnabled : " + UserGroupInformation.isSecurityEnabled());
                    } catch (Throwable t) {
                        log.error("Caught exception in getting UserGroupInformation. Please investigate: "
                                + t
                                + Arrays.asList(t.getStackTrace())
                                .stream()
                                .map(Objects::toString)
                                .collect(Collectors.joining("\n"))
                        );
                        log.error(t.getCause());
                        log.error("Exception while trying to initialize UGI: " + t.getMessage());
                        return false;
                    }
                    return true;
                }
            });

        log.debug("doPrivileged get UGI done");

        return initUGI;
    }

    private boolean checkRangerAuthorization(final User user, TransportAddress caller, String accessType, Set<String> indices, String clusterLevelAccessType) {
        boolean checkClusterLevelPermission = false;
        Date eventTime = new Date();
        String ipAddress = caller.address().getHostString();
        RangerAccessRequestImpl rangerRequest = new RangerAccessRequestImpl();
        rangerRequest.setUser(user.getName());

        rangerRequest.setClientIPAddress(ipAddress);
        rangerRequest.setAccessTime(eventTime);
        RangerAccessResourceImpl rangerResource = new RangerAccessResourceImpl();
        rangerRequest.setResource(rangerResource);
        rangerRequest.setAccessType(accessType);
        rangerRequest.setAction(accessType);
        rangerRequest.setUserGroups(user.getRoles());

        for (Iterator<String> it = indices.iterator(); it.hasNext();) {
            String index = it.next();
            log.debug("Checking for index: " + index + ", for user: " + user.getName() + " and accessType: " + accessType);
            rangerResource.setValue("index", index);
            RangerAccessResult result = rangerPlugin.isAccessAllowed(rangerRequest);
            if (result == null || !(result.getIsAllowed())) {
                if ((!index.equals("_all")) && (!index.equals("_cluster"))) {
                    checkClusterLevelPermission = true;
                } else {
                    log.debug("Index/Cluster Permission denied");
                    return false;
                }
            }
        }
        if (checkClusterLevelPermission) {
            log.debug("Checking all level permissions (_all), accessType: " + clusterLevelAccessType);
            rangerResource.setValue("index", "_all");
            rangerRequest.setAccessType(clusterLevelAccessType);
            RangerAccessResult result = rangerPlugin.isAccessAllowed(rangerRequest);
            if (result == null || !(result.getIsAllowed())) {
                log.debug("All level Permission denied");
                return false;
            }
        }
        return true;
    }

    private Tuple<Set<String>, Set<String>> resolveIndicesRequest(final User user, final String action, final IndicesRequest request,
                                                                  final MetaData metaData) {

        if (log.isDebugEnabled()) {
            log.debug("Resolve {} from {} for action {}", request.indices(), request.getClass(), action);
        }


        //TODO SG6 disable type security
        //final Boolean has5xIndices = clusterInfoHolder.getHas5xIndices();
        //final boolean fiveXIndicesPresent = has5xIndices == null || has5xIndices == Boolean.TRUE;

        final Class<? extends IndicesRequest> requestClass = request.getClass();
        final Set<String> requestTypes = new HashSet<String>();

        //if(fiveXIndicesPresent && !typeSecurityDisabled) {
        if(true) {
            Method typeMethod = null;
            if(typeCache.containsKey(requestClass)) {
                typeMethod = typeCache.get(requestClass);
            } else {
                try {
                    typeMethod = requestClass.getMethod("type");
                    typeCache.put(requestClass, typeMethod);
                } catch (NoSuchMethodException e) {
                    typeCache.put(requestClass, null);
                } catch (SecurityException e) {
                    log.error("Cannot evaluate type() for {} due to {}", requestClass, e, e);
                }

            }

            Method typesMethod = null;
            if(typesCache.containsKey(requestClass)) {
                typesMethod = typesCache.get(requestClass);
            } else {
                try {
                    typesMethod = requestClass.getMethod("types");
                    typesCache.put(requestClass, typesMethod);
                } catch (NoSuchMethodException e) {
                    typesCache.put(requestClass, null);
                } catch (SecurityException e) {
                    log.error("Cannot evaluate types() for {} due to {}", requestClass, e, e);
                }

            }

            if(typeMethod != null) {
                try {
                    String type = (String) typeMethod.invoke(request);
                    if(type != null) {
                        requestTypes.add(type);
                    }
                } catch (Exception e) {
                    log.error("Unable to invoke type() for {} due to", requestClass, e);
                }
            }

            if(typesMethod != null) {
                try {
                    final String[] types = (String[]) typesMethod.invoke(request);

                    if(types != null) {
                        requestTypes.addAll(Arrays.asList(types));
                    }
                } catch (Exception e) {
                    log.error("Unable to invoke types() for {} due to", requestClass, e);
                }
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("indicesOptions {}", request.indicesOptions());
            log.debug("{} raw indices {}", request.indices()==null?0:request.indices().length, Arrays.toString(request.indices()));
            log.debug("{} requestTypes {}", requestTypes.size(), requestTypes);
        }

        final Set<String> indices = new HashSet<String>();

        if(request.indices() == null || request.indices().length == 0 || new HashSet<String>(Arrays.asList(request.indices())).equals(NULL_SET)) {

            if(log.isDebugEnabled()) {
                log.debug("No indices found in request, assume _all");
            }

            indices.addAll(Arrays.asList(resolver.concreteIndexNames(clusterService.state(), DEFAULT_INDICES_OPTIONS, "*")));

        } else {

            String[] localIndices = request.indices();

            if(request instanceof FieldCapabilitiesRequest || request instanceof SearchRequest) {
                IndicesRequest.Replaceable searchRequest = (IndicesRequest.Replaceable) request;
                final Map<String, OriginalIndices> remoteClusterIndices = OpenDistroSecurityPlugin.GuiceHolder.getRemoteClusterService()
                        .groupIndices(searchRequest.indicesOptions(),searchRequest.indices(), idx -> resolver.hasIndexOrAlias(idx, clusterService.state()));

                if (remoteClusterIndices.size() > 1) {
                    // check permissions?

                    final OriginalIndices originalLocalIndices = remoteClusterIndices.get(RemoteClusterAware.LOCAL_CLUSTER_GROUP_KEY);
                    localIndices = originalLocalIndices.indices();

                    if (log.isDebugEnabled()) {
                        log.debug("remoteClusterIndices keys" + remoteClusterIndices.keySet() + "//remoteClusterIndices "
                                + remoteClusterIndices);
                    }

                    if(localIndices.length == 0) {
                        return new Tuple<Set<String>, Set<String>>(NO_INDICES_SET, requestTypes);
                    }
                }
            }

            try {
                final String[] dateMathIndices;
                if((dateMathIndices = WildcardMatcher.matches("<*>", localIndices, false)).length > 0) {
                    //date math

                    if(log.isDebugEnabled()) {
                        log.debug("Date math indices detected {} (all: {})", dateMathIndices, localIndices);
                    }

                    for(String dateMathIndex: dateMathIndices) {
                        indices.addAll(Arrays.asList(resolver.resolveDateMathExpression(dateMathIndex)));
                    }

                    if(log.isDebugEnabled()) {
                        log.debug("Resolved date math indices {} to {}", dateMathIndices, indices);
                    }

                    if(localIndices.length > dateMathIndices.length) {
                        for(String nonDateMath: localIndices) {
                            if(!WildcardMatcher.match("<*>", nonDateMath)) {
                                indices.addAll(Arrays.asList(resolver.concreteIndexNames(clusterService.state(), request.indicesOptions(), dateMathIndices)));
                            }
                        }

                        if(log.isDebugEnabled()) {
                            log.debug("Resolved additional non date math indices {} to {}", localIndices, indices);
                        }
                    }

                } else {

                    if(log.isDebugEnabled()) {
                        log.debug("No date math indices found");
                    }

                    indices.addAll(Arrays.asList(resolver.concreteIndexNames(clusterService.state(), request.indicesOptions(), localIndices)));
                    if(log.isDebugEnabled()) {
                        log.debug("Resolved {} to {}", localIndices, indices);
                    }
                }
            } catch (final Exception e) {
                log.debug("Cannot resolve {} (due to {}) so we use the raw values", Arrays.toString(localIndices), e);
                indices.addAll(Arrays.asList(localIndices));
            }
        }

        return new Tuple<Set<String>, Set<String>>(indices, requestTypes);
    }

    private Tuple<Set<String>, Set<String>> resolve(final User user, final String action, final TransportRequest request,
                                                    final MetaData metaData) {

        if(request instanceof PutMappingRequest) {

            if (log.isDebugEnabled()) {
                log.debug("PutMappingRequest will be handled in a "
                        + "special way cause they does not return indices via .indices()"
                        + "Instead .getConcreteIndex() must be used");
            }

            PutMappingRequest pmr = (PutMappingRequest) request;
            Index concreteIndex = pmr.getConcreteIndex();

            if(concreteIndex != null && (pmr.indices() == null || pmr.indices().length == 0)) {
                return new Tuple<Set<String>, Set<String>>(Sets.newHashSet(concreteIndex.getName()), Sets.newHashSet(pmr.type()));
            }
        }


        if (!(request instanceof CompositeIndicesRequest)
                && !(request instanceof IndicesRequest)
                && !(request instanceof IndicesAliasesRequest)) {

            if (log.isDebugEnabled()) {
                log.debug("{} is not an IndicesRequest", request.getClass());
            }
            if (action.startsWith("cluster:")) {
                return new Tuple<Set<String>, Set<String>>(Sets.newHashSet("_cluster"), Sets.newHashSet("_all"));
            }
            return new Tuple<Set<String>, Set<String>>(Sets.newHashSet("_all"), Sets.newHashSet("_all"));
        }

        Set<String> indices = new HashSet<String>();
        Set<String> types = new HashSet<String>();

        if (request instanceof IndicesAliasesRequest) {

            for(IndicesAliasesRequest.AliasActions ar: ((IndicesAliasesRequest) request).getAliasActions()) {
                final Tuple<Set<String>, Set<String>> t = resolveIndicesRequest(user, action, ar, metaData);
                indices.addAll(t.v1());
                types.addAll(t.v2());
            }

        } else if (request instanceof CompositeIndicesRequest) {

            if(request instanceof IndicesRequest) { //skip BulkShardRequest?

                final Tuple<Set<String>, Set<String>> t = resolveIndicesRequest(user, action, (IndicesRequest) request, metaData);
                indices.addAll(t.v1());
                types.addAll(t.v2());

            } else if(request instanceof BulkRequest) {

                for(DocWriteRequest<?> ar: ((BulkRequest) request).requests()) {

                    //TODO SG6 require also op type permissions
                    //require also op type permissions
                    //ar.opType()

                    final Tuple<Set<String>, Set<String>> t = resolveIndicesRequest(user, action, (IndicesRequest) ar, metaData);
                    indices.addAll(t.v1());
                    types.addAll(t.v2());
                }

            } else if(request instanceof MultiGetRequest) {

                for(MultiGetRequest.Item item: ((MultiGetRequest) request).getItems()) {
                    final Tuple<Set<String>, Set<String>> t = resolveIndicesRequest(user, action, item, metaData);
                    indices.addAll(t.v1());
                    types.addAll(t.v2());
                }

            } else if(request instanceof MultiSearchRequest) {

                for(ActionRequest ar: ((MultiSearchRequest) request).requests()) {
                    final Tuple<Set<String>, Set<String>> t = resolve(user, action, ar, metaData);
                    indices.addAll(t.v1());
                    types.addAll(t.v2());
                }

            } else if(request instanceof MultiTermVectorsRequest) {

                for(ActionRequest ar: (Iterable<TermVectorsRequest>) () -> ((MultiTermVectorsRequest) request).iterator()) {
                    final Tuple<Set<String>, Set<String>> t = resolve(user, action, ar, metaData);
                    indices.addAll(t.v1());
                    types.addAll(t.v2());
                }


            } else if(request instanceof ReindexRequest) {
                ReindexRequest reindexRequest = (ReindexRequest) request;
                Tuple<Set<String>, Set<String>> t = resolveIndicesRequest(user, action, reindexRequest.getDestination(), metaData);
                indices.addAll(t.v1());
                types.addAll(t.v2());

                t = resolveIndicesRequest(user, action, reindexRequest.getSearchRequest(), metaData);
                indices.addAll(t.v1());
                types.addAll(t.v2());
            } else {
                log.error("Can not handle composite request of type '"+request.getClass().getName()+"'for "+action+" here");
            }

        } else {
            //ccs goes here
            final Tuple<Set<String>, Set<String>> t = resolveIndicesRequest(user, action, (IndicesRequest) request, metaData);
            indices = t.v1();
            types = t.v2();
        }

        if(log.isDebugEnabled()) {
            log.debug("pre final indices: {}", indices);
            log.debug("pre final types: {}", types);
        }

        if(indices == NO_INDICES_SET) {
            return new Tuple<Set<String>, Set<String>>(Collections.emptySet(), Collections.unmodifiableSet(types));
        }

        //for PutIndexTemplateRequest the index does not exists yet typically
        if (IndexNameExpressionResolver.isAllIndices(new ArrayList<String>(indices))) {
            if(log.isDebugEnabled()) {
                log.debug("The following list are '_all' indices: {}", indices);
            }

            //fix https://github.com/floragunncom/search-guard/issues/332
            if(!indices.isEmpty()) {
                indices.clear();
                indices.add("_all");
            }
        }

        if (types.isEmpty()) {
            types.add("_all");
        }

        if(log.isDebugEnabled()) {
            log.debug("final indices: {}", indices);
            log.debug("final types: {}", types);
        }
        return new Tuple<Set<String>, Set<String>>(Collections.unmodifiableSet(indices), Collections.unmodifiableSet(types));
    }

    @Override
    public EvaluatorResponse evaluate(User user, String action, ActionRequest request, Task task) {
        if (!isInitialized()) {
            throw new ElasticsearchSecurityException("Open Distro is not initialized.");
        }

        log.info("user roles : " + user.getRoles());
        log.info("user : " + user);

        if(action.startsWith("internal:indices/admin/upgrade")) {
            action = "indices:admin/upgrade";
        }

        final TransportAddress caller = Objects.requireNonNull((TransportAddress) this.threadContext.getTransient(ConfigConstants.OPENDISTRO_SECURITY_REMOTE_ADDRESS));

        final RangerPrivilegesEvaluatorResponse presponse = new RangerPrivilegesEvaluatorResponse();

        if (log.isDebugEnabled()) {
            log.debug("### evaluate permissions for {} on {}", user, clusterService.localNode().getName());
            log.debug("action: "+action+" ("+request.getClass().getSimpleName()+")");
        }

        if (rangerPlugin == null) {
            log.error("Ranger Plugin not initialized");
            presponse.allowed = false;
            return presponse;
        }

        final IndexResolverReplacer.Resolved requestedResolved = irr.resolveRequest(request);

        if (log.isDebugEnabled()) {
            log.debug("requestedResolved : {}", requestedResolved );
        }

        final Settings config = getConfigSettings();
        log.debug("Action requested: " + action);

        boolean allowAction = false;
        Set<String> indices = new HashSet<String>();
        Set<String> types = new HashSet<String>();

        final ClusterState clusterState = clusterService.state();
        final MetaData metaData = clusterState.metaData();

        if(request instanceof PutMappingRequest) {

            log.debug("PutMappingRequest");

            PutMappingRequest pmr = (PutMappingRequest) request;
            Index concreteIndex = pmr.getConcreteIndex();

            if(concreteIndex != null && (pmr.indices() == null || pmr.indices().length == 0)) {
                String indexName = concreteIndex.getName();

                indices.clear();
                indices.add(indexName);
                allowAction = checkRangerAuthorization(user, caller, ACCESS_TYPE_WRITE, indices, ACCESS_TYPE_WRITE);
                presponse.allowed = allowAction;

                if (!allowAction) {
                    presponse.missingPrivileges.add(String.join(",", indices) + " : " + ACCESS_TYPE_WRITE);
                    log.info("Permission denied for User: " + user.getName() + " Action: " + action + ", required permission : " + ACCESS_TYPE_WRITE + " , indices: " + String.join(",", indices));
                }

                return presponse;
            }
        }

        if (!(request instanceof CompositeIndicesRequest)
                && !(request instanceof IndicesRequest)
                && !(request instanceof IndicesAliasesRequest)) {

            log.debug("Request class is {}", request.getClass());
            //Add code for Ranger - Admin
            indices.clear();
            indices.add("_all");
        } else if (request instanceof IndicesAliasesRequest) {
            log.debug("IndicesAliasesRequest");

            for(IndicesAliasesRequest.AliasActions ar: ((IndicesAliasesRequest) request).getAliasActions()) {
                final Tuple<Set<String>, Set<String>> t = resolveIndicesRequest(user, action, ar, metaData);
                indices.addAll(t.v1());
                types.addAll(t.v2());
            }
            //Add code for Ranger - Admin
            allowAction = checkRangerAuthorization(user, caller, ACCESS_TYPE_ADMIN, indices, ACCESS_TYPE_ADMIN);
            presponse.allowed = allowAction;

            if (!allowAction) {
                presponse.missingPrivileges.add(String.join(",", indices) + " : " + ACCESS_TYPE_ADMIN);
                log.info("Permission denied for User: " + user.getName() + " Action: " + action + ", required permission : " + ACCESS_TYPE_ADMIN + " , indices: " + String.join(",", indices));
            }

            return presponse;

        } else if (request instanceof CompositeIndicesRequest) {
            log.debug("CompositeIndicesRequest");

            if(request instanceof IndicesRequest) {
                log.debug("IndicesRequest");


                final Tuple<Set<String>, Set<String>> t = resolveIndicesRequest(user, action, (IndicesRequest) request, metaData);
                indices.addAll(t.v1());
                types.addAll(t.v2());
                allowAction = checkRangerAuthorization(user, caller, ACCESS_TYPE_READ, indices, ACCESS_TYPE_READ);
                presponse.allowed = allowAction;

                if (!allowAction) {
                    presponse.missingPrivileges.add(String.join(",", indices) + " : " + ACCESS_TYPE_READ);
                    log.info("Permission denied for User: " + user.getName() + " Action: " + action + ", required permission : " + ACCESS_TYPE_READ + " , indices: " + String.join(",", indices));
                }

                return presponse;

            } else if((request instanceof BulkRequest) || (action.equals(BulkAction.NAME)) ) {
                log.debug("BulkRequest");

                for(DocWriteRequest<?> ar: ((BulkRequest) request).requests()) {

                    //TODO SG6 require also op type permissions
                    //require also op type permissions
                    //ar.opType()

                    final Tuple<Set<String>, Set<String>> t = resolveIndicesRequest(user, action, (IndicesRequest) ar, metaData);
                    indices.addAll(t.v1());
                    types.addAll(t.v2());
                    //Add code for Ranger - write

                }
                allowAction = checkRangerAuthorization(user, caller, ACCESS_TYPE_WRITE, indices, ACCESS_TYPE_WRITE);
                presponse.allowed = allowAction;

                if (!allowAction) {
                    presponse.missingPrivileges.add(String.join(",", indices) + " : " + ACCESS_TYPE_WRITE);
                    log.info("Permission denied for User: " + user.getName() + " Action: " + action + ", required permission : " + ACCESS_TYPE_WRITE + " , indices: " + String.join(",", indices));
                }

                return presponse;

            } else if((request instanceof MultiGetRequest) || (action.equals(MultiGetAction.NAME))) {
                log.debug("MultiGetRequest");

                for(MultiGetRequest.Item item: ((MultiGetRequest) request).getItems()) {
                    final Tuple<Set<String>, Set<String>> t = resolveIndicesRequest(user, action, item, metaData);
                    indices.addAll(t.v1());
                    types.addAll(t.v2());
                    //Add code for Ranger - READ
                }
                allowAction = checkRangerAuthorization(user, caller, ACCESS_TYPE_READ, indices, ACCESS_TYPE_READ);
                presponse.allowed = allowAction;

                if (!allowAction) {
                    presponse.missingPrivileges.add(String.join(",", indices) + " : " + ACCESS_TYPE_READ);
                    log.info("Permission denied for User: " + user.getName() + " Action: " + action + ", required permission : " + ACCESS_TYPE_READ + " , indices: " + String.join(",", indices));
                }

                return presponse;

            } else if((request instanceof MultiSearchRequest) || (action.equals(MultiSearchAction.NAME))) {
                log.debug("MultiSearchRequest");

                for(ActionRequest ar: ((MultiSearchRequest) request).requests()) {
                    final Tuple<Set<String>, Set<String>> t = resolve(user, action, ar, metaData);
                    indices.addAll(t.v1());
                    types.addAll(t.v2());
                    //Add code for Ranger - READ
                }
                allowAction = checkRangerAuthorization(user, caller, ACCESS_TYPE_READ, indices, ACCESS_TYPE_READ);
                presponse.allowed = allowAction;

                if (!allowAction) {
                    presponse.missingPrivileges.add(String.join(",", indices) + " : " + ACCESS_TYPE_READ);
                    log.info("Permission denied for User: " + user.getName() + " Action: " + action + ", required permission : " + ACCESS_TYPE_READ + " , indices: " + String.join(",", indices));
                }

                return presponse;

            } else if((request instanceof MultiTermVectorsRequest) || (action.equals(MultiTermVectorsAction.NAME))) {
                log.debug("MultiTermVectorsRequest");

                for(ActionRequest ar: (Iterable<TermVectorsRequest>) () -> ((MultiTermVectorsRequest) request).iterator()) {
                    final Tuple<Set<String>, Set<String>> t = resolve(user, action, ar, metaData);
                    indices.addAll(t.v1());
                    types.addAll(t.v2());
                    //Add code for Ranger - Read
                }
                allowAction = checkRangerAuthorization(user, caller, ACCESS_TYPE_READ, indices, ACCESS_TYPE_READ);
                presponse.allowed = allowAction;

                if (!allowAction) {
                    presponse.missingPrivileges.add(String.join(",", indices) + " : " + ACCESS_TYPE_READ);
                    log.info("Permission denied for User: " + user.getName() + " Action: " + action + ", required permission : " + ACCESS_TYPE_READ + " , indices: " + String.join(",", indices));
                }

                return presponse;

            } else if((request instanceof ReindexRequest) || (action.equals(ReindexAction.NAME))) {
                log.debug("ReindexRequest");

                ReindexRequest reindexRequest = (ReindexRequest) request;
                Tuple<Set<String>, Set<String>> t = resolveIndicesRequest(user, action, reindexRequest.getDestination(), metaData);
                indices.clear();
                indices.addAll(t.v1());
                types.addAll(t.v2());
                allowAction = checkRangerAuthorization(user, caller, ACCESS_TYPE_WRITE, indices, ACCESS_TYPE_WRITE);
                if (!allowAction) {
                    presponse.allowed = allowAction;
                    presponse.missingPrivileges.add(String.join(",", indices) + " : " + ACCESS_TYPE_WRITE);
                    log.info("Permission denied for User: " + user.getName() + " Action: " + action + ", required permission : " + ACCESS_TYPE_WRITE + " , indices: " + String.join(",", indices));
                    return presponse;
                }

                t = resolveIndicesRequest(user, action, reindexRequest.getSearchRequest(), metaData);
                indices.clear();
                indices.addAll(t.v1());
                types.addAll(t.v2());
                allowAction = checkRangerAuthorization(user, caller, ACCESS_TYPE_READ, indices, ACCESS_TYPE_READ);
                presponse.allowed = allowAction;

                if (!allowAction) {
                    presponse.missingPrivileges.add(String.join(",", indices) + " : " + ACCESS_TYPE_READ);
                    log.info("Permission denied for User: " + user.getName() + " Action: " + action + ", required permission : " + ACCESS_TYPE_READ + " , indices: " + String.join(",", indices));
                }

                return presponse;
            } else {
                log.debug("Can not handle request of type '"+request.getClass().getName()+"'for "+action+" here");
            }

        } else {
            //ccs goes here
            final Tuple<Set<String>, Set<String>> t = resolveIndicesRequest(user, action, (IndicesRequest) request, metaData);
            indices = t.v1();
            types = t.v2();
        }

        log.debug("Action requested: " + action + " , indices: " + String.join(",", indices));
        if (action.startsWith("cluster:monitor/") || action.startsWith("indices:monitor/")) {
            allowAction = checkRangerAuthorization(user, caller, ACCESS_TYPE_MONITOR, indices, ACCESS_TYPE_MONITOR);
            if (!allowAction) {
                presponse.missingPrivileges.add(String.join(",", indices) + " : " + ACCESS_TYPE_MONITOR);
                log.info("Permission denied for User: " + user.getName() + " Action: " + action + ", required permission : " + ACCESS_TYPE_MONITOR + " , indices: " + String.join(",", indices));
            }
        } else if (action.startsWith("indices:admin/create")
                || (action.startsWith("indices:admin/mapping/put"))) {
            allowAction = checkRangerAuthorization(user, caller, ACCESS_TYPE_WRITE, indices, ACCESS_TYPE_WRITE);
            if (!allowAction) {
                presponse.missingPrivileges.add(String.join(",", indices) + " : " + ACCESS_TYPE_WRITE);
                log.info("Permission denied for User: " + user.getName() + " Action: " + action + ", required permission : " + ACCESS_TYPE_WRITE + " , indices: " + String.join(",", indices));
            }
        } else if ((action.startsWith("indices:data/read"))
                || (action.startsWith("indices:admin/template/get"))
                || (action.startsWith("indices:admin/mapping/get"))
                || (action.startsWith("indices:admin/mappings/get"))
                || (action.startsWith("indices:admin/mappings/fields/get"))
                || (action.startsWith("indices:admin/aliases/exists"))
                || (action.startsWith("indices:admin/aliases/get"))
                || (action.startsWith("indices:admin/exists"))
                || (action.startsWith("indices:admin/validate/query"))
                || (action.startsWith("indices:admin/get"))){
            //Add code for Ranger - Read
            allowAction = checkRangerAuthorization(user, caller, ACCESS_TYPE_READ, indices, ACCESS_TYPE_READ);
            if (!allowAction) {
                presponse.missingPrivileges.add(String.join(",", indices) + " : " + ACCESS_TYPE_READ);
                log.info("Permission denied for User: " + user.getName() + " Action: " + action + ", required permission : " + ACCESS_TYPE_READ + " , indices: " + String.join(",", indices));
            }

        } else if (action.startsWith("indices:data/write")
                || (action.startsWith("indices:data/"))) {
            allowAction = checkRangerAuthorization(user, caller, ACCESS_TYPE_WRITE, indices, ACCESS_TYPE_WRITE);
            if (!allowAction) {
                presponse.missingPrivileges.add(String.join(",", indices) + " : " + ACCESS_TYPE_WRITE);
                log.info("Permission denied for User: " + user.getName() + " Action: " + action + ", required permission : " + ACCESS_TYPE_WRITE + " , indices: " + String.join(",", indices));
            }
        } else if (action.startsWith("indices:")) {
            log.debug("All remaining unknown actions with indices:");
            allowAction = checkRangerAuthorization(user, caller, ACCESS_TYPE_ADMIN, indices, ACCESS_TYPE_ADMIN);
            if (!allowAction) {
                presponse.missingPrivileges.add(String.join(",", indices) + " : " + ACCESS_TYPE_ADMIN);
                log.info("Permission denied for User: " + user.getName() + " Action: " + action + ", required permission : " + ACCESS_TYPE_ADMIN + " , indices: " + String.join(",", indices));
            }
        } else {
            log.debug("All remaining unknown actions");
            indices.clear();
            indices.add("_cluster");
            allowAction = checkRangerAuthorization(user, caller, ACCESS_TYPE_ADMIN, indices, ACCESS_TYPE_ADMIN);
            if (!allowAction) {
                presponse.missingPrivileges.add(String.join(",", indices) + " : " + ACCESS_TYPE_ADMIN);
                log.info("Permission denied for User: " + user.getName() + " Action: " + action + ", required permission : " + ACCESS_TYPE_ADMIN + " , indices: " + String.join(",", indices));
            }
        }

        presponse.allowed = allowAction;
        return presponse;

    }

    @Override
    public boolean isInitialized() {
        return isInitialised;
    }
}
