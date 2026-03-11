package com.yourname.aiminecraft;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * Lightweight Google Custom Search client used to sanity‑check factual answers.
 *
 * It returns a short, human‑readable summary built from the top search results,
 * or an empty string if the lookup fails.
 */
public class SearchClient {

    private static final String BASE_URL = "https://www.googleapis.com/customsearch/v1";

    private final String apiKey;
    private final String cx;
    private final HttpClient httpClient;
    private final Gson gson;

    public SearchClient(String apiKey, String cx) {
        this.apiKey = apiKey;
        this.cx = cx;
        this.httpClient = HttpClient.newHttpClient();
        this.gson = new Gson();
    }

    /**
     * Performs a web search and returns a short summary string that can be
     * appended to the AI's answer (e.g., "web check: ...").
     */
    public CompletableFuture<String> searchSummary(String query) {
        try {
            String encodedQ = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = BASE_URL + "?key=" + apiKey + "&cx=" + cx + "&num=3&q=" + encodedQ;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() != 200) {
                            System.err.println("Search API error: " + response.statusCode() + " " + response.body());
                            return "";
                        }

                        try {
                            JsonObject root = gson.fromJson(response.body(), JsonObject.class);
                            JsonArray items = root.has("items") ? root.getAsJsonArray("items") : null;
                            if (items == null || items.size() == 0) return "";

                            StringBuilder sb = new StringBuilder();
                            sb.append("web check (top results, may be imperfect): ");

                            int limit = Math.min(2, items.size());
                            for (int i = 0; i < limit; i++) {
                                JsonObject item = items.get(i).getAsJsonObject();
                                String title = item.has("title") ? item.get("title").getAsString() : "";
                                String snippet = item.has("snippet") ? item.get("snippet").getAsString() : "";
                                if (!title.isEmpty()) {
                                    if (sb.length() > 0) sb.append(" | ");
                                    sb.append(title);
                                }
                                if (!snippet.isEmpty()) {
                                    sb.append(": ").append(snippet.replace('\n', ' '));
                                }
                            }

                            String summary = sb.toString().trim();
                            int maxLen = 320;
                            if (summary.length() > maxLen) {
                                summary = summary.substring(0, maxLen).trim() + "…";
                            }
                            return summary;
                        } catch (Exception e) {
                            System.err.println("Search API parse error: " + e.getMessage());
                            return "";
                        }
                    });
        } catch (Exception e) {
            System.err.println("Search API unexpected error: " + e.getMessage());
            return CompletableFuture.completedFuture("");
        }
    }
}

