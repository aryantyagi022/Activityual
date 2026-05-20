package com.activityual.coach.api;

import com.activityual.coach.rag.ChromaClient;
import com.activityual.coach.rag.OllamaClient;
import com.activityual.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/coach")
@RequiredArgsConstructor
public class CoachController {

    private final ChromaClient chroma;
    private final OllamaClient ollama;

    public record AskReq(@NotBlank String question) {}
    public record AskRes(String answer, List<String> contextChunks) {}

    @PostMapping("/ask")
    public AskRes ask(@Valid @RequestBody AskReq req) {
        UUID uid = CurrentUser.requireId();
        List<String> chunks;
        try {
            chunks = chroma.query(uid.toString(), req.question(), 8);
        } catch (Exception e) {
            chunks = List.of();
        }
        String context = chunks.isEmpty()
                ? "(no prior activity data)"
                : String.join("\n- ", chunks);

        String prompt = """
                You are Activityual, an empathetic habit coach. Use ONLY the user's activity
                history below to answer the question. If the data is insufficient, say so and
                suggest what to track next. Be concise (max 6 sentences) and actionable.

                USER ACTIVITY HISTORY:
                - %s

                USER QUESTION: %s
                """.formatted(context, req.question());

        String answer;
        try {
            answer = ollama.generate(prompt);
        } catch (Exception e) {
            throw new IllegalStateException("AI coach unavailable: " + e.getMessage());
        }
        return new AskRes(answer, chunks);
    }
}

