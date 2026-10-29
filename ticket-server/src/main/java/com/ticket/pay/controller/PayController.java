package com.ticket.pay.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.common.exception.BizException;
import com.ticket.common.result.Result;
import com.ticket.pay.entity.PayRecord;
import com.ticket.pay.mapper.PayRecordMapper;
import com.ticket.pay.service.PayService;

/**
 * 支付接口。注意:真实场景还有「网关回调」接口(支付宝异步通知,POST /api/pay/notify,
 * 需验签+白名单)——本项目模拟网关直接走 Java 内部调用,该接口省略,注释记之。
 */
@RestController
@RequestMapping("/api/pay")
public class PayController {

    private final PayService payService;
    private final PayRecordMapper payRecordMapper;

    public PayController(PayService payService, PayRecordMapper payRecordMapper) {
        this.payService = payService;
        this.payRecordMapper = payRecordMapper;
    }

    /** 用户点支付:创建支付单 → 模拟网关 → 回调处理 */
    @PostMapping("/pay")
    public Result<?> pay(@RequestParam("orderId") Long orderId,
                         @RequestHeader("X-User-Id") Long userId) {
        return Result.success(payService.createPayment(orderId, userId));
    }

    /** 支付状态查询(用户轮询/前端展示) */
    @GetMapping("/status")
    public Result<?> status(@RequestParam("payNo") String payNo) {
        PayRecord record = payRecordMapper.selectOne(
                new LambdaQueryWrapper<PayRecord>().eq(PayRecord::getPayNo, payNo));
        if (record == null) {
            throw new BizException(404, "支付单不存在");
        }
        return Result.success(record);
    }
}
