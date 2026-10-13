package com.training.common;

/**
 * 业务异常：携带业务 code，由全局异常处理器统一转成 Result 返回。
 */
public class BizException extends RuntimeException {

    private final Integer code;

    public BizException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }
}
