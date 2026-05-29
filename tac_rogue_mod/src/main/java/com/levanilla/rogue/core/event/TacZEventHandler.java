package com.levanilla.rogue.core.event;

import com.levanilla.rogue.core.*;
import com.levanilla.rogue.core.registry.TacZGunRegistry;
import com.levanilla.rogue.core.service.PerkStorageService;
import com.levanilla.rogue.core.service.RogueMobAlertService;
import com.levanilla.rogue.networking.TacRogueNetworking;
import com.levanilla.rogue.world.TacRogueBossEntity;
import com.tacz.guns.api.event.common.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
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
    private static final float SUPPORT_GUN_ONE_SHOT_DAMAGE = 4096.0F;

    /** Killed entity UUIDs already rewarded by TacZ gun events (prevents double reward) */
    private static final ConcurrentHashMap<UUID, Long> processedGunKills = new ConcurrentHashMap<>();

    /** 射撃統計: プレイヤー → 総発射数 */
    private static final ConcurrentHashMap<UUID, Integer> shotsFired = new ConcurrentHashMap<>();

    /** 射撃統計: プレイヤー → 総ヒット数 */
    private static final ConcurrentHashMap<UUID, Integer> shotsHit = new ConcurrentHashMap<>();

    /** 連続キル判定用: プレイヤー → 最終キルtick */
    private static final ConcurrentHashMap<UUID, Long> lastKillTick = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onGunReload(GunReloadEvent event) {
        // Real reload timing is handled once in MixinLivingEntityReload.
        // Animation timing is handled client-side in MixinObjectAnimationRunner.
    }

    // ===== GunShootEvent: 射撃入力 =====

    @SubscribeEvent
    public static void onGunShoot(GunShootEvent event) {
        if (event.getLogicalSide() != LogicalSide.SERVER) return;
        if (!(event.getShooter() instanceof ServerPlayer player)) return;

        if (player.level().dimension() == LOBBY_DIM) {
            return;
        }

        if (player.level().dimension() != ROGUE_DIM) return;

        shotsFired.compute(player.getUUID(), (k, v) -> (v == null ? 0 : v) + 1);

        // GunFireEvent is script/fire-stage dependent in TacZ. GunShootEvent is the stable
        // server-side trigger point, so sound alerting belongs here.
        TacZGunRegistry.GunSoundProfile sound = TacZGunRegistry.getGunSoundProfile(event.getGunItemStack());
        RogueMobAlertService.onGunshot(player, sound.suppressed(), sound.alertRadius());
    }

    // ===== GunFireEvent: 発火ごとの処理 =====

    /**
     * プレイヤーが銃を発射した瞬間に呼ばれる。
     * <ul>
     *   <li>弾薬節約など、実際の発火ごとに処理したい効果</li>
     * </ul>
     */
    @SubscribeEvent
    public static void onGunFire(GunFireEvent event) {
        if (event.getLogicalSide() != LogicalSide.SERVER) return;
        if (!(event.getShooter() instanceof ServerPlayer player)) return;

        // ロビーでは射撃確認とデバッグボス検証を許可する。
        // ダメージ側は CombatEventHandler / EntityHurtByGunEvent で対象を絞る。
        if (player.level().dimension() == LOBBY_DIM) {
            return;
        }

        if (player.level().dimension() != ROGUE_DIM) return;

        // === パーク: AMMO_EFFICIENCY (弾薬節約) ===
        float ammoEfficiency = PerkDefinition.sumCategoryEffect(player, PerkDefinition.Category.AMMO_EFFICIENCY);
        float saveChance = Math.min(GameConstants.AMMO_SAVE_MAX_CHANCE,
            Math.max(0.0f, PerkDefinition.getAmmoSaveChancePercent(ammoEfficiency) / 100.0f));
        if (saveChance > 0 && player.getRandom().nextFloat() < saveChance) {
            net.minecraft.world.item.ItemStack gun = event.getGunItemStack();
            if (gun.hasTag()) {
                int currentAmmo = gun.getTag().getInt("GunCurrentAmmoCount");
                int maxAmmo = WeaponRarity.getEffectiveMagazineSize(
                    gun, TacZRegistryHelper.getMagazineSize(gun.getTag().getString("GunId")));
                if (currentAmmo + 1 <= maxAmmo) {
                    gun.getTag().putInt("GunCurrentAmmoCount", currentAmmo + 1);
                }
            }
        }

        // === 修飾子: CORRUPTED (自傷ダメージ) ===
        int corruptedCount = 0;
        for (String tag : PerkStorageService.getPerkTags(player)) {
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
        if (isSupportShooter(event.getAttacker())) {
            if (event.getAttacker().level().dimension() != ROGUE_DIM || !isSupportTarget(event.getHurtEntity())) {
                event.setCanceled(true);
            } else {
                Entity hurt = event.getHurtEntity();
                if (hurt != null) hurt.invulnerableTime = 0;
                float damage = SUPPORT_GUN_ONE_SHOT_DAMAGE;
                if (hurt instanceof LivingEntity living) {
                    damage = Math.max(damage, living.getMaxHealth() * 16.0F);
                }
                event.setBaseAmount(damage);
            }
            return;
        }
        if (!(event.getAttacker() instanceof ServerPlayer attacker)) return;
        boolean lobbyDebugBoss = attacker.level().dimension() == LOBBY_DIM
            && event.getHurtEntity() != null
            && event.getHurtEntity().getTags().contains("rogue:boss");
        if (attacker.level().dimension() != ROGUE_DIM && !lobbyDebugBoss) return;
        if (TacRogueBossEntity.isSpawnProtectionActive(event.getHurtEntity())) {
            TacRogueBossEntity.forceEngageIfBoss(event.getHurtEntity(), attacker);
            event.setCanceled(true);
            return;
        }

        float damage = event.getBaseAmount();

        // === ヒット統計 ===
        shotsHit.compute(attacker.getUUID(), (k, v) -> (v == null ? 0 : v) + 1);
        if (event.getHurtEntity() instanceof net.minecraft.world.entity.LivingEntity hurt) {
            com.levanilla.rogue.core.service.RogueMobAlertService.onAllyHit(attacker, hurt);
        }

        // === パーク: DAMAGE (攻撃力ボーナス) ===
        float damageBonus = softcapPercent(sumPerkEffect(attacker, "perk:DAMAGE"), 150.0f, 0.35f, 350.0f) / 100.0f;
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

        ItemStack heldGun = attacker.getMainHandItem();
        boolean isShotgun = isShotgun(heldGun);

        // === パーク: FORTUNE (クリティカルヒット) ===
        float fortuneEffect = sumPerkEffect(attacker, "perk:FORTUNE");
        float critChance = PerkDefinition.getCriticalChance(fortuneEffect);
        boolean perkCrit = critChance > 0 && attacker.getRandom().nextFloat() < critChance;
        if (perkCrit) {
            damage *= PerkDefinition.getCriticalDamageMultiplier(fortuneEffect);
        }

        // GUN_PROFICIENCY is firearm control, not another flat DAMAGE copy.
        float gunProf = softcapPercent(sumPerkEffect(attacker, "perk:GUN_PROFICIENCY"), 180.0f, 0.35f, 320.0f) / 100.0f;
        if (gunProf > 0.0f) {
            float controlScale = event.isHeadShot() ? 0.45f : 0.18f;
            if (isShotgun) {
                controlScale *= 0.65f;
            }
            damage *= (1.0f + gunProf * controlScale);
        }

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
        damage *= WeaponRarity.getDamageMult(heldGun);
        damage = RogueCombatEffects.applyAdrenalineDamage(attacker, damage);
        damage *= com.levanilla.rogue.core.service.DeepProgressService.damageMultiplier(
            attacker,
            event.getHurtEntity() instanceof net.minecraft.world.entity.LivingEntity living ? living : null,
            heldGun,
            event.isHeadShot());

        // === ヘッドショットボーナスの強化 ===
        if (event.isHeadShot()) {
            float hsMult = event.getHeadshotMultiplier();
            float hsBonus = Math.min(0.25f, sumPerkEffect(attacker, "perk:FORTUNE") / 400.0f);
            float headHunterBonus = Math.min(0.75f, sumPerkEffect(attacker, "perk:HEAD_HUNTER") / 150.0f);
            float gunProfHeadshotBonus = Math.min(0.35f, gunProf * 0.35f);
            event.setHeadshotMultiplier(hsMult + hsBonus + headHunterBonus + gunProfHeadshotBonus);
        }

        // === スニーク/伏せ時のダメージボーナス ===
        boolean isCrawling = CombatPostureHelper.isProne(attacker);
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
                // 無敵時間を無効化
                wither.invulnerableTime = 0;
                // 全バフ適用済みのダメージでgenericダメージとして貫通させる
                wither.hurt(wither.damageSources().generic(), damage);
                // 吸血処理等も発動させるならこの段階で行うべきだが、ここではシンプルにキャンセルする。
                // (VAMPIRE等のイベントは通常通り通すなら、setCanceled(true)する代わりにVanillaのイベントキャンセルフラグを操作するか、
                // ここで独自にVAMPIREを計算する。今回は処理の重複を避けるためにイベント自体をキャンセル)
                
                float vampBonus = PerkDefinition.getRecoveryHealAmount(sumPerkEffect(attacker, "perk:VAMPIRE"));
                if (vampBonus > 0 && damage > 0 && attacker.getHealth() < attacker.getMaxHealth()) {
                    attacker.heal(Math.min(vampBonus, damage));
                }
                
                event.setCanceled(true);
                return;
            }
        }

        // === ショットガンの無敵時間無効化（ペレット連続ヒット） ===
        // TacZGunRegistry API でカテゴリ判定 → 追加ガンパックのショットガンにも対応
        if (isShotgun) {
            if (hurtEntity != null) {
                hurtEntity.invulnerableTime = 0;
            }
        }

        // ダメージを反映
        event.setBaseAmount(damage);

        // === ダメージコンテキスト登録 (CombatEventHandler.onLivingHurt で最終ダメージと共にインジケーター送信) ===
        // TacZがこの後 HS倍率 + 防具貫通を適用し、LivingHurtEvent を発火するため、
        // ここでは最終ダメージを知ることができない。コンテキストのみ受け渡す。
        if (hurtEntity != null) {
            net.minecraft.world.phys.Vec3 impactPos = resolveDamageIndicatorAnchor(attacker, hurtEntity, event.getBullet(), event.isHeadShot());
            float predictedIndicatorDamage = event.isHeadShot()
                ? damage * event.getHeadshotMultiplier()
                : damage;
            CombatEventHandler.registerGunDamageContext(
                event.getBullet().getId(), event.isHeadShot(), isShotgun, perkCrit, impactPos, predictedIndicatorDamage);
        }
    }

    private static boolean isSupportShooter(LivingEntity attacker) {
        return attacker != null && attacker.getTags().contains("tac_rogue_support_npc");
    }

    private static boolean isSupportTarget(Entity hurt) {
        if (hurt == null || hurt.getTags().contains("tac_rogue_npc")) return false;
        return hurt.getTags().contains("tac_rogue_spawned") || hurt.getTags().contains("rogue:boss");
    }

    private static net.minecraft.world.phys.Vec3 resolveDamageIndicatorAnchor(
        ServerPlayer attacker,
        net.minecraft.world.entity.Entity hurtEntity,
        net.minecraft.world.entity.Entity bullet,
        boolean headshot
    ) {
        net.minecraft.world.phys.AABB box = hurtEntity.getBoundingBox();
        if (bullet != null) {
            net.minecraft.world.phys.Vec3 bulletPos = bullet.position();
            if (isFinite(bulletPos) && box.inflate(0.55D).contains(bulletPos)) {
                return bulletPos;
            }
        }

        net.minecraft.world.phys.Vec3 eye = attacker.getEyePosition();
        net.minecraft.world.phys.Vec3 look = attacker.getLookAngle();
        if (isFinite(eye) && isFinite(look) && look.lengthSqr() > 0.001D) {
            double distance = Math.max(8.0D, eye.distanceTo(hurtEntity.position()) + 4.0D);
            net.minecraft.world.phys.Vec3 end = eye.add(look.normalize().scale(distance));
            java.util.Optional<net.minecraft.world.phys.Vec3> clipped = box.inflate(0.12D).clip(eye, end);
            if (clipped.isPresent() && isFinite(clipped.get())) {
                return clipped.get();
            }
        }

        return hurtEntity.position().add(0.0D, hurtEntity.getBbHeight() * (headshot ? 0.88D : 0.68D), 0.0D);
    }

    private static boolean isFinite(net.minecraft.world.phys.Vec3 vec) {
        return vec != null && Double.isFinite(vec.x) && Double.isFinite(vec.y) && Double.isFinite(vec.z);
    }

    @SubscribeEvent
    public static void onEntityHurtByGunPost(EntityHurtByGunEvent.Post event) {
        if (event.getLogicalSide() != LogicalSide.SERVER) return;
        if (!(event.getAttacker() instanceof ServerPlayer attacker)) return;
        if (attacker.level().dimension() != ROGUE_DIM) return;
        CombatEventHandler.flushGunDamageIndicator(attacker, event.getHurtEntity(), event.getBullet(), event.getBaseAmount());
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
            CombatEventHandler.flushGunDamageIndicator(killer, killed, event.getBullet(), event.getBaseDamage());
        }

        // Boss reward is shared with non-TacZ kills so gun kills do not miss the weapon roll.
        if (killed instanceof net.minecraft.world.entity.LivingEntity living && killed.getTags().contains("rogue:boss")) {
            com.levanilla.rogue.core.service.BossRewardService.handleBossKill(killer, living);
        }
        com.levanilla.rogue.core.service.FloorObjectiveService.onEliteKilled(killer, killed);

        // キル報酬
        com.levanilla.rogue.core.service.KillGoldRewardService.award(killer, killed, event.isHeadShot());

        // バンパイア効果
        float totalVampHeal = PerkDefinition.getRecoveryHealAmount(sumPerkEffect(killer, "perk:VAMPIRE"));
        if (totalVampHeal > 0) killer.heal(totalVampHeal);

        // パーク: BLOODLUST (キル時回復＆速度バフ)
        float bloodlustHeal = PerkDefinition.getRecoveryHealAmount(sumPerkEffect(killer, "perk:BLOODLUST"));
        if (bloodlustHeal > 0) {
            killer.heal(bloodlustHeal);
            killer.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 60, 0, false, false));
        }

        // ヘッドショットキルのクエスト進行
        if (event.isHeadShot()) {
            QuestManager.advanceQuest(killer, QuestManager.QuestType.HEADSHOT, 1);
        }

        // クエスト: キルカウント
        QuestManager.advanceQuest(killer, QuestManager.QuestType.KILL_COUNT, 1);

        if (killed != null && killed.getTags().contains("tac_rogue_variant")) {
            QuestManager.advanceQuest(killer, QuestManager.QuestType.ELITE_HUNT, 1);
        }

        net.minecraft.world.item.ItemStack heldGun = killer.getMainHandItem();
        if (!heldGun.isEmpty() && WeaponRarity.getRarity(heldGun).stars >= WeaponRarity.Rarity.RARE.stars) {
            QuestManager.advanceQuest(killer, QuestManager.QuestType.RARITY_KILL, 1);
        }

        long nowTick = killer.server.getTickCount();
        Long previousKill = lastKillTick.put(killer.getUUID(), nowTick);
        if (previousKill != null && nowTick - previousKill <= 100) {
            QuestManager.advanceQuest(killer, QuestManager.QuestType.FAST_CHAIN, 1);
        }

        // クエスト: WEAPON_MASTERY (銃での総キル数)
        QuestManager.advanceQuest(killer, QuestManager.QuestType.WEAPON_MASTERY, 1);
        com.levanilla.rogue.core.service.DeepProgressService.advanceDeepTask(
            killer, com.levanilla.rogue.core.service.DeepProgressService.DeepTaskType.WEAPON_MASTERY, 1);
        com.levanilla.rogue.core.service.DeepProgressService.onGunKill(killer, heldGun, event.isHeadShot());
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

                // 毎Tick強制的に着弾地点を見させ続け、警戒移動先もデコイ地点へ寄せる。
                RogueMobAlertService.applyDecoy(
                    mob,
                    hitPos,
                    owner instanceof net.minecraft.world.entity.LivingEntity livingOwner ? livingOwner : null,
                    100L);
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

    // PERF-2: PerkDefinition.sumEffect() に委譲（DRY原則）
    private static float sumPerkEffect(ServerPlayer player, String perkPrefix) {
        return PerkDefinition.sumEffect(player, perkPrefix);
    }

    private static float softcapPercent(float rawPercent, float softStart, float postSoftScale, float hardCap) {
        if (rawPercent <= softStart) {
            return rawPercent;
        }
        float compressed = softStart + (rawPercent - softStart) * postSoftScale;
        return Math.min(compressed, hardCap);
    }

    private static boolean isShotgun(ItemStack heldGun) {
        boolean isShotgun = false;
        net.minecraft.resources.ResourceLocation heldGunId = com.levanilla.rogue.core.registry.TacZGunRegistry.getGunId(heldGun);
        if (heldGunId != null) {
            com.levanilla.rogue.core.registry.TacZGunRegistry.GunProfile profile =
                com.levanilla.rogue.core.registry.TacZGunRegistry.getProfile(heldGunId);
            if (profile != null) {
                isShotgun = (profile.category == com.levanilla.rogue.core.registry.ShopCatalog.Category.SHOTGUN);
            }
        }
        if (!isShotgun && heldGun.hasTag() && heldGun.getTag() != null) {
            String gunIdStr = heldGun.getTag().getString("GunId").toLowerCase(java.util.Locale.ROOT);
            isShotgun = gunIdStr.contains("shotgun");
        }
        return isShotgun;
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

    // ===== メモリリーク防止 =====

    /** サーバー停止時にスタティックマップをクリア */
    public static void clearMemory() {
        processedGunKills.clear();
        shotsFired.clear();
        shotsHit.clear();
        lastKillTick.clear();
    }
}
