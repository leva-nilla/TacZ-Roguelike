package com.levanilla.rogue.world;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import com.levanilla.rogue.core.ModEntities;
import com.levanilla.rogue.core.ScalingEngine;
import com.levanilla.rogue.core.RogueActManager;
import com.levanilla.rogue.core.service.FloorInstanceManager;
import com.levanilla.rogue.world.goal.RogueMobEngagedTargetMonitorGoal;
import com.levanilla.rogue.world.goal.RogueMobGoalUtils;
import com.levanilla.rogue.world.goal.RogueMobTacticalAlertGoal;
import com.levanilla.rogue.world.goal.RogueMobVisionGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Random;

/**
 * 敵 Mob のスポーンを制御するクラス。
 * バイオーム群に応じた敵種を選択し、ScalingEngine でスケーリングを適用。
 * 全Mobに視界ベースのアグロゴールを付与。
 */
public class RoomManager {

    private static final Random RANDOM = new Random();

    /** バイオーム別Mobプール。遠距離(Skeleton/Pillager/Stray)と近接を4:2から5:1程度の近接偏重に修正 */
    @SuppressWarnings("unchecked")
    private static final EntityType<? extends Mob>[][] BIOME_MOBS = new EntityType[][] {
        /* RUINS       */ {EntityType.ZOMBIE,           EntityType.PILLAGER,  EntityType.SPIDER,           EntityType.ZOMBIE_VILLAGER, EntityType.CREEPER,     EntityType.WITCH},
        /* LAB         */ {EntityType.HUSK,             EntityType.SKELETON,  EntityType.CAVE_SPIDER,      EntityType.ZOMBIE,          EntityType.SLIME,       EntityType.CREEPER},
        /* UNDERGROUND */ {EntityType.ZOMBIE,           EntityType.STRAY,     EntityType.CAVE_SPIDER,      EntityType.SPIDER,          EntityType.SILVERFISH,  EntityType.SLIME},
        /* MILITARY    */ {EntityType.ZOMBIE,           EntityType.VINDICATOR,EntityType.PILLAGER,         EntityType.VINDICATOR,      EntityType.ZOMBIE,      EntityType.ZOMBIE},
        /* NETHER      */ {EntityType.WITHER_SKELETON,  EntityType.SKELETON,  EntityType.BLAZE,            EntityType.MAGMA_CUBE,      EntityType.PIGLIN_BRUTE,EntityType.ZOMBIFIED_PIGLIN},
        /* OCEAN       */ {EntityType.DROWNED,          EntityType.SKELETON,  EntityType.DROWNED,          EntityType.DROWNED,         EntityType.DROWNED,     EntityType.DROWNED},
        /* URBAN       */ {EntityType.ZOMBIE,           EntityType.VINDICATOR,EntityType.PILLAGER,         EntityType.CREEPER,         EntityType.ZOGLIN,      EntityType.ZOMBIE},
        /* TEMPLE      */ {EntityType.HUSK,             EntityType.VINDICATOR,EntityType.VINDICATOR,       EntityType.STRAY,           EntityType.VEX,         EntityType.WITCH},
        /* VOID        */ {EntityType.ENDERMAN,         EntityType.WITHER_SKELETON, EntityType.SKELETON,   EntityType.BLAZE,           EntityType.ENDERMAN,    EntityType.ENDERMAN}
    };

    /**
     * 通常の敵をスポーンさせる。
     */
    public static void spawnMobs(ServerLevel level, BlockPos pos, int count, int floor, int biomeIndex) {
        spawnMobs(level, pos, count, floor, biomeIndex, null, "SOLO", 1);
    }

    public static void spawnMobs(ServerLevel level, BlockPos pos, int count, int floor, int biomeIndex,
                                 String instanceId, String mode, int participantCount) {
        EntityType<? extends Mob>[] pool = BIOME_MOBS[biomeIndex % BIOME_MOBS.length];

        // ダンジョン内の最近プレイヤーを取得（aggro用）
        Player nearest = level.getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), 256.0, false);

        for (int i = 0; i < count; i++) {
            EntityType<? extends Mob> type = pool[RANDOM.nextInt(pool.length)];
            try {
                RogueMobVariant.Roll variantRoll = RogueMobVariant.roll(level, floor, RANDOM);
                Mob mob = variantRoll != null ? variantRoll.mob() : (Mob) type.create(level);
                if (mob == null) continue;

                int ox = RANDOM.nextInt(5) - 2;
                int oz = RANDOM.nextInt(5) - 2;
                BlockPos spawn = findSafeSpawnPos(level, pos.offset(ox, 0, oz), mob);
                mob.moveTo(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D, RANDOM.nextFloat() * 360.0F, 0.0F);

                ScalingEngine.applyScaling(mob, floor);
                RogueMobVariant.apply(variantRoll, floor, RANDOM);
                applyParticipantHealthScaling(mob, participantCount, false);

                // FIERYプレフィックス or 火炎属性モブに火炎耐性を付与
                if (mob.isOnFire() || mob.fireImmune()
                        || mob instanceof net.minecraft.world.entity.monster.Blaze
                        || mob instanceof net.minecraft.world.entity.monster.MagmaCube
                        || type == EntityType.WITHER_SKELETON) {
                    mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));
                }

                // ネームタグ非表示。特殊個体名はHUD/戦闘ログ側だけで扱い、壁越し表示を避ける。
                mob.setCustomNameVisible(false);

                // 遠距離Mobに武器を装備
                equipRangedWeapon(mob);

                // 蜘蛛の壁登り + 天井詰まり対策
                if (mob instanceof net.minecraft.world.entity.monster.Spider ||
                    mob instanceof net.minecraft.world.entity.monster.CaveSpider) {
                    fixSpider(mob, level);
                }

                // スライム・マグマキューブのサイズ調整 (1～2: 小～中程度)
                if (mob instanceof net.minecraft.world.entity.monster.Slime slime) {
                    slime.setSize(1 + RANDOM.nextInt(2), true);
                }

                // 重力固定
                mob.setNoGravity(false);
                RogueMobGoalUtils.removePassivePlayerLookGoals(mob);
                mob.goalSelector.addGoal(1, new RogueMobTacticalAlertGoal(mob));
                mob.goalSelector.addGoal(2, new RogueMobEngagedTargetMonitorGoal(mob));

                // バニラのTargetGoalを全消去して、360度感知を防止
                mob.targetSelector.removeAllGoals(goal -> true);

                // 視界ベースの発見ゴールを最優先で追加
                mob.targetSelector.addGoal(0, new RogueMobVisionGoal(mob));
                // フォールバック: 被弾時は攻撃者を即ターゲット（背後からの攻撃対応）
                if (mob instanceof net.minecraft.world.entity.PathfinderMob pm) {
                    mob.targetSelector.addGoal(1, new HurtByTargetGoal(pm).setAlertOthers());
                }

                mob.setPersistenceRequired();
                mob.addTag("tac_rogue_spawned");
                mob.getPersistentData().putInt("TacRogueSpawnFloor", floor);
                mob.getPersistentData().putLong("TacRogueSpawnTick", level.getServer().getTickCount());
                FloorInstanceManager.stampEntity(mob, instanceId, floor,
                    FloorInstanceManager.EntryMode.parse(mode), null);
                level.addFreshEntity(mob);
            } catch (Exception e) {
                spawnFallbackZombie(level, pos, floor, nearest, instanceId, mode, participantCount);
            }
        }
    }

    /**
     * ボスをスポーンさせる。
     */
    public static void spawnBoss(ServerLevel level, BlockPos pos, int floor, int biomeIndex) {
        spawnBoss(level, pos, floor, biomeIndex, null, "SOLO", 1);
    }

    public static void spawnBoss(ServerLevel level, BlockPos pos, int floor, int biomeIndex,
                                 String instanceId, String mode, int participantCount) {
        Player nearest = level.getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), 256.0, false);
        try {
            TacRogueBossEntity boss = ModEntities.TAC_ROGUE_BOSS.get().create(level);
            if (boss != null) {
                BlockPos spawn = findSafeSpawnPos(level, pos, boss);
                boss.setPos(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
                boss.configureForFloor(floor, biomeIndex);
                applyParticipantHealthScaling(boss, participantCount, true);
                FloorInstanceManager.stampEntity(boss, instanceId, floor,
                    FloorInstanceManager.EntryMode.parse(mode), null);
                level.addFreshEntity(boss);
                if (nearest != null) boss.setTarget(nearest);
            }
        } catch (Exception e) {
            Mob fallback = (Mob) EntityType.WITHER_SKELETON.create(level);
            if (fallback != null) {
                BlockPos spawn = findSafeSpawnPos(level, pos, fallback);
                fallback.setPos(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
                ScalingEngine.applyBossScaling(fallback, floor, biomeIndex);
                RogueMobGoalUtils.removePassivePlayerLookGoals(fallback);
                fallback.goalSelector.addGoal(1, new RogueMobTacticalAlertGoal(fallback));
                fallback.goalSelector.addGoal(2, new RogueMobEngagedTargetMonitorGoal(fallback));
                fallback.targetSelector.removeAllGoals(goal -> true);
                fallback.targetSelector.addGoal(0, new RogueMobVisionGoal(fallback));
                if (fallback instanceof net.minecraft.world.entity.PathfinderMob pm) {
                    fallback.targetSelector.addGoal(1, new HurtByTargetGoal(pm).setAlertOthers());
                }
                fallback.addTag("tac_rogue_spawned");
                fallback.addTag("rogue:boss");
                fallback.getPersistentData().putInt("TacRogueSpawnFloor", floor);
                fallback.getPersistentData().putLong("TacRogueSpawnTick", level.getServer().getTickCount());
                applyParticipantHealthScaling(fallback, participantCount, true);
                FloorInstanceManager.stampEntity(fallback, instanceId, floor,
                    FloorInstanceManager.EntryMode.parse(mode), null);
                level.addFreshEntity(fallback);
                if (nearest != null) fallback.setTarget(nearest);
            }
        }
    }

    /** 蜘蛛の壁登り + 天井詰まりを防ぐ */
    private static void fixSpider(Mob spider, ServerLevel level) {
        spider.getPersistentData().putBoolean("TacRogueNoSpiderClimb", true);
        spider.goalSelector.getAvailableGoals().removeIf(g -> {
            String name = g.getGoal().getClass().getName();
            return name.contains("Climb") || name.contains("Wall") || name.contains("climb");
        });
        spider.setNoGravity(false);
        spider.noPhysics = false;
    }

    /**
     * 遠距離Mobに武器を装備させる。
     * EntityType.create() は finalizeSpawn() を呼ばないため、手動で装備する必要がある。
     */
    private static void equipRangedWeapon(Mob mob) {
        net.minecraft.world.item.ItemStack mainHand = mob.getMainHandItem();
        if (!mainHand.isEmpty()) return; // 既に装備済み

        if (mob instanceof net.minecraft.world.entity.monster.AbstractSkeleton) {
            // Skeleton, Stray, WitherSkeleton → 弓を装備
            mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOW));
        } else if (mob instanceof net.minecraft.world.entity.monster.Pillager) {
            // Pillager → クロスボウを装備
            mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CROSSBOW));
        } else if (mob instanceof net.minecraft.world.entity.monster.Vindicator) {
            // Vindicator → 木の斧を装備（鉄の斧だと基礎攻撃力と重複してダメージが高すぎるため）
            mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WOODEN_AXE));
        } else if (mob instanceof net.minecraft.world.entity.monster.WitherSkeleton) {
            // WitherSkeleton → 石の剣を装備
            mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE_SWORD));
        }
    }

    private static void spawnFallbackZombie(ServerLevel level, BlockPos pos, int floor, Player nearest,
                                            String instanceId, String mode, int participantCount) {
        Mob zombie = (Mob) EntityType.ZOMBIE.create(level);
        if (zombie != null) {
            BlockPos spawn = findSafeSpawnPos(level, pos, zombie);
            zombie.setPos(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
            ScalingEngine.applyScaling(zombie, floor);
            applyParticipantHealthScaling(zombie, participantCount, false);
            RogueMobGoalUtils.removePassivePlayerLookGoals(zombie);
            zombie.goalSelector.addGoal(1, new RogueMobTacticalAlertGoal(zombie));
            zombie.goalSelector.addGoal(2, new RogueMobEngagedTargetMonitorGoal(zombie));
            zombie.targetSelector.removeAllGoals(goal -> true);
            zombie.targetSelector.addGoal(0, new RogueMobVisionGoal(zombie));
            zombie.targetSelector.addGoal(1, new HurtByTargetGoal((net.minecraft.world.entity.PathfinderMob) zombie).setAlertOthers());
            zombie.addTag("tac_rogue_spawned");
            zombie.getPersistentData().putInt("TacRogueSpawnFloor", floor);
            zombie.getPersistentData().putLong("TacRogueSpawnTick", level.getServer().getTickCount());
            FloorInstanceManager.stampEntity(zombie, instanceId, floor,
                FloorInstanceManager.EntryMode.parse(mode), null);
            level.addFreshEntity(zombie);
        }
    }

    private static BlockPos findSafeSpawnPos(ServerLevel level, BlockPos preferred, Mob mob) {
        int requiredClearance = mob.getBbHeight() > 2.2F ? 3 : 2;
        for (int radius = 0; radius <= 4; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                    BlockPos candidate = preferred.offset(dx, 0, dz);
                    if (isSafeSpawnPos(level, candidate, requiredClearance)) {
                        return candidate;
                    }
                }
            }
        }
        return preferred;
    }

    private static boolean isSafeSpawnPos(ServerLevel level, BlockPos pos, int requiredClearance) {
        if (level.getBlockState(pos.below()).isAir()) return false;
        for (int y = 0; y < requiredClearance; y++) {
            if (!level.getBlockState(pos.above(y)).isAir()) return false;
        }
        return true;
    }

    /**
     * Normal-floor total enemy budget.
     *
     * This is a floor-wide target, not a per-room value. Early floors stay
     * readable, while later floors still scale with floor, phase, and players.
     */
    public static int getTotalMobBudgetForFloor(ServerLevel level, int floor, int eligibleRoomCount) {
        return getTotalMobBudgetForFloor(level, floor, eligibleRoomCount, 1);
    }

    public static int getTotalMobBudgetForFloor(ServerLevel level, int floor, int eligibleRoomCount, int participantCount) {
        if (eligibleRoomCount <= 0) return 0;

        int safeFloor = Math.max(1, floor);
        int soloBudget = switch (safeFloor) {
            case 1 -> 8;
            case 2 -> 12;
            case 3 -> 16;
            case 4 -> 22;
            default -> Math.min(70, 18 + safeFloor * 3 + RogueActManager.getPhase(safeFloor).mobCountBonus * 3);
        };

        int players = Math.max(1, participantCount);
        double playerMultiplier = 1.0 + Math.max(0, players - 1) * 0.55;
        int budget = (int) Math.round(soloBudget * playerMultiplier);
        int roomCap = eligibleRoomCount * getMaxMobsPerRoomForFloor(safeFloor);
        return Math.max(1, Math.min(budget, roomCap));
    }

    public static int getMaxMobsPerRoomForFloor(int floor) {
        int safeFloor = Math.max(1, floor);
        return switch (safeFloor) {
            case 1 -> 2;
            case 2 -> 3;
            case 3 -> 4;
            case 4 -> 4;
            default -> Math.min(7, 3 + (int) Math.sqrt(safeFloor) + RogueActManager.getPhase(safeFloor).mobCountBonus);
        };
    }

    /**
     * Legacy per-room count. New dungeon generation should use
     * getTotalMobBudgetForFloor(...) and distribute the result.
     */
    public static int getMobCountForFloor(ServerLevel level, int floor) {
        int baseCount = Math.min(10, 1 + (int) Math.sqrt(Math.max(1, floor)));
        baseCount += RogueActManager.getPhase(Math.max(1, floor)).mobCountBonus;
        int players = level.players().size();
        return Math.min(getMaxMobsPerRoomForFloor(floor), baseCount * (players > 0 ? players : 1));
    }

    // レガシーAPI
    public static void spawnMobs(ServerLevel level, BlockPos pos, int count) {
        spawnMobs(level, pos, count, 1, 0);
    }

    private static void applyParticipantHealthScaling(Mob mob, int participantCount, boolean boss) {
        int extraPlayers = Math.max(0, participantCount - 1);
        if (extraPlayers <= 0) return;
        var maxHealth = mob.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) return;
        double multiplier = 1.0D + extraPlayers * (boss ? 0.45D : 0.30D);
        maxHealth.setBaseValue(maxHealth.getBaseValue() * multiplier);
        mob.setHealth(mob.getMaxHealth());
    }
}
