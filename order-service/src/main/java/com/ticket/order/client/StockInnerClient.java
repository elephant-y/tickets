package com.ticket.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.ticket.common.dto.DeductRequest;
import com.ticket.common.result.Result;

/**
 * ticket-service 库存接口的 Feign 客户端。
 * 扣减/回补的「远程镜像」:order 域动库存的唯一通道(仓库窗口)。
 * 幂等键在 DeductRequest.requestId —— Feign 超时重试时由 ticket-service 去重。
 */
@FeignClient(name = "ticket-service", path = "/api/inner/stock")
public interface StockInnerClient {

    @PostMapping("/deduct")
    Result<String> deduct(@RequestBody DeductRequest req);

    @PostMapping("/refund")
    Result<String> refund(@RequestBody DeductRequest req);
}
