# Plugin verification

Verified on 2026-10-11. Ten shared core tests and two plugin tests pass. The same production plugin was exercised on actual Paper **1.21.11 build 132 / Java 21** and **26.3 build 175 / Java 25**.

The isolated server harness passes 17 checks on 1.21.11 and 19 on 26.3 (including custom command names and aliases). It uses simulated player senders to verify private delivery, clickable links, cached details, identity validation, autocomplete with no request, one in-flight lookup, cooldown, disconnect handling, preservation of another plugin's `/check`, and command cleanup at shutdown.

An actual Fabric client connected to Paper 1.21.11 also verified both packages together: `/check` makes one local lookup, `/restricttiers:check` invokes the plugin, and plugin detail navigation makes no additional request. Shared core tests verify service errors and `Retry-After`; a service failure never means no restrictions.

## Reproduce

Run `gradlew build` for the unit tests and packages. For runtime tests, create an isolated local Paper server of the required version, bind it to `127.0.0.1`, use the appropriate Java runtime, and accept the Minecraft EULA before starting it. Install the built plugin and `verification/build/libs/RestrictTiers-Verification-DO-NOT-INSTALL-1.0.0.jar` only into that disposable test server. The harness uses fictional data, prints `RT_RUNTIME_PASS`, and shuts the server down.

For the real multiplayer test, use an isolated Paper **1.21.11** server on localhost port **25581**, with offline mode solely for this test instance. Install the production plugin and verification harness, and retain the default RestrictTiers command configuration. Start it with:

```powershell
java '-Drestricttiers.verify.client=true' -Xmx1G -jar server.jar --nogui --nojline
```

Wait for `RT_CLIENT_SERVER_READY`, then from the [mod repository](https://github.com/raincakecat/RestrictTiers-Mod) run:

```powershell
.\gradlew.bat :fabric:runClientGameTest -PpaperVerification
```

The fixture binds only to localhost **25583**. The test prints `RT_MULTIPLAYER_PASS`, disconnects, and shuts down the local server. Never install the verification harness on a real server.

The 26.3 tests run against the actual Paper/Adventure APIs with simulated senders; the actual Minecraft client network test uses 1.21.11. These checks do not claim support for other server implementations or Minecraft versions.
