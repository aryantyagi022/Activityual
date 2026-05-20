package com.activityual.coach;

import com.activityual.common.events.RabbitTopology;
import com.activityual.common.security.GatewayUserFilter;
import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootApplication
public class CoachApplication {
    public static void main(String[] args) { SpringApplication.run(CoachApplication.class, args); }

    @Bean Queue coachQueue() { return QueueBuilder.durable(RabbitTopology.QUEUE_COACH).build(); }
    @Bean TopicExchange ex() { return ExchangeBuilder.topicExchange(RabbitTopology.EXCHANGE).durable(true).build(); }
    @Bean Binding binding(Queue coachQueue, TopicExchange ex) {
        return BindingBuilder.bind(coachQueue).to(ex).with("activity.*");
    }

    @Bean(name = "chromaWebClient")
    WebClient chromaWebClient(@Value("${chroma.url}") String url) {
        return WebClient.builder().baseUrl(url).build();
    }

    @Bean(name = "ollamaWebClient")
    WebClient ollamaWebClient(@Value("${ollama.url}") String url) {
        var http = reactor.netty.http.client.HttpClient.create()
                .responseTimeout(java.time.Duration.ofMinutes(3));
        return WebClient.builder().baseUrl(url)
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(http))
                .codecs(c -> c.defaultCodecs().maxInMemorySize(8 * 1024 * 1024)).build();
    }

    @Bean
    FilterRegistrationBean<GatewayUserFilter> userFilter() {
        var b = new FilterRegistrationBean<>(new GatewayUserFilter());
        b.addUrlPatterns("/*");
        return b;
    }
}

