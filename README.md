# RestrictTiers Paper plugin

Paper plugin for Minecraft **1.21.11 and 26.3**. Run `/check <IGN or UUID>` or `/restrictcheck <IGN or UUID>` for private restriction counts, a clickable website profile, and cached parsed details. No automatic checks, permission requirement, or LuckPerms dependency.

## Install

1. Stop your Paper server and put `RestrictTiers-Paper-1.0.0.jar` in its `plugins` folder.
2. Start the server. Use Java **21** for Paper **1.21.11**, or Java **25** for Paper **26.3**.
3. Run `/check <IGN or UUID>`. Results are sent directly to the command runner and never broadcast. Console checks return output only to the console.

The [client mod](https://github.com/raincakecat/RestrictTiers-Mod) is optional. When it is installed, `/check` and `/restrictcheck` run locally; use `/restricttiers:check` to explicitly invoke the server plugin.

## Configure commands

Edit `plugins/RestrictTiers/config.yml`, then restart the server:

```yaml
command:
  name: check
  aliases:
    - restrictcheck
```

Names must start with a lowercase letter and contain only lowercase letters, numbers, underscores or hyphens, up to 32 characters. Names and aliases must be unique. `results` is reserved. Invalid configuration disables the plugin with an explanatory console message.

Existing commands are preserved. If another plugin owns `/check`, use `/restrictcheck` or `/restricttiers:check`. With `name: lookup`, use `/lookup` or `/restricttiers:lookup`. Every configured alias also has a namespaced form.

## How lookups work

The plugin calls `https://restricttiers.raincakecat.com/api/player/<identifier>` only after an explicit check. The website resolves an IGN to a verified UUID and reads its restriction database. The plugin displays returned public fields and contains no database or admin credentials. The server needs outbound HTTPS access to the website.

Autocomplete suggests visible online players without requesting restriction data. Detail pages reuse the result snapshot, so navigation makes no new lookup. See [RESULTS.md](RESULTS.md) for cooldowns, record limits, and errors.

## Build and test

Set `JAVA_HOME` to a Java **21 JDK**, then run:

```powershell
.\gradlew.bat build
```

On Linux/macOS use `sh ./gradlew build`. The wrapper downloads Gradle **9.6.0** with a verified distribution checksum. Installable output: `plugin/build/libs/RestrictTiers-Paper-1.0.0.jar`.

Do not install sources JARs, the common library alone, or the verification harness on a real server. `verification` is exclusively for isolated local testing. This repository includes its own `common` module and builds independently of the mod repository; keep shared lookup fixes consistent between repositories.

See [TEST-REPORT.md](TEST-REPORT.md) for runtime verification and [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) for licenses.
