package com.activityual.gateway;

import com.activityual.common.security.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class JwtAuthFilter extends AbstractGatewayFilterFactory<JwtAuthFilter.Config> {

    public static class Config {}

    private final JwtService jwt;

    public JwtAuthFilter(JwtService jwt) {
        super(Config.class);
        this.jwt = jwt;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            var req = exchange.getRequest();
            List<String> auth = req.getHeaders().get("Authorization");
            if (auth == null || auth.isEmpty() || !auth.get(0).startsWith("Bearer ")) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }
            try {
                Claims c = jwt.parse(auth.get(0).substring(7));
                if (!"access".equals(c.get(JwtService.CLAIM_TYPE, String.class))) {
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                }
                var mutated = req.mutate()
                        .header("X-User-Id", c.get(JwtService.CLAIM_USER_ID, String.class))
                        .header("X-User-Email", c.get(JwtService.CLAIM_EMAIL, String.class))
                        .build();
                return chain.filter(exchange.mutate().request(mutated).build());
            } catch (JwtException e) {
                log.warn("Invalid JWT: {}", e.getMessage());
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }
        };
    }
}

