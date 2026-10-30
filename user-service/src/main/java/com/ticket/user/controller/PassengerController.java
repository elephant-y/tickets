package com.ticket.user.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticket.common.result.Result;
import com.ticket.user.dto.PassengerRequest;
import com.ticket.user.service.PassengerService;

@RestController
@RequestMapping ("/api/user/passenger")
public class PassengerController {
    private final PassengerService passengerService;
    public PassengerController (PassengerService passengerService)
    {
        this.passengerService=passengerService;
    }
    /** uid 来自网关注入的 X-User-Id(覆盖语义防伪造),前端不传 userId,与 /me 同机制 */
    @GetMapping ("/list")
    public Result<?> list(@RequestHeader ("X-User-Id") Long uid)
    {
        return Result.success(passengerService.list(uid));
    }
    @PostMapping
    public Result<?> add(@RequestHeader ("X-User-Id") Long uid,
                         @RequestBody PassengerRequest request)
    {
        passengerService.add(uid, request);
        return Result.success();
    }
    @DeleteMapping ("/{id}")
    public Result<?> delete(@RequestHeader ("X-User-Id") Long uid,
                            @PathVariable ("id") Long id)
    {
        passengerService.delete(uid, id);
        return Result.success();
    }
}
