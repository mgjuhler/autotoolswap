# AutoToolSwap — designdokument

**Dato:** 2026-07-21
**Status:** Godkendt af Mads Juhler (mgjuhler)

## Formål

Client-side Fabric-mod til Minecraft 26.1.2, der automatisk skifter væk fra værktøj og våben med lav holdbarhed, så de ikke går i stykker — inspireret af "LowDurabilitySwitcher". Udgives på CurseForge under navnet **AutoToolSwap** (mod-id: `autotoolswap`).

## Krav

1. **Automatisk skift:** Når et overvåget item krydser holdbarhedsgrænsen, skiftes der til et tilsvarende item fra spillerens inventory. Findes ingen erstatning, skiftes der til en tom/ufarlig hotbar-plads, så itemet ikke går i stykker, og der gives én advarsel.
2. **Konfigurerbar grænse:** Procent af maks. holdbarhed, standard 10 %.
3. **Valgbare kategorier:** Spilleren vælger selv, hvilke item-kategorier der overvåges (værktøj, våben, bue/armbrøst, shield, saks m.fl.), hver med sit flueben.
4. **Shulker-understøttelse:**
   - Shulkerbokses indhold læses lokalt via itemets `minecraft:container`-component.
   - **Singleplayer:** Erstatninger hentes helt automatisk ud af shulkerbokse (via den integrerede server).
   - **Multiplayer:** Detektion + besked; byttet gennemføres automatisk, når spilleren selv åbner boksen (semi-auto).
5. **Håndtering af det slidte item** (konfigurerbart): behold i inventory / smid ud / gem i shulker (straks i singleplayer, ellers ved næste åbning af boksen).
6. **Config-UI:** JSON-fil + grafisk skærm via Cloth Config og ModMenu.
7. **Notifikationer:** Action bar-besked og/eller lyd, konfigurerbart.

## Arkitektur

Valgt tilgang: **event-baseret** (fravalgt: mixin-baseret interception, som er mere præcis, men skrøbelig over for spilopdateringer). Modden bruger Fabric API's events og udfører kun handlinger, en spiller selv kunne udføre (inventory-klik, hotbar-skift) — bortset fra singleplayer-fuld-auto, som går direkte til den integrerede server i samme proces.

Ren client-side: `"environment": "client"` i fabric.mod.json. Serveren behøver ikke modden.

### Komponenter

| Komponent | Ansvar |
|---|---|
| `AutoToolSwapClient` | ClientModInitializer-entrypoint; registrerer events og binder komponenterne sammen |
| `Config` | Alle indstillinger; JSON-persistens + Cloth Config-skærm, ModMenu-integration |
| `DurabilityMonitor` | Tick-handler; opdager når overvågede slots krydser grænsen; debounce |
| `ReplacementFinder` | Finder bedste erstatning i inventory og shulkerbokse (læser `container`-component) |
| `SwapExecutor` | Udfører flytninger: klik-pakker/hotbar-skift på servere; direkte server-side inventory-manipulation i singleplayer |
| `ContainerAssistant` | Semi-auto på servere: gennemfører ventende bytter, når spilleren åbner en container |
| `Notifier` | Action bar-beskeder og lyde |

### Erstatnings-matching

1. Samme item-type (fx netherite-hakke → netherite-hakke).
2. Ellers samme værktøjsklasse, bedste tilgængelige tier (fx netherite-hakke → jernhakke).
3. Blandt flere kandidater vælges den med mest resterende holdbarhed.

### Kerneflow (hvert tick)

1. `DurabilityMonitor` tjekker overvågede slots (hovedhånd, offhand m.v. efter config).
2. Ved krydsning af grænsen: `ReplacementFinder` leder i inventory → `SwapExecutor` bytter straks; det slidte item håndteres efter config.
3. Kun fund i shulker: singleplayer → automatisk udtræk; multiplayer → besked + ventende bytte, som `ContainerAssistant` gennemfører ved åbning.
4. Intet fund: skift til tom/ufarlig hotbar-plads + én advarsel.

## Kant-tilfælde og fejlhåndtering

- Ingen handling i creative mode.
- Items med `unbreakable`-component og items uden holdbarhed ignoreres.
- Debounce: der handles/advares kun én gang pr. item, indtil situationen ændrer sig.
- Fejlede inventory-handlinger verificeres næste tick; ét genforsøg, derefter besked og opgivelse.
- Åbne skærme pauser tick-swapping (undtagen `ContainerAssistant`s eget flow), så modden ikke klikker i spillerens menuer.

## Test

- **JUnit (ren JVM):** matching-logik i `ReplacementFinder`, config-serialisering.
- **Manuel tjekliste i singleplayer-testverden:** slidt værktøj + erstatning i inventory; erstatning kun i shulker; hver af de tre old-item-indstillinger; ingen erstatning; creative mode; unbreakable items.
- **Multiplayer-adfærd** verificeres mod en lokal dedikeret server (semi-auto-flowet).

## Toolchain og distribution

- Java 25 (JDK 25 skal installeres på udviklingsmaskinen — fx Temurin).
- Gradle + fabric-loom, **Mojang-mappings** (yarn er udgået efter 1.21.11).
- Fabric Loader 0.19.3, Fabric API 0.155.2+26.1.2.
- Afhængigheder: Cloth Config, ModMenu.
- GitHub-repo `autotoolswap` under mgjuhler-kontoen.
- Udgivelse på CurseForge som client-side mod til 26.1.2; første upload manuelt, senere evt. automatiseret via CurseForge Upload API.
