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
        if (currentHp >= maxHp) return;

        float regenEffect = 0;
        for (String tag : player.getTags()) {
            if (tag.startsWith("perk:REGENERATION")) {
                regenEffect += PerkDefinition.fromTag(tag).calculateEffect();
            }
        }

        if (regenEffect > 0) {
            player.heal(regenEffect / 10.0f);
            return;
        }

        float regenCap = maxHp * GameConstants.REGEN_BASE_CAP_RATIO;
        if (currentHp < regenCap) {
            float stamina = StaminaManager.getStamina(player);
            float maxStamina = StaminaManager.getMaxStamina(player);
            if (maxStamina > 0 && (stamina / maxStamina) >= GameConstants.REGEN_STAMINA_THRESHOLD) {
                player.heal(GameConstants.REGEN_BASE_HEAL);
            }
        }
    }
}
