# AutoToolSwap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Client-side Fabric-mod til Minecraft 26.1.2, der automatisk skifter væk fra værktøj/våben med lav holdbarhed (spec: `docs/superpowers/specs/2026-07-21-autotoolswap-design.md`).

**Architecture:** Event-baseret client-mod. Ren kernelogik (uden Minecraft-imports) i `src/main` med JUnit-tests; Minecraft-adaptere i `src/client` (split sourcesets fra Fabric-skabelonen). Ingen mixins.

**Tech Stack:** Java 25, Gradle 9.5.1 (wrapper), fabric-loom 1.17-SNAPSHOT, Fabric Loader 0.19.3, Fabric API 0.155.2+26.1.2, Cloth Config 26.1.154+fabric, ModMenu 18.0.0, Gson, JUnit 5.

## Global Constraints

- Minecraft-version: **26.1.2** (unobfuskeret — klassenavne som `net.minecraft.client.Minecraft` bruges direkte; yarn findes ikke for denne version).
- `JAVA_HOME` skal sættes i hver shell: `export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-25.0.3.9-hotspot"` (JDK 25 er installeret dér).
- Projektmappe: `C:/Users/mgjuh/autotoolswap`. Alle `./gradlew`-kommandoer køres derfra (git bash: `./gradlew.bat` virker ikke i bash — brug `./gradlew`; hvis den fejler, kør `sh gradlew`).
- Mod-id: `autotoolswap`. Package: `io.github.mgjuhler.autotoolswap`. Version starter på `0.1.0`.
- **Verificerede API-navne for 26.1.2** (slået op i klient-jarren — brug præcis disse):
  - `net.minecraft.resources.Identifier` (IKKE `ResourceLocation`)
  - `MultiPlayerGameMode.handleContainerInput(int containerId, int slotId, int button, ContainerInput input, Player player)` — slot-klik (gamle `handleInventoryMouseClick`/`ClickType` findes ikke)
  - `net.minecraft.world.inventory.ContainerInput` enum: `PICKUP, QUICK_MOVE, SWAP, CLONE, THROW, QUICK_CRAFT, PICKUP_ALL`
  - `ItemStack`: `isDamageableItem()`, `getDamageValue()`, `getMaxDamage()`, `nextDamageWillBreak()`, `has(...)`, `get(...)`, `is(TagKey)`
  - `Inventory`: `getSelectedSlot()`, `setSelectedSlot(int)`, `getItem(int)`, `getSelectedItem()`, `isHotbarSlot(int)`, `SLOT_OFFHAND`
  - `DataComponents.CONTAINER` → `ItemContainerContents` (`copyInto(NonNullList)`, `fromItems(List)`, `nonEmptyItemCopyStream()`), `DataComponents.UNBREAKABLE`
  - `InventoryMenu`-konstanter: `INV_SLOT_START` (=9), `USE_ROW_SLOT_START` (=36, hotbar), `SHIELD_SLOT` (=45, offhand)
  - `ItemTags.PICKAXES/AXES/SHOVELS/HOES/SWORDS`
- Alle spillervendte tekster går gennem lang-nøgler med både `en_us.json` og `da_dk.json` (rigtige danske tegn: æøå).
- Commit efter hver task (Conventional Commits-stil som vist i stepsene) med footer: `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`.
- Kompilérfejl pga. et API-navn, der alligevel afviger: slå navnet op i klient-jarren i stedet for at gætte: `"/c/Program Files/Eclipse Adoptium/jdk-25.0.3.9-hotspot/bin/javap.exe" -cp "C:/Users/mgjuh/AppData/Local/Temp/claude/C--Users-mgjuh/2fe573b7-68ff-4fe0-b78f-2b31d39f5cde/scratchpad/client.jar" <fuldt.klassenavn>`

---

### Task 1: Scaffold fra officiel skabelon + grøn build

**Files:**
- Create: hele projektskelettet i `C:/Users/mgjuh/autotoolswap` (kopieret fra FabricMC/fabric-example-mod master, som allerede er på 26.1.2)
- Modify: `gradle.properties`, `settings.gradle`, `build.gradle`, `src/main/resources/fabric.mod.json`
- Delete: skabelonens eksempel-klasser og mixins

**Interfaces:**
- Produces: kørende Gradle-build; client-entrypoint `io.github.mgjuhler.autotoolswap.client.AutoToolSwapClient` (tom `onInitializeClient()`), som senere tasks udvider.

- [ ] **Step 1: Kopiér skabelonen ind i projektet**

```bash
cd "C:/Users/mgjuh/AppData/Local/Temp/claude/C--Users-mgjuh/2fe573b7-68ff-4fe0-b78f-2b31d39f5cde/scratchpad"
# skabelonen er allerede klonet til ./template; ellers: git clone --depth 1 https://github.com/FabricMC/fabric-example-mod.git template
cd template && cp -r build.gradle settings.gradle gradle.properties gradlew gradlew.bat gradle src .gitignore LICENSE "C:/Users/mgjuh/autotoolswap/"
```

- [ ] **Step 2: Omdøb og ryd op**

`settings.gradle`: sidste linje ændres til `rootProject.name = 'autotoolswap'`.

`gradle.properties` — erstat Mod Properties/Dependencies-sektionerne (behold resten):

```properties
# Mod Properties
mod_version=0.1.0
maven_group=io.github.mgjuhler

# Dependencies
fabric_api_version=0.155.2+26.1.2
cloth_config_version=26.1.154+fabric
modmenu_version=18.0.0
```

`build.gradle`:
1. I `loom { mods { ... } }`: omdøb `"modid"` til `"autotoolswap"`.
2. Udfyld `repositories { }`-blokken (den tomme øverst, IKKE publishing-blokken):

```groovy
repositories {
	maven { name = 'Shedaniel'; url = 'https://maven.shedaniel.me/' }
	maven { name = 'Terraformers'; url = 'https://maven.terraformersmc.com/releases/' }
	exclusiveContent {
		forRepository { maven { name = 'Modrinth'; url = 'https://api.modrinth.com/maven' } }
		filter { includeGroup 'maven.modrinth' }
	}
}
```

3. Tilføj i `dependencies { }` efter fabric-api-linjen:

```groovy
	modApi("me.shedaniel.cloth:cloth-config-fabric:${project.cloth_config_version}") {
		exclude group: "net.fabricmc.fabric-api"
	}
	modImplementation "com.terraformersmc:modmenu:${project.modmenu_version}"

	testImplementation 'org.junit.jupiter:junit-jupiter:5.11.3'
	testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
	testImplementation 'com.google.code.gson:gson:2.11.0'
```

(Hvis Terraformers-maven giver 502: skift modmenu-linjen til `modImplementation "maven.modrinth:modmenu:${project.modmenu_version}"`.)

4. Tilføj nederst i filen:

```groovy
test {
	useJUnitPlatform()
}
```

Kildefiler:

```bash
cd "C:/Users/mgjuh/autotoolswap"
rm -rf src/main/java/com src/client/java/com src/client/resources/modid.client.mixins.json src/main/resources/modid.mixins.json
mkdir -p src/client/java/io/github/mgjuhler/autotoolswap/client
mkdir -p src/main/java/io/github/mgjuhler/autotoolswap/core
mkdir -p src/main/resources/assets/autotoolswap
mv src/main/resources/assets/modid/icon.png src/main/resources/assets/autotoolswap/icon.png && rmdir src/main/resources/assets/modid
```

Ny `src/client/java/io/github/mgjuhler/autotoolswap/client/AutoToolSwapClient.java`:

```java
package io.github.mgjuhler.autotoolswap.client;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoToolSwapClient implements ClientModInitializer {
	public static final String MOD_ID = "autotoolswap";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		LOGGER.info("AutoToolSwap loaded");
	}
}
```

Ny `src/main/resources/fabric.mod.json` (bemærk: intet `main`-entrypoint, ingen mixins, `environment: client`):

```json
{
	"schemaVersion": 1,
	"id": "autotoolswap",
	"version": "${version}",
	"name": "AutoToolSwap",
	"description": "Automatically switches away from tools and weapons at low durability so they never break. Reads shulker boxes for replacements.",
	"authors": ["Mads Juhler (mgjuhler)"],
	"contact": {
		"sources": "https://github.com/mgjuhler/autotoolswap"
	},
	"license": "MIT",
	"icon": "assets/autotoolswap/icon.png",
	"environment": "client",
	"entrypoints": {
		"client": ["io.github.mgjuhler.autotoolswap.client.AutoToolSwapClient"]
	},
	"depends": {
		"fabricloader": ">=0.19.3",
		"minecraft": "~26.1.2",
		"java": ">=25",
		"fabric-api": "*",
		"cloth-config": "*"
	},
	"suggests": {
		"modmenu": "*"
	}
}
```

Erstat `LICENSE` med MIT-licens (copyright `2026 Mads Juhler`).

- [ ] **Step 3: Byg**

```bash
cd "C:/Users/mgjuh/autotoolswap" && export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-25.0.3.9-hotspot" && sh gradlew build --no-daemon
```

Expected: `BUILD SUCCESSFUL` og `build/libs/autotoolswap-0.1.0.jar` findes. (Første kørsel henter Gradle + Minecraft — det tager flere minutter.)

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "feat: scaffold Fabric mod for MC 26.1.2 from official template"
```

---

### Task 2: DurabilityCheck (kernelogik, TDD)

**Files:**
- Create: `src/main/java/io/github/mgjuhler/autotoolswap/core/DurabilityCheck.java`
- Test: `src/test/java/io/github/mgjuhler/autotoolswap/core/DurabilityCheckTest.java`

**Interfaces:**
- Produces: `public static boolean DurabilityCheck.isLow(int damageValue, int maxDamage, int thresholdPercent)` — `true` når resterende holdbarhed er ≤ threshold-procent af maks. `maxDamage <= 0` → altid `false`.

- [ ] **Step 1: Skriv fejlende test**

`src/test/java/io/github/mgjuhler/autotoolswap/core/DurabilityCheckTest.java`:

```java
package io.github.mgjuhler.autotoolswap.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DurabilityCheckTest {
	@Test void fullDurabilityIsNotLow() {
		assertFalse(DurabilityCheck.isLow(0, 1561, 10));
	}
	@Test void belowTenPercentIsLow() {
		// 1561 max, 150 tilbage = 9.6 %
		assertTrue(DurabilityCheck.isLow(1561 - 150, 1561, 10));
	}
	@Test void exactBoundaryIsLow() {
		// 100 max, præcis 10 tilbage
		assertTrue(DurabilityCheck.isLow(90, 100, 10));
	}
	@Test void justAboveBoundaryIsNotLow() {
		assertFalse(DurabilityCheck.isLow(89, 100, 10));
	}
	@Test void nonDamageableIsNeverLow() {
		assertFalse(DurabilityCheck.isLow(0, 0, 10));
	}
}
```

- [ ] **Step 2: Kør testen — skal fejle**

```bash
cd "C:/Users/mgjuh/autotoolswap" && export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-25.0.3.9-hotspot" && sh gradlew test --tests '*DurabilityCheckTest' --no-daemon
```

Expected: FAIL — compilation error, `DurabilityCheck` findes ikke.

- [ ] **Step 3: Implementér**

`src/main/java/io/github/mgjuhler/autotoolswap/core/DurabilityCheck.java`:

```java
package io.github.mgjuhler.autotoolswap.core;

public final class DurabilityCheck {
	private DurabilityCheck() {}

	public static boolean isLow(int damageValue, int maxDamage, int thresholdPercent) {
		if (maxDamage <= 0) return false;
		int remaining = maxDamage - damageValue;
		return remaining * 100L <= (long) maxDamage * thresholdPercent;
	}
}
```

- [ ] **Step 4: Kør testen — skal bestå** (samme kommando). Expected: `BUILD SUCCESSFUL`, 5 tests grønne.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: add durability threshold check with unit tests"
```

---

### Task 3: Config-model og persistens (TDD)

**Files:**
- Create: `src/main/java/io/github/mgjuhler/autotoolswap/core/config/AutoToolSwapConfig.java`
- Create: `src/main/java/io/github/mgjuhler/autotoolswap/core/config/OldItemAction.java`
- Create: `src/main/java/io/github/mgjuhler/autotoolswap/core/config/ConfigIO.java`
- Test: `src/test/java/io/github/mgjuhler/autotoolswap/core/config/ConfigIOTest.java`

**Interfaces:**
- Produces: `AutoToolSwapConfig` med public felter: `boolean enabled` (true), `int thresholdPercent` (10), `boolean monitorOffhand` (true), `boolean monitorTools` (true), `boolean monitorWeapons` (true), `boolean monitorRanged` (true), `boolean monitorShield` (true), `boolean monitorShears` (true), `boolean searchShulkers` (true), `boolean singleplayerFullAuto` (true), `OldItemAction oldItemAction` (KEEP), `boolean chatNotifications` (true), `boolean soundNotifications` (true).
- `enum OldItemAction { KEEP, DROP, STORE_IN_SHULKER }`
- `AutoToolSwapConfig ConfigIO.load(Path file)` — defaults ved manglende/korrupt fil (og gemmer defaults); `void ConfigIO.save(AutoToolSwapConfig cfg, Path file)`.

- [ ] **Step 1: Skriv fejlende test**

`src/test/java/io/github/mgjuhler/autotoolswap/core/config/ConfigIOTest.java`:

```java
package io.github.mgjuhler.autotoolswap.core.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ConfigIOTest {
	@TempDir Path dir;

	@Test void missingFileGivesDefaultsAndWritesFile() {
		Path f = dir.resolve("autotoolswap.json");
		AutoToolSwapConfig cfg = ConfigIO.load(f);
		assertTrue(cfg.enabled);
		assertEquals(10, cfg.thresholdPercent);
		assertEquals(OldItemAction.KEEP, cfg.oldItemAction);
		assertTrue(Files.exists(f));
	}

	@Test void roundTripPreservesValues() throws Exception {
		Path f = dir.resolve("autotoolswap.json");
		AutoToolSwapConfig cfg = new AutoToolSwapConfig();
		cfg.thresholdPercent = 25;
		cfg.oldItemAction = OldItemAction.STORE_IN_SHULKER;
		cfg.monitorShears = false;
		ConfigIO.save(cfg, f);
		AutoToolSwapConfig loaded = ConfigIO.load(f);
		assertEquals(25, loaded.thresholdPercent);
		assertEquals(OldItemAction.STORE_IN_SHULKER, loaded.oldItemAction);
		assertFalse(loaded.monitorShears);
	}

	@Test void corruptFileGivesDefaults() throws Exception {
		Path f = dir.resolve("autotoolswap.json");
		Files.writeString(f, "{ not json !!");
		AutoToolSwapConfig cfg = ConfigIO.load(f);
		assertEquals(10, cfg.thresholdPercent);
	}
}
```

- [ ] **Step 2: Kør — skal fejle** (`sh gradlew test --tests '*ConfigIOTest' --no-daemon`, med JAVA_HOME). Expected: compilation error.

- [ ] **Step 3: Implementér**

`OldItemAction.java`:

```java
package io.github.mgjuhler.autotoolswap.core.config;

public enum OldItemAction { KEEP, DROP, STORE_IN_SHULKER }
```

`AutoToolSwapConfig.java`:

```java
package io.github.mgjuhler.autotoolswap.core.config;

public class AutoToolSwapConfig {
	public boolean enabled = true;
	public int thresholdPercent = 10;
	public boolean monitorOffhand = true;
	public boolean monitorTools = true;
	public boolean monitorWeapons = true;
	public boolean monitorRanged = true;
	public boolean monitorShield = true;
	public boolean monitorShears = true;
	public boolean searchShulkers = true;
	public boolean singleplayerFullAuto = true;
	public OldItemAction oldItemAction = OldItemAction.KEEP;
	public boolean chatNotifications = true;
	public boolean soundNotifications = true;
}
```

`ConfigIO.java`:

```java
package io.github.mgjuhler.autotoolswap.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigIO {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private ConfigIO() {}

	public static AutoToolSwapConfig load(Path file) {
		if (Files.exists(file)) {
			try {
				AutoToolSwapConfig cfg = GSON.fromJson(Files.readString(file), AutoToolSwapConfig.class);
				if (cfg != null) return cfg;
			} catch (Exception ignored) {
				// korrupt fil → falder igennem til defaults
			}
		}
		AutoToolSwapConfig cfg = new AutoToolSwapConfig();
		save(cfg, file);
		return cfg;
	}

	public static void save(AutoToolSwapConfig cfg, Path file) {
		try {
			if (file.getParent() != null) Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(cfg));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
```

- [ ] **Step 4: Kør — skal bestå.** Expected: 3 tests grønne.

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat: add config model with json persistence"`

---

### Task 4: ItemCategory, Candidate og ReplacementSelector (TDD)

**Files:**
- Create: `src/main/java/io/github/mgjuhler/autotoolswap/core/ItemCategory.java`
- Create: `src/main/java/io/github/mgjuhler/autotoolswap/core/Candidate.java`
- Create: `src/main/java/io/github/mgjuhler/autotoolswap/core/ReplacementSelector.java`
- Test: `src/test/java/io/github/mgjuhler/autotoolswap/core/ReplacementSelectorTest.java`

**Interfaces:**
- `enum ItemCategory { PICKAXE, AXE, SHOVEL, HOE, SWORD, BOW, CROSSBOW, TRIDENT, MACE, SHIELD, SHEARS, FISHING_ROD, OTHER }` med `boolean allowsCategoryFallback()` — kun `true` for PICKAXE/AXE/SHOVEL/HOE/SWORD.
- `record Candidate(String itemId, ItemCategory category, int damageValue, int maxDamage, Candidate.Location location, int inventorySlot, int slotInShulker)` + `enum Location { INVENTORY, SHULKER }` + `int remaining()`. For `INVENTORY` er `slotInShulker` -1; for `SHULKER` er `inventorySlot` shulker-stakkens plads og `slotInShulker` pladsen inde i boksen.
- `static Optional<Candidate> ReplacementSelector.selectBest(String wornItemId, ItemCategory category, int thresholdPercent, List<Candidate> candidates)`.
  Regler (i prioriteret rækkefølge): kassér kandidater der selv er under threshold; kassér ikke-matchende (match = samme itemId, eller samme kategori hvis `allowsCategoryFallback`); sortér: INVENTORY før SHULKER → samme itemId før kategori-match → højeste maxDamage (tier-proxy) → mest resterende holdbarhed.

- [ ] **Step 1: Skriv fejlende test**

`src/test/java/io/github/mgjuhler/autotoolswap/core/ReplacementSelectorTest.java`:

```java
package io.github.mgjuhler.autotoolswap.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static io.github.mgjuhler.autotoolswap.core.Candidate.Location.*;
import static org.junit.jupiter.api.Assertions.*;

class ReplacementSelectorTest {
	private static Candidate inv(String id, ItemCategory cat, int dmg, int max, int slot) {
		return new Candidate(id, cat, dmg, max, INVENTORY, slot, -1);
	}
	private static Candidate shulker(String id, ItemCategory cat, int dmg, int max, int invSlot, int boxSlot) {
		return new Candidate(id, cat, dmg, max, SHULKER, invSlot, boxSlot);
	}

	@Test void prefersSameItemOverHigherTierFallback() {
		var iron = inv("minecraft:iron_pickaxe", ItemCategory.PICKAXE, 0, 250, 10);
		var netherite = inv("minecraft:netherite_pickaxe", ItemCategory.PICKAXE, 0, 2031, 11);
		var best = ReplacementSelector.selectBest("minecraft:iron_pickaxe", ItemCategory.PICKAXE, 10, List.of(netherite, iron));
		assertEquals("minecraft:iron_pickaxe", best.orElseThrow().itemId());
	}

	@Test void fallsBackToBestTierInCategory() {
		var stone = inv("minecraft:stone_pickaxe", ItemCategory.PICKAXE, 0, 131, 10);
		var iron = inv("minecraft:iron_pickaxe", ItemCategory.PICKAXE, 0, 250, 11);
		var best = ReplacementSelector.selectBest("minecraft:netherite_pickaxe", ItemCategory.PICKAXE, 10, List.of(stone, iron));
		assertEquals("minecraft:iron_pickaxe", best.orElseThrow().itemId());
	}

	@Test void skipsCandidatesThatAreThemselvesLow() {
		var worn = inv("minecraft:iron_pickaxe", ItemCategory.PICKAXE, 245, 250, 10);
		assertTrue(ReplacementSelector.selectBest("minecraft:iron_pickaxe", ItemCategory.PICKAXE, 10, List.of(worn)).isEmpty());
	}

	@Test void prefersInventoryOverShulker() {
		var inShulker = shulker("minecraft:iron_pickaxe", ItemCategory.PICKAXE, 0, 250, 20, 3);
		var inInv = inv("minecraft:iron_pickaxe", ItemCategory.PICKAXE, 100, 250, 12);
		var best = ReplacementSelector.selectBest("minecraft:iron_pickaxe", ItemCategory.PICKAXE, 10, List.of(inShulker, inInv));
		assertEquals(Candidate.Location.INVENTORY, best.orElseThrow().location());
	}

	@Test void bowNeverMatchesCrossbow() {
		var crossbow = inv("minecraft:crossbow", ItemCategory.CROSSBOW, 0, 465, 10);
		assertTrue(ReplacementSelector.selectBest("minecraft:bow", ItemCategory.BOW, 10, List.of(crossbow)).isEmpty());
	}

	@Test void tieOnTierPicksMostRemaining() {
		var a = inv("minecraft:iron_pickaxe", ItemCategory.PICKAXE, 200, 250, 10);
		var b = inv("minecraft:iron_pickaxe", ItemCategory.PICKAXE, 50, 250, 11);
		var best = ReplacementSelector.selectBest("minecraft:iron_pickaxe", ItemCategory.PICKAXE, 10, List.of(a, b));
		assertEquals(11, best.orElseThrow().inventorySlot());
	}
}
```

- [ ] **Step 2: Kør — skal fejle** (compilation error).

- [ ] **Step 3: Implementér**

`ItemCategory.java`:

```java
package io.github.mgjuhler.autotoolswap.core;

public enum ItemCategory {
	PICKAXE, AXE, SHOVEL, HOE, SWORD, BOW, CROSSBOW, TRIDENT, MACE, SHIELD, SHEARS, FISHING_ROD, OTHER;

	public boolean allowsCategoryFallback() {
		return switch (this) {
			case PICKAXE, AXE, SHOVEL, HOE, SWORD -> true;
			default -> false;
		};
	}
}
```

`Candidate.java`:

```java
package io.github.mgjuhler.autotoolswap.core;

public record Candidate(String itemId, ItemCategory category, int damageValue, int maxDamage,
                        Location location, int inventorySlot, int slotInShulker) {
	public enum Location { INVENTORY, SHULKER }

	public int remaining() {
		return maxDamage - damageValue;
	}
}
```

`ReplacementSelector.java`:

```java
package io.github.mgjuhler.autotoolswap.core;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class ReplacementSelector {
	private ReplacementSelector() {}

	public static Optional<Candidate> selectBest(String wornItemId, ItemCategory category,
	                                             int thresholdPercent, List<Candidate> candidates) {
		return candidates.stream()
			.filter(c -> !DurabilityCheck.isLow(c.damageValue(), c.maxDamage(), thresholdPercent))
			.filter(c -> matches(wornItemId, category, c))
			.min(Comparator
				.comparing((Candidate c) -> c.location() == Candidate.Location.SHULKER)
				.thenComparing(c -> !c.itemId().equals(wornItemId))
				.thenComparing(Comparator.comparingInt(Candidate::maxDamage).reversed())
				.thenComparing(Comparator.comparingInt(Candidate::remaining).reversed()));
	}

	private static boolean matches(String wornItemId, ItemCategory category, Candidate c) {
		if (c.itemId().equals(wornItemId)) return true;
		return category.allowsCategoryFallback() && c.category() == category;
	}
}
```

- [ ] **Step 4: Kør alle tests — skal bestå** (`sh gradlew test --no-daemon`). Expected: alle tests grønne.

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat: add replacement selection core logic"`

---

### Task 5: Minecraft-adaptere: ItemClassifier + InventoryScanner

**Files:**
- Create: `src/client/java/io/github/mgjuhler/autotoolswap/client/ItemClassifier.java`
- Create: `src/client/java/io/github/mgjuhler/autotoolswap/client/InventoryScanner.java`

**Interfaces:**
- Consumes: `ItemCategory`, `Candidate`, `AutoToolSwapConfig` (Task 3-4).
- Produces: `static ItemCategory ItemClassifier.classify(ItemStack)`; `static boolean ItemClassifier.isMonitored(ItemCategory, AutoToolSwapConfig)`; `static List<Candidate> InventoryScanner.scan(LocalPlayer player, boolean includeShulkers, int excludeInventorySlot)` — kandidater fra inventory-slot 0–35 (undtagen excludeInventorySlot) og, hvis slået til, indholdet af alle stakke med `DataComponents.CONTAINER`; `static String InventoryScanner.itemId(ItemStack)`.

- [ ] **Step 1: Implementér**

`ItemClassifier.java`:

```java
package io.github.mgjuhler.autotoolswap.client;

import io.github.mgjuhler.autotoolswap.core.ItemCategory;
import io.github.mgjuhler.autotoolswap.core.config.AutoToolSwapConfig;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ItemClassifier {
	private ItemClassifier() {}

	public static ItemCategory classify(ItemStack stack) {
		if (stack.is(ItemTags.PICKAXES)) return ItemCategory.PICKAXE;
		if (stack.is(ItemTags.AXES)) return ItemCategory.AXE;
		if (stack.is(ItemTags.SHOVELS)) return ItemCategory.SHOVEL;
		if (stack.is(ItemTags.HOES)) return ItemCategory.HOE;
		if (stack.is(ItemTags.SWORDS)) return ItemCategory.SWORD;
		if (stack.is(Items.BOW)) return ItemCategory.BOW;
		if (stack.is(Items.CROSSBOW)) return ItemCategory.CROSSBOW;
		if (stack.is(Items.TRIDENT)) return ItemCategory.TRIDENT;
		if (stack.is(Items.MACE)) return ItemCategory.MACE;
		if (stack.is(Items.SHIELD)) return ItemCategory.SHIELD;
		if (stack.is(Items.SHEARS)) return ItemCategory.SHEARS;
		if (stack.is(Items.FISHING_ROD)) return ItemCategory.FISHING_ROD;
		return ItemCategory.OTHER;
	}

	public static boolean isMonitored(ItemCategory category, AutoToolSwapConfig cfg) {
		return switch (category) {
			case PICKAXE, AXE, SHOVEL, HOE, FISHING_ROD -> cfg.monitorTools;
			case SWORD, TRIDENT, MACE -> cfg.monitorWeapons;
			case BOW, CROSSBOW -> cfg.monitorRanged;
			case SHIELD -> cfg.monitorShield;
			case SHEARS -> cfg.monitorShears;
			case OTHER -> false;
		};
	}
}
```

(Kompilérer `stack.is(Items.BOW)` ikke — `is(Item)`-overload kan være væk — så brug `stack.getItem() == Items.BOW`.)

`InventoryScanner.java`:

```java
package io.github.mgjuhler.autotoolswap.client;

import io.github.mgjuhler.autotoolswap.core.Candidate;
import io.github.mgjuhler.autotoolswap.core.ItemCategory;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import java.util.ArrayList;
import java.util.List;

public final class InventoryScanner {
	public static final int MAIN_INVENTORY_SIZE = 36; // hotbar 0-8 + resten 9-35
	public static final int SHULKER_SIZE = 27;

	private InventoryScanner() {}

	public static String itemId(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	public static List<Candidate> scan(LocalPlayer player, boolean includeShulkers, int excludeInventorySlot) {
		List<Candidate> out = new ArrayList<>();
		var inv = player.getInventory();
		for (int slot = 0; slot < MAIN_INVENTORY_SIZE; slot++) {
			if (slot == excludeInventorySlot) continue;
			ItemStack stack = inv.getItem(slot);
			if (stack.isEmpty()) continue;
			if (stack.isDamageableItem()) {
				out.add(new Candidate(itemId(stack), ItemClassifier.classify(stack),
					stack.getDamageValue(), stack.getMaxDamage(),
					Candidate.Location.INVENTORY, slot, -1));
			}
			if (includeShulkers && stack.has(DataComponents.CONTAINER)) {
				ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
				NonNullList<ItemStack> items = NonNullList.withSize(SHULKER_SIZE, ItemStack.EMPTY);
				contents.copyInto(items);
				for (int boxSlot = 0; boxSlot < items.size(); boxSlot++) {
					ItemStack inner = items.get(boxSlot);
					if (inner.isEmpty() || !inner.isDamageableItem()) continue;
					out.add(new Candidate(itemId(inner), ItemClassifier.classify(inner),
						inner.getDamageValue(), inner.getMaxDamage(),
						Candidate.Location.SHULKER, slot, boxSlot));
				}
			}
		}
		return out;
	}
}
```

- [ ] **Step 2: Kompilér** — `sh gradlew build --no-daemon` (med JAVA_HOME). Expected: `BUILD SUCCESSFUL`. Ved API-afvigelser: slå op med javap (se Global Constraints) og ret navnet.

- [ ] **Step 3: Commit** — `git add -A && git commit -m "feat: add item classification and inventory scanning"`

---

### Task 6: Notifier og sprogfiler

**Files:**
- Create: `src/client/java/io/github/mgjuhler/autotoolswap/client/Notifier.java`
- Create: `src/main/resources/assets/autotoolswap/lang/en_us.json`
- Create: `src/main/resources/assets/autotoolswap/lang/da_dk.json`

**Interfaces:**
- Consumes: `AutoToolSwapConfig` (Task 3).
- Produces: `Notifier.actionBar(String langKey, Object... args)` og `Notifier.chat(String langKey, Object... args)` — begge spiller lyd hvis `soundNotifications`; beskeder undertrykkes hvis `chatNotifications` er false. `Notifier.init(Supplier<AutoToolSwapConfig>)` kaldes fra entrypoint.

- [ ] **Step 1: Implementér**

`Notifier.java`:

```java
package io.github.mgjuhler.autotoolswap.client;

import io.github.mgjuhler.autotoolswap.core.config.AutoToolSwapConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import java.util.function.Supplier;

public final class Notifier {
	private static Supplier<AutoToolSwapConfig> config = AutoToolSwapConfig::new;

	private Notifier() {}

	public static void init(Supplier<AutoToolSwapConfig> cfg) {
		config = cfg;
	}

	public static void actionBar(String langKey, Object... args) {
		send(langKey, true, args);
	}

	public static void chat(String langKey, Object... args) {
		send(langKey, false, args);
	}

	private static void send(String langKey, boolean overlay, Object... args) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		AutoToolSwapConfig cfg = config.get();
		if (cfg.chatNotifications) {
			mc.player.displayClientMessage(Component.translatable(langKey, args), overlay);
		}
		if (cfg.soundNotifications) {
			mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.4F));
		}
	}
}
```

(Hvis `SoundEvents.EXPERIENCE_ORB_PICKUP` er en `Holder<SoundEvent>` i 26.1.2, brug `SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP.value(), 1.4F)` — javap afgør det.)

`en_us.json`:

```json
{
	"autotoolswap.swapped": "Swapped to fresh %s",
	"autotoolswap.swapped_from_shulker": "Pulled fresh %s from shulker box",
	"autotoolswap.in_shulker": "Replacement %s is in a shulker box — open it to swap",
	"autotoolswap.no_replacement": "%s is almost broken — no replacement found",
	"autotoolswap.stored_old": "Stored worn %s in shulker box",
	"autotoolswap.dropped_old": "Dropped worn %s",
	"autotoolswap.title": "AutoToolSwap"
}
```

`da_dk.json`:

```json
{
	"autotoolswap.swapped": "Skiftede til frisk %s",
	"autotoolswap.swapped_from_shulker": "Hentede frisk %s fra shulkerboks",
	"autotoolswap.in_shulker": "Erstatningen %s ligger i en shulkerboks — åbn den for at bytte",
	"autotoolswap.no_replacement": "%s er ved at gå i stykker — ingen erstatning fundet",
	"autotoolswap.stored_old": "Gemte slidt %s i shulkerboks",
	"autotoolswap.dropped_old": "Smed slidt %s ud",
	"autotoolswap.title": "AutoToolSwap"
}
```

- [ ] **Step 2: Kompilér** — `sh gradlew build --no-daemon`. Expected: `BUILD SUCCESSFUL`.
- [ ] **Step 3: Commit** — `git add -A && git commit -m "feat: add notifier with en/da translations"`

---

### Task 7: DurabilityMonitor + SwapExecutor — modden virker i inventory-scenariet

**Files:**
- Create: `src/client/java/io/github/mgjuhler/autotoolswap/client/SwapExecutor.java`
- Create: `src/client/java/io/github/mgjuhler/autotoolswap/client/DurabilityMonitor.java`
- Modify: `src/client/java/io/github/mgjuhler/autotoolswap/client/AutoToolSwapClient.java`

**Interfaces:**
- Consumes: alt fra Task 2–6.
- Produces: `SwapExecutor.swapIntoMainHand(int fromInventorySlot)`, `SwapExecutor.swapIntoOffhand(int fromInventorySlot)`, `SwapExecutor.throwSlot(int inventorySlot)`, `SwapExecutor.selectSafeSlot()` (returnerer boolean), `static int SwapExecutor.menuSlot(int inventorySlot)`. `DurabilityMonitor.tick(Minecraft mc)` registreret på `ClientTickEvents.END_CLIENT_TICK`. `AutoToolSwapClient.config()` — statisk adgang til den indlæste config; `AutoToolSwapClient.CONFIG_PATH`.

- [ ] **Step 1: Implementér SwapExecutor**

```java
package io.github.mgjuhler.autotoolswap.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;

public final class SwapExecutor {
	private static final int OFFHAND_SWAP_BUTTON = 40; // vanilla: SWAP med knap 40 = offhand

	private SwapExecutor() {}

	/** Oversætter Inventory-indeks (0-8 hotbar, 9-35 resten) til InventoryMenu-slot-id. */
	public static int menuSlot(int inventorySlot) {
		return inventorySlot < 9
			? InventoryMenu.USE_ROW_SLOT_START + inventorySlot
			: InventoryMenu.INV_SLOT_START + (inventorySlot - 9);
	}

	public static void swapIntoMainHand(int fromInventorySlot) {
		Minecraft mc = Minecraft.getInstance();
		var inv = mc.player.getInventory();
		if (inv.isHotbarSlot(fromInventorySlot)) {
			inv.setSelectedSlot(fromInventorySlot);
			return;
		}
		mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId,
			menuSlot(fromInventorySlot), inv.getSelectedSlot(), ContainerInput.SWAP, mc.player);
	}

	public static void swapIntoOffhand(int fromInventorySlot) {
		Minecraft mc = Minecraft.getInstance();
		mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId,
			menuSlot(fromInventorySlot), OFFHAND_SWAP_BUTTON, ContainerInput.SWAP, mc.player);
	}

	public static void throwSlot(int inventorySlot) {
		Minecraft mc = Minecraft.getInstance();
		mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId,
			menuSlot(inventorySlot), 1, ContainerInput.THROW, mc.player); // knap 1 = hele stakken
	}

	/** Vælger en hotbar-plads uden sårbart item. Returnerer false hvis ingen findes. */
	public static boolean selectSafeSlot() {
		Minecraft mc = Minecraft.getInstance();
		var inv = mc.player.getInventory();
		for (int i = 0; i < 9; i++) {
			ItemStack s = inv.getItem(i);
			if (s.isEmpty() || !s.isDamageableItem()) {
				inv.setSelectedSlot(i);
				return true;
			}
		}
		return false;
	}
}
```

- [ ] **Step 2: Implementér DurabilityMonitor**

```java
package io.github.mgjuhler.autotoolswap.client;

import io.github.mgjuhler.autotoolswap.core.Candidate;
import io.github.mgjuhler.autotoolswap.core.DurabilityCheck;
import io.github.mgjuhler.autotoolswap.core.ItemCategory;
import io.github.mgjuhler.autotoolswap.core.ReplacementSelector;
import io.github.mgjuhler.autotoolswap.core.config.AutoToolSwapConfig;
import io.github.mgjuhler.autotoolswap.core.config.OldItemAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class DurabilityMonitor {
	/** Slots (inventory-indeks, -1 = offhand) der allerede er håndteret; ryddes når indholdet ændrer sig. */
	private final Set<String> handled = new HashSet<>();

	public void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		AutoToolSwapConfig cfg = AutoToolSwapClient.config();
		if (player == null || !cfg.enabled) return;
		if (player.isCreative() || player.isSpectator()) return;
		if (mc.screen != null) return; // rør ikke inventory mens en skærm er åben

		int selected = player.getInventory().getSelectedSlot();
		check(mc, player, cfg, player.getInventory().getSelectedItem(), selected, true);
		if (cfg.monitorOffhand) {
			check(mc, player, cfg, player.getInventory().getItem(Inventory.SLOT_OFFHAND), Inventory.SLOT_OFFHAND, false);
		}
	}

	private void check(Minecraft mc, LocalPlayer player, AutoToolSwapConfig cfg,
	                   ItemStack stack, int slot, boolean mainHand) {
		String key = slot + ":" + InventoryScanner.itemId(stack) + ":" + stack.getDamageValue();
		if (stack.isEmpty() || !stack.isDamageableItem() || stack.has(DataComponents.UNBREAKABLE)) {
			handled.removeIf(k -> k.startsWith(slot + ":"));
			return;
		}
		if (!DurabilityCheck.isLow(stack.getDamageValue(), stack.getMaxDamage(), cfg.thresholdPercent)) {
			handled.removeIf(k -> k.startsWith(slot + ":"));
			return;
		}
		if (handled.contains(key)) return;

		ItemCategory category = ItemClassifier.classify(stack);
		if (!ItemClassifier.isMonitored(category, cfg)) return;
		handled.add(key);

		String wornId = InventoryScanner.itemId(stack);
		var candidates = InventoryScanner.scan(player, cfg.searchShulkers, mainHand ? slot : -2);
		Optional<Candidate> best = ReplacementSelector.selectBest(wornId, category, cfg.thresholdPercent, candidates);

		if (best.isEmpty()) {
			if (mainHand && SwapExecutor.selectSafeSlot()) {
				Notifier.actionBar("autotoolswap.no_replacement", stack.getItemName());
			} else {
				Notifier.chat("autotoolswap.no_replacement", stack.getItemName());
			}
			return;
		}

		Candidate c = best.get();
		if (c.location() == Candidate.Location.INVENTORY) {
			int wornEndsUpIn = swapFromInventory(player, c, slot, mainHand);
			Notifier.actionBar("autotoolswap.swapped", stack.getItemName());
			if (cfg.oldItemAction == OldItemAction.DROP && wornEndsUpIn >= 0) {
				SwapExecutor.throwSlot(wornEndsUpIn);
				Notifier.actionBar("autotoolswap.dropped_old", stack.getItemName());
			}
			// STORE_IN_SHULKER for inventory-fund håndteres i Task 8 (pending store)
		} else {
			// Shulker-fund: Task 8 (semi-auto) og Task 9 (singleplayer fuld-auto)
			ShulkerFlow.onShulkerCandidate(mc, cfg, stack, slot, mainHand, c);
		}
	}

	/** Udfører byttet; returnerer inventory-slot hvor det slidte item lander (-1 = ukendt). */
	private int swapFromInventory(LocalPlayer player, Candidate c, int wornSlot, boolean mainHand) {
		if (mainHand) {
			if (player.getInventory().isHotbarSlot(c.inventorySlot())) {
				SwapExecutor.swapIntoMainHand(c.inventorySlot()); // rent slot-valg, intet flyttes
				return wornSlot;
			}
			SwapExecutor.swapIntoMainHand(c.inventorySlot());
			return c.inventorySlot(); // SWAP-klik: det slidte item lander i erstatningens gamle slot
		}
		SwapExecutor.swapIntoOffhand(c.inventorySlot());
		return c.inventorySlot();
	}
}
```

Indtil Task 8 findes `ShulkerFlow` ikke — opret en midlertidig stub, så Task 7 kompilerer:

```java
package io.github.mgjuhler.autotoolswap.client;

import io.github.mgjuhler.autotoolswap.core.Candidate;
import io.github.mgjuhler.autotoolswap.core.config.AutoToolSwapConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

public final class ShulkerFlow {
	private ShulkerFlow() {}

	public static void onShulkerCandidate(Minecraft mc, AutoToolSwapConfig cfg,
	                                      ItemStack worn, int wornSlot, boolean mainHand, Candidate c) {
		Notifier.chat("autotoolswap.in_shulker", worn.getItemName());
	}
}
```

- [ ] **Step 3: Registrér i entrypointet** — erstat `AutoToolSwapClient.java`:

```java
package io.github.mgjuhler.autotoolswap.client;

import io.github.mgjuhler.autotoolswap.core.config.AutoToolSwapConfig;
import io.github.mgjuhler.autotoolswap.core.config.ConfigIO;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;

public class AutoToolSwapClient implements ClientModInitializer {
	public static final String MOD_ID = "autotoolswap";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("autotoolswap.json");

	private static AutoToolSwapConfig config;

	public static AutoToolSwapConfig config() {
		return config;
	}

	public static void saveConfig() {
		ConfigIO.save(config, CONFIG_PATH);
	}

	@Override
	public void onInitializeClient() {
		config = ConfigIO.load(CONFIG_PATH);
		Notifier.init(AutoToolSwapClient::config);
		DurabilityMonitor monitor = new DurabilityMonitor();
		ClientTickEvents.END_CLIENT_TICK.register(monitor::tick);
		LOGGER.info("AutoToolSwap loaded");
	}
}
```

- [ ] **Step 4: Kompilér** — `sh gradlew build --no-daemon`. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Manuel verifikation i spillet**

```bash
sh gradlew runClient --no-daemon
```

I spillet: opret en creative-testverden (Superflat). Kør i chatten:

```
/give @s minecraft:iron_pickaxe[minecraft:damage=230]
/give @s minecraft:iron_pickaxe
/gamemode survival
```

Hold den slidte hakke (20/250 tilbage = 8 %) og slå ét slag på en blok. Expected: modden skifter til den friske hakke og viser „Skiftede til frisk Iron Pickaxe" i action baren. Test også: (a) uden erstatning → skifter til tom plads + advarsel, (b) config-fil `run/config/autotoolswap.json` er oprettet med defaults, (c) i creative mode sker der intet.

- [ ] **Step 6: Commit** — `git add -A && git commit -m "feat: add tick monitor and swap executor - core swapping works"`

---

### Task 8: Shulker semi-auto (server-sikker) + STORE_IN_SHULKER

**Files:**
- Modify: `src/client/java/io/github/mgjuhler/autotoolswap/client/ShulkerFlow.java` (erstat stubben)
- Modify: `src/client/java/io/github/mgjuhler/autotoolswap/client/DurabilityMonitor.java` (STORE_IN_SHULKER for inventory-fund)
- Modify: `src/client/java/io/github/mgjuhler/autotoolswap/client/AutoToolSwapClient.java` (registrér ShulkerFlow-tick)

**Interfaces:**
- Produces: `ShulkerFlow.onShulkerCandidate(...)` (samme signatur som stubben) — sætter `pendingRetrieve` og notificerer; `ShulkerFlow.requestStore(int inventorySlot)` — markerer et slot til at blive lagt i shulker; `ShulkerFlow.tick(Minecraft mc)` — når en container-skærm er åben og menuens slots indeholder det ventende item, udføres klikkene.

- [ ] **Step 1: Implementér ShulkerFlow**

```java
package io.github.mgjuhler.autotoolswap.client;

import io.github.mgjuhler.autotoolswap.core.Candidate;
import io.github.mgjuhler.autotoolswap.core.config.AutoToolSwapConfig;
import io.github.mgjuhler.autotoolswap.core.config.OldItemAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class ShulkerFlow {
	private static String pendingItemId = null;     // itemId der ventes hentet fra en container
	private static int pendingHotbarSlot = -1;      // hotbar-slot byttet skal lande i
	private static boolean pendingStoreWorn = false;

	private ShulkerFlow() {}

	public static void onShulkerCandidate(Minecraft mc, AutoToolSwapConfig cfg,
	                                      ItemStack worn, int wornSlot, boolean mainHand, Candidate c) {
		if (cfg.singleplayerFullAuto && mc.getSingleplayerServer() != null) {
			SingleplayerExtractor.extract(mc, cfg, wornSlot, mainHand, c); // Task 9
			return;
		}
		pendingItemId = c.itemId();
		pendingHotbarSlot = mainHand ? mc.player.getInventory().getSelectedSlot() : -1;
		pendingStoreWorn = cfg.oldItemAction == OldItemAction.STORE_IN_SHULKER;
		if (mainHand) SwapExecutor.selectSafeSlot(); // beskyt det slidte item indtil byttet
		Notifier.chat("autotoolswap.in_shulker", worn.getItemName());
	}

	/** Kaldes hvert tick. Udfører ventende bytte når en container-skærm med itemet er åben. */
	public static void tick(Minecraft mc) {
		if (pendingItemId == null || mc.player == null) return;
		if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) return;
		AbstractContainerMenu menu = screen.getMenu();
		if (menu == mc.player.inventoryMenu) return;

		int containerSlots = menu.slots.size() - 36; // sidste 36 slots er altid spillerens inventory
		for (int i = 0; i < containerSlots; i++) {
			Slot slot = menu.slots.get(i);
			ItemStack stack = slot.getItem();
			if (stack.isEmpty() || !InventoryScanner.itemId(stack).equals(pendingItemId)) continue;

			if (pendingHotbarSlot >= 0 && pendingStoreWorn) {
				// ét SWAP-klik: erstatning ind i hotbaren, det slidte item ind i boksen
				mc.gameMode.handleContainerInput(menu.containerId, i, pendingHotbarSlot,
					ContainerInput.SWAP, mc.player);
				Notifier.actionBar("autotoolswap.stored_old", stack.getItemName());
			} else if (pendingHotbarSlot >= 0) {
				mc.gameMode.handleContainerInput(menu.containerId, i, pendingHotbarSlot,
					ContainerInput.SWAP, mc.player);
				// gamle item ligger nu i containeren — hent det tilbage til inventory hvis KEEP
				if (AutoToolSwapClient.config().oldItemAction == OldItemAction.KEEP) {
					mc.gameMode.handleContainerInput(menu.containerId, i, 0,
						ContainerInput.QUICK_MOVE, mc.player);
				}
			} else {
				mc.gameMode.handleContainerInput(menu.containerId, i, 0,
					ContainerInput.QUICK_MOVE, mc.player);
			}
			if (pendingHotbarSlot >= 0) mc.player.getInventory().setSelectedSlot(pendingHotbarSlot);
			Notifier.actionBar("autotoolswap.swapped_from_shulker", stack.getItemName());
			clear();
			return;
		}
	}

	public static void clear() {
		pendingItemId = null;
		pendingHotbarSlot = -1;
		pendingStoreWorn = false;
	}
}
```

- [ ] **Step 2: Registrér tick i entrypointet** — i `AutoToolSwapClient.onInitializeClient()` tilføjes efter monitor-registreringen:

```java
		ClientTickEvents.END_CLIENT_TICK.register(ShulkerFlow::tick);
```

Bemærk: `DurabilityMonitor.tick` returnerer tidligt når `mc.screen != null`, så de to tick-handlers kolliderer ikke.

- [ ] **Step 3: Kompilér** — `sh gradlew build --no-daemon`. Expected: `BUILD SUCCESSFUL`. (`SingleplayerExtractor` findes ikke endnu — opret stub som i Task 7 med tom `extract(...)`-metode med samme parametre.)

- [ ] **Step 4: Manuel verifikation** — `sh gradlew runClient`. I testverdenen: sæt `singleplayerFullAuto` til `false` i `run/config/autotoolswap.json` (simulerer server-flowet). Giv dig selv en shulkerboks med en frisk hakke i, og en slidt hakke i hånden (`/give @s minecraft:iron_pickaxe[minecraft:damage=230]`). Survival, slå på en blok → besked om at erstatningen ligger i shulkeren; stil boksen, åbn den → modden bytter automatisk; med `oldItemAction: "STORE_IN_SHULKER"` skal den slidte hakke ligge i boksen bagefter.

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat: add shulker detection and semi-auto container swapping"`

---

### Task 9: Singleplayer fuld-auto

**Files:**
- Modify: `src/client/java/io/github/mgjuhler/autotoolswap/client/SingleplayerExtractor.java` (erstat stubben)

**Interfaces:**
- Consumes: `Candidate` (SHULKER-location: `inventorySlot` = shulker-stakkens plads, `slotInShulker` = plads i boksen).
- Produces: `static void SingleplayerExtractor.extract(Minecraft mc, AutoToolSwapConfig cfg, int wornSlot, boolean mainHand, Candidate c)` — kører på den integrerede servers tråd: tager erstatningen ud af shulker-komponenten, sætter den i det slidte items slot og håndterer det slidte item efter config.

- [ ] **Step 1: Implementér**

```java
package io.github.mgjuhler.autotoolswap.client;

import io.github.mgjuhler.autotoolswap.core.Candidate;
import io.github.mgjuhler.autotoolswap.core.config.AutoToolSwapConfig;
import io.github.mgjuhler.autotoolswap.core.config.OldItemAction;
import net.minecraft.client.Minecraft;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import java.util.UUID;

public final class SingleplayerExtractor {
	private SingleplayerExtractor() {}

	public static void extract(Minecraft mc, AutoToolSwapConfig cfg,
	                           int wornSlot, boolean mainHand, Candidate c) {
		var server = mc.getSingleplayerServer();
		if (server == null) return;
		UUID uuid = mc.player.getUUID();
		OldItemAction action = cfg.oldItemAction;
		int shulkerSlot = c.inventorySlot();
		int boxSlot = c.slotInShulker();
		int targetSlot = mainHand ? wornSlot : Inventory.SLOT_OFFHAND;

		server.execute(() -> {
			ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
			if (sp == null) return;
			Inventory inv = sp.getInventory();
			ItemStack shulker = inv.getItem(shulkerSlot);
			if (!shulker.has(DataComponents.CONTAINER)) return;

			NonNullList<ItemStack> items = NonNullList.withSize(InventoryScanner.SHULKER_SIZE, ItemStack.EMPTY);
			shulker.get(DataComponents.CONTAINER).copyInto(items);
			ItemStack replacement = items.get(boxSlot);
			if (replacement.isEmpty()) return; // indholdet har ændret sig — opgiv stille

			ItemStack worn = inv.getItem(targetSlot);
			items.set(boxSlot, action == OldItemAction.STORE_IN_SHULKER ? worn : ItemStack.EMPTY);
			shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
			inv.setItem(targetSlot, replacement);
			switch (action) {
				case DROP -> sp.drop(worn, true);
				case KEEP -> {
					if (action == OldItemAction.KEEP && !sp.getInventory().add(worn)) sp.drop(worn, true);
				}
				case STORE_IN_SHULKER -> {} // allerede lagt i boksen
			}
		});
		Notifier.actionBar("autotoolswap.swapped_from_shulker",
			mc.player.getInventory().getItem(wornSlot).getItemName());
	}
}
```

- [ ] **Step 2: Kompilér** — `sh gradlew build --no-daemon`. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Manuel verifikation** — `runClient`, testverden, `singleplayerFullAuto: true` (default). Slidt hakke i hånden, frisk hakke KUN i en shulkerboks i inventory. Slå på en blok → hakken skiftes automatisk uden at åbne boksen; med `STORE_IN_SHULKER` ligger den slidte hakke i boksen (tjek ved at åbne den). Test også KEEP og DROP.

- [ ] **Step 4: Commit** — `git add -A && git commit -m "feat: add singleplayer full-auto shulker extraction"`

---

### Task 10: Cloth Config-skærm + ModMenu

**Files:**
- Create: `src/client/java/io/github/mgjuhler/autotoolswap/client/ConfigScreenBuilder.java`
- Create: `src/client/java/io/github/mgjuhler/autotoolswap/client/ModMenuIntegration.java`
- Modify: `src/main/resources/fabric.mod.json` (modmenu-entrypoint)
- Modify: lang-filerne (option-nøgler)

**Interfaces:**
- Produces: `static Screen ConfigScreenBuilder.build(Screen parent)`; ModMenu-entrypoint `io.github.mgjuhler.autotoolswap.client.ModMenuIntegration`.

- [ ] **Step 1: Implementér**

`ConfigScreenBuilder.java`:

```java
package io.github.mgjuhler.autotoolswap.client;

import io.github.mgjuhler.autotoolswap.core.config.AutoToolSwapConfig;
import io.github.mgjuhler.autotoolswap.core.config.OldItemAction;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ConfigScreenBuilder {
	private ConfigScreenBuilder() {}

	public static Screen build(Screen parent) {
		AutoToolSwapConfig cfg = AutoToolSwapClient.config();
		ConfigBuilder builder = ConfigBuilder.create()
			.setParentScreen(parent)
			.setTitle(Component.translatable("autotoolswap.title"))
			.setSavingRunnable(AutoToolSwapClient::saveConfig);
		ConfigEntryBuilder e = builder.entryBuilder();
		ConfigCategory general = builder.getOrCreateCategory(Component.translatable("autotoolswap.category.general"));

		general.addEntry(e.startBooleanToggle(Component.translatable("autotoolswap.option.enabled"), cfg.enabled)
			.setDefaultValue(true).setSaveConsumer(v -> cfg.enabled = v).build());
		general.addEntry(e.startIntSlider(Component.translatable("autotoolswap.option.threshold"), cfg.thresholdPercent, 1, 50)
			.setDefaultValue(10).setSaveConsumer(v -> cfg.thresholdPercent = v).build());
		general.addEntry(e.startEnumSelector(Component.translatable("autotoolswap.option.old_item"), OldItemAction.class, cfg.oldItemAction)
			.setDefaultValue(OldItemAction.KEEP).setSaveConsumer(v -> cfg.oldItemAction = v).build());
		general.addEntry(e.startBooleanToggle(Component.translatable("autotoolswap.option.offhand"), cfg.monitorOffhand)
			.setDefaultValue(true).setSaveConsumer(v -> cfg.monitorOffhand = v).build());
		general.addEntry(e.startBooleanToggle(Component.translatable("autotoolswap.option.tools"), cfg.monitorTools)
			.setDefaultValue(true).setSaveConsumer(v -> cfg.monitorTools = v).build());
		general.addEntry(e.startBooleanToggle(Component.translatable("autotoolswap.option.weapons"), cfg.monitorWeapons)
			.setDefaultValue(true).setSaveConsumer(v -> cfg.monitorWeapons = v).build());
		general.addEntry(e.startBooleanToggle(Component.translatable("autotoolswap.option.ranged"), cfg.monitorRanged)
			.setDefaultValue(true).setSaveConsumer(v -> cfg.monitorRanged = v).build());
		general.addEntry(e.startBooleanToggle(Component.translatable("autotoolswap.option.shield"), cfg.monitorShield)
			.setDefaultValue(true).setSaveConsumer(v -> cfg.monitorShield = v).build());
		general.addEntry(e.startBooleanToggle(Component.translatable("autotoolswap.option.shears"), cfg.monitorShears)
			.setDefaultValue(true).setSaveConsumer(v -> cfg.monitorShears = v).build());
		general.addEntry(e.startBooleanToggle(Component.translatable("autotoolswap.option.shulkers"), cfg.searchShulkers)
			.setDefaultValue(true).setSaveConsumer(v -> cfg.searchShulkers = v).build());
		general.addEntry(e.startBooleanToggle(Component.translatable("autotoolswap.option.sp_full_auto"), cfg.singleplayerFullAuto)
			.setDefaultValue(true).setSaveConsumer(v -> cfg.singleplayerFullAuto = v).build());
		general.addEntry(e.startBooleanToggle(Component.translatable("autotoolswap.option.chat"), cfg.chatNotifications)
			.setDefaultValue(true).setSaveConsumer(v -> cfg.chatNotifications = v).build());
		general.addEntry(e.startBooleanToggle(Component.translatable("autotoolswap.option.sound"), cfg.soundNotifications)
			.setDefaultValue(true).setSaveConsumer(v -> cfg.soundNotifications = v).build());

		return builder.build();
	}
}
```

`ModMenuIntegration.java`:

```java
package io.github.mgjuhler.autotoolswap.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return ConfigScreenBuilder::build;
	}
}
```

I `fabric.mod.json` udvides `entrypoints`:

```json
	"entrypoints": {
		"client": ["io.github.mgjuhler.autotoolswap.client.AutoToolSwapClient"],
		"modmenu": ["io.github.mgjuhler.autotoolswap.client.ModMenuIntegration"]
	},
```

Tilføj til `en_us.json` (og tilsvarende dansk i `da_dk.json`):

```json
	"autotoolswap.category.general": "General",
	"autotoolswap.option.enabled": "Enabled",
	"autotoolswap.option.threshold": "Durability threshold (%)",
	"autotoolswap.option.old_item": "Worn item handling",
	"autotoolswap.option.offhand": "Monitor offhand",
	"autotoolswap.option.tools": "Monitor tools",
	"autotoolswap.option.weapons": "Monitor melee weapons",
	"autotoolswap.option.ranged": "Monitor bows & crossbows",
	"autotoolswap.option.shield": "Monitor shields",
	"autotoolswap.option.shears": "Monitor shears",
	"autotoolswap.option.shulkers": "Search shulker boxes",
	"autotoolswap.option.sp_full_auto": "Full auto in singleplayer",
	"autotoolswap.option.chat": "Show messages",
	"autotoolswap.option.sound": "Play sound"
```

Danske værdier til `da_dk.json`:

```json
	"autotoolswap.category.general": "Generelt",
	"autotoolswap.option.enabled": "Slået til",
	"autotoolswap.option.threshold": "Holdbarhedsgrænse (%)",
	"autotoolswap.option.old_item": "Håndtering af slidt item",
	"autotoolswap.option.offhand": "Overvåg offhand",
	"autotoolswap.option.tools": "Overvåg værktøj",
	"autotoolswap.option.weapons": "Overvåg nærkampsvåben",
	"autotoolswap.option.ranged": "Overvåg buer og armbrøster",
	"autotoolswap.option.shield": "Overvåg skjolde",
	"autotoolswap.option.shears": "Overvåg sakse",
	"autotoolswap.option.shulkers": "Søg i shulkerbokse",
	"autotoolswap.option.sp_full_auto": "Fuld automatik i singleplayer",
	"autotoolswap.option.chat": "Vis beskeder",
	"autotoolswap.option.sound": "Afspil lyd"
```

- [ ] **Step 2: Kompilér** — `sh gradlew build --no-daemon`. Expected: `BUILD SUCCESSFUL`. (Ved Cloth-API-afvigelser: dekompilér med javap mod cloth-jarren i Gradle-cachen `~/.gradle/caches/modules-2/files-2.1/me.shedaniel.cloth/`.)

- [ ] **Step 3: Manuel verifikation** — `runClient` → hovedmenu → Mods → AutoToolSwap → tandhjul. Ændr threshold til 25, gem, tjek at `run/config/autotoolswap.json` indeholder `"thresholdPercent": 25`.

- [ ] **Step 4: Commit** — `git add -A && git commit -m "feat: add cloth config screen and modmenu integration"`

---

### Task 11: Release — README, fuld testkørsel, GitHub, CurseForge

**Files:**
- Create: `README.md`
- Modify: `gradle.properties` (version 1.0.0)

**Interfaces:** ingen — ren udgivelse.

- [ ] **Step 1: README.md** (engelsk; kort: hvad modden gør, krav — MC 26.1.2/Fabric/Fabric API/Cloth Config, valgfrit ModMenu — installation, config-oversigt, licens MIT, credit til LowDurabilitySwitcher som inspiration).

- [ ] **Step 2: Fuld manuel test-tjekliste** (i `runClient`-testverden; alle skal bestå):
1. Slidt hakke + frisk hakke i inventory → auto-skift ✔
2. Slidt hakke + kun jernhakke (fallback-tier) → skifter til jern ✔
3. Ingen erstatning → skift til tom plads + besked, ingen spam ved gentagne slag ✔
4. Offhand-skjold under grænsen → byttes ✔
5. Erstatning kun i shulker, fuld-auto → hentes automatisk; STORE_IN_SHULKER lægger den slidte i boksen ✔
6. Samme scenarie med `singleplayerFullAuto: false` → besked + semi-auto ved åbning ✔
7. DROP → slidt item droppes ✔
8. Creative mode → ingen handling ✔
9. Unbreakable item (`/give @s minecraft:iron_pickaxe[minecraft:unbreakable={}]`... hvis komponent-syntaksen afviser tom compound, brug `minecraft:damage=0` og spring testen over med en note) → ignoreres ✔
10. Config-skærm gemmer ændringer ✔
11. Bue (ranged) matcher aldrig armbrøst ✔

- [ ] **Step 3: Version 1.0.0 + build** — sæt `mod_version=1.0.0` i gradle.properties; `sh gradlew build --no-daemon`; verificér `build/libs/autotoolswap-1.0.0.jar`.

- [ ] **Step 4: GitHub-repo + push**

```bash
cd "C:/Users/mgjuh/autotoolswap"
TOKEN=$(printf "protocol=https\nhost=github.com\n\n" | git credential fill | sed -n 's/^password=//p')
curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Accept: application/vnd.github+json" \
  -d '{"name":"autotoolswap","description":"Fabric client mod: auto-switch away from low-durability tools (MC 26.1.2)"}' \
  https://api.github.com/user/repos
git remote add origin https://github.com/mgjuhler/autotoolswap.git
git push -u origin master
```

Lav derefter en GitHub-release `v1.0.0` med jarren som asset (samme API-flow som world-pregen-releasen).

- [ ] **Step 5: CurseForge** — kræver manuelle trin i browseren (Mads skal selv logge ind): opret projekt på https://authors.curseforge.com → Mods → navn „AutoToolSwap", kategori Utility & QoL; upload `autotoolswap-1.0.0.jar`; sæt game version 26.1.2, loader Fabric, environment Client; angiv Cloth Config som required dependency og ModMenu som optional. Skriv trinene ud til Mads og stop dér.

- [ ] **Step 6: Commit** — `git add -A && git commit -m "chore: prepare 1.0.0 release"` og push.

---

## Self-review (udført under planskrivning)

- **Spec-dækning:** auto-skift (T7), konfigurerbar grænse (T2/T3/T10), valgbare kategorier (T3/T5/T10), shulker-læsning (T5), semi-auto på server (T8), fuld-auto SP (T9), old-item-håndtering (T7/T8/T9), config-UI (T10), notifikationer (T6), fallback til tom plads (T7), debounce (T7), creative/unbreakable-undtagelser (T7), JUnit på kernelogik (T2-T4), manuel tjekliste (T11), CurseForge (T11). Ingen huller fundet.
- **Kendt usikkerhed (bevidst accepteret):** enkelte API-detaljer (fx `is(Item)`-overload, `SoundEvents`-holder-typer, Cloth-builder-metoder) kan afvige i 26.1.2 — hver kompilérfejl løses med javap-opslag mod klient-jarren, jf. Global Constraints. Selve arkitekturen afhænger ikke af dem.
- **STORE_IN_SHULKER for inventory-fund** (slidt item skal i shulker, men erstatningen kom fra inventory): dækkes af `ShulkerFlow.requestStore`-mekanismen i T8's interface; hvis den viser sig at være mere kompleks end SWAP-flowet, nedgraderes til: notifikation + spilleren lægger selv itemet i boksen (dokumenteres i README).
