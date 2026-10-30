package com.ticket.pay.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ticket.common.dto.OrderInfo;
import com.ticket.common.exception.BizException;
import com.ticket.pay.client.OrderInnerClient;
import com.ticket.pay.entity.PayRecord;
import com.ticket.pay.mapper.PayRecordMapper;

/**
 * 支付服务(模拟支付网关)。
 *
 * 拆分后的职责边界:订单状态迁移(10→20)是订单域的职责 —— 本服务经 Feign 调
 * order-service 的 payNotify 接口远程触发;本地只管理支付流水(pay_record)。
 * 原「订单+支付单同一事务」变成「远程迁移 + 本地更新」两步:远程成功后本地更新失败,
 * 由支付流水的状态不一致暴露,靠对账兜底(最终一致性的代价,拆分引入)。
 */
@Service
public class PayService {

    private static final Logger log = LoggerFactory.getLogger(PayService.class);

    private final PayRecordMapper payRecordMapper;
    private final OrderInnerClient orderInnerClient;

    public PayService(PayRecordMapper payRecordMapper,
                      OrderInnerClient orderInnerClient) {
        this.payRecordMapper = payRecordMapper;
        this.orderInnerClient = orderInnerClient;
    }

    /** 用户点支付:校验(Feign 查订单)→ 建支付单(幂等)→ 模拟网关 → 回调处理。返回 payNo */
    public String createPayment(Long orderId, Long userId) {
        // ① 订单校验(远程):存在 / 归属 / 状态
        OrderInfo order = orderInnerClient.getOrder(orderId).getData();
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

        // ③ 模拟网关:真实场景 = 跳转支付宝 + 用户输密码 + 网关异步通知
        // ④ 回调(模拟网关内部直调;真实场景是网关 HTTP 异步通知,需验签 + 白名单)
        handlePayNotify(record.getPayNo());

        return record.getPayNo();
    }

    /** 网关回调(模拟):远程迁移订单状态 → 本地更新支付单。返回 true=本次生效 */
    public boolean handlePayNotify(String payNo) {
        PayRecord record = payRecordMapper.selectOne(
                new LambdaQueryWrapper<PayRecord>().eq(PayRecord::getPayNo, payNo));
        if (record == null) {
            throw new BizException(404, "支付单不存在");
        }

        // ① 远程迁移订单 10→20(order-service 内条件更新,幂等2;重复回调/已取消时返回 false)
        Boolean paid = orderInnerClient.payNotify(record.getOrderId()).getData();
        if (!Boolean.TRUE.equals(paid)) {
            log.warn("订单状态迁移未生效(重复回调或已取消): payNo={}, orderId={}",
                    payNo, record.getOrderId());
            // 重复回调场景:第一次回调已把支付单置 1 → 幂等返回 true
            PayRecord cur = payRecordMapper.selectById(record.getId());
            return cur != null && cur.getStatus() == 1;
        }

        // ② 支付单 0 → 1(条件更新 —— 幂等3)+ 支付时间
        int affected = payRecordMapper.update(null, new LambdaUpdateWrapper<PayRecord>()
                .eq(PayRecord::getPayNo, payNo)
                .eq(PayRecord::getStatus, 0)
                .set(PayRecord::getStatus, 1)
                .set(PayRecord::getPayTime, LocalDateTime.now()));
        if (affected == 0) {
            log.info("支付单已处理(并发回调): payNo={}", payNo);
        } else {
            log.info("支付成功: payNo={}, orderId={}", payNo, record.getOrderId());
        }
        return true;
    }

    /** 支付单号:P + 时间到秒 + 6 位随机(与订单号同款思路,uk_pay_no 兜底撞号) */
    private String generatePayNo() {
        return "P" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }
}
