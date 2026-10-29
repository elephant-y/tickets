package com.ticket.train.controller;

import org.springframework.web.bind.annotation.GetMapping;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ticket.common.result.Result;

import com.ticket.train.service.TrainService;

@RestController 
@RequestMapping ("/api/train")
public class TrainController {
    private final TrainService trainService;
    public TrainController(TrainService trainService)
    {
        this.trainService=trainService;
    }
    @GetMapping ("/stations")
    public Result<?> getStations()
    {
        return Result.success(trainService.listStations());
    }
    @GetMapping ("/search")
    public Result<?>searchTrain(@RequestParam  ("from")Long from,@RequestParam ("to")Long to)
    {
        return Result.success(trainService.search(from,to));
    }
}
