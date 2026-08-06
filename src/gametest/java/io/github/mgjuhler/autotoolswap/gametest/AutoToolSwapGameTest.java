package io.github.mgjuhler.autotoolswap.gametest;

import io.github.mgjuhler.autotoolswap.client.AutoToolSwapClient;
import io.github.mgjuhler.autotoolswap.client.ConfigScreenBuilder;
import io.github.mgjuhler.autotoolswap.client.ShulkerFlow;
import io.github.mgjuhler.autotoolswap.core.config.AutoToolSwapConfig;
import io.github.mgjuhler.autotoolswap.core.config.ConfigIO;
import io.github.mgjuhler.autotoolswap.core.config.OldItemAction;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Automated in-game checks mirroring the manual test checklist (plan Task 11).
 * The config screen scenario is a smoke test (opens, renders, persists via the
 * same save path) — it does not drive individual Cloth Config widgets.
 */
public class AutoToolSwapGameTest implements FabricClientGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("autotoolswap-gametest");

	/** Ticks to wait for command sync + monitor tick + swap round-trip. */
	private static final int SETTLE_TICKS = 15;

	// Max durability: diamond pickaxe 1561, iron pickaxe 250, shield 336, bow 384.
	private static final int WORN_DIAMOND_PICKAXE = 1550;
	private static final int WORN_IRON_PICKAXE = 245;
	private static final int WORN_SHIELD = 330;
	private static final int WORN_BOW = 378;

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			TestServerContext server = sp.getServer();
			server.runCommand("gamemode survival @a");
			server.runCommand("difficulty peaceful");
			ctx.waitTicks(20);

			basicSwap(ctx, server);
			tierFallback(ctx, server);
			noReplacement(ctx, server);
			offhandShield(ctx, server);
			fullAutoShulker(ctx, server);
			semiAutoShulker(ctx, server);
			dropOldItem(ctx, server);
			creativeNoAction(ctx, server);
			unbreakableIgnored(ctx, server);
			bowNeverMatchesCrossbow(ctx, server);
			configScreenSmoke(ctx);

			LOG.info("All AutoToolSwap client gametests passed");
		}
	}

	/** Scenario 1: worn pickaxe in hand + fresh one in inventory -> auto swap. */
	private void basicSwap(ClientGameTestContext ctx, TestServerContext server) {
		reset(ctx, server);
		server.runCommand("item replace entity @a inventory.0 with minecraft:diamond_pickaxe");
		ctx.waitTicks(2);
		server.runCommand("item replace entity @a hotbar.0 with minecraft:diamond_pickaxe[minecraft:damage=" + WORN_DIAMOND_PICKAXE + "]");
		ctx.waitTicks(SETTLE_TICKS);
		String err = ctx.computeOnClient(mc -> {
			Inventory inv = mc.player.getInventory();
			ItemStack hand = inv.getSelectedItem();
			ItemStack stored = inv.getItem(9);
			if (inv.getSelectedSlot() != 0) return "selected slot is " + inv.getSelectedSlot() + ", expected 0";
			if (hand.getItem() != Items.DIAMOND_PICKAXE || hand.getDamageValue() != 0)
				return "main hand is " + hand + ", expected fresh diamond pickaxe";
			if (stored.getItem() != Items.DIAMOND_PICKAXE || stored.getDamageValue() != WORN_DIAMOND_PICKAXE)
				return "inventory slot 9 is " + stored + ", expected the worn pickaxe";
			return null;
		});
		pass("basic swap", err);
	}

	/** Scenario 2: worn diamond pickaxe + only an iron pickaxe available -> falls back to iron. */
	private void tierFallback(ClientGameTestContext ctx, TestServerContext server) {
		reset(ctx, server);
		server.runCommand("item replace entity @a inventory.0 with minecraft:iron_pickaxe");
		ctx.waitTicks(2);
		server.runCommand("item replace entity @a hotbar.0 with minecraft:diamond_pickaxe[minecraft:damage=" + WORN_DIAMOND_PICKAXE + "]");
		ctx.waitTicks(SETTLE_TICKS);
		String err = ctx.computeOnClient(mc -> {
			ItemStack hand = mc.player.getInventory().getSelectedItem();
			if (hand.getItem() != Items.IRON_PICKAXE)
				return "main hand is " + hand + ", expected iron pickaxe";
			return null;
		});
		pass("tier fallback", err);
	}

	/** Scenario 3: no replacement -> switches to an empty hotbar slot, worn item untouched. */
	private void noReplacement(ClientGameTestContext ctx, TestServerContext server) {
		reset(ctx, server);
		server.runCommand("item replace entity @a hotbar.0 with minecraft:diamond_pickaxe[minecraft:damage=" + WORN_DIAMOND_PICKAXE + "]");
		ctx.waitTicks(SETTLE_TICKS);
		String err = ctx.computeOnClient(mc -> {
			Inventory inv = mc.player.getInventory();
			if (inv.getSelectedSlot() == 0) return "selected slot is still 0, expected a safe slot";
			ItemStack worn = inv.getItem(0);
			if (worn.getItem() != Items.DIAMOND_PICKAXE || worn.getDamageValue() != WORN_DIAMOND_PICKAXE)
				return "slot 0 is " + worn + ", expected the untouched worn pickaxe";
			return null;
		});
		pass("no replacement -> safe slot", err);
	}

	/** Scenario 4: worn shield in offhand + fresh shield in inventory -> offhand swapped. */
	private void offhandShield(ClientGameTestContext ctx, TestServerContext server) {
		reset(ctx, server);
		server.runCommand("item replace entity @a inventory.0 with minecraft:shield");
		ctx.waitTicks(2);
		server.runCommand("item replace entity @a weapon.offhand with minecraft:shield[minecraft:damage=" + WORN_SHIELD + "]");
		ctx.waitTicks(SETTLE_TICKS);
		String err = ctx.computeOnClient(mc -> {
			Inventory inv = mc.player.getInventory();
			ItemStack off = inv.getItem(Inventory.SLOT_OFFHAND);
			if (off.getItem() != Items.SHIELD || off.getDamageValue() != 0)
				return "offhand is " + off + ", expected fresh shield";
			ItemStack stored = inv.getItem(9);
			if (stored.getItem() != Items.SHIELD || stored.getDamageValue() != WORN_SHIELD)
				return "inventory slot 9 is " + stored + ", expected the worn shield";
			return null;
		});
		pass("offhand shield swap", err);
	}

	/** Scenario 5 (full-auto): replacement only inside a shulker item; STORE_IN_SHULKER puts the worn one in the box. */
	private void fullAutoShulker(ClientGameTestContext ctx, TestServerContext server) {
		reset(ctx, server);
		ctx.runOnClient(mc -> {
			AutoToolSwapConfig cfg = AutoToolSwapClient.config();
			cfg.singleplayerFullAuto = true;
			cfg.oldItemAction = OldItemAction.STORE_IN_SHULKER;
		});
		server.runCommand("item replace entity @a inventory.0 with minecraft:shulker_box[minecraft:container=[{slot:0,item:{id:\"minecraft:diamond_pickaxe\",count:1}}]]");
		ctx.waitTicks(2);
		server.runCommand("item replace entity @a hotbar.0 with minecraft:diamond_pickaxe[minecraft:damage=" + WORN_DIAMOND_PICKAXE + "]");
		ctx.waitTicks(SETTLE_TICKS);
		String err = ctx.computeOnClient(mc -> {
			Inventory inv = mc.player.getInventory();
			ItemStack hand = inv.getSelectedItem();
			if (hand.getItem() != Items.DIAMOND_PICKAXE || hand.getDamageValue() != 0)
				return "main hand is " + hand + ", expected fresh pickaxe from the shulker";
			ItemStack box = inv.getItem(9);
			if (box.getItem() != Items.SHULKER_BOX || !box.has(DataComponents.CONTAINER))
				return "inventory slot 9 is " + box + ", expected the shulker box";
			NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
			box.get(DataComponents.CONTAINER).copyInto(items);
			ItemStack stored = items.get(0);
			if (stored.getItem() != Items.DIAMOND_PICKAXE || stored.getDamageValue() != WORN_DIAMOND_PICKAXE)
				return "shulker slot 0 is " + stored + ", expected the worn pickaxe stored there";
			return null;
		});
		pass("full-auto shulker extract + store", err);
	}

	/** Scenario 6 (semi-auto): pending swap executes when the player opens a container holding the replacement. */
	private void semiAutoShulker(ClientGameTestContext ctx, TestServerContext server) {
		reset(ctx, server);
		ctx.runOnClient(mc -> {
			AutoToolSwapConfig cfg = AutoToolSwapClient.config();
			cfg.singleplayerFullAuto = false;
			cfg.oldItemAction = OldItemAction.STORE_IN_SHULKER;
		});
		// A placed shulker box two blocks east of the player, holding the replacement.
		server.runCommand("execute at @a run setblock ~2 ~ ~ minecraft:shulker_box");
		server.runCommand("execute at @a run item replace block ~2 ~ ~ container.0 with minecraft:diamond_pickaxe");
		// A shulker ITEM in the inventory holding a replacement makes the monitor schedule a pending swap.
		server.runCommand("item replace entity @a inventory.0 with minecraft:shulker_box[minecraft:container=[{slot:0,item:{id:\"minecraft:diamond_pickaxe\",count:1}}]]");
		ctx.waitTicks(2);
		server.runCommand("item replace entity @a hotbar.0 with minecraft:diamond_pickaxe[minecraft:damage=" + WORN_DIAMOND_PICKAXE + "]");
		ctx.waitTicks(SETTLE_TICKS);
		String err = ctx.computeOnClient(mc -> {
			if (!ShulkerFlow.isPending()) return "no pending swap was scheduled";
			if (mc.player.getInventory().getSelectedSlot() == 0) return "safe slot was not selected while waiting";
			return null;
		});
		pass("semi-auto shulker: pending + safe slot", err);

		// Open the placed box programmatically — same code path as a right click.
		ctx.runOnClient(mc -> {
			BlockPos pos = mc.player.blockPosition().offset(2, 0, 0);
			mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(pos), Direction.WEST, pos, false));
		});
		ctx.waitFor(mc -> mc.gui.screen() instanceof AbstractContainerScreen<?>);
		ctx.waitTicks(SETTLE_TICKS);
		err = ctx.computeOnClient(mc -> {
			Inventory inv = mc.player.getInventory();
			if (ShulkerFlow.isPending()) return "swap still pending after opening the container";
			if (inv.getSelectedSlot() != 0) return "selected slot is " + inv.getSelectedSlot() + ", expected back on 0";
			ItemStack hand = inv.getItem(0);
			if (hand.getItem() != Items.DIAMOND_PICKAXE || hand.getDamageValue() != 0)
				return "hotbar 0 is " + hand + ", expected the fresh pickaxe from the box";
			if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen))
				return "container screen unexpectedly closed";
			AbstractContainerMenu menu = screen.getMenu();
			boolean wornInBox = false;
			for (int i = 0; i < menu.slots.size() - 36; i++) {
				ItemStack s = menu.slots.get(i).getItem();
				if (s.getItem() == Items.DIAMOND_PICKAXE && s.getDamageValue() == WORN_DIAMOND_PICKAXE) wornInBox = true;
			}
			if (!wornInBox) return "worn pickaxe was not stored in the opened box";
			return null;
		});
		ctx.runOnClient(mc -> mc.player.closeContainer());
		ctx.waitTicks(5);
		pass("semi-auto shulker: swap on container open", err);
	}

	/** Scenario 7: oldItemAction=DROP -> the worn item is thrown out after the swap. */
	private void dropOldItem(ClientGameTestContext ctx, TestServerContext server) {
		reset(ctx, server);
		ctx.runOnClient(mc -> AutoToolSwapClient.config().oldItemAction = OldItemAction.DROP);
		server.runCommand("item replace entity @a inventory.0 with minecraft:iron_pickaxe");
		ctx.waitTicks(2);
		server.runCommand("item replace entity @a hotbar.0 with minecraft:iron_pickaxe[minecraft:damage=" + WORN_IRON_PICKAXE + "]");
		ctx.waitTicks(SETTLE_TICKS);
		String err = ctx.computeOnClient(mc -> {
			Inventory inv = mc.player.getInventory();
			ItemStack hand = inv.getSelectedItem();
			if (hand.getItem() != Items.IRON_PICKAXE || hand.getDamageValue() != 0)
				return "main hand is " + hand + ", expected fresh iron pickaxe";
			ItemStack slot9 = inv.getItem(9);
			if (!slot9.isEmpty())
				return "inventory slot 9 is " + slot9 + ", expected empty after DROP";
			return null;
		});
		server.runCommand("kill @e[type=item]");
		pass("DROP throws worn item", err);
	}

	/** Scenario 8: creative mode -> the mod does nothing. */
	private void creativeNoAction(ClientGameTestContext ctx, TestServerContext server) {
		reset(ctx, server);
		server.runCommand("gamemode creative @a");
		ctx.waitTicks(5);
		server.runCommand("item replace entity @a inventory.0 with minecraft:diamond_pickaxe");
		ctx.waitTicks(2);
		server.runCommand("item replace entity @a hotbar.0 with minecraft:diamond_pickaxe[minecraft:damage=" + WORN_DIAMOND_PICKAXE + "]");
		ctx.waitTicks(SETTLE_TICKS);
		String err = ctx.computeOnClient(mc -> {
			Inventory inv = mc.player.getInventory();
			ItemStack hand = inv.getSelectedItem();
			if (inv.getSelectedSlot() != 0) return "selected slot changed in creative";
			if (hand.getItem() != Items.DIAMOND_PICKAXE || hand.getDamageValue() != WORN_DIAMOND_PICKAXE)
				return "main hand is " + hand + ", expected the untouched worn pickaxe";
			return null;
		});
		server.runCommand("clear @a");
		server.runCommand("gamemode survival @a");
		ctx.waitTicks(5);
		pass("creative mode untouched", err);
	}

	/** Scenario 9: unbreakable item -> ignored even at high damage. */
	private void unbreakableIgnored(ClientGameTestContext ctx, TestServerContext server) {
		reset(ctx, server);
		server.runCommand("item replace entity @a inventory.0 with minecraft:iron_pickaxe");
		ctx.waitTicks(2);
		try {
			server.runCommand("item replace entity @a hotbar.0 with minecraft:iron_pickaxe[minecraft:damage=" + WORN_IRON_PICKAXE + ",minecraft:unbreakable={}]");
		} catch (RuntimeException e) {
			LOG.warn("SKIP unbreakable scenario, command rejected: {}", e.toString());
			return;
		}
		ctx.waitTicks(SETTLE_TICKS);
		String err = ctx.computeOnClient(mc -> {
			Inventory inv = mc.player.getInventory();
			ItemStack hand = inv.getSelectedItem();
			if (inv.getSelectedSlot() != 0) return "selected slot changed for an unbreakable item";
			if (hand.getItem() != Items.IRON_PICKAXE || hand.getDamageValue() != WORN_IRON_PICKAXE)
				return "main hand is " + hand + ", expected the untouched unbreakable pickaxe";
			return null;
		});
		pass("unbreakable ignored", err);
	}

	/** Scenario 11: a worn bow must never swap to a crossbow. */
	private void bowNeverMatchesCrossbow(ClientGameTestContext ctx, TestServerContext server) {
		reset(ctx, server);
		server.runCommand("item replace entity @a inventory.0 with minecraft:crossbow");
		ctx.waitTicks(2);
		server.runCommand("item replace entity @a hotbar.0 with minecraft:bow[minecraft:damage=" + WORN_BOW + "]");
		ctx.waitTicks(SETTLE_TICKS);
		String err = ctx.computeOnClient(mc -> {
			Inventory inv = mc.player.getInventory();
			if (inv.getSelectedItem().getItem() == Items.CROSSBOW)
				return "main hand is a crossbow, bow must never match crossbow";
			ItemStack bow = inv.getItem(0);
			if (bow.getItem() != Items.BOW || bow.getDamageValue() != WORN_BOW)
				return "slot 0 is " + bow + ", expected the untouched worn bow";
			if (inv.getSelectedSlot() == 0) return "selected slot is still 0, expected a safe slot";
			return null;
		});
		pass("bow never matches crossbow", err);
	}

	/** Scenario 10 (smoke): config screen opens and renders; the save path persists to disk. */
	private void configScreenSmoke(ClientGameTestContext ctx) {
		ctx.setScreen(() -> ConfigScreenBuilder.build(null));
		ctx.waitTicks(5);
		String err = ctx.computeOnClient(mc -> mc.gui.screen() != null ? null : "config screen did not open");
		pass("config screen opens", err);
		ctx.takeScreenshot("autotoolswap-config-screen");

		// Close via Cloth's save button when the label matches, otherwise dismiss.
		if (!ctx.tryClickScreenButton("Save & Quit") && !ctx.tryClickScreenButton("Save & Done")) {
			ctx.runOnClient(mc -> mc.gui.setScreen(null));
		}
		ctx.waitTicks(2);
		err = ctx.computeOnClient(mc -> mc.gui.screen() == null ? null : "config screen did not close");
		pass("config screen closes", err);

		// Round-trip through the exact save path the screen's saving runnable uses.
		ctx.runOnClient(mc -> {
			AutoToolSwapClient.config().thresholdPercent = 15;
			AutoToolSwapClient.saveConfig();
		});
		err = ctx.computeOnClient(mc -> {
			AutoToolSwapConfig loaded = ConfigIO.load(AutoToolSwapClient.CONFIG_PATH);
			return loaded.thresholdPercent == 15 ? null : "saved threshold did not persist";
		});
		ctx.runOnClient(mc -> {
			AutoToolSwapClient.config().thresholdPercent = 10;
			AutoToolSwapClient.saveConfig();
		});
		pass("config save/load round-trip", err);
	}

	/** Clears inventory and monitor state, resets config to known values (full-auto OFF, KEEP). */
	private void reset(ClientGameTestContext ctx, TestServerContext server) {
		server.runCommand("clear @a");
		server.runCommand("kill @e[type=item]");
		ctx.runOnClient(mc -> {
			ShulkerFlow.clear();
			mc.player.getInventory().setSelectedSlot(0);
			AutoToolSwapConfig cfg = AutoToolSwapClient.config();
			cfg.enabled = true;
			cfg.thresholdPercent = 10;
			cfg.monitorOffhand = true;
			cfg.monitorTools = true;
			cfg.monitorWeapons = true;
			cfg.monitorRanged = true;
			cfg.monitorShield = true;
			cfg.monitorShears = true;
			cfg.searchShulkers = true;
			cfg.singleplayerFullAuto = false;
			cfg.oldItemAction = OldItemAction.KEEP;
			cfg.chatNotifications = true;
			cfg.soundNotifications = false;
		});
		ctx.waitTicks(5);
	}

	private static void pass(String scenario, String err) {
		if (err != null) throw new AssertionError(scenario + ": " + err);
		LOG.info("PASS {}", scenario);
	}
}
