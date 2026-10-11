package com.raincakecat.restricttiers.core;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static com.raincakecat.restricttiers.core.Model.*;

public final class Results {
    private static final int PAGE_SIZE = 3;
    private Results() {}
    public static List<Line> summary(Player player, String navigation, String token) {
        List<Line> lines = new ArrayList<>();
        lines.add(Line.text("RestrictTiers · " + player.label()));
        lines.add(Line.text("UUID: " + player.uuid()));
        if (player.nameUnavailable()) lines.add(Line.text("Current name unavailable; results are identified by UUID."));
        lines.add(Line.text("Active: " + player.activeCount() + " · Past: " + player.historyCount()));
        if (player.activeCount() == 0 && player.historyCount() == 0) lines.add(Line.text("No recorded restrictions found."));
        lines.add(Line.link("[Open player profile]", player.url()));
        if (!player.active().isEmpty() || !player.history().isEmpty()) lines.add(Line.command("[View details]", navigation + " " + token + " 1"));
        if (player.incomplete()) lines.add(Line.text("Only the returned records are loaded. See the website for the complete list."));
        return List.copyOf(lines);
    }
    public static List<Line> page(Player player, int page, String navigation, String token) {
        int size = player.active().size() + player.history().size(), pages = Math.max(1, (size + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page < 1 || page > pages) return List.of(Line.text("Choose a page from 1 to " + pages + "."));
        List<Line> lines = new ArrayList<>();
        lines.add(Line.text("RestrictTiers · " + player.label() + " · Page " + page + "/" + pages));
        for (int i = (page - 1) * PAGE_SIZE; i < Math.min(size, page * PAGE_SIZE); i++) {
            boolean active = i < player.active().size();
            Restriction r = active ? player.active().get(i) : player.history().get(i - player.active().size());
            lines.add(Line.text((active ? "Active" : "Past") + " · " + Objects.requireNonNullElse(r.server(), "Unknown community")));
            String reason = Objects.requireNonNullElse(r.reason(), "No reason provided");
            // Render literal text, never parse record content as chat formatting or commands.
            for (int start = 0; start < reason.length(); start += 240) lines.add(Line.text(reason.substring(start, Math.min(start + 240, reason.length()))));
            if (!r.categories().isEmpty()) lines.add(Line.text("Categories: " + String.join(", ", r.categories())));
            lines.add(Line.text("Recorded: " + date(r.restrictedAt()) + " · Expires: " + date(r.expiresAt())));
            if (r.archivedAt() != null) lines.add(Line.text("Archived: " + date(r.archivedAt())));
            if (r.unverified()) lines.add(Line.text("Unverified record"));
            if (r.sourceUrl() != null) lines.add(Line.link("[View source]", r.sourceUrl()));
        }
        if (page > 1) lines.add(Line.command("[Previous page]", navigation + " " + token + " " + (page - 1)));
        if (page < pages) lines.add(Line.command("[Next page]", navigation + " " + token + " " + (page + 1)));
        lines.add(Line.link("[Open player profile]", player.url()));
        if (player.incomplete()) lines.add(Line.text("Additional records are available on the website."));
        return List.copyOf(lines);
    }
    private static String date(String value) {
        if (value == null) return "Not recorded";
        try { return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC).format(Instant.parse(value)); }
        catch (Exception ignored) { return "Not recorded"; }
    }
}
