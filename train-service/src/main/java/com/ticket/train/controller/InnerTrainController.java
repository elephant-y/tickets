package com.ticket.train.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.common.dto.StationInfo;
import com.ticket.common.dto.TrainInfo;
import com.ticket.common.exception.BizException;
import com.ticket.common.result.Result;
import com.ticket.train.entity.Train;
import com.ticket.train.entity.TrainStation;
import com.ticket.train.mapper.TrainMapper;
import com.ticket.train.mapper.TrainStationMapper;

/**
 * 内部接口(供 order 域经 Feign 调用)——拆分后「本地调用升级远程调用」的服务端。
 * 前缀 /api/inner/ 的语义:不对用户开放,网关路由不暴露;生产上还需服务间认证(阶段十)。
 *
 * 三个接口与 OrderService 的对应关系:
 *   /station  → 替换 resolveStationNo + resolveMileage(两处查询合并成一次远程调用)
 *   /stations → 替换 overlapIntervals 的经停列表查询
 *   /info     → 替换 trainMapper.selectById 取车次号
 */
@RestController
@RequestMapping("/api/inner/train")
public class InnerTrainController {

    private final TrainStationMapper trainStationMapper;
    private final TrainMapper trainMapper;

    public InnerTrainController(TrainStationMapper trainStationMapper, TrainMapper trainMapper) {
        this.trainStationMapper = trainStationMapper;
        this.trainMapper = trainMapper;
    }

    /** 站序+里程换算:两个字段同源(同一次查询),合并成一个接口 = 一次远程调用拿两样 */
    @GetMapping("/station")
    public Result<StationInfo> station(@RequestParam Long trainId, @RequestParam Long stationId) {
        TrainStation ts = trainStationMapper.selectOne(
                new LambdaQueryWrapper<TrainStation>()
                        .eq(TrainStation::getTrainId, trainId)
                        .eq(TrainStation::getStationId, stationId));
        if (ts == null) {
            throw new BizException(404, "站点不在该车次的经停站中");
        }
        return Result.success(new StationInfo(ts.getStationNo(), ts.getMileage()));
    }

    /** 经停站列表(按站序升序,重叠区间计算用) */
    @GetMapping("/stations")
    public Result<List<StationInfo>> stations(@RequestParam Long trainId) {
        List<TrainStation> list = trainStationMapper.selectList(
                new LambdaQueryWrapper<TrainStation>()
                        .eq(TrainStation::getTrainId, trainId)
                        .orderByAsc(TrainStation::getStationNo));
        List<StationInfo> infos = list.stream()
                .map(ts -> new StationInfo(ts.getStationNo(), ts.getMileage()))
                .collect(Collectors.toList());
        return Result.success(infos);
    }

    /** 车次信息(订单冗余车次号用) */
    @GetMapping("/info")
    public Result<TrainInfo> info(@RequestParam Long id) {
        Train train = trainMapper.selectById(id);
        if (train == null) {
            throw new BizException(404, "车次不存在");
        }
        return Result.success(new TrainInfo(train.getId(), train.getTrainNo()));
    }
}
