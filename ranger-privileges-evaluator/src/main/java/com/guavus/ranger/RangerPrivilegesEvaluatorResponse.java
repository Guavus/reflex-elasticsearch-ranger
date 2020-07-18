package com.guavus.ranger;

import com.amazon.opendistroforelasticsearch.security.privileges.EvaluatorResponse;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Divyansh Jain
 */

public class RangerPrivilegesEvaluatorResponse implements EvaluatorResponse {

    boolean allowed = false;
    Set<String> missingPrivileges = new HashSet<String>();
    Map<String,Set<String>> allowedFlsFields;
    Map<String,Set<String>> maskedFields;
    Map<String,Set<String>> queries;

    RangerPrivilegesEvaluatorResponse() {}

    @Override
    public boolean isAllowed() {
        return allowed;
    }

    @Override
    public Map<String, Set<String>> getAllowedFlsFields() {
        return allowedFlsFields;
    }

    @Override
    public Map<String, Set<String>> getMaskedFields() {
        return maskedFields;
    }

    @Override
    public Map<String, Set<String>> getQueries() {
        return null;
    }

    @Override
    public Set<String> getMissingPrivileges() {
        return new HashSet<String>(missingPrivileges);
    }
}
