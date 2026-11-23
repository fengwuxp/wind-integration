package com.wind.integration.kms;

import com.wind.common.WindConstants;
import com.wind.common.exception.BaseException;
import com.wind.common.exception.DefaultExceptionCode;
import lombok.Getter;

/**
 * kms client 异常
 */
@Getter
public class WindKmsException extends BaseException {

    /**
     * kms 请求异常
     */
    private final String reqeustId;

    public WindKmsException(Exception exception) {
        super(DefaultExceptionCode.COMMON_ERROR, "kms exception, message  = " + exception.getMessage(), exception);
        this.reqeustId = WindConstants.UNKNOWN;
    }

    public WindKmsException(String message, String reqeustId) {
        super(message);
        this.reqeustId = reqeustId;
    }
}
