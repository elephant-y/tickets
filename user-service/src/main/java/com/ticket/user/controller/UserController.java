package com.ticket.user.controller;

import org.springframework.web.bind.annotation.GetMapping;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticket.common.result.Result;
import com.ticket.user.dto.LoginRequest;
import com.ticket.user.dto.RegisterRequest;
import com.ticket.user.service.UserService;

@RestController 
@RequestMapping ("/api/user")
public class UserController {
    private final UserService userService;
    public UserController (UserService userService)
    {
        this.userService=userService;
    }
    @PostMapping ("/register")
    public Result<?> register(@RequestBody RegisterRequest request)
    {
        userService.register(request);
        return Result.success();
    }   
    @PostMapping ("/login")
    public Result<?> login(@RequestBody LoginRequest request)
    {
        return Result.success(userService.login(request));
    } 
    @GetMapping ("/me")
    public Result<?> me(@RequestHeader ("X-User-Id") Long uid)
    {
        return Result.success(userService.me(uid));
    } 
}
