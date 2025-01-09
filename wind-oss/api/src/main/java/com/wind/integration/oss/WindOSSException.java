package com.wind.integration.oss;

import com.wind.common.exception.BaseException;
import lombok.Getter;

/**
 * oss client 异常
 */
@Getter
public class WindOSSException extends BaseException {

    /**
     * oss 请求异常
     */
    private final String reqeustId;

    public WindOSSException(String message, String reqeustId) {
        super(message);
        this.reqeustId = reqeustId;
    }
}
