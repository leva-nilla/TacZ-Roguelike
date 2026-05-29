package com.levanilla.rogue.core.service;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

import static com.levanilla.rogue.core.CommonEventHandler.ROGUE_DIM;

public final class RogueUtilityItemService {
    public static final String UTILITY_ID_KEY = "TacRogueUtilityId";
    public static final int ACTIVE_PASSIVE_LIMIT = 3;

    private RogueUtilityItemService() {}

    public static boolean isUtilityStack(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.hasTag() && stack.getTag().contains(UTILITY_ID_KEY);
    }

    public static String getUtilityId(ItemStack stack) {
        if (!isUtilityStack(stack)) return "";
        return stack.getTag().getString(UTILITY_ID_KEY);
    }

    public static String translationSuffix(String id) {
        if (id == null || id.isBlank()) return "unknown";
        int colon = id.indexOf(':');
        String suffix = colon >= 0 ? id.substring(colon + 1) : id;
        return suffix.toLowerCase(Locale.ROOT);
    }

    public static boolean isPassiveUtilityId(String id) {
        return switch (id) {
            case "rogue:ballistic_charm", "rogue:quickdraw_charm", "rogue:ammo_saver_charm",
                 "rogue:ballistic_insert", "rogue:mag_pouch_rig",
                 "rogue:terminal_decoder", "rogue:recovery_beacon", "rogue:defense_sensor",
                 "rogue:blood_dogtag", "rogue:overheat_core",
                 "rogue:maintenance_kit", "rogue:ballistic_computer", "rogue:range_card" -> true;
            default -> false;
        };
    }

    public static boolean isConsumableUtilityId(String id) {
        return switch (id) {
            case "rogue:smoke_canister", "rogue:flash_charge", "rogue:noise_maker",
                 "rogue:portable_shield", "rogue:micro_turret" -> true;
            default -> false;
        };
    }

    public static int getConsumableShopUnlockFloor(String id) {
        return switch (id) {
            case "rogue:noise_maker" -> 6;
            case "rogue:smoke_canister" -> 8;
            case "rogue:flash_charge" -> 10;
            case "rogue:portable_shield" -> 12;
            case "rogue:micro_turret" -> 18;
            default -> 1;
        };
    }

    public static int getUtilityStackLimit(String id) {
        return switch (id) {
            case "rogue:noise_maker" -> 7;
            case "rogue:smoke_canister" -> 5;
            case "rogue:flash_charge" -> 5;
            case "rogue:portable_shield" -> 3;
            case "rogue:micro_turret" -> 3;
            default -> 1;
        };
    }

    public static int getReferencePrice(String id) {
        return switch (id) {
            case "rogue:ballistic_charm" -> 850;
            case "rogue:quickdraw_charm" -> 900;
            case "rogue:ammo_saver_charm" -> 950;
            case "rogue:terminal_decoder" -> 1100;
            case "rogue:recovery_beacon", "rogue:defense_sensor" -> 1250;
            case "rogue:maintenance_kit" -> 1050;
            case "rogue:range_card" -> 800;
            case "rogue:ballistic_computer" -> 1350;
            case "rogue:blood_dogtag" -> 1500;
            case "rogue:mag_pouch_rig" -> 1600;
            case "rogue:overheat_core" -> 1700;
            case "rogue:ballistic_insert" -> 1800;
            case "rogue:noise_maker" -> 700;
            case "rogue:smoke_canister" -> 950;
            case "rogue:flash_charge" -> 1150;
            case "rogue:portable_shield" -> 1650;
            case "rogue:micro_turret" -> 3200;
            default -> 250;
        };
    }

    public static Set<String> getActivePassiveIds(Player player) {
        LinkedHashSet<String> active = new LinkedHashSet<>();
        if (player == null) return active;
        int max = Math.min(36, player.getInventory().getContainerSize());
        for (int slot = 0; slot < max && active.size() < ACTIVE_PASSIVE_LIMIT; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            String id = getUtilityId(stack);
            if (!id.isEmpty() && isPassiveUtilityId(id)) {
                active.add(id);
            }
        }
        return active;
    }

    public static boolean isPassiveActive(Player player, String id) {
        return getActivePassiveIds(player).contains(id);
    }

    public static float getMovementSpeedPercent(Player player) {
        float value = 0.0F;
        Set<String> active = getActivePassiveIds(player);
        if (active.contains("rogue:ballistic_insert")) value -= 5.0F;
        if (active.contains("rogue:mag_pouch_rig")) value -= 4.0F;
        if (active.contains("rogue:overheat_core")) value -= 2.0F;
        return value;
    }

    public static float getDrawSpeedBonusPercent(Player player) {
        float value = 0.0F;
        Set<String> active = getActivePassiveIds(player);
        if (active.contains("rogue:quickdraw_charm")) value += 10.0F;
        if (active.contains("rogue:maintenance_kit")) value += 6.0F;
        if (active.contains("rogue:mag_pouch_rig")) value -= 5.0F;
        return value;
    }

    public static float getAmmoCapacityMultiplier(Player player) {
        return isPassiveActive(player, "rogue:mag_pouch_rig") ? 1.25F : 1.0F;
    }

    public static float getExtraAmmoSaveChance(Player player) {
        return isPassiveActive(player, "rogue:ammo_saver_charm") ? 0.05F : 0.0F;
    }

    public static float getIncomingDamageMultiplier(Player player) {
        Set<String> active = getActivePassiveIds(player);
        float reduction = 0.0F;
        if (active.contains("rogue:ballistic_charm")) reduction += 0.03F;
        if (active.contains("rogue:ballistic_insert")) reduction += 0.10F;
        reduction = Math.min(0.18F, reduction);

        float vulnerability = active.contains("rogue:overheat_core") ? 0.06F : 0.0F;
        return Math.max(0.70F, 1.0F - reduction) * (1.0F + vulnerability);
    }

    public static float getMeleeDamageMultiplier(Player player) {
        Set<String> active = getActivePassiveIds(player);
        float bonus = 0.0F;
        if (active.contains("rogue:blood_dogtag")) bonus += 0.06F;
        if (active.contains("rogue:overheat_core")) bonus += 0.08F;
        if (active.contains("rogue:maintenance_kit")) bonus += 0.04F;
        return 1.0F + bonus;
    }

    public static float getGunDamageMultiplier(Player player, Entity target, boolean headshot) {
        Set<String> active = getActivePassiveIds(player);
        float bonus = 0.0F;
        if (active.contains("rogue:blood_dogtag")) bonus += 0.06F;
        if (active.contains("rogue:overheat_core")) bonus += 0.08F;
        if (active.contains("rogue:maintenance_kit")) bonus += 0.03F;
        if (active.contains("rogue:ballistic_computer")) bonus += headshot ? 0.08F : 0.04F;
        if (target != null && active.contains("rogue:range_card") && player.distanceTo(target) >= 12.0D) {
            bonus += 0.08F;
        }
        return 1.0F + bonus;
    }

    public static double getObjectiveProgressMultiplier(Player player, FloorObjectiveService.ObjectiveType type) {
        Set<String> active = getActivePassiveIds(player);
        double mult = 1.0D;
        if (type == FloorObjectiveService.ObjectiveType.SECURE_TERMINAL && active.contains("rogue:terminal_decoder")) {
            mult += 0.35D;
        }
        if (type == FloorObjectiveService.ObjectiveType.HOLD_POSITION && active.contains("rogue:defense_sensor")) {
            mult += 0.20D;
        }
        return mult;
    }

    public static boolean hasRecoveryBeacon(Player player) {
        return isPassiveActive(player, "rogue:recovery_beacon");
    }

    public static void onPlayerKill(ServerPlayer player) {
        if (player == null || player.level().dimension() != ROGUE_DIM) return;
        if (isPassiveActive(player, "rogue:blood_dogtag") && player.getHealth() < player.getMaxHealth()) {
            player.heal(1.0F);
        }
    }

    public static boolean useUtilityItem(ServerPlayer player, ItemStack stack) {
        String id = getUtilityId(stack);
        if (id.isEmpty() || !isConsumableUtilityId(id)) return false;
        if (!(player.level() instanceof ServerLevel level) || player.level().dimension() != ROGUE_DIM) {
            player.displayClientMessage(Component.translatable("message.tac_rogue.utility.rogue_only"), true);
            return true;
        }

        boolean consumed = switch (id) {
            case "rogue:smoke_canister" -> useSmoke(player, level);
            case "rogue:flash_charge" -> useFlash(player, level);
            case "rogue:noise_maker" -> useNoiseMaker(player, level);
            case "rogue:portable_shield" -> usePortableShield(player, level);
            case "rogue:micro_turret" -> useMicroTurret(player, level);
            default -> false;
        };

        if (consumed && !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return consumed;
    }

    public static String rollChestUtilityId(Random random, int floor) {
        List<String> ids = new ArrayList<>();
        if (floor >= 5) ids.add("rogue:ballistic_charm");
        if (floor >= 7) ids.add("rogue:quickdraw_charm");
        if (floor >= 10) ids.add("rogue:terminal_decoder");
        if (floor >= 12) ids.add("rogue:defense_sensor");
        if (floor >= 14) ids.add("rogue:ammo_saver_charm");
        if (floor >= 16) ids.add("rogue:recovery_beacon");
        if (floor >= 18) ids.add("rogue:maintenance_kit");
        if (floor >= 20) ids.add("rogue:range_card");
        if (floor >= 24) ids.add("rogue:ballistic_computer");
        if (floor >= 28) ids.add("rogue:mag_pouch_rig");
        if (floor >= 34) ids.add("rogue:blood_dogtag");
        if (floor >= 40) ids.add("rogue:ballistic_insert");
        if (floor >= 45) ids.add("rogue:overheat_core");
        return ids.get(random.nextInt(ids.size()));
    }

    private static boolean useSmoke(ServerPlayer player, ServerLevel level) {
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 100, 0, false, true));
        AABB area = player.getBoundingBox().inflate(9.0D);
        int affected = 0;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, area, RogueUtilityItemService::isRogueMob)) {
            mob.setTarget(null);
            mob.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0, false, false));
            affected++;
        }
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, player.getX(), player.getY() + 0.5D, player.getZ(),
            36, 1.2D, 0.35D, 1.2D, 0.035D);
        level.playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 0.75F);
        player.displayClientMessage(Component.translatable("message.tac_rogue.utility.smoke", affected), true);
        return true;
    }

    private static boolean useFlash(ServerPlayer player, ServerLevel level) {
        AABB area = player.getBoundingBox().inflate(12.0D);
        int affected = 0;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, area, RogueUtilityItemService::isRogueMob)) {
            if (!hasLineOfEffect(level, player.getEyePosition(), mob.getEyePosition(), player)) continue;
            mob.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0, false, false));
            mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1, false, true));
            mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0, false, true));
            affected++;
        }
        level.sendParticles(ParticleTypes.FLASH, player.getX(), player.getEyeY(), player.getZ(), 1, 0, 0, 0, 0);
        level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.9F, 1.65F);
        player.displayClientMessage(Component.translatable("message.tac_rogue.utility.flash", affected), true);
        return true;
    }

    private static boolean useNoiseMaker(ServerPlayer player, ServerLevel level) {
        Vec3 eye = player.getEyePosition();
        Vec3 target = eye.add(player.getLookAngle().scale(12.0D));
        HitResult hit = level.clip(new ClipContext(eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 decoy = hit.getType() == HitResult.Type.MISS ? target : hit.getLocation();
        AABB area = new AABB(decoy, decoy).inflate(16.0D);
        int affected = 0;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, area, m -> isRogueMob(m) && !m.getTags().contains("rogue:boss"))) {
            if (mob.getTarget() != null) continue;
            RogueMobAlertService.applyDecoy(mob, decoy, player, 120L);
            mob.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, false, false));
            affected++;
        }
        level.sendParticles(ParticleTypes.NOTE, decoy.x, decoy.y + 0.25D, decoy.z, 8, 0.45D, 0.2D, 0.45D, 0.0D);
        level.playSound(null, net.minecraft.core.BlockPos.containing(decoy), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.0F, 0.85F);
        player.displayClientMessage(Component.translatable("message.tac_rogue.utility.noise", affected), true);
        return true;
    }

    private static boolean usePortableShield(ServerPlayer player, ServerLevel level) {
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 160, 0, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 160, 1, false, true));
        level.playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.9F, 0.85F);
        player.displayClientMessage(Component.translatable("message.tac_rogue.utility.shield"), true);
        return true;
    }

    private static boolean useMicroTurret(ServerPlayer player, ServerLevel level) {
        AABB area = player.getBoundingBox().inflate(18.0D);
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, area, m -> isRogueMob(m) && !m.getTags().contains("rogue:boss"));
        mobs.sort(java.util.Comparator.comparingDouble(player::distanceToSqr));
        int hits = 0;
        Vec3 muzzle = player.getEyePosition().add(player.getLookAngle().scale(0.45D));
        for (Mob mob : mobs) {
            if (hits >= 4) break;
            if (!hasLineOfEffect(level, player.getEyePosition(), mob.getEyePosition(), player)) continue;
            Vec3 target = mob.getEyePosition();
            for (int i = 1; i <= 6; i++) {
                Vec3 spark = muzzle.lerp(target, i / 6.0D);
                level.sendParticles(ParticleTypes.CRIT, spark.x, spark.y, spark.z, 1, 0.015D, 0.015D, 0.015D, 0.0D);
            }
            mob.invulnerableTime = 0;
            mob.hurt(player.damageSources().playerAttack(player), Math.max(40.0F, mob.getMaxHealth() + 4.0F));
            level.sendParticles(ParticleTypes.CRIT, mob.getX(), mob.getY() + mob.getBbHeight() * 0.55D, mob.getZ(),
                6, 0.22D, 0.22D, 0.22D, 0.02D);
            level.playSound(null, mob.blockPosition(), SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 0.35F, 1.8F);
            hits++;
        }
        level.playSound(null, player.blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.0F, 1.35F);
        player.displayClientMessage(Component.translatable("message.tac_rogue.utility.turret", hits), true);
        return true;
    }

    private static boolean isRogueMob(Mob mob) {
        return mob != null
            && mob.isAlive()
            && !mob.getTags().contains("tac_rogue_npc")
            && (mob.getTags().contains("tac_rogue_spawned") || mob.getTags().contains("rogue:boss"));
    }

    private static boolean hasLineOfEffect(ServerLevel level, Vec3 start, Vec3 end, Entity source) {
        HitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, source));
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(end) <= 0.85D;
    }
}
