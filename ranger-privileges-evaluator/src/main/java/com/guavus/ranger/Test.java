//package com.guavus.ranger;
//
//import com.google.common.base.Strings;
//import org.apache.hadoop.fs.Path;
//import org.apache.hadoop.security.UserGroupInformation;
//import org.apache.logging.log4j.LogManager;
//import org.apache.logging.log4j.Logger;
//import org.apache.ranger.audit.provider.MiscUtil;
//import org.apache.ranger.authorization.hadoop.config.RangerConfiguration;
//import org.apache.ranger.plugin.audit.RangerDefaultAuditHandler;
//import org.apache.ranger.plugin.service.RangerBasePlugin;
//import org.elasticsearch.SpecialPermission;
//import org.elasticsearch.secure_sm.SecureSM;
//import sun.security.krb5.Config;
//import sun.security.krb5.KrbException;
//
//import java.io.File;
//import java.lang.reflect.Method;
//import java.net.URL;
//import java.net.URLClassLoader;
//import java.security.AccessController;
//import java.security.PrivilegedAction;
//import java.util.Arrays;
//import java.util.Objects;
//import java.util.concurrent.TimeUnit;
//import java.util.stream.Collectors;
//
///**
// * @author Divyansh Jain
// */
//
//public class Test {
//
//    protected final static Logger logger = LogManager.getLogger(Test.class); // remove
//    private static RangerBasePlugin rangerPlugin = null;
//
//    public static void main(String[] args) throws Exception {
////        initializeUGII("HTTP/rafd002-slv-02.cloud.in.guavus.com@GVS.GGN",
////                "/etc/elasticsearch/spnego.service.keytab",
////                "/etc/elasticsearch/krb5.conf", args[2]);
//
//        initializeUGII(args[0], args[1], args[2], args[3]);
//
//        configureRangerPlugin(args[4], args[5]);
//
//    }
//
//    private static boolean initializeUGII(String svcName, String keytabPath, String krbConf, String securityManager) throws Exception {
//
////        String svcName = settings.get(ConfigConstants.OPENDISTRO_KERBEROS_ACCEPTOR_PRINCIPAL);
////        String keytabPath = settings.get(ConfigConstants.OPENDISTRO_KERBEROS_ACCEPTOR_KEYTAB_FILEPATH,
////                HTTPSpnegoAuthenticator.SERVER_KEYTAB_PATH);
////        String krbConf = settings.get(ConfigConstants.OPENDISTRO_KERBEROS_KRB5_FILEPATH,
////                HTTPSpnegoAuthenticator.KRB5_CONF);
//
//        if (Strings.isNullOrEmpty(svcName)) {
//            logger.error("Acceptor kerberos principal is empty or null");
//            return false;
//        } else if (Strings.isNullOrEmpty(keytabPath)) {
//            logger.error("Keytab Path is empty or null");
//            return false;
//        } else if (Strings.isNullOrEmpty(krbConf)) {
//            logger.error("krb5 filepath is empty or null");
//            return false;
//        }
//
//        if (securityManager.equals("true")) {
//            System.setSecurityManager(new SecureSM());
//        }
//
//        logger.error("hadoop home dir doPrivileged");
//        AccessController.doPrivileged(new PrivilegedAction() {
//            public Object run() {
//                System.setProperty("java.security.krb5.conf", krbConf);
//                System.setProperty("kerberos.client.enabled","true");
//                System.setProperty("hadoop.home.dir","/usr/hdp/3.1.4.0-315/hadoop/");
//                System.setProperty("sun.security.util.error enable", "true");
//                System.setProperty("sun.security.krb5.error", "true");
//                System.setProperty("hadoop.security.authentication","kerberos");
//                //System.setProperty("java.security.policy", "java.policy");
//                try {
//                    Config.refresh();
//                } catch (KrbException e) {
//                    e.printStackTrace();
//                    logger.error(e.getCause());
//                }
//                return null;
//            }
//        });
//
//        logger.error("hadoop home dir doPrivileged done");
//
//
//        logger.error("svcName : " + svcName);
//        logger.error("keytabPath : " + keytabPath);
//        logger.error("krbConf : " + krbConf);
//
////        SpnegoClient spnegoClient = AccessController.doPrivileged(new PrivilegedAction<SpnegoClient>() {
////            @Override
////            public SpnegoClient run() {
////                try {
////                    log.error("kinit");
////                    HTTPSpnegoAuthenticator.initSpnegoClient(svcName, keytabPath, krbConf);
////                    log.error("kinit done");
////                    SpnegoClient spnegoClient = HTTPSpnegoAuthenticator.getSpnegoClient();
////                    log.error("getSpnegoClient done");
////                    return spnegoClient;
////                } catch (Throwable e) {
////                    e.printStackTrace();
////                }
////                return null;
////            }
////        });
//
////        if (spnegoClient == null) {
////            log.error("Spnego client not initialized");
////            return false;
////        }
//
//        SecurityManager sm = System.getSecurityManager();
//        if (sm != null) {
//            sm.checkPermission(new SpecialPermission());
//        }
//
//        logger.error("getSecurityManager");
//
//        boolean initUGI = false;
//
//        try {
//            initUGI = AccessController.doPrivileged(new PrivilegedAction<Boolean>() {
//                public Boolean run() {
//                    logger.error("in run");
//                    //log.error("spnego client subject: " + spnegoClient.getSubject());
//                    //Subject subject = spnegoClient.getSubject();
//                    //log.error("getSubject : " + subject);
//                    try {
//                        logger.error("loginUserFromKeytab");
//                        //UserGrou.errorrmation.loginUserFromKeytab(svcName, keytabPath);
//                        org.apache.hadoop.conf.Configuration conf = new  org.apache.hadoop.conf.Configuration();
//                        conf.addResource(new Path("file:///usr/hdp/3.1.4.0-315/hadoop/conf/core-site.xml"));
//                        conf.addResource(new Path("file:///usr/hdp/3.1.4.0-315/hadoop/conf/hdfs-site.xml"));
//                        logger.error(conf);
//                        UserGroupInformation.setConfiguration(conf);
//                        UserGroupInformation ugi = UserGroupInformation.loginUserFromKeytabAndReturnUGI(svcName, keytabPath);
//                        MiscUtil.setUGILoginUser(ugi, null);
//
//                        logger.error("getLoginUser");
//                        logger.error("isSecurityEnabled : " + UserGroupInformation.isSecurityEnabled());
//                        logger.error("getLoginUser done");
//
//                        // UserGrou.errorrmation ugi = MiscUtil.createUGIFromSubject(subject);
//
////                        if (ugi != null) {
////                            //MiscUtil.setUGILoginUser(ugi, subject);
////                            log.error("setUGILoginUser");
////                        } else {
////                            log.error("Unable to initialize UGI");
////                            return false;
////                        }
//                    } catch (Throwable t) {
//                        logger.error("Caught exception in UserGroupInformation. Please investigate: "
//                                + t
//                                + Arrays.asList(t.getStackTrace())
//                                .stream()
//                                .map(Objects::toString)
//                                .collect(Collectors.joining("\n"))
//                        );
//                        logger.error(t.getCause());
//                        logger.error("Exception while trying to initialize UGI: " + t.getMessage());
//                        return false;
//                    }
//                    return true;
//                }
//            });
//        } catch (Throwable e){
//            logger.error("Caught exception in initializeUGI. Please investigate: "
//                    + e
//                    + Arrays.asList(e.getStackTrace())
//                    .stream()
//                    .map(Objects::toString)
//                    .collect(Collectors.joining("\n"))
//            );
//            logger.error(e.getCause());
//            logger.error(e.getMessage());
//        }
//
//        logger.error("doPrivileged");
//
//        return initUGI;
//    }
//
//    public static void configureRangerPlugin(String svcType, String appId) throws InterruptedException {
//        logger.error("configureRangerPlugin");
//
//        //String svcType = "elasticsearch";
//        //String appId = "rafd002_elasticsearch";
//
//        logger.error("svcType : " + svcType);
//        logger.error("appId : " + appId);
//
//        try {
//            RangerBasePlugin me = rangerPlugin;
//            if (me == null) {
//                synchronized(Test.class) {
//                    me = rangerPlugin;
//                    if (me == null) {
//                        me = rangerPlugin = new RangerBasePlugin(svcType, appId);
//                    }
//                }
//            }
//        } catch (Exception e) {
//            throw e;
//        }
//
//        logger.error("Calling ranger plugin init");
//        logger.error("Security manager");
//        try{
//            SecurityManager sm = System.getSecurityManager();
//            if (sm != null) {
//                sm.checkPermission(new SpecialPermission());
//            }
//        } catch (Exception e) {
//            throw e;
//        }
//
//
//        logger.error("call doPrivileged");
//        AccessController.doPrivileged(new PrivilegedAction() {
//            public Object run() {
//                ClassLoader cl = org.apache.ranger.authorization.hadoop.config.RangerConfiguration.class.getClassLoader();
//                URL[] urls = ((URLClassLoader)cl).getURLs();
//                String pluginPath = null;
//                for(URL url: urls){
//                    String urlFile = url.getFile();
//                    int idx = urlFile.indexOf("ranger-plugins-common");
//                    if (idx != -1) {
//                        pluginPath = urlFile.substring(0, idx);
//                    }
//                }
//
//                logger.error("pluginpath: " + pluginPath);
//
//                try {
//                    Method method = URLClassLoader.class.getDeclaredMethod("addURL", new Class[]{URL.class});
//                    method.setAccessible(true);
//                    String rangerResourcesPath = pluginPath + "resources/";
//                    logger.error(rangerResourcesPath);
//                    logger.error("method = " + method);
//                    method.invoke(cl, new Object[]{new File(rangerResourcesPath).toURI().toURL()});
//                } catch (Exception e) {
//                    logger.error("Error in adding ranger config files to classpath : " + e.getMessage());
//                    e.printStackTrace();
//                }
//
//                return null;
//            }
//        });
//
//        try {
//            logger.error("ranger init try");
//            rangerPlugin.init();
//            logger.error("ranger init try done");
//        } catch (Throwable e) {
//            logger.error("Caught exception while methodX. Please investigate: "
//                    + e
//                    + Arrays.asList(e.getStackTrace())
//                    .stream()
//                    .map(Objects::toString)
//                    .collect(Collectors.joining("\n"))
//            );
//        }
//
//        logger.error("end doPrivileged");
//        String rangerUrl = RangerConfiguration.getInstance().get("ranger.plugin.elasticsearch.policy.rest.url");
//        logger.error("Ranger uri : " + rangerUrl);
//        RangerDefaultAuditHandler auditHandler = new RangerDefaultAuditHandler();
//        rangerPlugin.setResultProcessor(auditHandler);
//        logger.error("sleep for 1 min");
//        TimeUnit.MINUTES.sleep(1);
//        logger.error("wake");
//    }
//}
