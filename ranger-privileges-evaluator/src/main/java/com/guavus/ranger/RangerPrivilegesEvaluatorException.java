package com.guavus.ranger;

import org.elasticsearch.ElasticsearchSecurityException;

public class RangerPrivilegesEvaluatorException extends ElasticsearchSecurityException {
    public RangerPrivilegesEvaluatorException(String errorMessage) {
        super(errorMessage);
    }

    public RangerPrivilegesEvaluatorException(String errorMessage, Throwable err) {
        super(errorMessage , err);
    }
}
