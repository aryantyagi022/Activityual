package com.activityual.notif;
import com.activityual.common.events.ActivityLoggedEvent;
import com.activityual.common.events.RabbitTopology;
import com.activityual.common.security.CurrentUser;
import com.activityual.common.security.GatewayUserFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
@SpringBootApplication
public class NotificationApplication {
    public static void main(String[] args) { SpringApplication.run(NotificationApplication.class, args); }
    @Bean Queue notifQueue() { return QueueBuilder.durable(RabbitTopology.QUEUE_NOTIF).build(); }
    @Bean TopicExchange ex() { return ExchangeBuilder.topicExchange(RabbitTopology.EXCHANGE).durable(true).build(); }
    @Bean Binding binding(Queue notifQueue, TopicExchange ex) {
        return BindingBuilder.bind(notifQueue).to(ex).with("activity.*");
    }
    @Bean
    FilterRegistrationBean<GatewayUserFilter> userFilter() {
        var b = new FilterRegistrationBean<>(new GatewayUserFilter());
        b.addUrlPatterns("/*");
        return b;
    }
    @Component
    @RequiredArgsConstructor
    public static class NotificationListener {
        private final NotificationRepository repo;
        private final ObjectMapper mapper;
        @RabbitListener(queues = RabbitTopology.QUEUE_NOTIF)
        public void on(String payload) throws Exception {
            ActivityLoggedEvent e = mapper.readValue(payload, ActivityLoggedEvent.class);
            String msg = "User %s the activity '%s'.".formatted(e.getStatus(), e.getActivityTitle());
            repo.save(Notification.builder()
                    .userId(e.getUserId()).message(msg)
                    .createdAt(Instant.now()).readFlag(false).build());
        }
    }
    @RestController
    @RequestMapping("/notifications")
    @RequiredArgsConstructor
    public static class NotifController {
        private final NotificationRepository repo;
        @GetMapping("/{userId}")
        public List<Notification> list(@PathVariable UUID userId) {
            return repo.findByUserIdOrderByCreatedAtDesc(userId);
        }
        @PostMapping("/{id}/read")
        public Notification markRead(@PathVariable UUID id) {
            var n = repo.findById(id).orElseThrow();
            if (!n.getUserId().equals(CurrentUser.requireId())) throw new IllegalStateException("forbidden");
            n.setReadFlag(true);
            return repo.save(n);
        }
    }
}
