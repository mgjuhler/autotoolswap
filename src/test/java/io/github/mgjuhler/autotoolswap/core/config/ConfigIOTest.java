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
