# Vendored Maven artifacts

`net/neoforged/minecraft-dependencies/1.21.1/minecraft-dependencies-1.21.1.module`

The NeoForge artifact `net.neoforged:minecraft-dependencies` was **removed from
`maven.neoforged.net`** (and from its `neoforged.forgecdn.net` mirror): both the
Gradle Module Metadata (`.module`) and the POM return HTTP 404, while neighbouring
artifacts (`neoforge`, `neoform`) still resolve. Any cold Gradle cache therefore
fails with:

```
Could not find net.neoforged:minecraft-dependencies:1.21.1
Required by: net.neoforged:neoforge:21.1.248 > net.neoforged:neoform:1.21.1-20240808.144430
```

The file in this directory is the authentic Gradle Module Metadata captured from a
warm local Gradle cache (`~/.gradle/caches/modules-2/files-2.1/...`) before the
upstream removal was noticed. It declares the full variant/dependency graph of
Minecraft 1.21.1's libraries; ModDevGradle consumes it through the
`:neoFormRuntimeDependencies*` configurations.

`settings.gradle` exposes it as a content-filtered file repository restricted to
exactly this module. If the project is ever bumped to a newer Minecraft/NeoForge
line, the matching `minecraft-dependencies-<version>.module` must be vendored here
again (take it from any warm cache while it is still obtainable).
