/*
 * Copyright 2020 ThalesGroup
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.guavus.ranger.services;

import java.util.HashMap;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ranger.plugin.model.RangerService;
import org.apache.ranger.plugin.model.RangerServiceDef;
import org.apache.ranger.plugin.service.RangerBaseService;
import org.apache.ranger.plugin.service.ResourceLookupContext;

import com.guavus.ranger.services.client.ServiceElasticSearchClient;
import com.guavus.ranger.services.client.ServiceElasticSearchConnectionMgr;

public class RangerElasticSearchService extends RangerBaseService{
	private static final Log LOG = LogFactory.getLog(RangerElasticSearchService.class);
	
	@Override
	public void init(RangerServiceDef serviceDef, RangerService service) {
		super.init(serviceDef, service);
	}
	
	
	@Override
	public List<String> lookupResource(ResourceLookupContext context)
			throws Exception {
		List<String> ret = null;

		if (LOG.isDebugEnabled()) {
			LOG.debug("==> RangerElasticSearchService.lookupResource(" + serviceName + ")");
		}

		if (configs != null) {
			ServiceElasticSearchClient serviceElasticSearchClient = ServiceElasticSearchConnectionMgr.getElasticSearchClient(serviceName, configs);

			ret = serviceElasticSearchClient.getResources(context);
		}

		if (LOG.isDebugEnabled()) {
			LOG.debug("<== RangerElasticSearchService.lookupResource(" + serviceName + "): ret=" + ret);
		}

		return ret;
	}

	@Override
	public HashMap<String, Object> validateConfig() throws Exception {
		HashMap<String, Object> ret = new HashMap<String, Object>();

		if (LOG.isDebugEnabled()) {
			LOG.debug("==> RangerElasticSearchService.validateConfig(" + serviceName + ")");
		}

		if (configs != null) {
			try {
				ret = ServiceElasticSearchConnectionMgr.connectionTest(serviceName, configs);
			} catch (Exception e) {
				LOG.error("<== RangerElasticSearchService.validateConfig Error:" + e);
				throw e;
			}
		}

		if (LOG.isDebugEnabled()) {
			LOG.debug("<== RangerElasticSearchService.validateConfig(" + serviceName + "): ret=" + ret);
		}

		return ret;
	}

}
