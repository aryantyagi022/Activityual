package com.activityual.coach.rag;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

@Slf4j
@Component
public class ChromaClient {

    public static final String COLLECTION = "activity-logs";

    private final WebClient chromaClient;
    private final WebClient ollamaClient;
    @Value("${ollama.embedding-model:nomic-embed-text}") private String embeddingModel;
    private String collectionId;

    public ChromaClient(@Qualifier("chromaWebClient") WebClient chromaClient,
                        @Qualifier("ollamaWebClient") WebClient ollamaClient) {
        this.chromaClient = chromaClient;
        this.ollamaClient = ollamaClient;
    }

    public synchronized String ensureCollection() {
        if (collectionId != null) return collectionId;
        Map<String,Object> body = Map.of("name", COLLECTION, "get_or_create", true);
        JsonNode resp = chromaClient.post().uri("/api/v1/collections")
                .bodyValue(body).retrieve().bodyToMono(JsonNode.class).block();
        collectionId = Objects.requireNonNull(resp).get("id").asText();
        return collectionId;
    }

    public List<Double> embed(String text) {
        Map<String,Object> body = Map.of("model", embeddingModel, "prompt", text);
        JsonNode resp = ollamaClient.post().uri("/api/embeddings")
                .bodyValue(body).retrieve().bodyToMono(JsonNode.class).block();
        List<Double> out = new ArrayList<>();
        for (JsonNode n : Objects.requireNonNull(resp).get("embedding")) out.add(n.asDouble());
        return out;
    }

    public void upsert(String id, String text, Map<String,Object> metadata) {
        try {
            String cid = ensureCollection();
            List<Double> emb = embed(text);
            Map<String,Object> body = Map.of(
                    "ids", List.of(id),
                    "embeddings", List.of(emb),
                    "documents", List.of(text),
                    "metadatas", List.of(metadata)
            );
            chromaClient.post().uri("/api/v1/collections/{cid}/upsert", cid)
                    .bodyValue(body).retrieve().toBodilessEntity().block();
        } catch (Exception e) {
            log.warn("Chroma upsert failed: {}", e.getMessage());
        }
    }

    public List<String> query(String userId, String question, int k) {
        try {
            String cid = ensureCollection();
            List<Double> emb = embed(question);
            Map<String,Object> body = Map.of(
                    "query_embeddings", List.of(emb),
                    "n_results", k,
                    "where", Map.of("user_id", userId)
            );
            JsonNode resp = chromaClient.post().uri("/api/v1/collections/{cid}/query", cid)
                    .bodyValue(body).retrieve().bodyToMono(JsonNode.class).block();
            List<String> docs = new ArrayList<>();
            JsonNode docsNode = Objects.requireNonNull(resp).get("documents");
            if (docsNode != null && docsNode.isArray() && docsNode.size() > 0) {
                for (JsonNode d : docsNode.get(0)) docs.add(d.asText());
            }
            return docs;
        } catch (Exception e) {
            log.warn("Chroma query failed: {}", e.getMessage());
            return List.of();
        }
    }
}

