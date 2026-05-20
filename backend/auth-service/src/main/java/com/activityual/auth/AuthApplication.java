package com.activityual.auth;

import com.activityual.common.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@SpringBootApplication
public class AuthApplication {
    public static void main(String[] args) { SpringApplication.run(AuthApplication.class, args); }

    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    JwtService jwtService(@Value("${jwt.secret}") String s,
                          @Value("${jwt.access-ttl-min:15}") long a,
                          @Value("${jwt.refresh-ttl-days:7}") long r) {
        return new JwtService(s, a, r);
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http.csrf(c -> c.disable())
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .build();
    }
}

