package com.levanilla.rogue.core;

import net.minecraft.world.entity.player.Player;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * プレイヤーごとのスタミナ管理。
 * ダッシュ消費・回復・カスタム自然回復を制御する。
 */
public class StaminaManager {
    private static final Map<UUID, Float> PLAYER_STAMINA = new HashMap<>();
    private static final Map<UUID, Float> PLAYER_MAX_STAMINA = new HashMap<>();

    public static void setMaxStamina(Player player, float value) {
        PLAYER_MAX_STAMINA.put(player.getUUID(), value);
    }

    public static float getMaxStamina(Player player) {
        return PLAYER_MAX_STAMINA.getOrDefault(player.getUUID(), GameConstants.DEFAULT_MAX_STAMINA);
    }

    public static float getStamina(Player player) {
        return PLAYER_STAMINA.getOrDefault(player.getUUID(), getMaxStamina(player));
    }

    public static void setStamina(Player player, float value) {
        PLAYER_STAMINA.put(player.getUUID(), value);
    }

    public static void tick(Player player) {
        tick(player, 1.0f);
    }

    public static void tick(Player player, float regenMultiplier) {
        UUID id = player.getUUID();
        float current = getStamina(player);
        float max = getMaxStamina(player);

        if (player.isSprinting()) {
            current = Math.max(0, current - GameConstants.STAMINA_CONSUME_RATE);
            if (current <= 0) {
                player.setSprinting(false);
            }
        } else {
            current = Math.min(max, current + (GameConstants.STAMINA_REGEN_RATE * regenMultiplier));
        }

        PLAYER_STAMINA.put(id, current);
        
        // Sync hunger bar visually (current / max * 20)
        // バニラの自然回復を防止するため、foodLevelは最大 17 に制限
        int displayFood = Math.min(17, (int)((current / max) * 20.0f));
        player.getFoodData().setFoodLevel(displayFood);
        player.getFoodData().setSaturation(0.0f);

    }
}
