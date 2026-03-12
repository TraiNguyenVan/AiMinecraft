package com.yourname.aiminecraft;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;

public class GeminiClient {
    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;
    private final Gson gson;
    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

    private boolean groundingEnabled = false;
    private boolean includeGroundingMetadata = false;

    public GeminiClient(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
        this.httpClient = HttpClient.newHttpClient();
        this.gson = new Gson();
    }

    public void setGroundingEnabled(boolean enabled) {
        this.groundingEnabled = enabled;
    }

    public void setIncludeGroundingMetadata(boolean include) {
        this.includeGroundingMetadata = include;
    }

    /** Text-only request (original method, unchanged). */
    public CompletableFuture<String> generateResponse(String prompt) {
        JsonObject root = new JsonObject();
        JsonArray contents = new JsonArray();
        JsonObject contentObj = new JsonObject();
        JsonArray parts = new JsonArray();
        JsonObject textPart = new JsonObject();

        textPart.addProperty("text", prompt);
        parts.add(textPart);
        contentObj.add("parts", parts);
        contents.add(contentObj);
        root.add("contents", contents);

        return sendRequest(root);
    }

    /**
     * Multimodal request: sends text + a PNG image to Gemini.
     * The image is base64-encoded and sent as inline_data.
     */
    public CompletableFuture<String> generateResponse(String prompt, byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return generateResponse(prompt); // fallback to text-only
        }

        JsonObject root = new JsonObject();
        JsonArray contents = new JsonArray();
        JsonObject contentObj = new JsonObject();
        JsonArray parts = new JsonArray();

        // Text part
        JsonObject textPart = new JsonObject();
        textPart.addProperty("text", prompt);
        parts.add(textPart);

        // Image part
        JsonObject imagePart = new JsonObject();
        JsonObject inlineData = new JsonObject();
        inlineData.addProperty("mime_type", "image/png");
        inlineData.addProperty("data", Base64.getEncoder().encodeToString(imageBytes));
        imagePart.add("inline_data", inlineData);
        parts.add(imagePart);

        contentObj.add("parts", parts);
        contents.add(contentObj);
        root.add("contents", contents);

        return sendRequest(root);
    }

    /** Shared method to send the request and parse the response. */
    private CompletableFuture<String> sendRequest(JsonObject requestBody) {
        if (groundingEnabled) {
            JsonArray tools = new JsonArray();
            JsonObject tool = new JsonObject();
            tool.add("google_search_retrieval", new JsonObject());
            tools.add(tool);
            requestBody.add("tools", tools);
        }

        String jsonBody = gson.toJson(requestBody);
        String apiUrl = BASE_URL + model + ":generateContent?key=" + apiKey;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        System.err.println("Gemini API Error: " + response.statusCode() + " " + response.body());
                        return "SKIP";
                    }
                    JsonObject resJson = gson.fromJson(response.body(), JsonObject.class);
                    try {
                        JsonObject candidate = resJson.getAsJsonArray("candidates").get(0).getAsJsonObject();
                        String text = candidate.getAsJsonObject("content")
                                .getAsJsonArray("parts")
                                .get(0).getAsJsonObject()
                                .get("text").getAsString();

                        if (includeGroundingMetadata && candidate.has("groundingMetadata")) {
                            // Optionally append a verification badge if the AI used search
                            text += " \u2713"; // Checkmark icon
                        }

                        return text;
                    } catch (Exception e) {
                        System.err.println("Gemini API Parse Error: " + e.getMessage());
                        return "SKIP";
                    }
                });
    }
}
