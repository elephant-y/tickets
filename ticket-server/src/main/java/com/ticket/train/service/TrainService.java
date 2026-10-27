package com.ticket.train.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.ticket.common.exception.BizException;
import com.ticket.train.dto.TrainSearchResponse;
import com.ticket.train.entity.Station;
import com.ticket.train.mapper.StationMapper;
import com.ticket.train.mapper.TrainMapper;

@Service 
public class TrainService {
    private final TrainMapper trainMapper;
    private final StationMapper stationMapper;   

    public TrainService(TrainMapper trainMapper, StationMapper stationMapper) {
        this.trainMapper = trainMapper;
        this.stationMapper = stationMapper;
    }
    public List<Station> listStations() {
        return stationMapper.selectList(null);
    }
    public List<TrainSearchResponse> search(Long from, Long to) {
        if (from.equals(to)) {
            throw new BizException(400, "出发站和到达站不能相同");
        }
        return trainMapper.searchByStations(from, to);   
    }
}
