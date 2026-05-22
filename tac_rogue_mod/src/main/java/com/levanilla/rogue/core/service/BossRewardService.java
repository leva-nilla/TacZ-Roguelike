package com.levanilla.rogue.core.service;

import com.levanilla.rogue.core.CurrencyManager;
import com.levanilla.rogue.core.PlayerRunData;
import com.levanilla.rogue.core.QuestManager;
import com.levanilla.rogue.core.RunManager;
import com.levanilla.rogue.core.registry.ShopCatalog;
import com.levanilla.rogue.networking.PopupNotificationMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BossRewardService {
    private static final java.util.Set<UUID> REWARDED_BOSSES = ConcurrentHashMap.newKeySet();

    private BossRewardService() {}

    public static boolean handleBossKill(ServerPlayer killer, LivingEntity boss) {
        if (killer == null || boss == null || !boss.getTags().contains("rogue:boss")) return false;
        if (!REWARDED_BOSSES.add(boss.getUUID())) return false;

        int floor = Math.max(1, boss.getPersistentData().getInt("TacRogueSpawnFloor"));
        List<ServerPlayer> recipients = FloorInstanceManager.getParticipantsForEntity(boss);
        if (recipients.isEmpty()) recipients = List.of(killer);
        for (ServerPlayer player : recipients) {
            rewardBossKill(player, floor);
        }

        return true;
    }

    private static void rewardBossKill(ServerPlayer player, int floor) {
        QuestManager.advanceQuest(player, QuestManager.QuestType.BOSS_KILL, 1);
        PlayerRunData runData = RunManager.getData(player);
        if (!runData.hasClaimedBossReward(floor)) {
            runData.markBossRewardClaimed(floor);
            CurrencyManager.addGold(player, 1000);
            DeepProgressService.awardDeepBossFirstKill(player, floor);
            PopupNotificationMessage.send(
                player,
                PopupNotificationMessage.PopupType.REWARD,
                Component.translatable("popup.tac_rogue.quest_weapon.title"),
                Component.translatable("message.tac_rogue.boss_first_kill_bonus", 1000),
                130
            );
            grantBossWeapon(player, floor);
        }

        RunManager.syncPlayer(player);
    }

    private static void grantBossWeapon(ServerPlayer player, int floor) {
        int maxPrice = 1400 + floor * 180;
        List<ShopCatalog.ShopItem> candidates = RewardSelectionService.weaponCandidates(
            floor,
            maxPrice,
            List.of(
                ShopCatalog.Category.RIFLE,
                ShopCatalog.Category.SHOTGUN,
                ShopCatalog.Category.SNIPER,
                ShopCatalog.Category.LMG,
                ShopCatalog.Category.EXPLOSIVE
            ));
        if (candidates.isEmpty()) return;

        ShopCatalog.ShopItem reward = candidates.get(player.getRandom().nextInt(candidates.size()));
        ItemStack stack = RogueItemFactory.createRewardWeaponStack(player, reward.id, Math.max(10, floor + 10), player.getRandom());
        if (stack.isEmpty()) return;
        DeepProgressService.maybeApplyDeepModifier(stack, floor, player.getRandom(), 0.45f);

        ShopPlacementService.placeRewardItem(player, stack, reward.id);
        PopupNotificationMessage.send(
            player,
            PopupNotificationMessage.PopupType.REWARD,
            Component.translatable("popup.tac_rogue.quest_weapon.title"),
            Component.translatable("message.tac_rogue.boss_weapon_reward", reward.displayName),
            150
        );
    }

    public static void clearMemory() {
        REWARDED_BOSSES.clear();
    }
}
