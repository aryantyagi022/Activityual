package com.activityual.coach.rag;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.Objects;

@Component
public class OllamaClient {

    private final WebClient ollamaClient;
    @Value("${ollama.model:llama3.2:3b}") private String model;

    public OllamaClient(@Qualifier("ollamaWebClient") WebClient ollamaClient) {
        this.ollamaClient = ollamaClient;
    }

    public String generate(String prompt) {
        Map<String,Object> body = Map.of(
                "model", model,
                "prompt", prompt,
                "stream", false,
                "keep_alive", "30m",
                "options", Map.of(
                        "temperature", 0.3,
                        "num_predict", 256,
                        "num_ctx", 2048
                )
        );
        JsonNode resp = ollamaClient.post().uri("/api/generate")
                .bodyValue(body).retrieve().bodyToMono(JsonNode.class).block();
        return Objects.requireNonNull(resp).get("response").asText();
    }
}

