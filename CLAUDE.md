# AutoToolSwap — projektnoter

Fabric **client-side** mod til Minecraft **26.3**: skifter automatisk væk fra værktøj/våben med lav holdbarhed, før de går i stykker. Finder erstatninger i inventory og shulkerbokse (fuld-auto i singleplayer, semi-auto på servere). Inspireret af LowDurabilitySwitcher.

## Status (2026-09-16)

- **v1.2.0 releaset til GitHub og uploadet til CurseForge via API** (file id 8896277, 16-09-2026 — afventer CF-godkendelse): understøtter MC 26.3 (`~26.3`). API-ændring i 26.3: `ServerPlayer.drop(ItemStack, boolean)` → `drop(ItemStack, boolean retainOwnership, Prediction)` — ny parameter styrer kun arm-sving (`SERVER_ONLY` når klienten ikke har forudsagt det). Cloth Config 26.3.158 og Mod Menu 21.0.0-beta.1 var på release-dagen kun mærket til 26.3-rc, men accepterer `minecraft >=26.3-` og består alle gametests.
- **v1.1.0 releaset til GitHub OG uploadet til CurseForge via API** (file id 8589005, CF-godkendt pr. 2026-08-23): understøtter MC 26.2 (`~26.2`). https://github.com/mgjuhler/autotoolswap/releases/tag/v1.1.0 — API-ændringer i 26.2: `Minecraft.screen` → `mc.gui.screen()`, `Gui.setOverlayMessage` → `mc.gui.hud.setOverlayMessage` (ny Hud-klasse)
- **v1.0.1 releaset og uploadet til CurseForge**: understøtter MC 26.1/26.1.1/26.1.2 (`~26.1`-range). GitHub-release: https://github.com/mgjuhler/autotoolswap/releases/tag/v1.0.1
- CurseForge-projekt: "AutoToolSwap" (Utility & QoL, MIT, Cloth Config required + Mod Menu optional)
- Alle 14 JUnit-tests grønne; hele in-game-tjeklisten er nu automatiseret som client gametests (14 tjek, alle grønne pr. 2026-08-06 — se testafsnittet)

## Build og test

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot'   # JDK 25 påkrævet
.\gradlew.bat build              # kompilerer + kører tests; jar i build\libs\
.\gradlew.bat test               # kun JUnit (14 tests: core-logik + config)
.\gradlew.bat runClient          # starter Minecraft med modden (testverden i run\)
.\gradlew.bat runClientGameTest  # automatiske in-game-tests (~35 s, se nedenfor)
```

I git bash: `sh gradlew ...` (ikke gradlew.bat). Kør ALTID tests før commit.
Dobbeltklik-genveje: `run-client.bat` og `run-gametests.bat`.

### Automatiske in-game-tests (client gametests)

`src/gametest/` er et separat source set (oprettet af `fabricApi.configureTests` i
build.gradle) med `AutoToolSwapGameTest`, der kører via Fabrics client-gametest-API:
starter klienten, opretter en testverden og efterprøver ALLE scenarier fra den
manuelle tjekliste (basis-skift, tier-fallback, sikker plads, offhand-skjold,
fuld-auto shulker inkl. STORE_IN_SHULKER, semi-auto shulker via programmatisk
containeråbning med `gameMode.useItemOn`, DROP, creative, unbreakable,
bue≠armbrøst, samt config-skærmen som røgtest: åbner, screenshottes, lukkes via
"Save & Quit"-knappen og gem/indlæs-rundtur). Testene muterer configen i
hukommelsen (`AutoToolSwapClient.config()`) og asserter direkte på klientens
inventory efter et antal ticks. Screenshot lander i
`build/run/clientGameTest/screenshots/`.

Eneste rest-manuelle: at klikke rundt i selve Cloth-widgets (slider/enum-cycling)
— røgtesten driver ikke de enkelte widgets.

## Toolchain (versioner verificeret virkende)

| Ting | Version/detalje |
|---|---|
| Java | 25 (Temurin, sti ovenfor) |
| Gradle | 9.5.1 (wrapper) |
| fabric-loom | 1.17-SNAPSHOT (virker også med 26.3) |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.160.6+26.3 |
| Cloth Config | 26.3.158 (me.shedaniel maven — BEMÆRK: intet `+fabric`-suffix) |
| ModMenu | 21.0.0-beta.1 via **Modrinth maven** (`maven.modrinth:modmenu` — Terraformers-maven gav 502) |

**Vigtigt om MC 26.1+:** Spillet er **unobfuskeret** — klassenavne bruges direkte, yarn findes ikke (stoppede ved 1.21.11), og loom bruger almindelige `api`/`implementation` (IKKE `modApi`/`modImplementation` — de findes ikke længere).

### API-omdøbninger fundet undervejs (26.1.2 vs. ældre viden)

- `ResourceLocation` → `net.minecraft.resources.Identifier`
- `handleInventoryMouseClick` → `MultiPlayerGameMode.handleContainerInput(int, int, int, ContainerInput, Player)`
- `ClickType` → `net.minecraft.world.inventory.ContainerInput`
- `LocalPlayer.displayClientMessage` **findes ikke** → brug `mc.gui.setOverlayMessage(comp, false)` (action bar) / `player.sendSystemMessage(comp)` (chat)
- `Item.getName()` kræver ItemStack → brug `new ItemStack(item).getItemName()`
- 26.3: `ServerPlayer.drop(ItemStack, boolean)` findes ikke → `drop(stack, retainOwnership, net.minecraft.util.Prediction)` (samme `createItemStackToDrop(stack, false, retainOwnership)` som før)

**Metode ved tvivl om API-navne:** slå op i den rigtige klient-jar med javap i stedet for at gætte:
```bash
"/c/Program Files/Eclipse Adoptium/jdk-25.0.3.9-hotspot/bin/javap.exe" -cp <klient-jar> net.minecraft.klasse.Navn
```
Klient-jarren hentes via piston-meta version-manifestet (ingen `client_mappings` længere — jarren ER navnene).

## Arkitektur

- `src/main/java/.../core/` — **ren JVM-logik, ingen Minecraft-imports, JUnit-testet**: `DurabilityCheck` (grænse-tjek), `ItemCategory`/`Candidate`/`ReplacementSelector` (valg af erstatning), `config/` (model + Gson-IO)
- `src/client/java/.../client/` — Minecraft-vendte adaptere: `AutoToolSwapClient` (entrypoint), `DurabilityMonitor` (tick-tjek + forsøgstæller), `SwapExecutor` (slot-klik/hotbar-skift), `InventoryScanner` (+ shulker-læsning via `DataComponents.CONTAINER`), `ShulkerFlow` (pending-bytte ved container-åbning), `SingleplayerExtractor` (fuld-auto via integrated server), `Notifier`, `ConfigScreenBuilder` + `ModMenuIntegration`
- Ingen mixins. Sprogfiler: `en_us.json` + `da_dk.json` (dansk med rigtige æøå)

### Adfærdsregler der er nemme at glemme

- `DurabilityMonitor.handled` er en forsøgstæller pr. `slot:itemId`-nøgle: 2 forsøg, derefter én gave_up-besked ved **overgangen forbi** MAX (ikke ved starten af sidste forsøg), derefter stilhed til tilstanden ændrer sig. **Shulker-stien parkerer tælleren på MAX+1** (den har sin egen besked) — flyt ikke det tilbage til MAX, det giver falske gave_up-beskeder.
- `selectSafeSlot` vælger første tomme ELLER u-sårbare hotbar-plads — det kan være shulkerboksen selv (det er korrekt og praktisk).
- STORE_IN_SHULKER for erstatninger fundet i inventory er bevidst nedgraderet til behold+besked (plan-sanktioneret fallback).
- ShulkerFlow reagerer kun i `ChestMenu`/`ShulkerBoxMenu` (ambolt-bugfix fra slutreviewet).

## Dokumenter

- Spec: `docs/superpowers/specs/2026-07-21-autotoolswap-design.md`
- Implementeringsplan (inkl. **manuel in-game-testtjekliste** i Task 11): `docs/superpowers/plans/2026-07-21-autotoolswap.md`

## Overvågning af nye versioner

`.github/workflows/check-updates.yml` kører `scripts/check-updates.py` dagligt (05:30 UTC)
og holder ét GitHub-issue med label `update-check` ajour: oprettes/opdateres når der er
en ny Minecraft-release (med tjek af om Fabric Loader/API, Cloth Config og Mod Menu
er klar til den) eller nyere build-afhængigheder til den nuværende MC-version; lukkes
automatisk når alt er ajour. Kør lokalt: `python3 scripts/check-updates.py`.
Manuel kørsel: `gh workflow run check-updates.yml`. Snapshots udløser bevidst ikke noget.

## Ny Minecraft-version — opskrift

1. Bump `minecraft_version`, `loader_version`, `fabric_api_version` m.fl. i `gradle.properties` (tjek https://fabricmc.net/develop og fabric-example-mod master)
2. Hent ny klient-jar via piston-meta og verificér API-navne med javap ved kompilérfejl
3. Opdatér Cloth Config/ModMenu-versioner (Modrinth API kan filtrere på game version). På release-dagen er de ofte kun mærket til `-rc` — tjek `depends.minecraft` i jarens fabric.mod.json; `check-updates.py` godtager RC-mærkede builds og skriver det
4. `gradlew build` + 14 JUnit-tests grønne
5. `gradlew runClientGameTest` — hele in-game-tjeklisten kører automatisk (14 tjek)
6. Bump `mod_version`, merge, GitHub-release med jar (`gh release create`)
7. CurseForge-upload: **tjek FØRST om Mads allerede har uploadet filen** (spørg, eller se fillisten på CF) — så `bash scripts/upload-curseforge.sh build/libs/autotoolswap-X.Y.Z.jar X.Y.Z NN.N <changelog.md>`. Display name er ALTID kun "AutoToolSwap X.Y.Z" (aldrig "(MC …)"-suffiks — scriptet danner det selv). Token i `~/.curseforge/token` (synkes på tværs af maskiner via kf_claude_sync/secrets/curseforge/token), projekt-id 1620708 står i scriptet
