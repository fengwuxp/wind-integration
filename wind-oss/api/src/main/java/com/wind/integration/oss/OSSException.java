package com.wind.integration.oss;

import com.wind.common.exception.BaseException;

public class OSSException extends BaseException {

    private final String reqeustId;

    public OSSException(String message, String reqeustId) {
        super(message);
        this.reqeustId = reqeustId;
    }
}
