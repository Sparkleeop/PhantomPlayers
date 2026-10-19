package me.sparklee.phantomplayers.skin;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

public final class MojangProfileLookup implements ProfileLookup {

    private static final String NAME_ENDPOINT = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String SESSION_ENDPOINT = "https://sessionserver.mojang.com/session/minecraft/profile/";

    private final HttpClient client;
    private final Gson gson = new Gson();
    private final Duration requestTimeout;
    private final String userAgent;

    public MojangProfileLookup(int timeoutSeconds, String userAgent) {
        this.requestTimeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
        this.userAgent = userAgent;
        this.client = HttpClient.newBuilder()
                .connectTimeout(requestTimeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public LookupOutcome lookup(String username) throws IOException, InterruptedException {
        String encodedName = URLEncoder.encode(username, StandardCharsets.UTF_8);
        HttpResponse<String> nameResponse = get(NAME_ENDPOINT + encodedName);
        int nameStatus = nameResponse.statusCode();
        if (nameStatus == 404 || nameStatus == 204) {
            return LookupOutcome.notFound("username is not registered");
        }
        if (nameStatus == 429) {
            return LookupOutcome.error("Mojang rate limit reached (HTTP 429)");
        }
        if (nameStatus != 200) {
            return LookupOutcome.error("profile name lookup returned HTTP " + nameStatus);
        }

        NameResponse name;
        try {
            name = gson.fromJson(nameResponse.body(), NameResponse.class);
        } catch (JsonSyntaxException exception) {
            return LookupOutcome.error("profile name response was not valid JSON");
        }
        if (name == null || name.id == null || name.id.isBlank()) {
            return LookupOutcome.error("profile name response did not contain an id");
        }

        HttpResponse<String> sessionResponse = get(SESSION_ENDPOINT + name.id);
        int sessionStatus = sessionResponse.statusCode();
        if (sessionStatus == 204 || sessionStatus == 404) {
            return LookupOutcome.found(SkinData.EMPTY);
        }
        if (sessionStatus == 429) {
            return LookupOutcome.error("Mojang rate limit reached (HTTP 429)");
        }
        if (sessionStatus != 200) {
            return LookupOutcome.error("session profile lookup returned HTTP " + sessionStatus);
        }

        SessionResponse session;
        try {
            session = gson.fromJson(sessionResponse.body(), SessionResponse.class);
        } catch (JsonSyntaxException exception) {
            return LookupOutcome.error("session profile response was not valid JSON");
        }
        if (session == null) {
            return LookupOutcome.error("session profile response was empty");
        }
        if (session.properties != null) {
            for (PropertyResponse property : session.properties) {
                if (property != null && "textures".equals(property.name) && property.value != null) {
                    return LookupOutcome.found(new SkinData(property.value, property.signature));
                }
            }
        }
        return LookupOutcome.found(SkinData.EMPTY);
    }

    private HttpResponse<String> get(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .header("User-Agent", userAgent)
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static final class NameResponse {
        String id;
        String name;
    }

    private static final class SessionResponse {
        String id;
        String name;
        List<PropertyResponse> properties;
    }

    private static final class PropertyResponse {
        String name;
        String value;
        String signature;
    }
}
