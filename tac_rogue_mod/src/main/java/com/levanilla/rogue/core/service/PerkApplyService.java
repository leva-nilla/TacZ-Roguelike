package com.levanilla.rogue.core.service;

import com.levanilla.rogue.core.PerkDefinition;
import com.levanilla.rogue.core.RunManager;
import com.levanilla.rogue.core.GameConstants;
import com.levanilla.rogue.networking.RogueActionMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** Single server-side path for validating and applying perk choices. */
public final class PerkApplyService {
    private PerkApplyService() {}

    public static boolean applySelectedPerk(ServerPlayer player, String rawTag) {
        if (player == null || rawTag == null || rawTag.isEmpty()) return false;

        long perkCount = PerkStorageService.getPerkCount(player);
        if (perkCount >= GameConstants.MAX_PERK_TAG_COUNT) {
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

        String context = RogueActionMessage.getPendingPerkContext(player.getUUID());
        com.levanilla.rogue.core.PlayerRunData runData = RunManager.getData(player);
        int floor = runData.getCurrentFloor();
        if ("floor_clear".equals(context) && floor > 0 && runData.hasClaimedPerkReward(floor)) {
            RogueActionMessage.clearPerkChoices(player.getUUID());
            player.sendSystemMessage(Component.literal("\u00A7e[PERK] This floor reward was already claimed."));
            return false;
        }

        RogueActionMessage.clearPerkChoices(player.getUUID());
        int serial = player.getPersistentData().getInt("TacRoguePerkSerial") + 1;
        player.getPersistentData().putInt("TacRoguePerkSerial", serial);
        String storedTag = perkTag + ":#" + serial;
        PerkStorageService.addPerk(player, storedTag);
        if ("floor_clear".equals(context) && floor > 0) {
            runData.markPerkRewardClaimed(floor);
        }
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
}
