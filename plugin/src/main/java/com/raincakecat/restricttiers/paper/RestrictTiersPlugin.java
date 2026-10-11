package com.raincakecat.restricttiers.paper;

import com.raincakecat.restricttiers.core.*;
import com.raincakecat.restricttiers.core.Model.Line;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;

public final class RestrictTiersPlugin extends JavaPlugin implements Listener {
    private final Map<UUID, Session> sessions = new HashMap<>();
    private Session consoleSession = new Session();
    private final List<Command> registered = new ArrayList<>();
    private final ApiClient api = new ApiClient();
    private volatile boolean running;
    @Override public void onEnable() {
        saveDefaultConfig();
        CommandNames names;
        try { names = CommandNames.validate(getConfig().getString("command.name", "check"), getConfig().getStringList("command.aliases")); }
        catch (IllegalArgumentException e) { getLogger().severe(e.getMessage()); Bukkit.getPluginManager().disablePlugin(this); return; }
        running = true;
        Bukkit.getPluginManager().registerEvents(this, this);
        Command lookup = new Command(names.name(), "Privately check RestrictTiers", "/" + names.name() + " <IGN or UUID>", names.aliases()) {
            @Override public boolean execute(CommandSender sender, String label, String[] args) { return check(sender, label, args); }
            @Override public List<String> tabComplete(CommandSender sender, String label, String[] args) {
                if (args.length != 1 || !(sender instanceof Player viewer)) return List.of();
                String prefix = args[0].toLowerCase(Locale.ROOT);
                return Bukkit.getOnlinePlayers().stream().filter(viewer::canSee).map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted(String.CASE_INSENSITIVE_ORDER).toList();
            }
        };
        install(lookup, names.name(), true);
        for (String alias : names.aliases()) install(lookup, alias, true);
        Command details = new Command("results") {
            @Override public boolean execute(CommandSender sender, String label, String[] args) { return details(sender, args); }
            @Override public List<String> tabComplete(CommandSender sender, String label, String[] args) { return List.of(); }
        };
        install(details, "results", false);
        Bukkit.getOnlinePlayers().forEach(Player::updateCommands);
        getLogger().info("Private lookup ready: /" + names.name() + " (also /restricttiers:" + names.name() + "). No permissions required.");
    }
    private void install(Command command, String label, boolean publicAlias) {
        var map = Bukkit.getCommandMap();
        var known = map.getKnownCommands();
        // Paper forwards get/put into Brigadier. HashMap.putIfAbsent bypasses that bridge.
        if (known.containsKey("restricttiers:" + label)) throw new IllegalStateException("RestrictTiers namespaced command is already registered: " + label);
        known.put("restricttiers:" + label, command);
        if (publicAlias) {
            if (known.containsKey(label)) getLogger().warning("/" + label + " belongs to another plugin; use /restricttiers:" + label + ".");
            else known.put(label, command);
        }
        command.register(map);
        if (!registered.contains(command)) registered.add(command);
    }
    private Session session(CommandSender sender) {
        if (sender instanceof Player player) return sessions.computeIfAbsent(player.getUniqueId(), key -> new Session());
        return consoleSession;
    }
    private boolean check(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player) && !(sender instanceof ConsoleCommandSender)) { sender.sendMessage("Use this command as a player or from the console."); return true; }
        if (args.length != 1) { sender.sendMessage("Usage: /" + label + " <IGN or UUID>"); return true; }
        String identifier;
        try { identifier = ApiClient.identifier(args[0]); }
        catch (IllegalArgumentException e) { sender.sendMessage(e.getMessage()); return true; }
        Session session = session(sender);
        String blocked = session.begin(System.currentTimeMillis());
        if (blocked != null) { sender.sendMessage(blocked); return true; }
        sender.sendMessage("Checking RestrictTiers…");
        api.lookup(identifier).thenAccept(outcome -> {
            if (!running) return;
            try { Bukkit.getScheduler().runTask(this, () -> {
                if (!running || (sender instanceof Player player && (!player.isOnline() || sessions.get(player.getUniqueId()) != session))) return;
                session.complete(outcome, System.currentTimeMillis());
                if (!outcome.success()) { sender.sendMessage(outcome.error()); return; }
                send(sender, Results.summary(outcome.player(), "/restricttiers:results", session.token()));
            }); } catch (org.bukkit.plugin.IllegalPluginAccessException ignored) { /* Plugin was disabled while request completed. */ }
        });
        return true;
    }
    private boolean details(CommandSender sender, String[] args) {
        if (!(sender instanceof Player) && !(sender instanceof ConsoleCommandSender)) return true;
        Session session = session(sender);
        if (args.length != 2 || !session.token().equals(args[0]) || session.snapshot() == null) { sender.sendMessage("This result is no longer available. Run a new check."); return true; }
        try { send(sender, Results.page(session.snapshot(), Integer.parseInt(args[1]), "/restricttiers:results", session.token())); }
        catch (NumberFormatException e) { sender.sendMessage("Invalid result page."); }
        return true;
    }
    static Component render(Line line) {
        Component text = Component.text(line.text(), line.url() != null || line.command() != null ? NamedTextColor.AQUA : NamedTextColor.GRAY);
        if (line.url() != null) return text.clickEvent(ClickEvent.openUrl(line.url())).hoverEvent(HoverEvent.showText(Component.text("Open in browser")));
        if (line.command() != null) return text.clickEvent(ClickEvent.runCommand(line.command())).hoverEvent(HoverEvent.showText(Component.text("Show cached results")));
        return text;
    }
    private void send(CommandSender sender, List<Line> lines) { lines.forEach(line -> sender.sendMessage(render(line))); }
    @EventHandler public void quit(PlayerQuitEvent event) { sessions.remove(event.getPlayer().getUniqueId()); }
    @Override public void onDisable() {
        running = false;
        sessions.clear(); consoleSession = new Session();
        var known = Bukkit.getCommandMap().getKnownCommands();
        List<String> owned = known.entrySet().stream().filter(entry -> registered.contains(entry.getValue())).map(Map.Entry::getKey).toList();
        owned.forEach(known::remove);
        registered.forEach(command -> command.unregister(Bukkit.getCommandMap())); registered.clear();
    }
}
