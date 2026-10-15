package com.util.demo2.common;

/**
 * 全局统一返回体，严格按《统一接口规范》：
 * {"code":200,"msg":"操作成功","data":{}}
 *
 * 约定：HTTP 状态码一律返回 200，业务状态放在 body 的 code 里，
 * 前端 axios 拦截器请判断 res.data.code，不要判断 HTTP 状态码。
 */
public class Result<T> {

    /** 成功 */
    public static final int CODE_SUCCESS = 200;
    /** 参数错误 / 业务校验失败（对规范 4 个码的扩展，需组内确认，见 README） */
    public static final int CODE_PARAM_ERROR = 400;
    /** 未登录、token 无效或过期 */
    public static final int CODE_UNAUTHORIZED = 401;
    /** 已登录但权限不足 */
    public static final int CODE_FORBIDDEN = 403;
    /** 服务器异常 */
    public static final int CODE_SERVER_ERROR = 500;

    private Integer code;
    private String msg;
    private T data;

    public Result() {
    }

    public Result(Integer code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static <T> Result<T> success() {
        return new Result<>(CODE_SUCCESS, "操作成功", null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(CODE_SUCCESS, "操作成功", data);
    }

    public static <T> Result<T> success(String msg, T data) {
        return new Result<>(CODE_SUCCESS, msg, data);
    }

    public static <T> Result<T> fail(int code, String msg) {
        return new Result<>(code, msg, null);
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
