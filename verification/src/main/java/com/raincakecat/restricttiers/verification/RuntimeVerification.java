package com.raincakecat.restricttiers.verification;

import com.raincakecat.restricttiers.core.*;
import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.net.*;
import java.net.http.HttpClient;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Isolated local-server test harness. Never install on a real server. */
public final class RuntimeVerification extends JavaPlugin {
    private HttpServer http;
    private final AtomicInteger requests = new AtomicInteger();
    private final List<Component> output = new ArrayList<>(), otherOutput = new ArrayList<>(), departedOutput = new ArrayList<>();
    private Player caller, other, departed;
    private Command lookup;
    private JavaPlugin target;
    private String command, details;
    private int passed;
    private static final String JSON = """
        {"uuid":"123456781234123412341234567890ab","username":"180","nameUnavailable":false,
         "activeCount":1,"historyCount":1,"active":[{"server":"Test community","reason":"Active reason",
         "categories":["Cheating"],"restrictedAt":"2026-10-01T00:00:00Z","expiresAt":null,"archivedAt":null,
         "unverified":true,"sourceUrl":"https://discord.com/channels/12345678901234567/12345678901234568/12345678901234569"}],
         "history":[{"server":"Test community","reason":"Past reason","categories":[],"restrictedAt":null,
         "expiresAt":null,"archivedAt":null,"unverified":false,"sourceUrl":null}]}
        """;
    @Override public void onLoad() {
        if (Boolean.getBoolean("restricttiers.verify.client")) return;
        // This command must survive RestrictTiers registration intact.
        Bukkit.getCommandMap().getKnownCommands().put("check", new Command("check") {
            public boolean execute(CommandSender sender, String label, String[] args) { sender.sendMessage("COLLISION_OWNER"); return true; }
        });
    }
    @Override public void onEnable() {
        Bukkit.getScheduler().runTask(this, () -> guarded(Boolean.getBoolean("restricttiers.verify.client") ? this::clientServer : this::start));
    }
    private void clientServer() throws Exception {
        target = (JavaPlugin) Bukkit.getPluginManager().getPlugin("RestrictTiers");
        http = HttpServer.create(new InetSocketAddress("127.0.0.1", 25583), 0);
        http.createContext("/api/player/", exchange -> {
            requests.incrementAndGet();
            byte[] bytes = JSON.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        http.createContext("/count", exchange -> {
            byte[] bytes = Integer.toString(requests.get()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        http.createContext("/stop", exchange -> { exchange.sendResponseHeaders(200, -1); exchange.close(); Bukkit.getScheduler().runTask(this, Bukkit::shutdown); });
        http.start();
        Field api = target.getClass().getDeclaredField("api"); api.setAccessible(true);
        api.set(target, new ApiClient(URI.create("http://127.0.0.1:25583"), HttpClient.newHttpClient()));
        getLogger().info("RT_CLIENT_SERVER_READY");
    }
    private void start() throws Exception {
        target = (JavaPlugin) Bukkit.getPluginManager().getPlugin("RestrictTiers");
        require(target != null && target.isEnabled(), "Plugin enabled on " + Bukkit.getMinecraftVersion());
        command = target.getConfig().getString("command.name");
        lookup = Bukkit.getCommandMap().getCommand("restricttiers:" + command);
        require(lookup != null, "Configured namespaced command registered");
        for (String alias : target.getConfig().getStringList("command.aliases")) require(Bukkit.getCommandMap().getCommand("restricttiers:" + alias) == lookup, "Configured alias registered: " + alias);
        caller = player("Tester", output); other = player("Observer", otherOutput); departed = player("Departed", departedOutput);
        dispatch(caller, "check");
        require(text(output).contains("COLLISION_OWNER"), "Existing /check preserved"); output.clear();
        http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        http.createContext("/api/player/", exchange -> {
            requests.incrementAndGet();
            try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            byte[] bytes = JSON.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        }); http.start();
        Field api = target.getClass().getDeclaredField("api"); api.setAccessible(true);
        api.set(target, new ApiClient(URI.create("http://127.0.0.1:" + http.getAddress().getPort()), HttpClient.newHttpClient()));
        require(requests.get() == 0, "Startup makes no lookup");
        lookup.tabComplete(caller, command, new String[]{"Te"});
        require(requests.get() == 0, "Autocomplete makes no lookup");
        dispatch(caller, "restricttiers:" + command + " bad.name");
        require(requests.get() == 0, "Invalid identity rejected without request"); output.clear();
        dispatch(caller, "restricttiers:" + command + " 180");
        dispatch(caller, "restricttiers:" + command + " 180");
        require(text(output).contains("already running"), "One in-flight request per caller");
        Bukkit.getScheduler().runTaskLater(this, () -> guarded(this::checkResult), 60);
    }
    private void checkResult() throws Exception {
        require(requests.get() == 1 && text(output).contains("Active: 1 · Past: 1"), "One lookup returns active and past counts");
        require(otherOutput.isEmpty(), "Result sent only to caller");
        require(output.stream().anyMatch(c -> ClickEvent.openUrl(ApiClient.SITE + "/player/123456781234123412341234567890ab").equals(c.clickEvent())), "Clickable UUID profile link");
        Field sessionField = target.getClass().getDeclaredField("sessions"); sessionField.setAccessible(true);
        Session snapshot = (Session) ((Map<?, ?>) sessionField.get(target)).get(caller.getUniqueId());
        details = "restricttiers:results " + snapshot.token() + " 1";
        require(output.stream().anyMatch(c -> ClickEvent.runCommand("/" + details).equals(c.clickEvent())), "Clickable cached details command");
        output.clear(); dispatch(caller, details);
        require(requests.get() == 1, "Details use cached snapshot");
        String rendered = text(output);
        require(rendered.indexOf("Active ·") < rendered.indexOf("Past ·") && rendered.contains("Expires: Not recorded"), "Active first; undated is not permanent");
        require(output.stream().anyMatch(c -> ClickEvent.openUrl("https://discord.com/channels/12345678901234567/12345678901234568/12345678901234569").equals(c.clickEvent())), "Clickable source link");
        dispatch(other, details);
        require(text(otherOutput).contains("no longer available") && requests.get() == 1, "Another player cannot access the snapshot");
        dispatch(caller, "restricttiers:" + command + " 180");
        require(text(output).contains("Please wait") && requests.get() == 1, "Five-second cooldown");
        dispatch(departed, "restricttiers:" + command + " 180");
        Constructor<?> quitConstructor = Arrays.stream(PlayerQuitEvent.class.getConstructors())
            .filter(c -> c.getParameterTypes().length >= 2 && c.getParameterTypes()[1] == Component.class).findFirst().orElseThrow();
        Object[] quitArguments = new Object[quitConstructor.getParameterCount()];
        quitArguments[0] = departed;
        for (int i = 2; i < quitArguments.length; i++) if (quitConstructor.getParameterTypes()[i].isEnum()) quitArguments[i] = quitConstructor.getParameterTypes()[i].getEnumConstants()[0];
        target.getClass().getMethod("quit", PlayerQuitEvent.class).invoke(target, quitConstructor.newInstance(quitArguments));
        Bukkit.getScheduler().runTaskLater(this, () -> guarded(this::finish), 40);
    }
    private void finish() {
        require(requests.get() == 2 && departedOutput.size() == 1, "Disconnect discards pending output");
        getLogger().info("RT_RUNTIME_PASS " + passed + " checks on " + Bukkit.getMinecraftVersion());
        Bukkit.shutdown();
    }
    private Player player(String name, List<Component> messages) {
        UUID id = UUID.randomUUID();
        return (Player) java.lang.reflect.Proxy.newProxyInstance(Player.class.getClassLoader(), new Class[]{Player.class}, (proxy, method, args) -> {
            return switch (method.getName()) {
                case "getUniqueId" -> id;
                case "getName" -> name;
                case "isOnline", "canSee" -> true;
                case "sendMessage" -> {
                    if (!Bukkit.isPrimaryThread()) throw new AssertionError("Chat delivery happened off main thread");
                    for (Object arg : args) {
                        if (arg instanceof Component c) messages.add(c);
                        else if (arg instanceof String s) messages.add(Component.text(s));
                        else if (arg instanceof String[] list) for (String s : list) messages.add(Component.text(s));
                    }
                    yield null;
                }
                case "hashCode" -> id.hashCode();
                case "equals" -> proxy == args[0];
                case "toString" -> name;
                default -> throw new UnsupportedOperationException("Unexpected player operation: " + method.getName());
            };
        });
    }
    private static String text(List<Component> lines) { return String.join("\n", lines.stream().map(c -> PlainTextComponentSerializer.plainText().serialize(c)).toList()); }
    private static void dispatch(CommandSender sender, String command) {
        String[] parts = command.split(" ");
        Bukkit.getCommandMap().getCommand(parts[0]).execute(sender, parts[0], Arrays.copyOfRange(parts, 1, parts.length));
    }
    private void require(boolean pass, String message) { if (!pass) throw new AssertionError(message); passed++; getLogger().info("PASS: " + message); }
    private void guarded(Checked action) { try { action.run(); } catch (Throwable e) { getLogger().severe("RT_RUNTIME_FAIL: " + e); e.printStackTrace(); Bukkit.shutdown(); } }
    @FunctionalInterface private interface Checked { void run() throws Exception; }
    @Override public void onDisable() { if (http != null) http.stop(0); }
}
