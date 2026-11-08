package com.smartplate.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Adapter pattern - a second Adapter, for a local model served by Ollama
 * (https://ollama.com).
 *
 * <p>
 * Works like AIAdapter: it implements the AIModel interface the agent expects,
 * translates
 * {@code generate(prompt)} into Ollama's {@code POST /api/chat} request, and
 * translates the JSON
 * reply back into plain text. Errors become AIServiceException, so the agent
 * needs no changes.
 * It uses only the JDK's HttpClient and Jackson (already on the classpath
 * through the Anthropic SDK).
 */
public class OllamaAdapter implements AIModel {
    /**
     * The default base URL for the Ollama API.
     */
    public static final String DEFAULT_BASE_URL = "http://localhost:11434";
    public static final String DEFAULT_MODEL = "OllMA_LOCAL_MODEL";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    /**
     * Local models can be slow, especially the first call while the model loads
     * into memory.
     */
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(180);
    /**
     * Low temperature: picking ids from a list should be predictable, not creative.
     */
    private static final double TEMPERATURE = 0.2;

    private static final ObjectMapper JSON = new ObjectMapper();

    private final String baseUrl;
    private final String model;
    private final Duration requestTimeout;
    private final HttpClient http;

    public OllamaAdapter(String baseUrl, String model) {
        this(baseUrl, model, DEFAULT_REQUEST_TIMEOUT);
    }

    /** Lets tests use a short timeout. */
    OllamaAdapter(String baseUrl, String model, Duration requestTimeout) {
        this.baseUrl = stripTrailingSlash(baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl.trim());
        this.model = model == null || model.isBlank() ? DEFAULT_MODEL : model.trim();
        this.requestTimeout = requestTimeout;
        this.http = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                // Ollama is normally local; never send it through a corporate/system proxy
                .proxy(HttpClient.Builder.NO_PROXY)
                .build();
    }

    @Override
    public String generate(String prompt) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/chat"))
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(prompt), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException e) {
            throw new AIServiceException("The local model (" + model + ") did not answer within "
                    + requestTimeout.toSeconds() + " seconds.", e);
        } catch (ConnectException e) {
            throw new AIServiceException("Could not reach Ollama at " + baseUrl
                    + ". Is it running (ollama serve)?", e);
        } catch (IOException e) {
            throw new AIServiceException("Could not reach Ollama at " + baseUrl + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AIServiceException("The request to Ollama was interrupted.", e);
        }
        return extractText(response.statusCode(), response.body());
    }

    /** Translates a plain prompt into Ollama's /api/chat JSON body. */
    String buildRequestBody(String prompt) {
        ObjectNode body = JSON.createObjectNode();
        body.put("model", model);
        body.put("stream", false);
        var messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", AIAdapter.SYSTEM_PROMPT);
        messages.addObject().put("role", "user").put("content", prompt);
        body.putObject("options").put("temperature", TEMPERATURE);
        return body.toString();
    }

    /** Translates Ollama's reply back into plain text. */
    String extractText(int status, String body) {
        if (status == 404) {
            throw new AIServiceException("Ollama does not have the model '" + model
                    + "'. Run: ollama pull " + model);
        }
        if (status < 200 || status >= 300) {
            throw new AIServiceException("Ollama returned an error (HTTP " + status + "): " + errorText(body));
        }
        String text;
        try {
            text = JSON.readTree(body).path("message").path("content").asText("");
        } catch (IOException e) {
            throw new AIServiceException("Ollama returned a reply that is not valid JSON.", e);
        }
        text = stripThinking(text).trim();
        if (text.isEmpty()) {
            throw new AIServiceException("The local model returned an empty answer.");
        }
        return text;
    }

    /**
     * Reasoning models (e.g. deepseek-r1, qwen3) put their thinking in <think> tags
     * before the answer.
     */
    static String stripThinking(String text) {
        return text.replaceAll("(?s)<think>.*?</think>", "");
    }

    private static String errorText(String body) {
        try {
            JsonNode error = JSON.readTree(body).path("error");
            if (error.isTextual()) {
                return error.asText();
            }
        } catch (IOException ignored) {
            // fall through to the raw body
        }
        return body == null ? "" : body.trim();
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public String getModel() {
        return model;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    @Override
    public String getName() {
        return "Ollama (" + model + ")";
    }
}
