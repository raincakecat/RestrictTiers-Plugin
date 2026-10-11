package com.raincakecat.restricttiers.paper;

import java.util.*;

public record CommandNames(String name, List<String> aliases) {
    public static CommandNames validate(String name, List<String> aliases) {
        List<String> validated = new ArrayList<>();
        if (!valid(name)) throw new IllegalArgumentException("command.name must start with a lowercase letter and contain only a–z, 0–9, underscore or hyphen (maximum 32 characters).");
        for (String alias : aliases) {
            if (!valid(alias)) throw new IllegalArgumentException("Invalid command alias: " + alias);
            if (alias.equals(name) || validated.contains(alias)) throw new IllegalArgumentException("Command names and aliases must be unique.");
            validated.add(alias);
        }
        return new CommandNames(name, List.copyOf(validated));
    }
    private static boolean valid(String name) { return name != null && name.matches("[a-z][a-z0-9_-]{0,31}") && !name.equals("results"); }
}
