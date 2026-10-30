package com.ticket.pay.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.ticket.common.dto.OrderInfo;
import com.ticket.common.result.Result;

/**
 * order-service 内部接口的 Feign 客户端。
 * 支付服务的「订单窗口」:查询订单做支付预检,支付成功远程触发状态迁移(10→20)。
 */
@FeignClient(name = "order-service", path = "/api/inner/order")
public interface OrderInnerClient {

    @GetMapping("/{id}")
    Result<OrderInfo> getOrder(@PathVariable("id") Long id);

    @PostMapping("/payNotify")
    Result<Boolean> payNotify(@RequestParam("orderId") Long orderId);
}
