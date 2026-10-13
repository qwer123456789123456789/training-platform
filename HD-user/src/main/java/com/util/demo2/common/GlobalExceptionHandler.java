package com.util.demo2.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：保证任何异常返回给前端的都是 {code,msg,data} 结构，
 * 而不是 Spring 默认的错误 JSON（否则前端 res.data.code 会取不到值）。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常：Service 主动抛出 */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.debug("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /** @Valid 参数校验失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidException(MethodArgumentNotValidException e) {
        String msg = "参数校验失败";
        if (e.getBindingResult().getFieldError() != null) {
            msg = e.getBindingResult().getFieldError().getDefaultMessage();
        }
        return Result.fail(Result.CODE_PARAM_ERROR, msg);
    }

    /** 唯一键冲突：例如新增用户时账号重复 */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKeyException(DuplicateKeyException e) {
        log.warn("唯一键冲突: {}", e.getMessage());
        return Result.fail(Result.CODE_PARAM_ERROR, "数据已存在，请检查账号等唯一字段");
    }

    /** 兜底：不要把堆栈信息返回给前端 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("服务器异常", e);
        return Result.fail(Result.CODE_SERVER_ERROR, "服务器异常，请联系管理员");
    }
}
