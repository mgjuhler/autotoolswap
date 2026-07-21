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
	private static DurabilityMonitor monitor;

	public static AutoToolSwapConfig config() {
		return config;
	}

	public static void saveConfig() {
		ConfigIO.save(config, CONFIG_PATH);
	}

	public static void resetDebounce() {
		if (monitor != null) monitor.resetDebounce();
	}

	@Override
	public void onInitializeClient() {
		config = ConfigIO.load(CONFIG_PATH);
		Notifier.init(AutoToolSwapClient::config);
		monitor = new DurabilityMonitor();
		ClientTickEvents.END_CLIENT_TICK.register(monitor::tick);
		ClientTickEvents.END_CLIENT_TICK.register(ShulkerFlow::tick);
		LOGGER.info("AutoToolSwap loaded");
	}
}
