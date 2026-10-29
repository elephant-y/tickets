package com.ticket.pay.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ticket.common.exception.BizException;
import com.ticket.order.entity.Order;
import com.ticket.order.mapper.OrderMapper;
import com.ticket.pay.entity.PayRecord;
import com.ticket.pay.mapper.PayRecordMapper;


@Service
public class PayService {

    private static final Logger log = LoggerFactory.getLogger(PayService.class);

    private final PayRecordMapper payRecordMapper;
    private final OrderMapper orderMapper;
    private final TransactionTemplate transactionTemplate;

    public PayService(PayRecordMapper payRecordMapper,
                      OrderMapper orderMapper,
                      TransactionTemplate transactionTemplate) {
        this.payRecordMapper = payRecordMapper;
        this.orderMapper = orderMapper;
        this.transactionTemplate = transactionTemplate;
    }

    /** 用户点支付:校验 → 建支付单(幂等)→ 模拟网关 → 回调处理。返回 payNo */
    public String createPayment(Long orderId, Long userId) {
        // ① 订单校验:存在 / 归属 / 状态
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(404, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BizException(403, "无权操作该订单");
        }
        if (order.getStatus() != 10) {
            throw new BizException(409, "订单不是待支付状态");
        }

        // ② 建支付单:uk_order 唯一 —— 重复点支付撞键 → 返回已有支付单(幂等1)
        PayRecord record = new PayRecord();
        record.setPayNo(generatePayNo());
        record.setOrderId(orderId);
        record.setAmount(order.getTotalAmount());
        record.setStatus(0);
        try {
            payRecordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            PayRecord existing = payRecordMapper.selectOne(
                    new LambdaQueryWrapper<PayRecord>().eq(PayRecord::getOrderId, orderId));
            if (existing != null) {
                return existing.getPayNo();   // 已点过支付,返回原支付单
            }
            throw e;
        }

        // ③ 模拟网关:真实场景 = 跳转支付宝 + 用户输密码 + 网关异步通知;
        //    本项目直接视为「支付成功」,由下一步的回调完成状态迁移
        // ④ 回调(模拟网关内部直调;真实场景是网关 HTTP 异步通知,需验签 + 白名单)
        handlePayNotify(record.getPayNo());

        return record.getPayNo();
    }

    /** 网关回调(模拟):事务内「订单 10→20 + 支付单 0→1」。返回 true=本次生效 */
    public boolean handlePayNotify(String payNo) {
        // 用编程式事务:createPayment 调本方法是 this 自调用,声明式事务会失效(阶段四教训)
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            PayRecord record = payRecordMapper.selectOne(
                    new LambdaQueryWrapper<PayRecord>().eq(PayRecord::getPayNo, payNo));
            if (record == null) {
                throw new BizException(404, "支付单不存在");
            }

            // ① 订单 10 → 20(条件更新 —— 幂等2)
            int affected = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                    .eq(Order::getId, record.getOrderId())
                    .eq(Order::getStatus, 10)
                    .set(Order::getStatus, 20)
                    .set(Order::getPayTime, LocalDateTime.now()));
            if (affected == 0) {
                // 两种情况:已支付(重复回调)→ 静默;已取消(支付晚了)→ 值得告警
                Order cur = orderMapper.selectById(record.getOrderId());
                if (cur != null && cur.getStatus() == 20) {
                    log.info("重复回调,已处理: payNo={}", payNo);
                } else {
                    log.warn("回调到达但订单已非待支付(已取消?): payNo={}, status={}",
                            payNo, cur == null ? "无" : cur.getStatus());
                }
                return false;
            }

            // ② 支付单 0 → 1(条件更新 —— 幂等3)+ 支付时间
            payRecordMapper.update(null, new LambdaUpdateWrapper<PayRecord>()
                    .eq(PayRecord::getPayNo, payNo)
                    .eq(PayRecord::getStatus, 0)
                    .set(PayRecord::getStatus, 1)
                    .set(PayRecord::getPayTime, LocalDateTime.now()));

            log.info("支付成功: payNo={}, orderId={}", payNo, record.getOrderId());
            return true;
        }));
    }

    /** 支付单号:P + 时间到秒 + 6 位随机(与订单号同款思路,uk_pay_no 兜底撞号) */
    private String generatePayNo() {
        return "P" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }
}
