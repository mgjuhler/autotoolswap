# AutoToolSwap — projektnoter

Fabric **client-side** mod til Minecraft **26.1.2**: skifter automatisk væk fra værktøj/våben med lav holdbarhed, før de går i stykker. Finder erstatninger i inventory og shulkerbokse (fuld-auto i singleplayer, semi-auto på servere). Inspireret af LowDurabilitySwitcher.

## Status (2026-07-22)

- **v1.0.0 releaset**: merged til `master`, GitHub-release med jar: https://github.com/mgjuhler/autotoolswap/releases/tag/v1.0.0
- Alle 14 JUnit-tests grønne; alle 10 in-game-testscenarier manuelt bekræftet af Mads
- **Udestående:** CurseForge-upload (kræver Mads' login på authors.curseforge.com; `curseforge-logo.png` i repo-roden er lavet til projektsiden)
- Mod-ikonet i `src/main/resources/assets/autotoolswap/icon.png` er opdateret EFTER v1.0.0-releasen — det kommer med i næste version

## Build og test

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot'   # JDK 25 påkrævet
.\gradlew.bat build      # kompilerer + kører tests; jar i build\libs\
.\gradlew.bat test       # kun JUnit (14 tests: core-logik + config)
.\gradlew.bat runClient  # starter Minecraft 26.1.2 med modden (testverden i run\)
```

I git bash: `sh gradlew ...` (ikke gradlew.bat). Kør ALTID tests før commit.

## Toolchain (versioner verificeret virkende)

| Ting | Version/detalje |
|---|---|
| Java | 25 (Temurin, sti ovenfor) |
| Gradle | 9.5.1 (wrapper) |
| fabric-loom | 1.17-SNAPSHOT |
| Fabric Loader | 0.19.3 |
| Fabric API | 0.155.2+26.1.2 |
| Cloth Config | 26.1.154 (me.shedaniel maven — BEMÆRK: intet `+fabric`-suffix) |
| ModMenu | 18.0.0 via **Modrinth maven** (`maven.modrinth:modmenu` — Terraformers-maven gav 502) |

**Vigtigt om MC 26.1+:** Spillet er **unobfuskeret** — klassenavne bruges direkte, yarn findes ikke (stoppede ved 1.21.11), og loom bruger almindelige `api`/`implementation` (IKKE `modApi`/`modImplementation` — de findes ikke længere).

### API-omdøbninger fundet undervejs (26.1.2 vs. ældre viden)

- `ResourceLocation` → `net.minecraft.resources.Identifier`
- `handleInventoryMouseClick` → `MultiPlayerGameMode.handleContainerInput(int, int, int, ContainerInput, Player)`
- `ClickType` → `net.minecraft.world.inventory.ContainerInput`
- `LocalPlayer.displayClientMessage` **findes ikke** → brug `mc.gui.setOverlayMessage(comp, false)` (action bar) / `player.sendSystemMessage(comp)` (chat)
- `Item.getName()` kræver ItemStack → brug `new ItemStack(item).getItemName()`

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

## Ny Minecraft-version — opskrift

1. Bump `minecraft_version`, `loader_version`, `fabric_api_version` m.fl. i `gradle.properties` (tjek https://fabricmc.net/develop og fabric-example-mod master)
2. Hent ny klient-jar via piston-meta og verificér API-navne med javap ved kompilérfejl
3. Opdatér Cloth Config/ModMenu-versioner (Modrinth API kan filtrere på game version)
4. `gradlew build` + 14 JUnit-tests grønne
5. Mads kører in-game-tjeklisten (plan Task 11) i `runClient`
6. Bump `mod_version`, merge, GitHub-release med jar, upload til CurseForge
