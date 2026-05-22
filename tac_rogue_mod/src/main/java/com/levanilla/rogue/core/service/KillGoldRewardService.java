package com.levanilla.rogue.core.service;

import com.levanilla.rogue.core.DifficultyManager;
import com.levanilla.rogue.core.PerkDefinition;
import com.levanilla.rogue.core.PriceManager;
import com.levanilla.rogue.core.RunManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public final class KillGoldRewardService {
    private static final float VARIANT_MULT = 2.0F;
    private static final float BOSS_ADD_MULT = 1.5F;
    private static final float BOSS_MULT = 8.0F;
    private static final float HEADSHOT_BONUS_MULT = 0.30F;

    private KillGoldRewardService() {}

    public static int award(ServerPlayer killer, Entity killed, boolean headshot) {
        int floor = RunManager.getData(killer).getCurrentFloor();
        float goldBonus = sumPerkEffect(killer, "perk:GOLD_RUSH") / 100.0F;
        float typeMult = rewardTypeMultiplier(killed);
        float difficultyMult = DifficultyManager.getGoldMultiplier();
        int baseReward = Math.max(1, Math.round(PriceManager.getKillReward(floor)
            * typeMult
            * (1.0F + goldBonus)
            * difficultyMult));
        int headshotBonus = headshot ? Math.max(1, Math.round(baseReward * HEADSHOT_BONUS_MULT)) : 0;
        int total = baseReward + headshotBonus;
        GoldGainService.award(killer, total);
        return total;
    }

    private static float rewardTypeMultiplier(Entity killed) {
        if (killed == null) return 1.0F;
        if (killed.getTags().contains("rogue:boss")) return BOSS_MULT;
        if (killed.getTags().contains("tac_rogue_variant")) return VARIANT_MULT;
        if (killed.getTags().contains("tac_rogue_boss_add")) return BOSS_ADD_MULT;
        return 1.0F;
    }

    private static float sumPerkEffect(ServerPlayer player, String perkPrefix) {
        float sum = 0.0F;
        for (String tag : player.getTags()) {
            if (tag.startsWith(perkPrefix)) {
                sum += PerkDefinition.fromTag(tag).calculateEffect();
            }
        }
        return sum;
    }
}
