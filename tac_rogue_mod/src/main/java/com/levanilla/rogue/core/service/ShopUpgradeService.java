package com.levanilla.rogue.core.service;

import com.levanilla.rogue.core.CurrencyManager;
import com.levanilla.rogue.core.GameConstants;
import com.levanilla.rogue.core.PerkDefinition;
import com.levanilla.rogue.core.PriceManager;
import com.levanilla.rogue.core.RunManager;
import com.levanilla.rogue.core.StashSavedData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class ShopUpgradeService {

    private ShopUpgradeService() {}

    public static void handleStashUpgrade(ServerPlayer player) {
        StashSavedData data = StashSavedData.get(player.serverLevel());
        StashSavedData.PlayerStash stash = data.getStash(player.getUUID());
        if (stash.unlockedLines >= GameConstants.STASH_MAX_LINES) {
            player.sendSystemMessage(Component.translatable("message.tac_rogue.stash_max"));
            return;
        }

        int currentLevel = Math.max(0, stash.unlockedLines - 2);
        int price = PriceManager.getUpgradePrice("rogue:stash_upgrade", currentLevel);
        if (CurrencyManager.consumeGold(player, price)) {
            stash.unlockedLines++;
            data.setDirty();
            player.sendSystemMessage(Component.translatable("message.tac_rogue.stash_upgraded", stash.unlockedLines * 9));
            ShopService.handleSyncStash(player);
            RunManager.syncPlayer(player);
        } else {
            player.sendSystemMessage(Component.translatable("gui.tac_rogue.inventory.insufficient_funds"));
        }
    }

    public static void handleLegacyStashUpgrade(ServerPlayer player) {
        StashSavedData data = StashSavedData.get(player.serverLevel());
        StashSavedData.PlayerStash stash = data.getStash(player.getUUID());

        if (stash.unlockedLines < GameConstants.STASH_MAX_LINES) {
            int cost = GameConstants.STASH_UPGRADE_COST_BASE * (stash.unlockedLines + 1);
            if (CurrencyManager.consumeGold(player, cost)) {
                stash.unlockedLines++;
                data.setDirty();
                player.sendSystemMessage(Component.translatable("message.tac_rogue.stash_upgraded", stash.unlockedLines * 9));
                ShopService.handleSyncStash(player);
            } else {
                player.sendSystemMessage(Component.translatable("gui.tac_rogue.inventory.insufficient_funds"));
            }
        } else {
            player.sendSystemMessage(Component.translatable("message.tac_rogue.stash_max"));
        }
    }

    public static void handleInventoryUpgrade(ServerPlayer player) {
        int invLevel = player.getPersistentData().getInt("TacRogue_InvLevel");
        if (invLevel >= GameConstants.INV_MAX_LEVEL) {
            player.sendSystemMessage(Component.translatable("message.tac_rogue.inv_max"));
            return;
        }

        int price = PriceManager.getUpgradePrice("rogue:inv_upgrade", invLevel);
        if (CurrencyManager.consumeGold(player, price)) {
            player.getPersistentData().putInt("TacRogue_InvLevel", invLevel + 1);
            int newSlots = Math.min(GameConstants.SLOT_AMMO_GUN2_END + (invLevel + 1) * 2, 35) - GameConstants.SLOT_AMMO_GUN2_END;
            player.sendSystemMessage(Component.translatable("message.tac_rogue.inv_upgraded", newSlots));
            RunManager.syncPlayer(player);
        } else {
            player.sendSystemMessage(Component.translatable("gui.tac_rogue.inventory.insufficient_funds"));
        }
    }

    public static void handleAmmoCapacityUpgrade(ServerPlayer player) {
        int capLevel = RunManager.getData(player).getAmmoCapacityLevel();
        if (capLevel >= GameConstants.AMMO_CAPACITY_MAX_LEVEL) {
            player.sendSystemMessage(Component.literal("\u00A7c[SHOP] \u00A7fAMMO POUCH is already at MAX LEVEL!"));
            return;
        }

        int price = PriceManager.getUpgradePrice("rogue:ammo_capacity_upgrade", capLevel);
        if (CurrencyManager.consumeGold(player, price)) {
            RunManager.getData(player).setAmmoCapacityLevel(capLevel + 1);
            player.sendSystemMessage(Component.literal("\u00A7e[UPGRADE] \u00A7fAMMO POUCH upgraded to Lvl " + (capLevel + 1) + "!"));
            RunManager.syncPlayer(player);
        } else {
            player.sendSystemMessage(Component.translatable("gui.tac_rogue.inventory.insufficient_funds"));
        }
    }

    public static void handleMeleeUpgrade(ServerPlayer player) {
        int level = player.getPersistentData().getInt("TacRogueMeleeLevel");
        if (level >= GameConstants.MELEE_MAX_LEVEL) {
            player.sendSystemMessage(Component.literal("\u00A7c[SHOP] \u00A7fMELEE WEAPON is already at MAX LEVEL!"));
            return;
        }

        int price = PriceManager.getUpgradePrice("rogue:melee_upgrade", level);
        if (CurrencyManager.consumeGold(player, price)) {
            player.getPersistentData().putInt("TacRogueMeleeLevel", level + 1);
            player.sendSystemMessage(Component.literal("\u00A7e[UPGRADE] \u00A7fMELEE WEAPON upgraded to Lvl " + (level + 1) + "!"));
            RunManager.syncPlayer(player);
        } else {
            player.sendSystemMessage(Component.translatable("gui.tac_rogue.inventory.insufficient_funds"));
        }
    }

    public static void handleFlashlightUpgrade(ServerPlayer player) {
        int level = player.getPersistentData().getInt("TacRogueFlashlightLevel");
        if (level >= GameConstants.FLASHLIGHT_MAX_LEVEL) {
            player.sendSystemMessage(Component.literal("\u00A7c[SHOP] \u00A7fFLASHLIGHT is already at MAX LEVEL!"));
            return;
        }

        int price = PriceManager.getUpgradePrice("rogue:flashlight_upgrade", level);
        if (CurrencyManager.consumeGold(player, price)) {
            player.getPersistentData().putInt("TacRogueFlashlightLevel", level + 1);
            player.sendSystemMessage(Component.literal("\u00A7e[UPGRADE] \u00A7fFLASHLIGHT upgraded to Lvl " + (level + 1) + "!"));
            RunManager.syncPlayer(player);
        } else {
            player.sendSystemMessage(Component.translatable("gui.tac_rogue.inventory.insufficient_funds"));
        }
    }

    public static void handleRandomPerkPurchase(ServerPlayer player) {
        long perkCount = player.getTags().stream().filter(t -> t.startsWith("perk:")).count();
        if (perkCount >= GameConstants.MAX_PERK_TAG_COUNT) {
            player.sendSystemMessage(Component.literal("\u00A7c[SHOP] \u00A7fPerk storage limit reached."));
            return;
        }

        int buys = player.getPersistentData().getInt("RandomPerkBuys");
        int price = PriceManager.getUpgradePrice("rogue:random_perk", buys);
        if (!CurrencyManager.consumeGold(player, price)) {
            player.sendSystemMessage(Component.translatable("gui.tac_rogue.inventory.insufficient_funds"));
            return;
        }

        player.getPersistentData().putInt("RandomPerkBuys", buys + 1);

        Set<String> existing = new HashSet<>();
        for (String tag : player.getTags()) {
            if (tag.startsWith("perk:")) {
                existing.add(PerkDefinition.fromTag(tag).toTag());
            }
        }

        int targetFloor = Math.max(RunManager.getData(player).getCurrentFloor(), RunManager.getData(player).getMaxReachedFloor());
        int maxLevel = Math.max(1, Math.min(10, 1 + (targetFloor * 9 / 80)));
        PerkDefinition chosen = chooseRandomPerk(existing, maxLevel);

        int serial = player.getPersistentData().getInt("TacRogueRandomPerkSerial") + 1;
        player.getPersistentData().putInt("TacRogueRandomPerkSerial", serial);
        String uniqueTag = chosen.toTag() + ":#" + serial;
        player.addTag(uniqueTag);
        RunManager.savePerkTags(player);
        player.sendSystemMessage(Component.literal("\u00A7a[SHOP] \u00A7fObtained Perk: " + chosen.getDisplayName()));
        RunManager.syncPlayer(player);
    }

    private static PerkDefinition chooseRandomPerk(Set<String> existing, int maxLevel) {
        PerkDefinition.Category[] categories = PerkDefinition.Category.values();
        for (int attempt = 0; attempt < 50; attempt++) {
            PerkDefinition.Category category = categories[ThreadLocalRandom.current().nextInt(categories.length)];
            int level = 1 + ThreadLocalRandom.current().nextInt(maxLevel);
            PerkDefinition candidate = new PerkDefinition(category, PerkDefinition.Modifier.NONE, level);
            if (!existing.contains(candidate.toTag())) {
                return candidate;
            }
        }

        PerkDefinition.Category fallback = categories[ThreadLocalRandom.current().nextInt(categories.length)];
        return new PerkDefinition(fallback, PerkDefinition.Modifier.NONE, 1);
    }
}
