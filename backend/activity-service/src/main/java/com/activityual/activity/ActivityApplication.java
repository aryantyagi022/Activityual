package com.activityual.activity;

import com.activityual.common.security.GatewayUserFilter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ActivityApplication {
    public static void main(String[] args) { SpringApplication.run(ActivityApplication.class, args); }

    @Bean
    FilterRegistrationBean<GatewayUserFilter> userFilter() {
        var b = new FilterRegistrationBean<>(new GatewayUserFilter());
        b.addUrlPatterns("/*");
        return b;
    }
}

