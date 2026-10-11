package com.raincakecat.restricttiers.core;

import static com.raincakecat.restricttiers.core.Model.*;

/** Called only on the owning platform's main thread. */
public final class Session {
    private long nextLookup;
    private boolean busy;
    private Player snapshot;
    private String token = java.util.UUID.randomUUID().toString().replace("-", "");
    public String token() { return token; }
    public Player snapshot() { return snapshot; }
    public String begin(long now) {
        if (busy) return "A lookup is already running.";
        if (now < nextLookup) return "Please wait " + Math.max(1, (nextLookup - now + 999) / 1000) + " seconds before checking again.";
        busy = true;
        snapshot = null;
        token = java.util.UUID.randomUUID().toString().replace("-", "");
        nextLookup = now + 5000;
        return null;
    }
    public void complete(ApiClient.Outcome outcome, long now) {
        busy = false;
        snapshot = outcome.player();
        nextLookup = Math.max(nextLookup, now + outcome.retrySeconds() * 1000);
    }
}
