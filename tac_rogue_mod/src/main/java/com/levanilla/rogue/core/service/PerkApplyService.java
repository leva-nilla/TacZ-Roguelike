package com.levanilla.rogue.core.service;

import com.levanilla.rogue.core.GameConstants;
import com.levanilla.rogue.core.PerkDefinition;
import com.levanilla.rogue.core.RunManager;
import com.levanilla.rogue.networking.RogueActionMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** Single server-side path for validating and applying perk choices. */
public final class PerkApplyService {
    private static final int MAX_TITANIC_PERKS = 2;
    private static final int MAX_CORRUPTED_PERKS = 3;

    private PerkApplyService() {}

    public static boolean applySelectedPerk(ServerPlayer player, String rawTag) {
        if (player == null || rawTag == null || rawTag.isEmpty()) return false;

        long perkCount = player.getTags().stream().filter(t -> t.startsWith("perk:")).count();
        if (perkCount > 150) {
            player.sendSystemMessage(Component.literal("\u00A7c[ANTI-CHEAT] Too many perks."));
            return false;
        }

        PerkDefinition perk = PerkDefinition.fromTag(rawTag);
        String perkTag = perk.toTag();
        List<String> allowed = RogueActionMessage.getPendingPerkChoices(player.getUUID());
        if (allowed == null || !allowed.contains(perkTag)) {
            player.sendSystemMessage(Component.literal("\u00A7c[ANTI-CHEAT] Invalid perk selection."));
            return false;
        }

        if (!validateModifierLimit(player, perk.modifier)) return false;

        RogueActionMessage.clearPerkChoices(player.getUUID());
        int serial = player.getPersistentData().getInt("TacRoguePerkSerial") + 1;
        player.getPersistentData().putInt("TacRoguePerkSerial", serial);
        String storedTag = perkTag + ":#" + serial;
        player.addTag(storedTag);
        RunManager.savePerkTags(player);
        net.minecraft.network.chat.MutableComponent acquired =
            Component.literal(perk.getDisplayName() + " \u00A77- " + perk.getDescription());
        if (perk.modifier == PerkDefinition.Modifier.CURSED) {
            acquired.append(Component.literal(" \u00A78/ \u00A7c"));
            acquired.append(PerkDefinition.getCursedPenaltyDescription(player.getUUID(), storedTag));
        }
        player.sendSystemMessage(Component.translatable(
            "message.tac_rogue.perk_acquired", acquired));
        RunManager.syncPlayer(player);
        return true;
    }

    private static boolean validateModifierLimit(ServerPlayer player, PerkDefinition.Modifier modifier) {
        int limit = switch (modifier) {
            case OVERCLOCKED -> GameConstants.MAX_OVERCLOCKED_PERKS;
            case CURSED -> GameConstants.MAX_CURSED_PERKS;
            case TITANIC -> MAX_TITANIC_PERKS;
            case CORRUPTED -> MAX_CORRUPTED_PERKS;
            default -> -1;
        };
        if (limit < 0) return true;
        int count = countModifier(player, modifier);
        if (count >= limit) {
            player.sendSystemMessage(Component.literal(
                "\u00A7c[PERK] " + modifier.name() + " limit reached (" + limit + "/" + limit + ")"));
            return false;
        }
        return true;
    }

    private static int countModifier(ServerPlayer player, PerkDefinition.Modifier modifier) {
        int count = 0;
        String token = ":" + modifier.name() + ":";
        for (String tag : player.getTags()) {
            if (tag.startsWith("perk:") && tag.contains(token)) count++;
        }
        return count;
    }
}
