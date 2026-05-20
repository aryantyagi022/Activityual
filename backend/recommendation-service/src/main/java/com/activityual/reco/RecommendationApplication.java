package com.activityual.reco;

import com.activityual.common.events.RabbitTopology;
import com.activityual.common.security.GatewayUserFilter;
import org.springframework.amqp.core.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class RecommendationApplication {
    public static void main(String[] args) { SpringApplication.run(RecommendationApplication.class, args); }

    @Bean Queue recoQueue() { return QueueBuilder.durable(RabbitTopology.QUEUE_RECO).build(); }
    @Bean TopicExchange ex() { return ExchangeBuilder.topicExchange(RabbitTopology.EXCHANGE).durable(true).build(); }
    @Bean Binding binding(Queue recoQueue, TopicExchange ex) {
        return BindingBuilder.bind(recoQueue).to(ex).with("activity.*");
    }

    @Bean
    FilterRegistrationBean<GatewayUserFilter> userFilter() {
        var b = new FilterRegistrationBean<>(new GatewayUserFilter());
        b.addUrlPatterns("/*");
        return b;
    }
}

