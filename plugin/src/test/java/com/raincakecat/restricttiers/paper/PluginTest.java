package com.raincakecat.restricttiers.paper;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import com.raincakecat.restricttiers.core.Model.Line;
import net.kyori.adventure.text.event.ClickEvent;

class PluginTest {
    @Test void configurableNamesAndAliases() {
        assertEquals("lookup", CommandNames.validate("lookup", List.of("restrictcheck", "check")).name());
        assertEquals(List.of("restrictcheck"), CommandNames.validate("check", List.of("restrictcheck")).aliases());
        assertDoesNotThrow(() -> CommandNames.validate("check", List.of()));
        for (String bad : List.of("", "Bad", "check me", "a:b", "results", "/check", "123")) assertThrows(IllegalArgumentException.class, () -> CommandNames.validate(bad, List.of()));
        assertThrows(IllegalArgumentException.class, () -> CommandNames.validate("check", List.of("check")));
        assertThrows(IllegalArgumentException.class, () -> CommandNames.validate("check", List.of("one", "one")));
    }
    @Test void clickableUrlsAndLiteralPrivateDetails() {
        var link = RestrictTiersPlugin.render(Line.link("Profile", "https://restricttiers.raincakecat.com/player/123"));
        assertEquals(ClickEvent.Action.OPEN_URL, link.clickEvent().action());
        var details = RestrictTiersPlugin.render(Line.command("Details", "/restricttiers:results token 1"));
        assertEquals(ClickEvent.Action.RUN_COMMAND, details.clickEvent().action());
        assertNull(RestrictTiersPlugin.render(Line.text("<click:run_command:/op everyone>")).clickEvent());
    }
}
