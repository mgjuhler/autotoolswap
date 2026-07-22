# AutoToolSwap

A Fabric client mod for Minecraft that automatically swaps away from tools, weapons, and shields before they break, replacing them with a fresh copy from your inventory or a shulker box.

## What it does

- Monitors the durability of the item(s) in your main hand and offhand every tick.
- When an item's remaining durability drops below a configurable threshold, it looks for a replacement:
  - First an exact match (same item), then a same-category fallback (e.g. an iron pickaxe if no diamond one is left) for pickaxes, axes, shovels, hoes, and swords.
  - Bows and crossbows are never matched against each other — a ranged weapon is only replaced by the same type.
- Replacements are found in your inventory and in shulker boxes:
  - **Singleplayer, full auto:** the replacement is pulled out of the shulker box automatically, no matter where the box is.
  - **Multiplayer (or full-auto disabled):** the game notifies you that a replacement is waiting in a shulker box. The actual swap happens the moment you open that box or a chest containing it — this is a semi-automatic flow, since the client cannot reach into a container on a server without the player opening it first.
- If no replacement can be found, the worn item is moved to an empty slot and you get a one-time notification — it won't spam you on every subsequent hit.
- Creative mode and unbreakable items are ignored entirely.

## Requirements

- Minecraft 26.1.2
- Fabric Loader >= 0.19.3
- Fabric API
- Cloth Config (required — used for the config screen and config file handling)
- ModMenu (optional — only needed to open the config screen from the mod list)

AutoToolSwap is **client-side only**. It works on any server, vanilla or modded, without needing to be installed server-side.

## Installation

Drop the built jar (`autotoolswap-<version>.jar`) into your `mods` folder, alongside Fabric API and Cloth Config.

## Configuration

Open the config screen through ModMenu, or edit the config file directly. Options:

- **Enabled** — master on/off switch.
- **Durability threshold (%)** — swap when remaining durability falls below this percentage. Default: 10.
- **Monitored categories** — toggle which item types are watched: tools (pickaxe/axe/shovel/hoe), melee weapons (sword/trident/mace), bows & crossbows, shield, shears. Offhand is monitored separately and can be toggled off.
- **Search shulker boxes** — whether shulker box contents count as a source of replacements.
- **Full auto in singleplayer** — when enabled, replacements found in a shulker box are extracted automatically in singleplayer worlds. When disabled (or on a server), swaps from a shulker box become semi-automatic: you get a notification and the swap completes when you open the box.
- **Worn item handling** — what happens to the item that got swapped out:
  - **Keep** — put back in your inventory.
  - **Drop** — thrown on the ground.
  - **Store in shulker box** — put into the shulker box the replacement came from. This is fully automatic only when the replacement itself came from that same shulker box (or in singleplayer full-auto mode). If the replacement came from your regular inventory instead, there is no box open to store the old item in, so the mod keeps it in your inventory and reminds you to store it manually.
- **Show messages** — action bar and chat notifications for swaps, pending shulker swaps, and "no replacement found" warnings.
- **Play sound** — a short sound cue alongside notifications.

## Notifications

Notifications are shown both as an action bar message and (for some events) a chat message, optionally paired with a sound. All messages are localized in English and Danish (`en_us` / `da_dk`).

## License

MIT — see [LICENSE](LICENSE).

## Credits

Inspired by [LowDurabilitySwitcher](https://www.curseforge.com/minecraft/mc-mods/lowdurabilityswitcher).
