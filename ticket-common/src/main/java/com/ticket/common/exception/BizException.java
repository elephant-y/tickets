package com.ticket.common.exception;

/**
 * 业务异常:携带 HTTP 状态码 + 用户可读提示。
 *
 * <p>设计:业务代码(Service)只负责「发现错误并抛出」,抛什么状态码、怎么转成响应,
 * 由 GlobalExceptionHandler 统一处理——错误表达与错误发现解耦,Controller/Service 不写 try-catch 面条。
 */
public class BizException extends RuntimeException {

    /** HTTP 同义状态码(401 未授权、404 不存在、409 冲突等) */
    private final Integer code;

    public BizException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }
}
