package com.gauvus.ranger.services.client;

import java.util.HashMap;
import java.util.Map;

import com.google.common.base.Strings;

public class ServiceElasticSearchConnectionMgr {

	static public ServiceElasticSearchClient getElasticSearchClient(String serviceName,
			Map<String, String> configs) throws Exception {
		String esUrl = configs.get("es.url");
		
	    if (esUrl == null) {
	        throw new Exception("Required properties are not set for "
	                + serviceName + ". URL information not provided.");
	    }

	    if (
	            ((Strings.isNullOrEmpty(configs.get("es.spn"))) ||
	             (Strings.isNullOrEmpty(configs.get("principal"))) ||
	             (Strings.isNullOrEmpty(configs.get("keytab"))))
	            &&
	            ((Strings.isNullOrEmpty(configs.get("username"))) ||
	             (Strings.isNullOrEmpty(configs.get("password"))))
	       ) {
	                
	        throw new Exception("Required properties are not set for "
                    + serviceName + ". Either Kerberos (spn, principal, keytab) "
                    + "or Simple auth related properties (username, password) should be provided");
        }
	
		ServiceElasticSearchClient serviceElasticSearchClient = new ServiceElasticSearchClient(
					serviceName, configs);
		return serviceElasticSearchClient;
	}

	/**
	 * @param serviceName
	 * @param configs
	 * @return
	 */
	public static HashMap<String, Object> connectionTest(String serviceName,
			Map<String, String> configs) throws Exception {
		ServiceElasticSearchClient serviceElasticSearchClient = getElasticSearchClient(serviceName,
				configs);
		return serviceElasticSearchClient.connectionTest();
	}

}
