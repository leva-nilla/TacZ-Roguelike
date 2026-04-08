package com.levanilla.rogue.world;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import com.levanilla.rogue.core.ScalingEngine;
import com.levanilla.rogue.world.goal.RogueMobVisionGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;

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
        /* RUINS       */ {EntityType.ZOMBIE,           EntityType.ZOMBIE,    EntityType.PILLAGER,         EntityType.SPIDER,      EntityType.ZOMBIE_VILLAGER, EntityType.ZOMBIE},
        /* LAB         */ {EntityType.HUSK,             EntityType.ZOMBIE,    EntityType.SKELETON,         EntityType.CAVE_SPIDER, EntityType.HUSK,        EntityType.ZOMBIE},
        /* UNDERGROUND */ {EntityType.ZOMBIE,           EntityType.ZOMBIE,    EntityType.STRAY,            EntityType.CAVE_SPIDER, EntityType.SPIDER,      EntityType.SILVERFISH},
        /* MILITARY    */ {EntityType.ZOMBIE,           EntityType.VINDICATOR,EntityType.VINDICATOR,       EntityType.PILLAGER,    EntityType.ZOMBIE,      EntityType.ZOMBIE},
        /* NETHER      */ {EntityType.WITHER_SKELETON,  EntityType.WITHER_SKELETON, EntityType.SKELETON, EntityType.BLAZE, EntityType.WITHER_SKELETON, EntityType.WITHER_SKELETON},
        /* OCEAN       */ {EntityType.DROWNED,          EntityType.DROWNED,   EntityType.SKELETON,         EntityType.DROWNED,     EntityType.DROWNED,     EntityType.DROWNED},
        /* URBAN       */ {EntityType.ZOMBIE,           EntityType.VINDICATOR,EntityType.PILLAGER,         EntityType.VINDICATOR,  EntityType.ZOMBIE,      EntityType.ZOMBIE},
        /* TEMPLE      */ {EntityType.HUSK,             EntityType.HUSK,      EntityType.VINDICATOR,       EntityType.VINDICATOR,  EntityType.STRAY,       EntityType.VEX},
        /* VOID        */ {EntityType.ENDERMAN,         EntityType.ENDERMAN,  EntityType.WITHER_SKELETON,  EntityType.SKELETON,    EntityType.BLAZE,       EntityType.ENDERMAN}
    };

    @SuppressWarnings("unchecked")
    private static final EntityType<? extends Mob>[] BOSS_MOBS = new EntityType[] {
        EntityType.RAVAGER,       // RUINS
        EntityType.IRON_GOLEM,   // LAB
        EntityType.WARDEN,        // UNDERGROUND
        EntityType.RAVAGER,       // MILITARY
        EntityType.WITHER,        // NETHER
        EntityType.DROWNED,       // OCEAN (was Guardian)
        EntityType.RAVAGER,       // URBAN
        EntityType.EVOKER,        // TEMPLE
        EntityType.WARDEN         // VOID
    };

    /**
     * 通常の敵をスポーンさせる。
     */
    public static void spawnMobs(ServerLevel level, BlockPos pos, int count, int floor, int biomeIndex) {
        EntityType<? extends Mob>[] pool = BIOME_MOBS[biomeIndex % BIOME_MOBS.length];

        // ダンジョン内の最近プレイヤーを取得（aggro用）
        Player nearest = level.getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), 256.0, false);

        for (int i = 0; i < count; i++) {
            EntityType<? extends Mob> type = pool[RANDOM.nextInt(pool.length)];
            try {
                Mob mob = (Mob) type.create(level);
                if (mob == null) continue;

                double ox = (RANDOM.nextDouble() - 0.5) * 4.0;
                double oz = (RANDOM.nextDouble() - 0.5) * 4.0;
                mob.setPos(pos.getX() + 0.5 + ox, pos.getY() + 1, pos.getZ() + 0.5 + oz);

                ScalingEngine.applyScaling(mob, floor);

                // FIERYプレフィックス or 火炎属性モブに火炎耐性を付与
                if (mob.isOnFire() || mob.fireImmune()
                        || mob instanceof net.minecraft.world.entity.monster.Blaze
                        || mob instanceof net.minecraft.world.entity.monster.MagmaCube
                        || type == EntityType.WITHER_SKELETON) {
                    mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));
                }

                // ネームタグ非表示
                mob.setCustomNameVisible(false);

                // 遠距離Mobに武器を装備
                equipRangedWeapon(mob);

                // 蜘蛛の壁登り + 天井詰まり対策
                if (mob instanceof net.minecraft.world.entity.monster.Spider ||
                    mob instanceof net.minecraft.world.entity.monster.CaveSpider) {
                    fixSpider(mob, level);
                }

                // 重力固定
                mob.setNoGravity(false);

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
                level.addFreshEntity(mob);
            } catch (Exception e) {
                spawnFallbackZombie(level, pos, floor, nearest);
            }
        }
    }

    /**
     * ボスをスポーンさせる。
     */
    public static void spawnBoss(ServerLevel level, BlockPos pos, int floor, int biomeIndex) {
        EntityType<? extends Mob> bossType = BOSS_MOBS[biomeIndex % BOSS_MOBS.length];
        Player nearest = level.getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), 256.0, false);
        try {
            Mob boss = (Mob) bossType.create(level);
            if (boss != null) {
                boss.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
                ScalingEngine.applyBossScaling(boss, floor, biomeIndex);
                
                boss.targetSelector.removeAllGoals(goal -> true);
                boss.targetSelector.addGoal(0, new RogueMobVisionGoal(boss));
                if (boss instanceof net.minecraft.world.entity.PathfinderMob pm) {
                    boss.targetSelector.addGoal(1, new HurtByTargetGoal(pm).setAlertOthers());
                }
                boss.addTag("tac_rogue_spawned");
                boss.getPersistentData().putInt("TacRogueSpawnFloor", floor);
                boss.getPersistentData().putLong("TacRogueSpawnTick", level.getServer().getTickCount());
                level.addFreshEntity(boss);
            }
        } catch (Exception e) {
            Mob fallback = (Mob) EntityType.WITHER_SKELETON.create(level);
            if (fallback != null) {
                fallback.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
                ScalingEngine.applyBossScaling(fallback, floor, biomeIndex);
                fallback.targetSelector.removeAllGoals(goal -> true);
                fallback.targetSelector.addGoal(0, new RogueMobVisionGoal(fallback));
                if (fallback instanceof net.minecraft.world.entity.PathfinderMob pm) {
                    fallback.targetSelector.addGoal(1, new HurtByTargetGoal(pm).setAlertOthers());
                }
                fallback.addTag("tac_rogue_spawned");
                fallback.getPersistentData().putInt("TacRogueSpawnFloor", floor);
                fallback.getPersistentData().putLong("TacRogueSpawnTick", level.getServer().getTickCount());
                level.addFreshEntity(fallback);
                if (nearest != null) fallback.setTarget(nearest);
            }
        }
    }

    /** 蜘蛛の壁登り + 天井詰まりを防ぐ */
    private static void fixSpider(Mob spider, ServerLevel level) {
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
            // Vindicator → 鉄の斧を装備
            mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_AXE));
        } else if (mob instanceof net.minecraft.world.entity.monster.WitherSkeleton) {
            // WitherSkeleton → 石の剣を装備
            mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE_SWORD));
        }
    }

    private static void spawnFallbackZombie(ServerLevel level, BlockPos pos, int floor, Player nearest) {
        Mob zombie = (Mob) EntityType.ZOMBIE.create(level);
        if (zombie != null) {
            zombie.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
            ScalingEngine.applyScaling(zombie, floor);
            zombie.targetSelector.removeAllGoals(goal -> true);
            zombie.targetSelector.addGoal(0, new RogueMobVisionGoal(zombie));
            zombie.targetSelector.addGoal(1, new HurtByTargetGoal((net.minecraft.world.entity.PathfinderMob) zombie).setAlertOthers());
            zombie.addTag("tac_rogue_spawned");
            zombie.getPersistentData().putInt("TacRogueSpawnFloor", floor);
            zombie.getPersistentData().putLong("TacRogueSpawnTick", level.getServer().getTickCount());
            level.addFreshEntity(zombie);
        }
    }

    /**
     * 階層とプレイヤー数に応じた敵の数を計算。
     */
    public static int getMobCountForFloor(ServerLevel level, int floor) {
        // F1=1, F3=2, F6=3, F9=4, F12=5, F15+=6
        int baseCount = Math.min(6, 1 + floor / 3);
        int players = level.players().size();
        return baseCount * (players > 0 ? players : 1);
    }

    // レガシーAPI
    public static void spawnMobs(ServerLevel level, BlockPos pos, int count) {
        spawnMobs(level, pos, count, 1, 0);
    }
}
