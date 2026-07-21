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
