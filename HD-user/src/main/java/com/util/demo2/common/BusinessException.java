package com.util.demo2.common;

/**
 * 业务异常。Service 层直接 throw，由 GlobalExceptionHandler 统一转成 Result。
 * 默认 400（参数/业务校验失败），401/403 由调用方显式指定。
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String message) {
        this(Result.CODE_PARAM_ERROR, message);
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
