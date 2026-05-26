package com.levanilla.rogue.core.service;

import com.levanilla.rogue.core.GameConstants;
import com.levanilla.rogue.core.PerkDefinition;
import com.levanilla.rogue.core.StaminaManager;
import com.levanilla.rogue.core.event.CombatEventHandler;
import net.minecraft.server.level.ServerPlayer;

public final class PlayerRegenService {

    private PlayerRegenService() {}

    public static void applyCustomHealthRegen(ServerPlayer player) {
        long lastDamageTick = CombatEventHandler.getLastDamageTick(player.getUUID());
        long currentTick = player.level().getGameTime();
        if ((currentTick - lastDamageTick) < GameConstants.REGEN_DAMAGE_COOLDOWN_TICKS) return;

        float currentHp = player.getHealth();
        float maxHp = player.getMaxHealth();
        float maxAllowedHp = LowHealthChallengeService.isActive(player)
            ? LowHealthChallengeService.capHealth(player)
            : maxHp;
        if (currentHp >= maxAllowedHp) {
            LowHealthChallengeService.enforceCap(player);
            return;
        }
        if (currentHp >= maxHp) return;

        float regenEffect = PerkDefinition.sumCategoryEffect(player, PerkDefinition.Category.REGENERATION);

        float regenCapRatio = Math.min(1.0f,
            GameConstants.REGEN_BASE_CAP_RATIO + PerkDefinition.getRegenerationCapUnlockRatio(regenEffect));
        float regenCap = Math.min(maxAllowedHp, maxHp * regenCapRatio);
        if (currentHp >= regenCap) {
            LowHealthChallengeService.enforceCap(player);
            return;
        }

        if (regenEffect > 0) {
            applyCappedHeal(player, currentHp, regenCap, PerkDefinition.getRegenerationHealPerSecond(regenEffect));
            LowHealthChallengeService.enforceCap(player);
            return;
        }

        if (currentHp < regenCap) {
            float stamina = StaminaManager.getStamina(player);
            float maxStamina = StaminaManager.getMaxStamina(player);
            if (maxStamina > 0 && (stamina / maxStamina) >= GameConstants.REGEN_STAMINA_THRESHOLD) {
                applyCappedHeal(player, currentHp, regenCap, GameConstants.REGEN_BASE_HEAL);
                LowHealthChallengeService.enforceCap(player);
            }
        }
    }

    private static void applyCappedHeal(ServerPlayer player, float currentHp, float capHp, float healAmount) {
        if (healAmount <= 0.0f) return;
        player.setHealth(Math.min(capHp, Math.min(player.getMaxHealth(), currentHp + healAmount)));
    }
}
