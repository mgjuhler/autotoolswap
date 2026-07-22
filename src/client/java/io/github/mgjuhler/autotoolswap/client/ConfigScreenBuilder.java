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
			.setDefaultValue(OldItemAction.KEEP).setSaveConsumer(v -> cfg.oldItemAction = v)
			.setEnumNameProvider(v -> Component.translatable("autotoolswap.old_item." + v.name().toLowerCase(java.util.Locale.ROOT)))
			.build());
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
