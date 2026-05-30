package com.levanilla.rogue.core.service;

import com.levanilla.rogue.core.CommonEventHandler;
import com.levanilla.rogue.core.GameConstants;
import com.levanilla.rogue.core.WeaponRarity;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TacZFireRateService {
    private static final long OVERFLOW_CACHE_TICKS = 100L;
    private static final ConcurrentHashMap<UUID, OverflowCache> OVERFLOW_CACHE = new ConcurrentHashMap<>();

    private TacZFireRateService() {}

    public static boolean canApply(LivingEntity shooter) {
        return shooter != null
            && (shooter.level().dimension() == CommonEventHandler.ROGUE_DIM
                || shooter.level().dimension() == CommonEventHandler.LOBBY_DIM);
    }

    public static long adjustedShootIntervalMs(long tacZIntervalMs, ItemStack gun, LivingEntity shooter) {
        if (tacZIntervalMs <= 0L) return tacZIntervalMs;
        RpmProfile profile = calculateProfile(tacZIntervalMs, gun, shooter);
        rememberOverflow(shooter, profile);
        if (profile.effectiveRpm() <= profile.baseRpm() + 0.5f) return tacZIntervalMs;
        long adjustedIntervalMs = Math.max(1L, Math.round(60000.0f / profile.effectiveRpm()));
        return Math.min(tacZIntervalMs, adjustedIntervalMs);
    }

    public static double adjustedShootIntervalSeconds(double tacZIntervalSeconds, ItemStack gun, LivingEntity shooter) {
        if (tacZIntervalSeconds <= 0.0D) return tacZIntervalSeconds;
        long originalMs = Math.max(1L, Math.round(tacZIntervalSeconds * 1000.0D));
        return adjustedShootIntervalMs(originalMs, gun, shooter) / 1000.0D;
    }

    public static DisplayProfile displayProfile(ItemStack stack, LivingEntity holder) {
        try {
            IGun gun = IGun.getIGunOrNull(stack);
            if (gun == null) return DisplayProfile.empty();

            var gunIndex = TimelessAPI.getCommonGunIndex(gun.getGunId(stack));
            if (gunIndex.isEmpty() || gunIndex.get().getGunData() == null) return DisplayProfile.empty();

            GunData gunData = gunIndex.get().getGunData();
            FireMode fireMode = normalizeFireMode(gun.getFireMode(stack), gunData);
            if (fireMode == null) return DisplayProfile.empty();

            int baseRpm = Math.max(1, gunData.getRoundsPerMinute(fireMode));
            long baseIntervalMs = Math.max(1L, Math.round(60000.0f / baseRpm));
            long effectiveIntervalMs = holder == null
                ? adjustedShootIntervalMs(Math.max(1L, Math.round(60000.0f / baseRpm)), stack, holder)
                : gunData.getShootInterval(holder, fireMode, stack);
            RpmProfile profile = calculateProfile(baseIntervalMs, stack, holder);
            int effectiveRpm = rpmFromInterval(effectiveIntervalMs, baseRpm);
            int practicalRpm = effectiveRpm;
            return new DisplayProfile(baseRpm, effectiveRpm, practicalRpm, fireMode,
                overflowDamageBonusPercent(profile.overflowRpm()));
        } catch (Throwable ignored) {
            return DisplayProfile.empty();
        }
    }

    private static FireMode normalizeFireMode(FireMode fireMode, GunData gunData) {
        if (fireMode != null && fireMode != FireMode.UNKNOWN) return fireMode;
        List<FireMode> modes = gunData.getFireModeSet();
        if (modes == null || modes.isEmpty()) return null;
        return modes.get(0);
    }

    private static int rpmFromInterval(long intervalMs, int fallbackRpm) {
        if (intervalMs <= 0L) return Math.max(1, fallbackRpm);
        return Math.min(GameConstants.MAX_EFFECTIVE_FIRE_RATE_RPM,
            Math.max(Math.max(1, fallbackRpm), Math.round(60000.0f / intervalMs)));
    }

    public static float overflowDamageMultiplier(ItemStack gun, LivingEntity shooter) {
        return 1.0f + overflowDamageBonusPercent(gun, shooter) / 100.0f;
    }

    public static float overflowDamageBonusPercent(ItemStack gun, LivingEntity shooter) {
        OverflowCache cached = cachedOverflow(shooter);
        if (cached != null) return overflowDamageBonusPercent(cached.overflowRpm());
        RpmProfile profile = calculateProfile(stackBaseIntervalMs(gun), gun, shooter);
        return overflowDamageBonusPercent(profile.overflowRpm());
    }

    public static void clearOverflowCache(UUID shooterId) {
        if (shooterId != null) OVERFLOW_CACHE.remove(shooterId);
    }

    public static void clearOverflowCache() {
        OVERFLOW_CACHE.clear();
    }

    private static void rememberOverflow(LivingEntity shooter, RpmProfile profile) {
        if (shooter == null || shooter.level() == null || profile.baseRpm() <= 0.0f) return;
        OVERFLOW_CACHE.put(shooter.getUUID(), new OverflowCache(profile.overflowRpm(), shooter.level().getGameTime()));
    }

    private static OverflowCache cachedOverflow(LivingEntity shooter) {
        if (shooter == null || shooter.level() == null) return null;
        OverflowCache cached = OVERFLOW_CACHE.get(shooter.getUUID());
        if (cached == null) return null;
        if (shooter.level().getGameTime() - cached.gameTime() > OVERFLOW_CACHE_TICKS) {
            OVERFLOW_CACHE.remove(shooter.getUUID(), cached);
            return null;
        }
        return cached;
    }

    private static long stackBaseIntervalMs(ItemStack stack) {
        try {
            IGun gun = IGun.getIGunOrNull(stack);
            if (gun == null) return -1L;
            var gunIndex = TimelessAPI.getCommonGunIndex(gun.getGunId(stack));
            if (gunIndex.isEmpty() || gunIndex.get().getGunData() == null) return -1L;
            GunData gunData = gunIndex.get().getGunData();
            FireMode fireMode = normalizeFireMode(gun.getFireMode(stack), gunData);
            if (fireMode == null) return -1L;
            int rpm = Math.max(1, gunData.getRoundsPerMinute(fireMode));
            return Math.max(1L, Math.round(60000.0f / rpm));
        } catch (Throwable ignored) {
            return -1L;
        }
    }

    private static RpmProfile calculateProfile(long tacZIntervalMs, ItemStack gun, LivingEntity shooter) {
        if (tacZIntervalMs <= 0L) return RpmProfile.empty();
        float baseRpm = Math.max(1.0f, 60000.0f / tacZIntervalMs);
        float fireRateMult = WeaponRarity.getEffectiveFireRateMult(gun, shooter);
        if (fireRateMult <= 1.005f) {
            return new RpmProfile(baseRpm, Math.min(baseRpm, GameConstants.MAX_EFFECTIVE_FIRE_RATE_RPM), 0.0f);
        }
        float targetRpm = baseRpm * fireRateMult;
        float effectiveRpm = applyApproachSoftcap(baseRpm, targetRpm);
        float overflowRpm = Math.max(0.0f, targetRpm - effectiveRpm);
        return new RpmProfile(baseRpm, effectiveRpm, overflowRpm);
    }

    private static float applyApproachSoftcap(float baseRpm, float targetRpm) {
        float maxRpm = GameConstants.MAX_EFFECTIVE_FIRE_RATE_RPM;
        if (targetRpm <= baseRpm) return Math.min(baseRpm, maxRpm);
        if (baseRpm >= maxRpm) return maxRpm;
        float desiredGain = targetRpm - baseRpm;
        float headroom = Math.max(1.0f, maxRpm - baseRpm);
        float ratio = desiredGain / headroom;
        float curve = Math.max(0.05f, GameConstants.FIRE_RATE_RPM_APPROACH_SOFTCAP_CURVE);
        float scaledGain = headroom * (1.0f - (float)Math.exp(-ratio * curve));
        return Math.min(maxRpm, baseRpm + Math.min(desiredGain, scaledGain));
    }

    private static float overflowDamageBonusPercent(float overflowRpm) {
        if (overflowRpm <= 0.0f) return 0.0f;
        float bonus = (overflowRpm / 100.0f) * GameConstants.FIRE_RATE_OVERFLOW_DAMAGE_PER_100_RPM * 100.0f;
        return Math.min(GameConstants.FIRE_RATE_OVERFLOW_DAMAGE_MAX * 100.0f, bonus);
    }

    private record RpmProfile(float baseRpm, float effectiveRpm, float overflowRpm) {
        static RpmProfile empty() {
            return new RpmProfile(0.0f, 0.0f, 0.0f);
        }
    }

    private record OverflowCache(float overflowRpm, long gameTime) {}

    public record DisplayProfile(int baseRpm, int effectiveRpm, int practicalRpm, FireMode fireMode,
                                 float overflowDamageBonusPercent) {
        public static DisplayProfile empty() {
            return new DisplayProfile(-1, -1, -1, FireMode.UNKNOWN, 0.0f);
        }

        public boolean valid() {
            return effectiveRpm > 0;
        }

        public boolean hasAutoPracticalLimit() {
            return false;
        }

        public boolean hasOverflowDamageBonus() {
            return overflowDamageBonusPercent > 0.05f;
        }
    }
}
