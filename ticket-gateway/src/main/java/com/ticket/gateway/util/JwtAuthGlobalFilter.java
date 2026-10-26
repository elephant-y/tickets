package com.ticket.gateway.util;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import reactor.core.publisher.Mono;

/**
 * JWT 全局鉴权过滤器:除白名单外,所有请求必须携带合法 token 才能继续。
 *
 * <p>为什么写在网关:鉴权是横切关注点,收口一处(见 docs/03 §2.2.1);
 * 业务服务不写任何鉴权代码,只信任网关注入的 X-User-Id 头。
 *
 * <p>为什么实现 GlobalFilter 而不是路由级 Filter:所有路由都要过,一处生效。
 * order = -100:保证在路由匹配/转发之前执行(内置转发过滤器的 order 都是万级)。
 *
 * <p>安全要点:注入 X-User-Id 用 header() 的覆盖语义 ——
 * 客户端伪造的同名头会被验签得到的 uid 覆盖,伪造无效。
 */
@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    /** 豁免路径:注册/登录天生没有 token,不豁免就死循环 */
    private static final Set<String> WHITELIST = Set.of("/api/user/register", "/api/user/login");

    private final SecretKey key;

    public JwtAuthGlobalFilter(@Value("${ticket.jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** 验签并解析 claims;过期/篡改/格式错分别抛 ExpiredJwtException/SignatureException/MalformedJwtException(均为 JwtException 子类) */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // ① 白名单:直接放行(原样 exchange)
        String path = exchange.getRequest().getPath().toString();
        if (WHITELIST.contains(path)) {
            return chain.filter(exchange);
        }

        // ② 取头:Authorization: Bearer xxx;没带或格式不对 → 401
        String auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.startsWith("Bearer ")) {
            return unauthorized(exchange);
        }

        // ③ 验签,④ 注入透传头(覆盖语义防伪造)
        try {
            Claims claims = parseToken(auth.substring(7)); // 去掉 "Bearer " 前缀
            String uid = claims.getSubject();              // sub 里存的就是 uid
            ServerWebExchange mutated = exchange.mutate()
                    .request(r -> r.header("X-User-Id", uid))
                    .build();
            return chain.filter(mutated);
        } catch (JwtException e) {
            // 过期 / 签名不匹配 / 格式错误,统一 401
            return unauthorized(exchange);
        }
    }

    /** 拒绝:设 401 并完成响应。注意:不调用 chain.filter = 过滤器链到此终止 */
    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
