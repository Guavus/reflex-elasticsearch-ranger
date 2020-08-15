package com.guavus.ranger;

import org.elasticsearch.ElasticsearchException;

public class RangerPrivilegesEvaluatorException extends ElasticsearchException {
    public RangerPrivilegesEvaluatorException(String errorMessage) {
        super(errorMessage);
    }

    public RangerPrivilegesEvaluatorException(String errorMessage, Throwable err) {
        super(errorMessage , err);
    }
}
