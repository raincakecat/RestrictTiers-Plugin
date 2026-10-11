package com.raincakecat.restricttiers.core;

import java.util.List;

public final class Model {
    private Model() {}
    public record Restriction(String server, String reason, List<String> categories,
            String restrictedAt, String expiresAt, String archivedAt, boolean unverified, String sourceUrl) {}
    public record Player(String uuid, String username, boolean nameUnavailable,
            List<Restriction> active, int activeCount, List<Restriction> history, int historyCount) {
        public String label() { return username == null || username.isBlank() ? uuid : username; }
        public String url() { return ApiClient.SITE + "/player/" + uuid; }
        public boolean incomplete() { return activeCount > active.size() || historyCount > history.size(); }
    }
    public record Line(String text, String url, String command) {
        public static Line text(String text) { return new Line(text, null, null); }
        public static Line link(String text, String url) { return new Line(text, url, null); }
        public static Line command(String text, String command) { return new Line(text, null, command); }
    }
}
