package io.github.mgjuhler.autotoolswap.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigIO {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Logger LOGGER = LoggerFactory.getLogger("autotoolswap");
	private ConfigIO() {}

	public static AutoToolSwapConfig load(Path file) {
		if (Files.exists(file)) {
			try {
				AutoToolSwapConfig cfg = GSON.fromJson(Files.readString(file), AutoToolSwapConfig.class);
				if (cfg != null) {
					if (cfg.oldItemAction == null) cfg.oldItemAction = OldItemAction.KEEP;
					cfg.thresholdPercent = Math.max(1, Math.min(100, cfg.thresholdPercent));
					return cfg;
				}
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
			LOGGER.warn("Could not save config to {}", file, e);
		}
	}
}
