package com.smartplate.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Adapter pattern - a third Adapter, for Google's Gemini API.
 *
 * <p>
 * Works like AIAdapter and OllamaAdapter: it implements the AIModel interface
 * the agent
 * expects, translates {@code generate(prompt)} into a Gemini
 * {@code generateContent} REST
 * request, and translates the JSON reply back into plain text. Errors become
 * AIServiceException, so the agent needs no changes. It uses only the JDK's
 * HttpClient and
 * Jackson (already on the classpath through the Anthropic SDK).
 */
public class GeminiAdapter implements AIModel {

    public static final String API_KEY_VARIABLE = "GEMINI_API_KEY";
    public static final String MODEL_VARIABLE = "GEMINI_MODEL";
    /**
     * Google's alias for its newest Flash model, so the default keeps working as
     * models are retired.
     */
    public static final String DEFAULT_MODEL = "GEMINI_MODEL";
    public static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(120);

    private static final ObjectMapper JSON = new ObjectMapper();

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final Duration requestTimeout;
    private final HttpClient http;

    public GeminiAdapter(String apiKey, String model) {
        this(apiKey, model, DEFAULT_BASE_URL, DEFAULT_REQUEST_TIMEOUT);
    }

    /** Lets tests point the adapter at a fake server with a short timeout. */
    GeminiAdapter(String apiKey, String model, String baseUrl, Duration requestTimeout) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null || model.isBlank() ? DEFAULT_MODEL : model.trim();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.requestTimeout = requestTimeout;
        this.http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    }

    public boolean isConfigured() {
        return !apiKey.isEmpty();
    }

    @Override
    public String generate(String prompt) {
        if (!isConfigured()) {
            throw new AIServiceException("No API key: set " + API_KEY_VARIABLE + " to use Gemini.");
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/v1beta/models/"
                + URLEncoder.encode(model, StandardCharsets.UTF_8) + ":generateContent"))
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                // the key goes in a header, never in the URL, so it cannot end up in logs
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(prompt), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException e) {
            throw new AIServiceException("Gemini did not answer within " + requestTimeout.toSeconds() + " seconds.", e);
        } catch (IOException e) {
            throw new AIServiceException("Could not reach Gemini: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AIServiceException("The request to Gemini was interrupted.", e);
        }
        return extractText(response.statusCode(), response.body());
    }

    /** Translates a plain prompt into the generateContent JSON body. */
    String buildRequestBody(String prompt) {
        ObjectNode body = JSON.createObjectNode();
        body.putObject("systemInstruction").putArray("parts").addObject().put("text", AIAdapter.SYSTEM_PROMPT);
        ObjectNode user = body.putArray("contents").addObject();
        user.put("role", "user");
        user.putArray("parts").addObject().put("text", prompt);
        return body.toString();
    }

    /** Translates Gemini's reply back into plain text. */
    String extractText(int status, String body) {
        if (status < 200 || status >= 300) {
            throw new AIServiceException(describeError(status, body));
        }
        JsonNode root;
        try {
            root = JSON.readTree(body);
        } catch (IOException e) {
            throw new AIServiceException("Gemini returned a reply that is not valid JSON.", e);
        }
        String blocked = root.path("promptFeedback").path("blockReason").asText("");
        if (!blocked.isEmpty()) {
            throw new AIServiceException("Gemini declined this request (" + blocked + ").");
        }
        JsonNode candidate = root.path("candidates").path(0);
        StringBuilder text = new StringBuilder();
        for (JsonNode part : candidate.path("content").path("parts")) {
            if (!part.path("thought").asBoolean(false)) { // skip thinking summaries, keep the answer
                text.append(part.path("text").asText(""));
            }
        }
        String answer = text.toString().trim();
        if (answer.isEmpty()) {
            String reason = candidate.path("finishReason").asText("");
            throw new AIServiceException("Gemini returned an empty answer"
                    + (reason.isEmpty() ? "." : " (" + reason + ")."));
        }
        return answer;
    }

    private String describeError(int status, String body) {
        String message = "";
        try {
            message = JSON.readTree(body).path("error").path("message").asText("");
        } catch (IOException ignored) {
            // not JSON; use the status code alone
        }
        if (status == 400 && message.toLowerCase().contains("api key")
                || status == 401 || status == 403) {
            return "Gemini rejected the API key. Check " + API_KEY_VARIABLE + ".";
        }
        if (status == 404) {
            return "Gemini does not know the model '" + model + "'. Check " + MODEL_VARIABLE + ".";
        }
        if (status == 429) {
            return "Gemini is busy or your quota is used up (rate limited). Try again shortly.";
        }
        return "Gemini returned an error (HTTP " + status + ")" + (message.isEmpty() ? "." : ": " + message);
    }

    public String getModel() {
        return model;
    }

    @Override
    public String getName() {
        return "Gemini (" + model + ")";
    }
}
