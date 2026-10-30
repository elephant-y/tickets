package com.ticket.order.client;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.ticket.common.dto.StationInfo;
import com.ticket.common.dto.TrainInfo;
import com.ticket.common.result.Result;

/**
 * train-service 的 Feign 客户端 —— 「本地调用升级远程调用」的调用方侧。
 * name = "train-service":按服务名走 Nacos 服务发现(与网关 lb:// 同款),Feign 内置负载均衡。
 */
@FeignClient(name = "train-service", path = "/api/inner/train")
public interface TrainInnerClient {

    @GetMapping("/station")
    Result<StationInfo> getStation(@RequestParam Long trainId, @RequestParam Long stationId);

    @GetMapping("/stations")
    Result<List<StationInfo>> listStations(@RequestParam Long trainId);

    @GetMapping("/info")
    Result<TrainInfo> getTrainInfo(@RequestParam Long id);
}
