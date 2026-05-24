package com.levanilla.rogue.core;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * プレイヤーごとのスタミナ管理。
 * ダッシュ消費・回復・カスタム自然回復を制御する。
 *
 * サーバー側: PLAYER_STAMINA / PLAYER_MAX_STAMINA で実値を管理。
 * クライアント側: clientStamina / clientMaxStamina で同期値を表示用に保持。
 */
public class StaminaManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(StaminaManager.class);
    private static final String BASE_MAX_STAMINA_KEY = "TacRogueBaseMaxStamina";
    private static final String CURRENT_STAMINA_KEY = "TacRogueCurrentStamina";
    private static final String MAX_STAMINA_KEY = "TacRogueMaxStamina";
    private static final String EXHAUSTED_KEY = "TacRogueStaminaExhausted";
    private static final String ADS_EXHAUST_PENALTY_KEY = "TacRogueAdsExhaustPenalty";
    private static final String ADS_EXHAUST_DAMAGE_EVENT_KEY = "TacRogueAdsExhaustDamageEvent";
    private static final UUID ADS_EXHAUST_SPEED_UUID = UUID.fromString("64e17476-1f8f-42f5-92d1-c0214de97b1e");
    private static final Map<UUID, Float> PLAYER_STAMINA = new HashMap<>();
    private static final Map<UUID, Float> PLAYER_MAX_STAMINA = new HashMap<>();
    private static final Map<UUID, Float> PLAYER_BASE_MAX_STAMINA = new HashMap<>();
    private static final Map<UUID, Boolean> PLAYER_EXHAUSTED = new HashMap<>();
    private static final Map<UUID, Integer> ADS_EXHAUST_TICKS = new HashMap<>();
    private static final Map<UUID, Long> ADS_EXHAUST_LAST_DAMAGE_TICK = new HashMap<>();
    private static final Map<UUID, Boolean> CLIENT_ADS_INPUT = new HashMap<>();
    private static final Map<UUID, Long> CLIENT_ADS_INPUT_TICK = new HashMap<>();
    private static final Map<UUID, Float> ADS_FORCED_HEALTH = new HashMap<>();
    private static final Map<UUID, Long> ADS_FORCED_HEALTH_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> ADS_DEBUG_TRACE_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> ADS_DEBUG_TRACE_LAST_TICK = new HashMap<>();

    // === サーバー側: 前回同期値（差分同期用） ===
    private static final Map<UUID, Float> lastSyncedStamina = new HashMap<>();
    private static final Map<UUID, Float> lastSyncedMaxStamina = new HashMap<>();
    private static final Map<UUID, Boolean> lastSyncedExhausted = new HashMap<>();

    // === クライアント側: 同期キャッシュ ===
    private static float clientStamina = GameConstants.DEFAULT_MAX_STAMINA;
    private static float clientMaxStamina = GameConstants.DEFAULT_MAX_STAMINA;
    private static float clientDisplayedStamina = GameConstants.DEFAULT_MAX_STAMINA;
    private static float clientDisplayedMaxStamina = GameConstants.DEFAULT_MAX_STAMINA;
    private static boolean clientExhausted = false;
    private static boolean clientDisplayInitialized = false;

    public static void setMaxStamina(Player player, float value) {
        PLAYER_MAX_STAMINA.put(player.getUUID(), value);
    }

    public static void setBaseMaxStamina(Player player, float value) {
        PLAYER_BASE_MAX_STAMINA.put(player.getUUID(), value);
        player.getPersistentData().putFloat(BASE_MAX_STAMINA_KEY, value);
    }

    public static float getBaseMaxStamina(Player player) {
        Float cached = PLAYER_BASE_MAX_STAMINA.get(player.getUUID());
        if (cached != null) return cached;
        if (player.getPersistentData().contains(BASE_MAX_STAMINA_KEY)) {
            float value = player.getPersistentData().getFloat(BASE_MAX_STAMINA_KEY);
            PLAYER_BASE_MAX_STAMINA.put(player.getUUID(), value);
            return value;
        }
        return GameConstants.DEFAULT_MAX_STAMINA;
    }

    public static float getMaxStamina(Player player) {
        Float cached = PLAYER_MAX_STAMINA.get(player.getUUID());
        if (cached != null) return cached;
        if (player.getPersistentData().contains(MAX_STAMINA_KEY)) {
            float value = Math.max(20.0f, player.getPersistentData().getFloat(MAX_STAMINA_KEY));
            PLAYER_MAX_STAMINA.put(player.getUUID(), value);
            return value;
        }
        return GameConstants.DEFAULT_MAX_STAMINA;
    }

    public static float getStamina(Player player) {
        Float cached = PLAYER_STAMINA.get(player.getUUID());
        if (cached != null) return cached;
        if (player.getPersistentData().contains(CURRENT_STAMINA_KEY)) {
            float value = Math.max(0.0f, Math.min(getMaxStamina(player), player.getPersistentData().getFloat(CURRENT_STAMINA_KEY)));
            PLAYER_STAMINA.put(player.getUUID(), value);
            return value;
        }
        return getMaxStamina(player);
    }

    public static void setStamina(Player player, float value) {
        float max = getMaxStamina(player);
        float clamped = Math.max(0.0f, Math.min(max, value));
        PLAYER_STAMINA.put(player.getUUID(), clamped);
        if (clamped > 0.0f) {
            resetAdsExhaustion(player);
        }
        updateExhaustedState(player, clamped, max);
    }

    public static void saveToPersistentData(Player player) {
        float max = getMaxStamina(player);
        player.getPersistentData().putFloat(MAX_STAMINA_KEY, max);
        player.getPersistentData().putFloat(CURRENT_STAMINA_KEY, Math.max(0.0f, Math.min(max, getStamina(player))));
    }

    public static void restoreFromPersistentData(Player player) {
        float currentMax = getMaxStamina(player);
        PLAYER_MAX_STAMINA.put(player.getUUID(), currentMax);
        float current = player.getPersistentData().contains(CURRENT_STAMINA_KEY)
            ? player.getPersistentData().getFloat(CURRENT_STAMINA_KEY)
            : currentMax;
        PLAYER_STAMINA.put(player.getUUID(), Math.max(0.0f, Math.min(currentMax, current)));
        PLAYER_EXHAUSTED.put(player.getUUID(), player.getPersistentData().getBoolean(EXHAUSTED_KEY));
        clearAdsExhaustPenalty(player);
        updateExhaustedState(player, getStamina(player), currentMax);
        saveToPersistentData(player);
    }

    // === クライアント側アクセサ（HUD / GUI 用） ===

    public static float getClientStamina() { return clientStamina; }
    public static float getClientMaxStamina() { return clientMaxStamina; }
    public static float getClientDisplayedStamina() { return clientDisplayedStamina; }
    public static float getClientDisplayedMaxStamina() { return clientDisplayedMaxStamina; }
    public static boolean isClientExhausted() { return clientExhausted; }

    /** サーバーからの同期データ受信 */
    public static void setClientData(float stamina, float maxStamina) {
        setClientData(stamina, maxStamina, false);
    }

    public static void setClientData(float stamina, float maxStamina, boolean exhausted) {
        clientStamina = stamina;
        clientMaxStamina = maxStamina;
        clientExhausted = exhausted;
        if (!clientDisplayInitialized) {
            clientDisplayedStamina = stamina;
            clientDisplayedMaxStamina = maxStamina;
            clientDisplayInitialized = true;
        }
    }

    public static void updateClientDisplay() {
        clientDisplayedMaxStamina = smooth(clientDisplayedMaxStamina, clientMaxStamina, 0.18f);
        clientDisplayedStamina = smooth(clientDisplayedStamina, clientStamina, 0.22f);
        clientDisplayedStamina = Math.max(0.0f, Math.min(clientDisplayedMaxStamina, clientDisplayedStamina));
    }

    public static void tick(Player player) {
        tick(player, 1.0f);
    }

    public static void tick(Player player, float regenMultiplier) {
        UUID id = player.getUUID();
        float current = getStamina(player);
        float max = getMaxStamina(player);
        float drain = 0.0f;
        boolean exhausted = isExhausted(player);
        boolean aiming = isAimingGun(player);

        if (exhausted && player.isSprinting()) {
            player.setSprinting(false);
        }

        if (!exhausted && player.isSprinting()) {
            drain += GameConstants.STAMINA_CONSUME_RATE;
        }
        if (aiming) {
            drain += getAdsStaminaCost(player);
        }

        if (drain > 0.0f && current > 0.0f) {
            current = Math.max(0.0f, current - drain);
        } else if (drain <= 0.0f) {
            current = Math.min(max, current + (GameConstants.STAMINA_REGEN_RATE * regenMultiplier));
        }

        updateExhaustedState(player, current, max);
        exhausted = isExhausted(player);
        if (exhausted && player.isSprinting()) player.setSprinting(false);
        updateAdsOveruseState(player, aiming, current);
        enforceAdsFatigueHealth(player);
        tickAdsDebugTrace(player, aiming, current);

        PLAYER_STAMINA.put(id, current);
        if (player.tickCount % 20 == 0) {
            saveToPersistentData(player);
        }
        
        // Sync hunger visually without ever reaching vanilla starvation.
        // foodLevel 18+ triggers vanilla natural regen, so the display is still capped at 17.
        int displayFood = exhausted ? 1 : current > 0.0f
            ? Math.max(GameConstants.SPRINT_RESTORE_FOOD, Math.min(17, (int)((current / max) * 20.0f)))
            : 1;
        player.getFoodData().setFoodLevel(displayFood);
        player.getFoodData().setSaturation(0.0f);
        if (exhausted && player.isSprinting()) player.setSprinting(false);

        // === サーバー→クライアント同期（差分がある場合のみ、5tickごと） ===
        if (player instanceof net.minecraft.server.level.ServerPlayer sp && sp.tickCount % 5 == 0) {
            float prevSta = lastSyncedStamina.getOrDefault(id, -1.0f);
            float prevMax = lastSyncedMaxStamina.getOrDefault(id, -1.0f);
            boolean prevExhausted = lastSyncedExhausted.getOrDefault(id, !exhausted);
            // 値が変化した場合のみ同期（浮動小数点を整数精度で比較）
            if (Math.abs(current - prevSta) > 0.5f || Math.abs(max - prevMax) > 0.5f || exhausted != prevExhausted) {
                lastSyncedStamina.put(id, current);
                lastSyncedMaxStamina.put(id, max);
                lastSyncedExhausted.put(id, exhausted);
                String syncPayload = String.format("stamina:%.1f:%.1f:%s", current, max, exhausted ? "1" : "0");
                com.levanilla.rogue.networking.TacRogueNetworking.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sp),
                    new com.levanilla.rogue.networking.SyncDataMessage(syncPayload)
                );
            }
        }
    }

    public static void handleJump(Player player) {
        float current = getStamina(player);
        float max = getMaxStamina(player);
        if (isExhausted(player)) {
            var motion = player.getDeltaMovement();
            if (motion.y > 0.0D) {
                player.setDeltaMovement(motion.x, 0.0D, motion.z);
            }
            player.hurtMarked = true;
            return;
        }

        current = Math.max(0.0f, current - GameConstants.STAMINA_JUMP_COST);
        if (current <= 0.0f) {
            setExhausted(player, true);
        }
        PLAYER_STAMINA.put(player.getUUID(), current);
        updateExhaustedState(player, current, max);
    }

    public static boolean isExhausted(Player player) {
        Boolean cached = PLAYER_EXHAUSTED.get(player.getUUID());
        if (cached != null) return cached;
        boolean exhausted = player.getPersistentData().getBoolean(EXHAUSTED_KEY);
        PLAYER_EXHAUSTED.put(player.getUUID(), exhausted);
        return exhausted;
    }

    public static boolean isAdsExhaustDamage(Player player) {
        return player.getPersistentData().getBoolean(ADS_EXHAUST_DAMAGE_EVENT_KEY);
    }

    public static void setClientAdsInput(Player player, boolean aiming) {
        UUID id = player.getUUID();
        CLIENT_ADS_INPUT.put(id, aiming);
        CLIENT_ADS_INPUT_TICK.put(id, player.level().getGameTime());
        if (isAdsTraceActive(player)) {
            LOGGER.info("[TacRogue][ADS-trace] input player={} tick={} aiming={} heldGun={} hp={}/{} stamina={}/{} adsTicks={}",
                player.getGameProfile().getName(),
                player.level().getGameTime(),
                aiming,
                safeMainHandHoldGun(player),
                String.format(java.util.Locale.ROOT, "%.3f", player.getHealth()),
                String.format(java.util.Locale.ROOT, "%.3f", player.getMaxHealth()),
                String.format(java.util.Locale.ROOT, "%.3f", getStamina(player)),
                String.format(java.util.Locale.ROOT, "%.3f", getMaxStamina(player)),
                ADS_EXHAUST_TICKS.getOrDefault(id, 0));
        }
        if (!aiming) {
            resetAdsExhaustion(player);
        }
    }

    private static void updateExhaustedState(Player player, float current, float max) {
        if (current <= 0.0f) {
            setExhausted(player, true);
            return;
        }
        float release = Math.min(GameConstants.STAMINA_EXHAUST_RECOVERY, Math.max(1.0f, max));
        if (isExhausted(player) && current >= release) {
            setExhausted(player, false);
            ADS_EXHAUST_TICKS.remove(player.getUUID());
            clearAdsExhaustPenalty(player);
        }
    }

    private static void setExhausted(Player player, boolean exhausted) {
        PLAYER_EXHAUSTED.put(player.getUUID(), exhausted);
        player.getPersistentData().putBoolean(EXHAUSTED_KEY, exhausted);
    }

    private static void applyAdsExhaustPenalty(Player player) {
        player.getPersistentData().putBoolean(ADS_EXHAUST_PENALTY_KEY, true);
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(ADS_EXHAUST_SPEED_UUID) == null) {
            speed.addTransientModifier(new AttributeModifier(
                ADS_EXHAUST_SPEED_UUID,
                "rogue_ads_exhaustion",
                GameConstants.STAMINA_ADS_EXHAUST_SPEED_PENALTY,
                AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    private static void clearAdsExhaustPenalty(Player player) {
        player.getPersistentData().putBoolean(ADS_EXHAUST_PENALTY_KEY, false);
        ADS_EXHAUST_LAST_DAMAGE_TICK.remove(player.getUUID());
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(ADS_EXHAUST_SPEED_UUID) != null) {
            speed.removeModifier(ADS_EXHAUST_SPEED_UUID);
        }
    }

    private static void resetAdsExhaustion(Player player) {
        UUID id = player.getUUID();
        ADS_EXHAUST_TICKS.remove(id);
        ADS_FORCED_HEALTH.remove(id);
        ADS_FORCED_HEALTH_UNTIL.remove(id);
        clearAdsExhaustPenalty(player);
    }

    private static void updateAdsOveruseState(Player player, boolean aiming, float finalStamina) {
        if (!aiming || finalStamina > 0.0f) {
            resetAdsExhaustion(player);
            return;
        }

        int overTicks = ADS_EXHAUST_TICKS.merge(player.getUUID(), 1, Integer::sum);
        if (overTicks >= GameConstants.STAMINA_ADS_EXHAUST_GRACE_TICKS) {
            applyAdsExhaustPenalty(player);
            damageAdsExhaustedPlayer(player, finalStamina);
        } else if (overTicks == 1 || overTicks % 20 == 0) {
            logAdsFatigue(player, "grace", finalStamina, 0.0f, player.getHealth(), player.getHealth(), overTicks);
        }
    }

    private static void damageAdsExhaustedPlayer(Player player, float currentStamina) {
        if (player.level().isClientSide || player.isSpectator()) {
            logAdsFatigue(player, "skip_side_or_spectator", currentStamina, 0.0f, player.getHealth(), player.getHealth(), getAdsOverTicks(player));
            return;
        }
        if (currentStamina > 0.0f) {
            logAdsFatigue(player, "skip_stamina_recovered", currentStamina, 0.0f, player.getHealth(), player.getHealth(), getAdsOverTicks(player));
            resetAdsExhaustion(player);
            return;
        }

        UUID id = player.getUUID();
        long now = player.level().getGameTime();
        long last = ADS_EXHAUST_LAST_DAMAGE_TICK.getOrDefault(id, Long.MIN_VALUE);
        if (last != Long.MIN_VALUE && now - last < 20L) {
            return;
        }

        ADS_EXHAUST_LAST_DAMAGE_TICK.put(id, now);
        float damage = Math.max(0.0f, player.getMaxHealth() * GameConstants.STAMINA_ADS_EXHAUST_DAMAGE_PER_SECOND);
        float beforeHealth = player.getHealth();
        applyDirectAdsFatigueDamage(player, beforeHealth, damage);
    }

    private static void applyDirectAdsFatigueDamage(Player player, float beforeHealth, float damage) {
        UUID id = player.getUUID();
        float baseline = Math.min(beforeHealth, ADS_FORCED_HEALTH.getOrDefault(id, beforeHealth));
        float nextHealth = Math.max(1.0f, baseline - damage);
        if (nextHealth < beforeHealth) {
            ADS_FORCED_HEALTH.put(id, nextHealth);
            ADS_FORCED_HEALTH_UNTIL.put(id, player.level().getGameTime() + 30L);
            applyForcedHealth(player, nextHealth, true);
            logAdsFatigue(player, "apply", getStamina(player), damage, beforeHealth, nextHealth, getAdsOverTicks(player));
        } else {
            logAdsFatigue(player, "floor_or_no_delta", getStamina(player), damage, beforeHealth, nextHealth, getAdsOverTicks(player));
        }
    }

    private static void enforceAdsFatigueHealth(Player player) {
        UUID id = player.getUUID();
        Float forced = ADS_FORCED_HEALTH.get(id);
        if (forced == null) return;
        long until = ADS_FORCED_HEALTH_UNTIL.getOrDefault(id, 0L);
        if (player.level().getGameTime() > until) {
            ADS_FORCED_HEALTH.remove(id);
            ADS_FORCED_HEALTH_UNTIL.remove(id);
            return;
        }
        if (player.getHealth() > forced) {
            applyForcedHealth(player, forced, false);
            logAdsFatigue(player, "enforce", getStamina(player), 0.0f, player.getHealth(), forced, getAdsOverTicks(player));
        }
    }

    private static void applyForcedHealth(Player player, float health, boolean playEffect) {
        float clamped = Math.max(1.0f, Math.min(player.getMaxHealth(), health));
        player.invulnerableTime = 0;
        player.setHealth(clamped);
        player.hurtMarked = true;
        if (playEffect) {
            player.level().broadcastEntityEvent(player, (byte) 2);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            com.levanilla.rogue.core.event.CombatEventHandler.markPlayerDamaged(serverPlayer);
            serverPlayer.connection.send(new ClientboundSetHealthPacket(
                serverPlayer.getHealth(),
                serverPlayer.getFoodData().getFoodLevel(),
                serverPlayer.getFoodData().getSaturationLevel()));
            com.levanilla.rogue.networking.TacRogueNetworking.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> serverPlayer),
                new com.levanilla.rogue.networking.SyncDataMessage(String.format(java.util.Locale.ROOT,
                    "health:%.4f:%.4f:1600", serverPlayer.getHealth(), serverPlayer.getMaxHealth()))
            );
        }
    }

    public static String debugStatus(Player player) {
        UUID id = player.getUUID();
        return String.format(java.util.Locale.ROOT,
            "hp=%.3f/%.3f stamina=%.3f/%.3f exhausted=%s adsTicks=%d forcedHp=%s forcedUntil=%d aiming=%s clientAds=%s food=%d",
            player.getHealth(),
            player.getMaxHealth(),
            getStamina(player),
            getMaxStamina(player),
            isExhausted(player),
            ADS_EXHAUST_TICKS.getOrDefault(id, 0),
            ADS_FORCED_HEALTH.containsKey(id) ? String.format(java.util.Locale.ROOT, "%.3f", ADS_FORCED_HEALTH.get(id)) : "none",
            ADS_FORCED_HEALTH_UNTIL.getOrDefault(id, 0L),
            isAimingGun(player),
            hasFreshClientAdsInput(player),
            player.getFoodData().getFoodLevel());
    }

    public static void startAdsDebugTrace(ServerPlayer player, int seconds) {
        int clampedSeconds = Math.max(5, Math.min(120, seconds));
        UUID id = player.getUUID();
        ADS_DEBUG_TRACE_UNTIL.put(id, player.level().getGameTime() + clampedSeconds * 20L);
        ADS_DEBUG_TRACE_LAST_TICK.remove(id);
        LOGGER.info("[TacRogue][ADS-trace] start player={} seconds={} tick={} status={}",
            player.getGameProfile().getName(),
            clampedSeconds,
            player.level().getGameTime(),
            debugStatus(player));
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
            "§a[ADS TRACE] §f" + clampedSeconds + "s tracking started. Close chat and hold ADS."), false);
    }

    private static void tickAdsDebugTrace(Player player, boolean aiming, float finalStamina) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        UUID id = player.getUUID();
        Long until = ADS_DEBUG_TRACE_UNTIL.get(id);
        if (until == null) return;

        long now = player.level().getGameTime();
        if (now > until) {
            ADS_DEBUG_TRACE_UNTIL.remove(id);
            ADS_DEBUG_TRACE_LAST_TICK.remove(id);
            LOGGER.info("[TacRogue][ADS-trace] end player={} tick={} status={}",
                player.getGameProfile().getName(),
                now,
                debugStatus(player));
            serverPlayer.displayClientMessage(net.minecraft.network.chat.Component.literal("§7[ADS TRACE] ended"), false);
            return;
        }

        long last = ADS_DEBUG_TRACE_LAST_TICK.getOrDefault(id, Long.MIN_VALUE);
        if (now - last < 10L) return;
        ADS_DEBUG_TRACE_LAST_TICK.put(id, now);

        Float forced = ADS_FORCED_HEALTH.get(id);
        String forcedText = forced == null ? "-" : String.format(java.util.Locale.ROOT, "%.2f", forced);
        String phase = finalStamina > 0.0f ? "drain"
            : aiming && getAdsOverTicks(player) >= GameConstants.STAMINA_ADS_EXHAUST_GRACE_TICKS ? "damage"
            : aiming ? "grace"
            : "idle";
        String text = String.format(java.util.Locale.ROOT,
            "§b[ADS] §f%s aim=%s client=%s hp=%.2f/%.2f sta=%.2f/%.2f ex=%s ticks=%d forced=%s food=%d",
            phase,
            aiming,
            hasFreshClientAdsInput(player),
            player.getHealth(),
            player.getMaxHealth(),
            finalStamina,
            getMaxStamina(player),
            isExhausted(player),
            getAdsOverTicks(player),
            forcedText,
            player.getFoodData().getFoodLevel());
        serverPlayer.displayClientMessage(net.minecraft.network.chat.Component.literal(text), true);
        LOGGER.info("[TacRogue][ADS-trace] tick player={} tick={} phase={} aim={} clientAds={} heldGun={} hp={}/{} stamina={}/{} exhausted={} adsTicks={} forced={} forcedUntil={} food={} creative={} spectator={}",
            player.getGameProfile().getName(),
            now,
            phase,
            aiming,
            hasFreshClientAdsInput(player),
            safeMainHandHoldGun(player),
            String.format(java.util.Locale.ROOT, "%.3f", player.getHealth()),
            String.format(java.util.Locale.ROOT, "%.3f", player.getMaxHealth()),
            String.format(java.util.Locale.ROOT, "%.3f", finalStamina),
            String.format(java.util.Locale.ROOT, "%.3f", getMaxStamina(player)),
            isExhausted(player),
            getAdsOverTicks(player),
            forcedText,
            ADS_FORCED_HEALTH_UNTIL.getOrDefault(id, 0L),
            player.getFoodData().getFoodLevel(),
            player.isCreative(),
            player.isSpectator());
    }

    private static int getAdsOverTicks(Player player) {
        return ADS_EXHAUST_TICKS.getOrDefault(player.getUUID(), 0);
    }

    private static boolean isAdsTraceActive(Player player) {
        Long until = ADS_DEBUG_TRACE_UNTIL.get(player.getUUID());
        return until != null && player.level().getGameTime() <= until;
    }

    private static boolean safeMainHandHoldGun(Player player) {
        try {
            return IGun.mainHandHoldGun(player);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void logAdsFatigue(Player player, String phase, float stamina, float damage,
                                      float beforeHealth, float afterHealth, int adsTicks) {
        if (!RogueConfig.debugLogs() && !isAdsTraceActive(player)) return;
        LOGGER.info("[TacRogue][ADS-fatigue] phase={} player={} dim={} hp={}/{} -> {} damage={} stamina={}/{} exhausted={} adsTicks={} forced={} food={} creative={} spectator={}",
            phase,
            player.getGameProfile().getName(),
            player.level().dimension().location(),
            String.format(java.util.Locale.ROOT, "%.3f", beforeHealth),
            String.format(java.util.Locale.ROOT, "%.3f", player.getMaxHealth()),
            String.format(java.util.Locale.ROOT, "%.3f", afterHealth),
            String.format(java.util.Locale.ROOT, "%.3f", damage),
            String.format(java.util.Locale.ROOT, "%.3f", stamina),
            String.format(java.util.Locale.ROOT, "%.3f", getMaxStamina(player)),
            isExhausted(player),
            adsTicks,
            ADS_FORCED_HEALTH.containsKey(player.getUUID()) ? String.format(java.util.Locale.ROOT, "%.3f", ADS_FORCED_HEALTH.get(player.getUUID())) : "none",
            player.getFoodData().getFoodLevel(),
            player.isCreative(),
            player.isSpectator());
    }

    private static boolean isAimingGun(Player player) {
        if (hasFreshClientAdsInput(player)) return true;
        try {
            if (!IGun.mainHandHoldGun(player)) return false;
            IGunOperator operator = IGunOperator.fromLivingEntity(player);
            return operator != null && (operator.getSynIsAiming() || operator.getSynAimingProgress() > 0.05f);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean hasFreshClientAdsInput(Player player) {
        UUID id = player.getUUID();
        if (!Boolean.TRUE.equals(CLIENT_ADS_INPUT.get(id))) return false;
        long tick = CLIENT_ADS_INPUT_TICK.getOrDefault(id, Long.MIN_VALUE);
        if (player.level().getGameTime() - tick > 8L) {
            CLIENT_ADS_INPUT.remove(id);
            CLIENT_ADS_INPUT_TICK.remove(id);
            return false;
        }
        try {
            return IGun.mainHandHoldGun(player);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void stopAimingGun(Player player) {
        try {
            if (!IGun.mainHandHoldGun(player)) return;
            IGunOperator operator = IGunOperator.fromLivingEntity(player);
            if (operator != null && (operator.getSynIsAiming() || operator.getSynAimingProgress() > 0.0f)) {
                operator.aim(false);
            }
        } catch (Throwable ignored) {
        }
    }

    private static float getAdsStaminaCost(Player player) {
        float cost = GameConstants.STAMINA_ADS_CONSUME_RATE;
        if (CombatPostureHelper.isProne(player)) {
            return cost * GameConstants.STAMINA_ADS_PRONE_MULT;
        }
        if (player.hasPose(Pose.CROUCHING) || player.isShiftKeyDown()) {
            return cost * GameConstants.STAMINA_ADS_SNEAK_MULT;
        }
        return cost;
    }

    // ===== メモリリーク防止 =====

    /** サーバー停止時にスタティックマップをクリア */
    public static void clearMemory() {
        PLAYER_STAMINA.clear();
        PLAYER_MAX_STAMINA.clear();
        PLAYER_BASE_MAX_STAMINA.clear();
        PLAYER_EXHAUSTED.clear();
        ADS_EXHAUST_TICKS.clear();
        ADS_EXHAUST_LAST_DAMAGE_TICK.clear();
        CLIENT_ADS_INPUT.clear();
        CLIENT_ADS_INPUT_TICK.clear();
        ADS_FORCED_HEALTH.clear();
        ADS_FORCED_HEALTH_UNTIL.clear();
        ADS_DEBUG_TRACE_UNTIL.clear();
        ADS_DEBUG_TRACE_LAST_TICK.clear();
        lastSyncedStamina.clear();
        lastSyncedMaxStamina.clear();
        lastSyncedExhausted.clear();
        clientStamina = GameConstants.DEFAULT_MAX_STAMINA;
        clientMaxStamina = GameConstants.DEFAULT_MAX_STAMINA;
        clientDisplayedStamina = GameConstants.DEFAULT_MAX_STAMINA;
        clientDisplayedMaxStamina = GameConstants.DEFAULT_MAX_STAMINA;
        clientExhausted = false;
        clientDisplayInitialized = false;
    }

    private static float smooth(float current, float target, float alpha) {
        if (Math.abs(target - current) < 0.02f) return target;
        return current + (target - current) * alpha;
    }
}
