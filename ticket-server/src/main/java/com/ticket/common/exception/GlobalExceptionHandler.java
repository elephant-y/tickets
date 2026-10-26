package com.ticket.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.ticket.common.result.Result;

/**
 * 全局异常处理:把各类异常统一翻译成 Result 响应。
 *
 * <p>关键坑(90% 的人第一次写都会踩):
 * @ExceptionHandler 方法的返回默认 HTTP 200!直接 return Result.error(401, ...) 时,
 * body 里是 401,但 HTTP 状态码还是 200 —— 前端、网关、监控看状态码全部误判。
 * 所以这里返回 ResponseEntity,用异常的 code 动态设置真实 HTTP 状态码。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常:按携带的 code 返回对应的 HTTP 状态码 */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<?>> handleBiz(BizException e) {
        return ResponseEntity.status(e.getCode())
                .body(Result.error(e.getCode(), e.getMessage()));
    }

    /** 唯一键冲突兜底:即使某个 Service 忘了捕获 DuplicateKeyException,也不会返回 500 */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Result<?>> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("唯一键冲突: {}", e.getMessage());
        return ResponseEntity.status(409)
                .body(Result.error(409, "数据已存在"));
    }

    /** 兜底:未预期异常。对外隐藏细节(不把堆栈/内部信息泄露给调用方),对内日志打全 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<?>> handleOther(Exception e) {
        log.error("未处理异常", e);
        return ResponseEntity.status(500)
                .body(Result.error(500, "系统繁忙,请稍后重试"));
    }
}
