package com.ticket.user.dto;

public class LoginResponse {
    private String token;      // JWT 串
    private Integer expiresIn; // 过期时长(小时),前端据此安排刷新时机
    public String getToken() {
        return token;
    }
    public void setToken(String token) {
        this.token = token;
    }
    public Integer getExpiresIn() {
        return expiresIn;
    }
    public void setExpiresIn(Integer expiresIn) {
        this.expiresIn = expiresIn;
    }
}
