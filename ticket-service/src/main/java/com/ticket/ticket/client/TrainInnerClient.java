package com.ticket.ticket.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.ticket.common.dto.StationInfo;
import com.ticket.common.result.Result;

/**
 * train-service 的 Feign 客户端(站序换算:余票查询按区间过滤时,把用户传的站 id 换成站序)。
 */
@FeignClient(name = "train-service", path = "/api/inner/train")
public interface TrainInnerClient {

    @GetMapping("/station")
    Result<StationInfo> getStation(@RequestParam("trainId") Long trainId,
                                   @RequestParam("stationId") Long stationId);
}
