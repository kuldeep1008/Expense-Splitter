package com.example.expensesplitter.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Second "talking point" of the project: instead of making the user pick a
 * category from a dropdown, we ask an LLM to classify the expense description
 * (e.g. "Uber to airport" -> "Travel") and store the result.
 *
 * Two things worth explaining in an interview:
 *  1. Graceful degradation - if the AI call fails or times out, we fall back
 *     to "Uncategorized" rather than blocking the user from adding an expense.
 *  2. A tiny in-memory cache so identical descriptions ("Starbucks", "Uber")
 *     don't trigger a new API call (and cost) every time.
 */
@Service
@Slf4j
public class AiCategorizationService {

    private static final List<String> ALLOWED_CATEGORIES = List.of(
            "Food", "Travel", "Utilities", "Entertainment", "Groceries",
            "Rent", "Shopping", "Health", "Other"
    );

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    @Value("${app.ai.api-key:}")
    private String apiKey;

    @Value("${app.ai.model:claude-3-5-haiku-20241022}")
    private String model;

    @Value("${app.ai.enabled:true}")
    private boolean enabled;

    public AiCategorizationService() {
        this.restClient = RestClient.builder()
                .baseUrl("https://api.anthropic.com/v1/messages")
                .build();
    }

    public String categorize(String description) {
        if (!enabled || apiKey == null || apiKey.isBlank()) {
            return "Uncategorized";
        }

        String cacheKey = description.trim().toLowerCase();
        if (cache.containsKey(cacheKey)) {
            return cache.get(cacheKey);
        }

        try {
            String prompt = "Classify this expense description into exactly one of these categories: "
                    + String.join(", ", ALLOWED_CATEGORIES)
                    + ". Reply with ONLY the category word, nothing else. Description: \"" + description + "\"";

            Map<String, Object> body = Map.of(
                    "model", model,
                    "max_tokens", 10,
                    "messages", List.of(Map.of("role", "user", "content", prompt))
            );

            String response = restClient.post()
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            String raw = root.path("content").path(0).path("text").asText("Other").trim();

            String category = ALLOWED_CATEGORIES.stream()
                    .filter(c -> c.equalsIgnoreCase(raw))
                    .findFirst()
                    .orElse("Other");

            cache.put(cacheKey, category);
            return category;

        } catch (Exception e) {
            // Never let an AI hiccup block expense creation - degrade gracefully.
            log.warn("AI categorization failed for '{}': {}", description, e.getMessage());
            return "Uncategorized";
        }
    }
}
