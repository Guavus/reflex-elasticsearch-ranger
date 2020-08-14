package com.guavus.tagsync.source.atlas;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.ranger.tagsync.source.atlas.AtlasResourceMapper;
import org.apache.ranger.plugin.model.RangerPolicy;
import org.apache.ranger.plugin.model.RangerPolicy.RangerPolicyResource;
import org.apache.ranger.plugin.model.RangerServiceResource;
import org.apache.ranger.tagsync.source.atlasrest.RangerAtlasEntity;

/*
 * Provides mapping of Atlas ES entity to Ranger ES entity for Tag based policies

 * @author rajat.goel
 */

public class AtlasESResourceMapper extends AtlasResourceMapper {
	public static final String ENTITY_TYPE_ES_INDEX = "es_index";
	public static final String RANGER_TYPE_ES_INDEX = "index";

	public static final String ENTITY_ATTRIBUTE_QUALIFIED_NAME = "qualifiedName";

	public static final String[] SUPPORTED_ENTITY_TYPES = { ENTITY_TYPE_ES_INDEX };

	public AtlasESResourceMapper() {
		super("elasticsearch", SUPPORTED_ENTITY_TYPES);
	}

	@Override
	public RangerServiceResource buildResource(final RangerAtlasEntity entity) throws Exception {
		String entityGuid    = entity.getGuid() != null ? entity.getGuid() : null;
		String qualifiedName = (String)entity.getAttributes().get(AtlasResourceMapper.ENTITY_ATTRIBUTE_QUALIFIED_NAME);

		return getServiceResource(entityGuid, qualifiedName);
	}

	private RangerServiceResource getServiceResource(String entityGuid, String qualifiedName) throws Exception {
		String index = getResourceNameFromQualifiedName(qualifiedName);

		if(StringUtils.isEmpty(index)) {
			throwExceptionWithMessage("index not found in attribute '" + ENTITY_ATTRIBUTE_QUALIFIED_NAME +  "'");
		}

		String clusterName = getClusterNameFromQualifiedName(qualifiedName);

		if(StringUtils.isEmpty(clusterName)) {
			clusterName = defaultClusterName;
		}

		if(StringUtils.isEmpty(clusterName)) {
			throwExceptionWithMessage("attribute '" + ENTITY_ATTRIBUTE_QUALIFIED_NAME +  "' not found in entity");
		}


		Map<String, RangerPolicyResource> elements = new HashMap<String, RangerPolicy.RangerPolicyResource>();
		Boolean isExcludes  = Boolean.FALSE;
		Boolean isRecursive = Boolean.FALSE;

		elements.put(RANGER_TYPE_ES_INDEX, new RangerPolicyResource(index, isExcludes, isRecursive));

		String  serviceName = getRangerServiceName(clusterName);

		return new RangerServiceResource(entityGuid, serviceName, elements);
	}
}
