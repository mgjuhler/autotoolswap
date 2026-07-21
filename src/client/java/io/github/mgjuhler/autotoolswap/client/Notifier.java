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
		Component component = Component.translatable(langKey, args);
		if (cfg.chatNotifications) {
			if (overlay) {
				mc.gui.setOverlayMessage(component, false);
			} else {
				mc.player.sendSystemMessage(component);
			}
		}
		if (cfg.soundNotifications) {
			mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.4F));
		}
	}
}
