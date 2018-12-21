package com.gauvus.ranger.services.client;

import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.RequestLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.apache.ranger.plugin.client.BaseClient;
import org.apache.ranger.plugin.service.ResourceLookupContext;
import org.apache.ranger.plugin.util.TimedEventUtil;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;

import com.google.common.base.Strings;
import com.kerb4j.client.SpnegoClient;
import com.kerb4j.client.SpnegoContext;

public class ServiceElasticSearchClient {
	private static final Logger LOG = Logger.getLogger(ServiceElasticSearchClient.class);

	enum RESOURCE_TYPE {
		INDEX
	}

	String serviceName = null;
	String esUrl = null;
    private SpnegoClient spnegoClient = null;
    private String esSPN = null;
    private String username = null;
    private String password = null;

	private static final String errMessage = " You can still save the repository and start creating "
			+ "policies, but you would not be able to use autocomplete for "
			+ "resource names. Check server logs for more info.";

	private static final String INDEX_KEY = "index";
	private static final long LOOKUP_TIMEOUT_SEC = 5;
	private static final String KRB5_DEBUG = "false";
	private static final String KRB5_CONF = "/etc/krb5.conf";

	public ServiceElasticSearchClient(String serviceName, Map<String, String> configs) {
		this.serviceName = serviceName;
		this.esUrl = configs.get("es.url");
		this.esSPN = configs.get("es.spn");
		this.username = configs.get("username");
		this.password = configs.get("password");
        
		String princName = configs.get("principal");
        String keytabPath = configs.get("keytab");
        
		if ((!Strings.isNullOrEmpty(this.esSPN)) && (!Strings.isNullOrEmpty(princName)) 
		        && (!Strings.isNullOrEmpty(keytabPath))) {
		    this.spnegoClient = SpnegoClient.loginWithKeyTab(princName, keytabPath);
		    AccessController.doPrivileged(new PrivilegedAction() {
		        public Object run() {
		            System.setProperty("sun.security.krb5.debug", KRB5_DEBUG);
		            System.setProperty("java.security.krb5.conf", KRB5_CONF);
		            return null;
		        }
		    });
		}
	}

	public HashMap<String, Object> connectionTest() throws Exception {
		String errMsg = errMessage;
		HashMap<String, Object> responseData = new HashMap<String, Object>();
		try {
			getIndexList(null);
			// If it doesn't throw exception, then assume the instance is
			// reachable
			String successMsg = "ConnectionTest Successful";
			BaseClient.generateResponseDataMap(true, successMsg,
					successMsg, null, null, responseData);
		} catch (IOException e) {
			LOG.error("Error connecting to Elastic Search. elasticSearch Client=" + this, e);
			String failureMsg = "Unable to connect to Elastic Search instance."
					+ e.getMessage();
			BaseClient.generateResponseDataMap(false, failureMsg,
					failureMsg + errMsg, null, null, responseData);
		}
		return responseData;
	}

	private List<String> getIndexList(List<String> ignoreIndexList) throws Exception {
		List<String> ret = new ArrayList<String>();
		RestClient lowLevelClient = getRestClient("https");
        Response response = null;
        
		try {
			response = lowLevelClient.performRequest("GET", "/_cat/indices");
		} catch (IOException ie) {
		    LOG.info("Trying http scheme");
		    try {
		        lowLevelClient.close();
		    } catch (Exception e) {}
		    
			try {
			    lowLevelClient = getRestClient("http");
			    response = lowLevelClient.performRequest("GET", "/_cat/indices");
			} catch (IOException ioe) {ioe.printStackTrace();}            
		}
		finally {
		    try {
			    lowLevelClient.close();
		    } catch (Exception e) {}
		}
			
		int statusCode = response.getStatusLine().getStatusCode();
		if(statusCode == HttpStatus.SC_OK) {
			String responseBody = EntityUtils.toString(response.getEntity());
			String[] rows = responseBody.split("\n");
			for(String row : rows) {
			    ret.add(row.split(" ")[2]);
			}
		} else {
				//TODO throw exception
			throw new Exception("Status Code => " + statusCode);
	    }
		    
		if (ignoreIndexList == null || ignoreIndexList.isEmpty()) {
		    ret.add("_all");
		    ret.add("_cluster");
		}
		return ret;
	}

	/**
	 * @param serviceName
	 * @param context
	 * @return
	 */
	public List<String> getResources(ResourceLookupContext context) {

		String userInput = context.getUserInput();
		String resource = context.getResourceName();
		Map<String, List<String>> resourceMap = context.getResources();
		List<String> resultList = null;
		List<String> topicList = null;

		RESOURCE_TYPE lookupResource = RESOURCE_TYPE.INDEX;

		if (LOG.isDebugEnabled()) {
			LOG.debug("<== getResources()  UserInput: \"" + userInput
					+ "\" resource : " + resource + " resourceMap: "
					+ resourceMap);
		}

		if (userInput != null && resource != null) {
			if (resourceMap != null && !resourceMap.isEmpty()) {
				topicList = resourceMap.get(INDEX_KEY);
			}
			switch (resource.trim().toLowerCase()) {
			case INDEX_KEY:
				lookupResource = RESOURCE_TYPE.INDEX;
				break;
			default:
				break;
			}
		}

		if (userInput != null) {
			try {
				Callable<List<String>> callableObj = null;
				final String userInputFinal = userInput;

				final List<String> finalTopicList = topicList;

				if (lookupResource == RESOURCE_TYPE.INDEX) {
					// get the topic list for given Input
					callableObj = new Callable<List<String>>() {
						@Override
						public List<String> call() {
							List<String> retList = new ArrayList<String>();
							try {
								List<String> list = getIndexList(finalTopicList);
								if (userInputFinal != null
										&& !userInputFinal.isEmpty()) {
									for (String value : list) {
										if (value.startsWith(userInputFinal)) {
											retList.add(value);
										}
									}
								} else {
									retList.addAll(list);
								}
							} catch (Exception ex) {
								LOG.error("Error getting topic.", ex);
							}
							return retList;
						};
					};
				}
				// If we need to do lookup
				if (callableObj != null) {
					synchronized (this) {
						resultList = TimedEventUtil.timedTask(callableObj,
								LOOKUP_TIMEOUT_SEC, TimeUnit.SECONDS);
					}
				}
			} catch (Exception e) {
				LOG.error("Unable to get es resources.", e);
			}
		}

		return resultList;
	}

	@Override
	public String toString() {
		return "ServiceElasticSearchClient [serviceName=" + serviceName
				+ ", esUrl=" + esUrl + "]";
	}

	private RestClient getRestClient(String scheme) {
	    RestClient lowLevelClient = null;
	    if (this.spnegoClient != null) {
	        try {
	            SpnegoContext ctx = spnegoClient.createContextForSPN(this.esSPN);
	            String authHeader = ctx.createTokenAsAuthroizationHeader();
	            Header[] headers = {
                    new BasicHeader("Authorization", authHeader)
	            };
            
	            lowLevelClient = RestClient.builder(
                    new HttpHost(esUrl.split(":")[0], Integer.parseInt(esUrl.split(":")[1]), scheme))
                    .setDefaultHeaders(headers)
                    .setHttpClientConfigCallback(new RestClientBuilder.HttpClientConfigCallback() {
                        @Override
                        public HttpAsyncClientBuilder customizeHttpClient(HttpAsyncClientBuilder httpClientBuilder) {
                            return httpClientBuilder;
                        }
                    })
                    .build();
	        } catch (Exception e) {
	            if (LOG.isDebugEnabled()) {
	                e.printStackTrace();
	            }
	            LOG.error("Exception in Spnego authentication: " + e.getMessage());
	            return null;
	        }
        } else {
            final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY,
                    new UsernamePasswordCredentials(username, password));
        
            lowLevelClient = RestClient.builder(
                    new HttpHost(esUrl.split(":")[0], Integer.parseInt(esUrl.split(":")[1]), scheme))
                    .setHttpClientConfigCallback(new RestClientBuilder.HttpClientConfigCallback() {
                        @Override
                        public HttpAsyncClientBuilder customizeHttpClient(HttpAsyncClientBuilder httpClientBuilder) {
                            return httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                        }
                    })
                    .build();
        }
	    return lowLevelClient;
	}
	
	public static void main (String args[]) {
	    Map<String, String> configs = new HashMap<String,String>();
	    configs.put("es.url", args[0]);
	    configs.put("username", args[1]);
	    configs.put("password", args[2]);

	    ServiceElasticSearchClient serv = new ServiceElasticSearchClient("elasticsearch", configs);
	    try {
	        HashMap<String, Object> res = serv.connectionTest();
	        Set<String> keys = res.keySet();
	        Iterator<String> iter = keys.iterator();
	        while (iter.hasNext()) {
	            String key = iter.next();
	            Object val = res.get(key);
	            System.out.println("Key = " + key);
	            if (val != null) {
	                System.out.println("Val = " + val.toString());    
	            }
	        }
	        System.out.println();
	    } catch (Exception e) {
	        e.printStackTrace();
	    }
	}
}
