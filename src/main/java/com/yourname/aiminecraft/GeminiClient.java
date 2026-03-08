package com.yourname.aiminecraft;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public class GeminiClient {
    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;
    private final Gson gson;
    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

    public GeminiClient(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
        this.httpClient = HttpClient.newHttpClient();
        this.gson = new Gson();
    }

    public CompletableFuture<String> generateResponse(String prompt) {
        // Build the JSON request body
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

        String jsonBody = gson.toJson(root);
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
                    // Parse response to extract the text
                    JsonObject resJson = gson.fromJson(response.body(), JsonObject.class);
                    try {
                        return resJson.getAsJsonArray("candidates")
                                .get(0).getAsJsonObject()
                                .getAsJsonObject("content")
                                .getAsJsonArray("parts")
                                .get(0).getAsJsonObject()
                                .get("text").getAsString();
                    } catch (Exception e) {
                        System.err.println("Gemini API Parse Error: " + e.getMessage());
                        return "SKIP";
                    }
                });
    }
}
