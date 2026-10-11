package com.raincakecat.restricttiers.core;

import com.google.gson.*;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.nio.ByteBuffer;
import java.util.regex.Pattern;
import static com.raincakecat.restricttiers.core.Model.*;

public final class ApiClient {
    public static final String SITE = "https://restricttiers.raincakecat.com";
    private static final Pattern IGN = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Pattern UUID = Pattern.compile("(?:[a-fA-F0-9]{32}|[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12})");
    private final HttpClient http;
    private final URI base;
    private volatile long blockedUntil;
    public ApiClient() { this(URI.create(SITE), HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build()); }
    public ApiClient(URI base, HttpClient http) { this.base = base; this.http = http; }
    public static String identifier(String raw) {
        String value = raw.trim();
        if (UUID.matcher(value).matches()) return value.toLowerCase(Locale.ROOT).replace("-", "");
        if (IGN.matcher(value).matches()) return value;
        throw new IllegalArgumentException("Enter a complete Minecraft IGN (3–16 letters, numbers or underscores) or UUID.");
    }
    public record Outcome(Player player, String error, long retrySeconds) {
        public boolean success() { return player != null; }
    }
    public CompletableFuture<Outcome> lookup(String raw) {
        String value = identifier(raw);
        long wait = blockedUntil - System.currentTimeMillis();
        if (wait > 0) {
            long seconds = (wait + 999) / 1000;
            return CompletableFuture.completedFuture(new Outcome(null, "Rate limited. Try again in " + seconds + " seconds.", seconds));
        }
        HttpRequest request = HttpRequest.newBuilder(base.resolve("/api/player/" + value))
            .timeout(Duration.ofSeconds(15)).header("Accept", "application/json")
            .header("User-Agent", "RestrictTiers-Minecraft/1.0.0").GET().build();
        return http.sendAsync(request, info -> new BoundedBody()).thenApplyAsync(response -> {
            try {
                return switch (response.statusCode()) {
                    case 200 -> {
                        byte[] bytes = response.body();
                        yield new Outcome(parse(new String(bytes, java.nio.charset.StandardCharsets.UTF_8), value), null, 0);
                    }
                    case 400 -> new Outcome(null, "Invalid Minecraft name or UUID.", 0);
                    case 404 -> new Outcome(null, "Player not found. Check the IGN or use their UUID.", 0);
                    case 429 -> {
                        long seconds = retrySeconds(response.headers().firstValue("Retry-After").orElse("60"));
                        blockedUntil = Math.max(blockedUntil, System.currentTimeMillis() + seconds * 1000);
                        yield new Outcome(null, "Rate limited. Try again in " + seconds + " seconds.", seconds);
                    }
                    default -> new Outcome(null, "Restriction service unavailable. No result was confirmed.", 0);
                };
            } catch (Exception e) { return new Outcome(null, "Restriction service returned an invalid response. No result was confirmed.", 0); }
        }).exceptionally(e -> new Outcome(null, "Could not reach the restriction service. No result was confirmed.", 0));
    }
    /** Bound allocation without completing early at headers; request timeout covers body reception. */
    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private Flow.Subscription subscription;
        private int received;
        private boolean done;
        public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
        public void onSubscribe(Flow.Subscription subscription) { this.subscription = subscription; delegate.onSubscribe(subscription); }
        public void onNext(List<ByteBuffer> chunks) {
            if (done) return;
            for (ByteBuffer chunk : chunks) {
                if (chunk.remaining() > 1_048_576 - received) {
                    done = true; subscription.cancel(); delegate.onError(new IllegalArgumentException("Response too large")); return;
                }
                received += chunk.remaining();
            }
            delegate.onNext(chunks);
        }
        public void onError(Throwable error) { if (!done) { done = true; delegate.onError(error); } }
        public void onComplete() { if (!done) { done = true; delegate.onComplete(); } }
    }
    static long retrySeconds(String header) {
        try { return Math.max(1, Math.min(86400, Long.parseLong(header))); }
        catch (Exception ignored) {
            try { return Math.max(1, Math.min(86400, Duration.between(Instant.now(), ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).getSeconds())); }
            catch (Exception e) { return 60; }
        }
    }
    public static Player parse(String json, String requested) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        String uuid = string(root, "uuid");
        if (uuid == null || !UUID.matcher(uuid).matches()) throw new IllegalArgumentException("Missing UUID");
        uuid = identifier(uuid);
        if (UUID.matcher(requested).matches() && !uuid.equals(identifier(requested))) throw new IllegalArgumentException("UUID mismatch");
        String name = string(root, "username");
        if (name != null && !IGN.matcher(name).matches()) throw new IllegalArgumentException("Invalid username");
        List<Restriction> active = restrictions(root, "active", 100), past = restrictions(root, "history", 50);
        int activeCount = count(root, "activeCount"), historyCount = count(root, "historyCount");
        if (activeCount < active.size() || historyCount < past.size()) throw new IllegalArgumentException("Invalid counts");
        return new Player(uuid, name, root.get("nameUnavailable").getAsBoolean(), active, activeCount, past, historyCount);
    }
    private static int count(JsonObject root, String key) {
        int n = root.get(key).getAsInt();
        if (n < 0) throw new IllegalArgumentException("Negative count");
        return n;
    }
    private static List<Restriction> restrictions(JsonObject root, String key, int limit) {
        JsonArray array = root.getAsJsonArray(key);
        if (array.size() > limit) throw new IllegalArgumentException("Too many records");
        List<Restriction> rows = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject r = element.getAsJsonObject();
            List<String> categories = new ArrayList<>();
            for (JsonElement c : r.getAsJsonArray("categories")) categories.add(clean(c.getAsString()));
            String source = string(r, "sourceUrl");
            if (source != null && !source.matches("https://discord\\.com/channels/[1-9][0-9]{16,19}/[1-9][0-9]{16,19}/[1-9][0-9]{16,19}")) source = null;
            rows.add(new Restriction(string(r, "server"), string(r, "reason"), List.copyOf(categories),
                string(r, "restrictedAt"), string(r, "expiresAt"), string(r, "archivedAt"), r.get("unverified").getAsBoolean(), source));
        }
        return List.copyOf(rows);
    }
    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? null : clean(value.getAsString());
    }
    public static String clean(String value) {
        return value.replaceAll("[\\p{Cntrl}§]", " ").strip();
    }
}
