package com.finance.common.exception;

/**
 * 携带业务状态码的异常，用于区分参数错误、资源不存在和越权访问。
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
