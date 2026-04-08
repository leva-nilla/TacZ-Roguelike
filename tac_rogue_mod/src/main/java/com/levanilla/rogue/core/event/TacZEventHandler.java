package com.levanilla.rogue.core.event;

import com.levanilla.rogue.core.*;
import com.levanilla.rogue.core.registry.TacZGunRegistry;
import com.levanilla.rogue.networking.TacRogueNetworking;
import com.tacz.guns.api.event.common.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.levanilla.rogue.core.CommonEventHandler.ROGUE_DIM;
import static com.levanilla.rogue.core.CommonEventHandler.LOBBY_DIM;

/**
 * TacZ 公式イベントハンドラ — v0.4.0
 *
 * <p>TacZ が提供する専用イベント ({@link GunFireEvent}, {@link EntityHurtByGunEvent},
 * {@link EntityKillByGunEvent} 等) を直接購読し、銃撃ローグライクの戦闘処理を
 * より正確かつ効率的に実装する。</p>
 *
 * <p>従来の {@link CombatEventHandler} では {@code LivingHurtEvent} で間接的に
 * 銃撃を推定していたが、本ハンドラでは銃の ID・ヘッドショット判定・被弾側情報を
 * TacZ から直接取得できる。</p>
 */
@Mod.EventBusSubscriber(modid = "tac_rogue")
public class TacZEventHandler {

    /** Killed entity UUIDs already rewarded by TacZ gun events (prevents double reward) */
    private static final ConcurrentHashMap<UUID, Long> processedGunKills = new ConcurrentHashMap<>();

    /** 射撃統計: プレイヤー → 総発射数 */
    private static final ConcurrentHashMap<UUID, Integer> shotsFired = new ConcurrentHashMap<>();

    /** 射撃統計: プレイヤー → 総ヒット数 */
    private static final ConcurrentHashMap<UUID, Integer> shotsHit = new ConcurrentHashMap<>();

    // ===== GunFireEvent: 射撃の瞬間 =====

    /**
     * プレイヤーが銃を発射した瞬間に呼ばれる。
     * <ul>
     *   <li>射撃音による敵アラート（サプレッサー考慮）</li>
     *   <li>射撃統計の記録</li>
     * </ul>
     */
    @SubscribeEvent
    public static void onGunFire(GunFireEvent event) {
        if (event.getLogicalSide() != LogicalSide.SERVER) return;
        if (!(event.getShooter() instanceof ServerPlayer player)) return;

        // ロビーでの射撃禁止 (正規 TacZ API — リフレクション不要)
        if (player.level().dimension() == LOBBY_DIM) {
            event.setCanceled(true);
            return;
        }

        if (player.level().dimension() != ROGUE_DIM) return;

        // 射撃統計
        shotsFired.compute(player.getUUID(), (k, v) -> (v == null ? 0 : v) + 1);

        // === パーク: AMMO_EFFICIENCY (弾薬節約) ===
        float ammoEff = sumPerkEffect(player, "perk:AMMO_EFFICIENCY");
        if (ammoEff > 0) {
            float chance = Math.min(ammoEff / 100.0f, 0.5f); // 上限50%
            if (player.getRandom().nextFloat() < chance) {
                net.minecraft.world.item.ItemStack gun = event.getGunItemStack();
                if (gun.hasTag()) {
                    int currentAmmo = gun.getTag().getInt("GunCurrentAmmoCount");
                    int maxAmmo = TacZRegistryHelper.getMagazineSize(gun.getTag().getString("GunId"));
                    if (currentAmmo + 1 <= maxAmmo) {
                        gun.getTag().putInt("GunCurrentAmmoCount", currentAmmo + 1);
                    }
                }
            }
        }

        // 射撃音アラート — TacZGunRegistry API でサプレッサー判定
        boolean suppressed = TacZGunRegistry.hasSuppressor(event.getGunItemStack());
        alertNearbyMobsByGunshot(player, suppressed);

        // === 修飾子: CORRUPTED (自傷ダメージ) ===
        int corruptedCount = 0;
        for (String tag : player.getTags()) {
            if (tag.startsWith("perk:") && tag.contains(":CORRUPTED:")) corruptedCount++;
        }
        if (corruptedCount > 0) {
            if (player.getHealth() > 1.0f) {
                player.setHealth(Math.max(1.0f, player.getHealth() - (0.2f * corruptedCount)));
            }
        }
    }

    // ===== EntityHurtByGunEvent.Pre: 銃弾ダメージ計算 =====

    /**
     * TacZ の銃弾がエンティティに命中した直後（ダメージ適用前）に呼ばれる。
     * パーク（DAMAGE, FORTUNE, GUN_PROFICIENCY, HANDLING）のダメージ修正を適用。
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityHurtByGun(EntityHurtByGunEvent.Pre event) {
        if (event.getLogicalSide() != LogicalSide.SERVER) return;
        if (!(event.getAttacker() instanceof ServerPlayer attacker)) return;
        if (attacker.level().dimension() != ROGUE_DIM) return;

        float damage = event.getBaseAmount();

        // === ヒット統計 ===
        shotsHit.compute(attacker.getUUID(), (k, v) -> (v == null ? 0 : v) + 1);

        // === パーク: DAMAGE (攻撃力ボーナス) ===
        float damageBonus = sumPerkEffect(attacker, "perk:DAMAGE") / 100.0f;
        damage *= (1.0f + damageBonus);

        // === パーク: SHARPSHOOTER (狙撃手) ===
        float sharpshooterBonus = sumPerkEffect(attacker, "perk:SHARPSHOOTER") / 100.0f;
        if (sharpshooterBonus > 0 && event.getHurtEntity() != null) {
            double dist = attacker.distanceTo(event.getHurtEntity());
            if (dist > 10.0) {
                float distMult = (float) Math.min(30.0, dist - 10.0) / 20.0f;
                damage *= (1.0f + sharpshooterBonus * distMult);
            }
        }

        // === パーク: EXECUTIONER (処刑人) ===
        float execBonus = sumPerkEffect(attacker, "perk:EXECUTIONER") / 100.0f;
        if (execBonus > 0 && event.getHurtEntity() instanceof net.minecraft.world.entity.LivingEntity le) {
            if (le.getHealth() <= le.getMaxHealth() * 0.3f) {
                damage *= (1.0f + execBonus);
            }
        }

        // === パーク: FORTUNE (クリティカルヒット) ===
        float critChance = sumPerkEffect(attacker, "perk:FORTUNE") / 100.0f;
        float overCritBonus = Math.max(0, critChance - 1.0f);
        boolean perkCrit = critChance > 0 && attacker.getRandom().nextFloat() < critChance;
        if (perkCrit) {
            damage *= (GameConstants.CRITICAL_DAMAGE_MULT + overCritBonus);
        }

        // === パーク: GUN_PROFICIENCY (射撃熟練度) ===
        float gunProfBonus = sumPerkEffect(attacker, "perk:GUN_PROFICIENCY") / 100.0f;
        damage *= (1.0f + gunProfBonus * 0.7f);

        // === パーク: HANDLING (取り回し) ===
        float handlingBonus = sumPerkEffect(attacker, "perk:HANDLING") / 100.0f;
        if (handlingBonus > 0) {
            if (attacker.isShiftKeyDown()) {
                damage *= (1.0f + handlingBonus * 0.4f);
            } else {
                damage *= (1.0f + handlingBonus * 0.15f);
            }
        }

        // === 武器レアリティダメージ倍率 ===
        net.minecraft.world.item.ItemStack heldGun = attacker.getMainHandItem();
        damage *= WeaponRarity.getDamageMult(heldGun);

        // === ヘッドショットボーナスの強化 ===
        if (event.isHeadShot()) {
            float hsMult = event.getHeadshotMultiplier();
            float hsBonus = sumPerkEffect(attacker, "perk:FORTUNE") / 200.0f; // クリティカルの半分
            float headHunterBonus = sumPerkEffect(attacker, "perk:HEAD_HUNTER") / 100.0f;
            event.setHeadshotMultiplier(hsMult + hsBonus + headHunterBonus);
        }

        // === スニーク/伏せ時のダメージボーナス ===
        boolean isCrawling = attacker.isSwimming();
        boolean isSneaking = attacker.isShiftKeyDown();
        if (isCrawling) {
            damage *= GameConstants.CRAWL_DAMAGE_MULT;
        } else if (isSneaking) {
            damage *= GameConstants.SNEAK_DAMAGE_MULT;
        }

        // === ウィザーの弾丸無効化バリア (Projectile Immunity) を貫通させる処理 ===
        net.minecraft.world.entity.Entity hurtEntity = event.getHurtEntity();
        if (hurtEntity instanceof net.minecraft.world.entity.boss.wither.WitherBoss wither) {
            if (wither.getHealth() <= wither.getMaxHealth() / 2.0F) {
                // 全バフ適用済みのダメージでgenericダメージとして貫通させる
                wither.hurt(wither.damageSources().generic(), damage);
                // 吸血処理等も発動させるならこの段階で行うべきだが、ここではシンプルにキャンセルする。
                // (VAMPIRE等のイベントは通常通り通すなら、setCanceled(true)する代わりにVanillaのイベントキャンセルフラグを操作するか、
                // ここで独自にVAMPIREを計算する。今回は処理の重複を避けるためにイベント自体をキャンセル)
                
                float vampBonus = sumPerkEffect(attacker, "perk:VAMPIRE") / 10f;
                if (vampBonus > 0 && damage > 0 && attacker.getHealth() < attacker.getMaxHealth()) {
                    attacker.heal(Math.min(vampBonus, damage));
                }
                
                event.setCanceled(true);
                return;
            }
        }

        // ダメージを反映
        event.setBaseAmount(damage);

        // === ダメージインジケーター通知 ===
        if (hurtEntity != null) {
            double x = hurtEntity.getX();
            double y = hurtEntity.getY() + hurtEntity.getBbHeight();
            double z = hurtEntity.getZ();
            boolean isCritical = perkCrit || event.isHeadShot() || damage > GameConstants.CRITICAL_DAMAGE_THRESHOLD;

            String data = String.format("%.1f:%.2f:%.2f:%.2f:%b", damage, x, y, z, isCritical);
            TacRogueNetworking.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.ALL.noArg(),
                new com.levanilla.rogue.networking.SyncDataMessage("dmg:" + data));
        }
    }

    // ===== EntityKillByGunEvent: 銃撃キル報酬 =====

    /**
     * TacZ の銃弾でエンティティが死亡した時に呼ばれる。
     * CombatEventHandler.onLivingDeath の銃撃キル部分を正確に置き換え。
     */
    @SubscribeEvent
    public static void onEntityKillByGun(EntityKillByGunEvent event) {
        if (event.getLogicalSide() != LogicalSide.SERVER) return;
        if (!(event.getAttacker() instanceof ServerPlayer killer)) return;
        if (killer.level().dimension() != ROGUE_DIM) return;

        PlayerRunData runData = RunManager.getData(killer);
        if (!runData.isRunActive()) return;

        // Mark this kill as processed by TacZ (prevents CombatEventHandler double reward)
        net.minecraft.world.entity.Entity killed = event.getKilledEntity();
        if (killed != null) {
            processedGunKills.put(killed.getUUID(), System.currentTimeMillis());
        }

        // キル報酬
        int reward = PriceManager.getKillReward(runData.getCurrentFloor());
        float goldBonus = sumPerkEffect(killer, "perk:GOLD_RUSH") / 100.0f;
        reward = (int)(reward * (1.0f + goldBonus));
        CurrencyManager.addGold(killer, (int)(reward * DifficultyManager.getGoldMultiplier()));
        RunManager.syncPlayer(killer);

        // バンパイア効果
        float totalVampHeal = sumPerkEffect(killer, "perk:VAMPIRE") / 10.0f;
        if (totalVampHeal > 0) killer.heal(totalVampHeal);

        // パーク: BLOODLUST (キル時回復＆速度バフ)
        float bloodlust = sumPerkEffect(killer, "perk:BLOODLUST") / 10.0f;
        if (bloodlust > 0) {
            killer.heal(bloodlust * 0.5f);
            killer.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 60, 0, false, false));
        }

        // ヘッドショットキルのボーナスゴールド
        if (event.isHeadShot()) {
            int hsBonus = (int)(reward * 0.3f);
            CurrencyManager.addGold(killer, hsBonus);
            // クエスト: ヘッドショット
            QuestManager.advanceQuest(killer, QuestManager.QuestType.HEADSHOT, 1);
        }

        // クエスト: キルカウント
        QuestManager.advanceQuest(killer, QuestManager.QuestType.KILL_COUNT, 1);

        // クエスト: ステルスキル (ターゲットに気づかれていない状態でのキル)
        if (killed instanceof Mob mob && mob.getTarget() == null) {
            QuestManager.advanceQuest(killer, QuestManager.QuestType.STEALTH_KILL, 1);
        }

        // クエスト: WEAPON_MASTERY (銃での総キル数)
        QuestManager.advanceQuest(killer, QuestManager.QuestType.WEAPON_MASTERY, 1);
    }

    // ===== 射撃音アラート (GunFireEvent ベース) =====

    private static void alertNearbyMobsByGunshot(ServerPlayer shooter, boolean suppressed) {
        double radius = suppressed ? GameConstants.SUPPRESSED_ALERT_RADIUS : GameConstants.GUNSHOT_ALERT_RADIUS;
        double reducedRadius = radius / 3.0;
        AABB area = new AABB(shooter.blockPosition()).inflate(radius);
        List<Mob> mobs = shooter.level().getEntitiesOfClass(
            Mob.class, area, m -> m.isAlive() && m.getTags().contains("tac_rogue_spawned"));
        for (Mob mob : mobs) {
            if (mob.getTarget() == null) {
                boolean hasLOS = mob.hasLineOfSight(shooter);
                double dist = mob.distanceTo(shooter);
                if (hasLOS || dist <= reducedRadius) {
                    mob.getPersistentData().putLong("LastAlertTick", mob.level().getGameTime());
                    mob.setTarget(shooter);
                }
            }
        }
    }

    // ===== 雪玉のデコイ機能 (壁や敵に当ててもデコイとして働く) =====

    @SubscribeEvent
    public static void onProjectileImpact(net.minecraftforge.event.entity.ProjectileImpactEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (event.getEntity() instanceof net.minecraft.world.entity.projectile.Snowball snowball) {
            net.minecraft.world.phys.HitResult hitResult = event.getRayTraceResult();
            net.minecraft.world.phys.Vec3 hitPos = hitResult.getLocation();
            net.minecraft.world.entity.Entity owner = snowball.getOwner();

            double radius = 15.0; // 警戒させる範囲
            AABB area = new AABB(hitPos.x - radius, hitPos.y - radius, hitPos.z - radius,
                                 hitPos.x + radius, hitPos.y + radius, hitPos.z + radius);

            List<Mob> mobs = snowball.level().getEntitiesOfClass(
                Mob.class, area, m -> m.isAlive() && m.getTags().contains("tac_rogue_spawned"));

            for (Mob mob : mobs) {
                // ボス除外
                if (mob.getTags().contains("rogue:boss")) continue;
                if (mob.hasCustomName() && mob.getCustomName() != null && mob.getCustomName().getString().toLowerCase().contains("boss")) continue;

                // 既にターゲット(発見状態)がある場合は雪玉を完全に無視する
                if (mob.getTarget() != null) {
                    continue;
                }

                // デコイ地点に注意を向け、誘導中に偶然プレイヤーを視認しにくくするため一時的な盲目(Blindness)と移動速度低下を付与
                mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.BLINDNESS, 100, 0, false, false));
                
                mob.getLookControl().setLookAt(hitPos.x, hitPos.y, hitPos.z, 30.0F, 30.0F);
                mob.getNavigation().moveTo(hitPos.x, hitPos.y, hitPos.z, 1.2);

                // 毎Tick強制的に着弾地点を見させ続けるための予約
                mob.getPersistentData().putDouble("DecoyX", hitPos.x);
                mob.getPersistentData().putDouble("DecoyY", hitPos.y);
                mob.getPersistentData().putDouble("DecoyZ", hitPos.z);
                mob.getPersistentData().putLong("DecoyEndTime", mob.level().getGameTime() + 100);
            }
        }
    }

    // ===== 統計アクセサ =====

    public static int getShotsFired(UUID playerId) { return shotsFired.getOrDefault(playerId, 0); }
    public static int getShotsHit(UUID playerId) { return shotsHit.getOrDefault(playerId, 0); }
    public static float getAccuracy(UUID playerId) {
        int fired = getShotsFired(playerId);
        return fired > 0 ? (float) getShotsHit(playerId) / fired * 100.0f : 0.0f;
    }

    /**
     * Check if an entity was already killed and rewarded by TacZ gun events.
     * Used by CombatEventHandler to prevent double reward with 100% certainty.
     */
    public static boolean wasGunKill(UUID entityId) {
        Long timestamp = processedGunKills.remove(entityId);
        return timestamp != null;
    }

    /** Periodic cleanup of stale entries (called from tick handler if needed) */
    public static void cleanupProcessedKills() {
        long now = System.currentTimeMillis();
        processedGunKills.entrySet().removeIf(e -> now - e.getValue() > 5000);
    }

    public static void resetStats(UUID playerId) {
        shotsFired.remove(playerId);
        shotsHit.remove(playerId);
    }

    // ===== パーク効果合算 =====

    private static float sumPerkEffect(ServerPlayer player, String perkPrefix) {
        float total = 0;
        for (String tag : player.getTags()) {
            if (tag.startsWith(perkPrefix)) {
                PerkDefinition perk = PerkDefinition.fromTag(tag);
                total += perk.calculateEffect();
            }
        }
        return total;
    }

    // ===== サーバー毎チック (メモリリーク防止) =====

    @SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END) {
            if (event.getServer().getTickCount() % 100 == 0) {
                cleanupProcessedKills();
            }
        }
    }
}
