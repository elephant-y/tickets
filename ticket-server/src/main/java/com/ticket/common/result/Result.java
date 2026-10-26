package com.ticket.common.result;


public class Result<T> {

    /** 0 = 成功,非 0 = 失败(具体错误码业务自定义) */
    private Integer code;

    /** 给人看的提示信息 */
    private String message;

    /** 业务数据 */
    private T data;

    /** 无参构造:Jackson 反序列化需要 */
    public Result() {
    }

    public Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /** 成功(带数据) */
    public static <T> Result<T> success(T data) {
        return new Result<>(0, "success", data);
    }

    /** 成功(无数据,如注册场景) */
    public static <T> Result<T> success() {
        return success(null);
    }

    /** 失败 */
    public static <T> Result<T> error(Integer code, String message) {
        return new Result<>(code, message, null);
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
