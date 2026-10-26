package com.ticket.common.util;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component 
public class JwtUtil {
    private final SecretKey key;
    private final long expireMs;
    public JwtUtil(@Value("${ticket.jwt.secret}")String secret,@Value("${ticket.jwt.expire-hours:2}")long expireHours)
    {
        this.key=Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMs = TimeUnit.HOURS.toMillis(expireHours);
    }
     public String createToken(Long uid, String username) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(uid))          // subject 放 uid:JWT 标准字段,语义=「主体是谁」
                .claim("username", username)           // 自定义声明放用户名
                .issuedAt(now)                         // iat:签发时间
                .expiration(new Date(now.getTime() + expireMs))  // exp:相对签发时间 +2 小时
                .signWith(key)                         // HS256 签名(算法由 key 长度自动确定)
                .compact();                            // 三段 Base64URL 拼装
    }
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)       // 验签用的密钥(必须和签发同一把)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
    public int getExpireHours() {
    return (int) (expireMs / 3_600_000);
}

}
