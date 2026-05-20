package com.activityual.analytics;

import com.activityual.common.events.RabbitTopology;
import com.activityual.common.security.GatewayUserFilter;
import org.springframework.amqp.core.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class AnalyticsApplication {
    public static void main(String[] args) { SpringApplication.run(AnalyticsApplication.class, args); }

    @Bean Queue analyticsQueue() { return QueueBuilder.durable(RabbitTopology.QUEUE_ANALYTICS).build(); }
    @Bean TopicExchange ex() { return ExchangeBuilder.topicExchange(RabbitTopology.EXCHANGE).durable(true).build(); }
    @Bean Binding binding(Queue analyticsQueue, TopicExchange ex) {
        return BindingBuilder.bind(analyticsQueue).to(ex).with("activity.*");
    }

    @Bean
    FilterRegistrationBean<GatewayUserFilter> userFilter() {
        var b = new FilterRegistrationBean<>(new GatewayUserFilter());
        b.addUrlPatterns("/*");
        return b;
    }
}

