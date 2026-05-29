package com.levanilla.rogue.core.event;

import com.levanilla.rogue.core.*;
import com.levanilla.rogue.core.service.PerkStorageService;
import com.levanilla.rogue.core.service.RogueMobAlertService;
import com.levanilla.rogue.core.service.RoguePickupService;
import com.levanilla.rogue.networking.StealthTakedownHintMessage;
import com.levanilla.rogue.networking.TacRogueNetworking;
import com.levanilla.rogue.world.TacRogueBossEntity;
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;

import java.util.List;
import java.util.ArrayList;
import java.util.Locale;

import static com.levanilla.rogue.core.CommonEventHandler.ROGUE_DIM;
import static com.levanilla.rogue.core.CommonEventHandler.LOBBY_DIM;

@Mod.EventBusSubscriber(modid = "tac_rogue")
public class CombatEventHandler {

    // Used by regen logic to delay natural healing after damage.
    private static final java.util.Map<java.util.UUID, Long> lastDamageTickMap = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<java.util.UUID, Long> lastKillTick = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<java.util.UUID, Integer> lastStealthHintTarget = new java.util.concurrent.ConcurrentHashMap<>();
    private static final String STEALTH_TAKEDOWN_BY = "TacRogueStealthTakedownBy";
    private static final String STEALTH_TAKEDOWN_TICK = "TacRogueStealthTakedownTick";
    private static final int STEALTH_HINT_DURATION_MS = 350;
    private static final double STEALTH_HEAD_SEES_PLAYER_DOT = 0.25D;
    private static final double STEALTH_BODY_BACK_DOT = -0.25D;
    private static final double STEALTH_HEAD_BACK_DOT = -0.55D;
    private static final double DECOY_STEALTH_HEAD_SEES_PLAYER_DOT = 0.85D;

    // TacZ damage context is registered per bullet, accumulated in LivingDamageEvent,
    // and flushed from TacZ Post/Kill after split armor/non-armor damage is done.
    public static class GunDamageContext {
        public final boolean isHeadShot;
        public final boolean isShotgun;
        public final boolean isPerkCrit;
        public final net.minecraft.world.phys.Vec3 impactPos;
        public final float predictedDamage;
        public float appliedDamage;
        public final long createdAtMs;

        public GunDamageContext(boolean isHeadShot, boolean isShotgun, boolean isPerkCrit, net.minecraft.world.phys.Vec3 impactPos, float predictedDamage) {
            this.isHeadShot = isHeadShot;
            this.isShotgun = isShotgun;
            this.isPerkCrit = isPerkCrit;
            this.impactPos = impactPos;
            this.predictedDamage = predictedDamage;
            this.appliedDamage = 0.0f;
            this.createdAtMs = System.currentTimeMillis();
        }
    }
    private static final java.util.Map<Integer, GunDamageContext> pendingGunContext = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<Integer, java.util.List<com.levanilla.rogue.core.registry.ShopCatalog.ShopItem>> weaponDropCandidatesByBand =
        new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, java.util.List<String>> attachmentIdsBySlot =
        new java.util.concurrent.ConcurrentHashMap<>();
    private static final long PENDING_GUN_CONTEXT_TTL_MS = 5000L;
    private static final String RESISTANCE_EFFECT_ADJUSTING_KEY = "TacRogueResistanceEffectAdjusting";

    public static void registerGunDamageContext(int damageKey, boolean isHeadShot, boolean isShotgun, boolean isPerkCrit, net.minecraft.world.phys.Vec3 impactPos, float predictedDamage) {
        pendingGunContext.put(damageKey, new GunDamageContext(isHeadShot, isShotgun, isPerkCrit, impactPos, predictedDamage));
    }

    public static void recordAppliedGunDamage(Entity entity, DamageSource source, float amount) {
        if (entity == null || !Float.isFinite(amount) || amount <= 0.0f) return;
        Entity direct = source == null ? null : source.getDirectEntity();
        int damageKey = direct != null ? direct.getId() : entity.getId();
        GunDamageContext ctx = pendingGunContext.get(damageKey);
        if (ctx == null && direct != null) {
            ctx = pendingGunContext.get(entity.getId());
        }
        if (ctx != null) {
            ctx.appliedDamage += amount;
        }
    }

    public static void flushGunDamageIndicator(ServerPlayer attacker, Entity hurtEntity, Entity bullet, float fallbackDamage) {
        if (attacker == null || hurtEntity == null) return;
        int damageKey = bullet != null ? bullet.getId() : hurtEntity.getId();
        GunDamageContext ctx = pendingGunContext.remove(damageKey);
        if (ctx == null && bullet != null) {
            ctx = pendingGunContext.remove(hurtEntity.getId());
        }
        float finalDamage = 0.0f;
        boolean isCritical = false;
        boolean isHeadShot = false;
        boolean isShotgun = false;
        net.minecraft.world.phys.Vec3 impact = hurtEntity.position().add(0.0D, hurtEntity.getBbHeight() * 0.72D, 0.0D);
        if (ctx != null) {
            finalDamage = ctx.appliedDamage > 0.0f ? ctx.appliedDamage : ctx.predictedDamage;
            isCritical = ctx.isPerkCrit;
            isHeadShot = ctx.isHeadShot;
            isShotgun = ctx.isShotgun;
            if (ctx.impactPos != null) impact = ctx.impactPos;
        }
        if ((!Float.isFinite(finalDamage) || finalDamage <= 0.0f) && Float.isFinite(fallbackDamage)) {
            finalDamage = fallbackDamage;
        }
        if (!Float.isFinite(finalDamage) || finalDamage <= 0.0f) {
            finalDamage = 0.0f;
        }
        String data = String.format(java.util.Locale.US, "%.1f:%.2f:%.2f:%.2f:%b:%b:%b",
            finalDamage, impact.x, impact.y, impact.z, isCritical, isHeadShot, isShotgun);
        TacRogueNetworking.CHANNEL.send(
            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> attacker),
            new com.levanilla.rogue.networking.SyncDataMessage("dmg:" + data));
    }

    @SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        if (event.getServer().getTickCount() % 100 == 0) {
            cleanupPendingGunContexts();
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        java.util.UUID uuid = player.getUUID();
        lastDamageTickMap.remove(uuid);
        lastKillTick.remove(uuid);
        lastStealthHintTarget.remove(uuid);
    }

    public static long getLastDamageTick(java.util.UUID playerId) {
        return lastDamageTickMap.getOrDefault(playerId, 0L);
    }

    public static void markPlayerDamaged(ServerPlayer player) {
        if (player == null) return;
        lastDamageTickMap.put(player.getUUID(), currentServerTick(player));
    }

    private static long currentServerTick(ServerPlayer player) {
        return player.server != null ? player.server.getTickCount() : player.level().getGameTime();
    }

    public static void syncStealthTakedownHint(ServerPlayer player) {
        if (player == null || player.level().dimension() != ROGUE_DIM || player.isSpectator()) return;
        Mob target = findStealthTakedownTarget(player);
        int targetId = target == null ? -1 : target.getId();
        Integer previous = lastStealthHintTarget.put(player.getUUID(), targetId);
        if (previous != null && previous == targetId && targetId < 0) return;
        TacRogueNetworking.CHANNEL.sendTo(
            new StealthTakedownHintMessage(targetId, targetId >= 0 ? STEALTH_HINT_DURATION_MS : 1),
            player.connection.connection,
            NetworkDirection.PLAY_TO_CLIENT);
    }

    // Stealth target filtering: mobs do not acquire players outside their forward cone unless recently alerted.
    @SubscribeEvent
    public static void onLivingChangeTarget(net.minecraftforge.event.entity.living.LivingChangeTargetEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (event.getNewTarget() instanceof ServerPlayer player && event.getEntity() instanceof Mob mob) {
            if (mob.getTags().contains("rogue:boss")) return;
            if (RogueMobAlertService.isDecoyActive(mob)
                    && RogueMobAlertService.getAlertLevel(mob) != RogueMobAlertService.AlertLevel.ENGAGED) {
                event.setCanceled(true);
                return;
            }

            long lastAlertTick = mob.getPersistentData().getLong("LastAlertTick");
            long currentTick = mob.level().getGameTime();
            if (currentTick - lastAlertTick < 100) return;

            net.minecraft.world.phys.Vec3 lookVec = mob.getViewVector(1.0F).normalize();
            net.minecraft.world.phys.Vec3 toPlayer = player.position().subtract(mob.position()).normalize();
            double dotProduct = lookVec.dot(toPlayer);
            if (dotProduct < 0.3) {
                event.setCanceled(true);
            }
        }
    }

    // Rogue/lobby dimensions suppress vanilla XP drops.
    @SubscribeEvent
    public static void onExperienceDrop(net.minecraftforge.event.entity.living.LivingExperienceDropEvent event) {
        if (event.getEntity().level().dimension() == ROGUE_DIM || event.getEntity().level().dimension() == LOBBY_DIM) {
            event.setCanceled(true);
        }
    }

    // Applies non-TacZ combat modifiers, player damage penalties, and damage indicator sync.
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;

        if (TacRogueBossEntity.isSpawnProtectionActive(event.getEntity())) {
            if (event.getSource().getEntity() instanceof net.minecraft.world.entity.LivingEntity attacker) {
                TacRogueBossEntity.forceEngageIfBoss(event.getEntity(), attacker);
            }
            event.setCanceled(true);
            return;
        }

        if ((event.getEntity().level().dimension() == ROGUE_DIM || event.getEntity().level().dimension() == LOBBY_DIM)
                && event.getSource().is(DamageTypes.STARVE)) {
            event.setCanceled(true);
            return;
        }

        if (event.getEntity() instanceof ServerPlayer adsFatiguePlayer
                && StaminaManager.isAdsExhaustDamage(adsFatiguePlayer)) {
            markPlayerDamaged(adsFatiguePlayer);
            return;
        }

        if (event.getSource().getDirectEntity() instanceof ServerPlayer srcPlayer) {
            net.minecraft.world.item.ItemStack hand = srcPlayer.getMainHandItem();
            if (isMeleeWeapon(hand)) {
                float dmgMul = WeaponRarity.getDamageMult(hand);
                int meleeLevel = srcPlayer.getPersistentData().getInt("TacRogueMeleeLevel");
                if (meleeLevel > 0) {
                    dmgMul *= (1.0f + meleeLevel * 0.5f);
                }
                event.setAmount(event.getAmount() * dmgMul);
            }
        }

        boolean lobbyDebugBossHit = event.getEntity().level().dimension() == LOBBY_DIM
            && event.getEntity().getTags().contains("rogue:boss")
            && event.getSource().getEntity() instanceof ServerPlayer;
        if ((event.getEntity().level().dimension() == LOBBY_DIM && !lobbyDebugBossHit)
            || event.getEntity().getTags().contains("rogue:merchant")) {
            event.setCanceled(true);
            return;
        }

        if (event.getEntity() instanceof Mob mob) {
            boolean stealthTakedown = isStealthTakedownHit(event, mob);
            if (stealthTakedown) {
                ServerPlayer srcPlayer = (ServerPlayer) event.getSource().getEntity();
                event.setAmount(Math.max((event.getAmount() * 10.0f) + 50.0f, mob.getHealth() + 2.0f));
                mob.getPersistentData().putString(STEALTH_TAKEDOWN_BY, srcPlayer.getUUID().toString());
                mob.getPersistentData().putLong(STEALTH_TAKEDOWN_TICK, mob.level().getGameTime());
                srcPlayer.displayClientMessage(net.minecraft.network.chat.Component.literal("\u00A7c\u00A7l* STEALTH TAKEDOWN *"), true);
                srcPlayer.level().playSound(null, mob.blockPosition(), net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_CRIT, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.8f);
            } else if (mob.level().dimension() == ROGUE_DIM
                && event.getSource().getEntity() instanceof ServerPlayer srcPlayer
                && event.getSource().getDirectEntity() == srcPlayer) {
                com.levanilla.rogue.core.service.RogueMobAlertService.onMeleeAllyHit(srcPlayer, mob);
                mob.getPersistentData().putLong("LastAlertTick", mob.level().getGameTime());
            }
        }

        if (event.getEntity() instanceof ServerPlayer damagedPlayer) {
            float dodgeEffect = PerkDefinition.sumCategoryEffect(damagedPlayer, PerkDefinition.Category.DODGE);
            float dodgeChance = Math.min(GameConstants.DODGE_MAX_CHANCE,
                PerkDefinition.getDodgeChancePercent(dodgeEffect) / 100.0f);
            if (dodgeChance > 0 && damagedPlayer.getRandom().nextFloat() < dodgeChance) {
                event.setCanceled(true);
                damagedPlayer.displayClientMessage(net.minecraft.network.chat.Component.literal("\u00A7a* DODGE! *"), true);
                return;
            }

            int volatilePerks = 0;
            for (String tag : PerkStorageService.getPerkTags(damagedPlayer)) {
                if (tag.startsWith("perk:") && tag.contains(":VOLATILE:")) {
                    volatilePerks++;
                }
            }
            if (volatilePerks > 0) {
                event.setAmount(event.getAmount() * (1.0f + volatilePerks * 0.2f));
            }

            if (isSpecialResistanceSource(event.getSource())) {
                float resistMult = getSpecialResistanceMultiplier(damagedPlayer);
                if (resistMult < 1.0f) {
                    event.setAmount(event.getAmount() * resistMult);
                }
            }
            markPlayerDamaged(damagedPlayer);
        }

        if (!(event.getEntity() instanceof Mob)) return;
        if (event.getEntity().level().dimension() != ROGUE_DIM) return;

        ServerPlayer attacker = findAttacker(event);

        if (attacker != null) {
            // TacZ bullet damage is already finalized by TacZ, but still needs indicator context.
            String msgId = event.getSource().getMsgId();
            boolean isTaczGunDamage = event.getSource().is(
                com.tacz.guns.init.ModDamageTypes.BULLETS_TAG) ||
                (msgId.contains("tacz") && msgId.contains("bullet"));

            if (!isTaczGunDamage) {
                float damage = event.getAmount();
                boolean meleeHit = event.getSource().getDirectEntity() == attacker
                    && isMeleeWeapon(attacker.getMainHandItem());

                float damageBonus = sumPerkEffect(attacker, "perk:DAMAGE") / 100.0f;
                damage *= (1.0f + damageBonus);
                damage = RogueCombatEffects.applyAdrenalineDamage(attacker, damage);
                if (meleeHit) {
                    damage *= WeaponRarity.getMeleeSpeedOverflowDamageMultiplier(attacker);
                }

                float fortuneEffect = sumPerkEffect(attacker, "perk:FORTUNE");
                float critChance = PerkDefinition.getCriticalChance(fortuneEffect);
                boolean perkCrit = critChance > 0 && attacker.getRandom().nextFloat() < critChance;
                boolean meleeCrit = meleeHit && isMeleeCriticalState(attacker);
                if (perkCrit) {
                    damage *= PerkDefinition.getCriticalDamageMultiplier(fortuneEffect);
                }
                if ((perkCrit || meleeCrit) && meleeHit) {
                    damage *= WeaponRarity.getMeleeSpeedOverflowCriticalDamageMultiplier(attacker);
                }

                float explosiveBonus = sumPerkEffect(attacker, "perk:EXPLOSIVE") / 100.0f;
                if (explosiveBonus > 0) {
                    String dmgType = event.getSource().getMsgId();
                    if (dmgType.contains("explosion") || dmgType.contains("fireworks")) {
                        damage *= (1.0f + explosiveBonus);
                    }
                }

                boolean isSneaking = attacker.isShiftKeyDown();
                boolean isCrawling = CombatPostureHelper.isProne(attacker);
                if (isCrawling) damage *= GameConstants.CRAWL_DAMAGE_MULT;
                else if (isSneaking) damage *= GameConstants.SNEAK_DAMAGE_MULT;

                event.setAmount(damage);

                double x = event.getEntity().getX();
                double y = event.getEntity().getY() + event.getEntity().getBbHeight();
                double z = event.getEntity().getZ();
                boolean isCritical = perkCrit || meleeCrit || damage > GameConstants.CRITICAL_DAMAGE_THRESHOLD;
                String data = String.format(java.util.Locale.US, "%.1f:%.2f:%.2f:%.2f:%b:%b:%b", damage, x, y, z, isCritical, false, false);
                TacRogueNetworking.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> attacker),
                    new com.levanilla.rogue.networking.SyncDataMessage("dmg:" + data));
            } else {
                // TacZ can split one bullet into normal and armor-piercing damage events.
                // The indicator is flushed from TacZ Post/Kill after LivingDamage has
                // accumulated the actual applied amount for the whole bullet.
            }
        }

    }



    @SubscribeEvent
    public static void onMobEffectAdded(MobEffectEvent.Added event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().dimension() != ROGUE_DIM && player.level().dimension() != LOBBY_DIM) return;
        if (player.getPersistentData().getBoolean(RESISTANCE_EFFECT_ADJUSTING_KEY)) return;

        MobEffectInstance instance = event.getEffectInstance();
        if (instance == null || instance.isInfiniteDuration()) return;
        MobEffect effect = instance.getEffect();
        if (effect == null || effect.isInstantenous() || effect.getCategory() != MobEffectCategory.HARMFUL) return;

        int original = instance.getDuration();
        int adjusted = reduceNegativeEffectDuration(player, original);
        if (adjusted >= original) return;

        MobEffectInstance replacement = new MobEffectInstance(
            effect,
            adjusted,
            instance.getAmplifier(),
            instance.isAmbient(),
            instance.isVisible(),
            instance.showIcon());
        replacement.setCurativeItems(instance.getCurativeItems());

        player.getPersistentData().putBoolean(RESISTANCE_EFFECT_ADJUSTING_KEY, true);
        try {
            player.removeEffect(effect);
            player.addEffect(replacement, event.getEffectSource());
        } finally {
            player.getPersistentData().remove(RESISTANCE_EFFECT_ADJUSTING_KEY);
        }
    }



    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !player.level().isClientSide
                && player.level().dimension() == ROGUE_DIM) {
            // Rogue-dimension player deaths apply difficulty penalties, then return the run to the lobby.
            int currentGold = CurrencyManager.getGold(player);
            int penalty = (int)(currentGold * DifficultyManager.getDeathPenaltyRate());
            if (penalty > 0) {
                CurrencyManager.consumeGold(player, penalty);
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.tac_rogue.death_penalty", penalty), true);
            }

            DifficultyManager.Difficulty diff = DifficultyManager.getDifficulty();
            int perksToLose = switch (diff) {
                case HARD -> 1;
                case EXTREME -> 2;
                case IRONMAN -> Integer.MAX_VALUE;
                default -> 0;
            };
            if (perksToLose > 0) {
                java.util.List<String> perkTags = new java.util.ArrayList<>(PerkStorageService.getPerkTags(player));
                java.util.Collections.shuffle(perkTags);
                int removed = 0;
                for (String tag : perkTags) {
                    if (removed >= perksToLose) break;
                    if (PerkStorageService.removePerk(player, tag)) removed++;
                }
                if (removed > 0) {
                    RunManager.savePerkTags(player);
                    player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                        "message.tac_rogue.perk_lost", removed), true);
                }
            }

            PlayerRunData data = RunManager.getData(player);
            boolean shouldReturnToLobby = data.isRunActive() && player.level().dimension() == ROGUE_DIM;
            if (diff == DifficultyManager.Difficulty.IRONMAN) {
                resetIronmanRun(player, data);
            }
            if (shouldReturnToLobby) {
                event.setCanceled(true);
                player.setHealth(player.getMaxHealth());
                com.levanilla.rogue.core.service.FloorInstanceManager.leaveInstance(player, false);
                data.setRunActive(false);

                ServerLevel lobbyLevel = player.server.getLevel(LOBBY_DIM);
                if (lobbyLevel != null) {
                    com.levanilla.rogue.world.LobbyGenerator.ensureLobbyBuilt(
                        lobbyLevel, com.levanilla.rogue.world.LobbyGenerator.DEFAULT_CENTER);
                    player.teleportTo(lobbyLevel,
                        GameConstants.LOBBY_X, GameConstants.LOBBY_Y, GameConstants.LOBBY_Z, 0, 0);
                }
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.tac_rogue.mission_failed"), true);
                RunManager.syncPlayer(player);
            }
        }

        if (event.getEntity().level() instanceof ServerLevel level && level.dimension() == ROGUE_DIM) {
            ServerPlayer killer = findKiller(event, level);

            if (killer != null) {
                // TacZEventHandler handles gun-kill rewards first; this path covers other damage sources.
                boolean alreadyRewardedByTacz = TacZEventHandler.wasGunKill(event.getEntity().getUUID());
                boolean isBulletDamage = event.getSource().is(
                    com.tacz.guns.init.ModDamageTypes.BULLETS_TAG);

                if (!alreadyRewardedByTacz && !isBulletDamage) {
                    if (event.getEntity().getTags().contains("rogue:boss")) {
                        com.levanilla.rogue.core.service.BossRewardService.handleBossKill(killer, event.getEntity());
                    }
                    com.levanilla.rogue.core.service.FloorObjectiveService.onEliteKilled(killer, event.getEntity());

                    com.levanilla.rogue.core.service.KillGoldRewardService.award(killer, event.getEntity(), false);

                    float totalVampHeal = PerkDefinition.getRecoveryHealAmount(sumPerkEffect(killer, "perk:VAMPIRE"));
                    if (totalVampHeal > 0) killer.heal(totalVampHeal);

                    float bloodlustHeal = PerkDefinition.getRecoveryHealAmount(sumPerkEffect(killer, "perk:BLOODLUST"));
                    if (bloodlustHeal > 0) {
                        killer.heal(bloodlustHeal);
                        killer.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 60, 0, false, false));
                    }

                    QuestManager.advanceQuest(killer, QuestManager.QuestType.KILL_COUNT, 1);
                    if (isMarkedStealthKill(event.getEntity(), killer)) {
                        QuestManager.advanceQuest(killer, QuestManager.QuestType.STEALTH_KILL, 1);
                    }
                    if (event.getEntity().getTags().contains("tac_rogue_variant")) {
                        QuestManager.advanceQuest(killer, QuestManager.QuestType.ELITE_HUNT, 1);
                    }
                    long nowTick = killer.server.getTickCount();
                    Long previousKill = lastKillTick.put(killer.getUUID(), nowTick);
                    if (previousKill != null && nowTick - previousKill <= 100) {
                        QuestManager.advanceQuest(killer, QuestManager.QuestType.FAST_CHAIN, 1);
                    }
                }

                float dropChance = GameConstants.DROP_BASE_CHANCE * DifficultyManager.getDropMultiplier();
                dropChance *= (1.0f + sumPerkEffect(killer, "perk:SCAVENGER") / 100.0f);

                if (level.random.nextFloat() < dropChance) {
                    net.minecraft.world.item.ItemStack dropItem = selectRoguelikeDrop(level.random, RunManager.getData(killer).getCurrentFloor());
                    net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(level,
                        event.getEntity().getX(), event.getEntity().getY(), event.getEntity().getZ(), dropItem);
                    com.levanilla.rogue.core.service.FloorInstanceManager.protectDropFor(drop, killer);
                    level.addFreshEntity(drop);

                    String dropName = dropItem.getHoverName().getString().replaceAll("\\u00A7.", "");
                    String dropData = String.format("%s:%.2f:%.2f:%.2f", dropName,
                        event.getEntity().getX(), event.getEntity().getY() + 1.0, event.getEntity().getZ());
                    TacRogueNetworking.CHANNEL.send(
                        net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> killer),
                        new com.levanilla.rogue.networking.SyncDataMessage("drop:" + dropData));
                }
            }
        }
    }


    @SubscribeEvent
    public static void onLivingDrops(net.minecraftforge.event.entity.living.LivingDropsEvent event) {
        if (!event.getEntity().level().isClientSide && event.getEntity().level().dimension() == ROGUE_DIM) {
            if (!(event.getEntity() instanceof net.minecraft.world.entity.player.Player)) {
                event.setCanceled(true);
            }
        }
    }

    private static void resetIronmanRun(ServerPlayer player, PlayerRunData data) {
        data.updateHighestEverFloor(data.getMaxReachedFloor());
        data.updateHighestEverFloor(data.getCurrentFloor());
        data.setCurrentFloor(0);
        data.setMaxReachedFloor(0);
        data.setRunActive(false);
        data.setFloorCleared(false);
        data.clearCurrentDeepTask();
        data.clearRunRewardClaims();
        player.removeTag("rogue:gear_selected");
        PerkStorageService.clearPerks(player);
        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
            "message.tac_rogue.ironman_run_reset"), true);
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (!event.getEntity().level().isClientSide
            && event.getEntity().level().dimension() == ROGUE_DIM
            && event.getEntity() instanceof Mob
            && event.getSource().is(com.tacz.guns.init.ModDamageTypes.BULLETS_TAG)) {
            recordAppliedGunDamage(event.getEntity(), event.getSource(), event.getAmount());
        }

        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().dimension() != ROGUE_DIM) return;
        if (event.getSource().is(DamageTypeTags.BYPASSES_ARMOR)) return;

        double armor = player.getAttributeValue(Attributes.ARMOR);
        float extraReduction = GameConstants.getArmorOvercapExtraReduction(armor);
        if (extraReduction <= 0.0f) return;

        event.setAmount(event.getAmount() * (1.0f - extraReduction));
    }

    private static boolean isStealthTakedownHit(LivingHurtEvent event, Mob mob) {
        if (mob.level().isClientSide || mob.level().dimension() != ROGUE_DIM) return false;
        if (!(event.getSource().getEntity() instanceof ServerPlayer srcPlayer)) return false;
        if (event.getSource().getDirectEntity() != srcPlayer) return false;
        return isStealthTakedownEligible(srcPlayer, mob);
    }

    private static Mob findStealthTakedownTarget(ServerPlayer player) {
        if (player.level().dimension() != ROGUE_DIM) return null;
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F);
        double range = 3.2D;
        Vec3 end = eye.add(look.scale(range));
        AABB searchBox = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0D);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
            player,
            eye,
            end,
            searchBox,
            entity -> entity instanceof Mob candidate && isStealthTakedownEligible(player, candidate),
            range * range);
        return hit != null && hit.getEntity() instanceof Mob target ? target : null;
    }

    private static boolean isStealthTakedownEligible(ServerPlayer srcPlayer, Mob mob) {
        if (mob == null || !mob.isAlive()) return false;
        if (mob.level().isClientSide || mob.level().dimension() != ROGUE_DIM) return false;
        if (mob.getTags().contains("rogue:boss")) return false;
        if (mob.getTarget() != null) return false;

        long now = mob.level().getGameTime();
        boolean decoyActive = isActiveDecoyDistraction(mob, now);
        if (mob.distanceTo(srcPlayer) > (decoyActive ? 3.5D : 3.0D)) return false;

        RogueMobAlertService.AlertLevel alertLevel = RogueMobAlertService.getAlertLevel(mob);
        if (alertLevel != RogueMobAlertService.AlertLevel.NONE
                && !(decoyActive && alertLevel != RogueMobAlertService.AlertLevel.ENGAGED)) {
            return false;
        }

        Vec3 toPlayer = srcPlayer.position().subtract(mob.position()).normalize();
        double headDot = horizontalFacingFromYaw(mob.getYHeadRot()).dot(flatten(toPlayer));
        if (decoyActive) {
            return headDot < DECOY_STEALTH_HEAD_SEES_PLAYER_DOT && mob.hasLineOfSight(srcPlayer);
        }
        if (headDot >= STEALTH_HEAD_SEES_PLAYER_DOT) return false;

        double bodyDot = horizontalFacingFromYaw(mob.yBodyRot).dot(flatten(toPlayer));
        if (bodyDot >= STEALTH_BODY_BACK_DOT && headDot >= STEALTH_HEAD_BACK_DOT) return false;
        return mob.hasLineOfSight(srcPlayer);
    }

    private static boolean isActiveDecoyDistraction(Mob mob, long now) {
        long decoyEnd = mob.getPersistentData().getLong(RogueMobAlertService.DECOY_END_TIME);
        return decoyEnd > now;
    }

    private static Vec3 flatten(Vec3 vec) {
        Vec3 flat = new Vec3(vec.x, 0.0D, vec.z);
        double len = flat.length();
        return len < 1.0E-4D ? Vec3.ZERO : flat.scale(1.0D / len);
    }

    private static Vec3 horizontalFacingFromYaw(float yawDegrees) {
        float radians = yawDegrees * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(radians), 0.0D, Mth.cos(radians)).normalize();
    }

    private static boolean isMarkedStealthKill(net.minecraft.world.entity.LivingEntity killed, ServerPlayer killer) {
        if (!(killed instanceof Mob)) return false;
        String id = killed.getPersistentData().getString(STEALTH_TAKEDOWN_BY);
        if (!killer.getUUID().toString().equals(id)) return false;
        long tick = killed.getPersistentData().getLong(STEALTH_TAKEDOWN_TICK);
        return tick > 0L && killed.level().getGameTime() - tick <= 40L;
    }

    // Rogue-dimension pickups are routed into dedicated inventory, ammo, and stash slots.
    @SubscribeEvent
    public static void onItemPickup(net.minecraftforge.event.entity.player.EntityItemPickupEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().dimension() != ROGUE_DIM) return;

        net.minecraft.world.item.ItemStack stack = event.getItem().getItem();
        if (stack.isEmpty()) return;
        if (!com.levanilla.rogue.core.service.FloorInstanceManager.canPickupProtectedDrop(event.getItem(), player)) {
            event.setCanceled(true);
            event.getItem().setPickUpDelay(20);
            return;
        }
        RoguePickupService.routePickup(player, event);
    }

    // MEDIC perk scales incoming healing while inside the rogue dimension.
    @SubscribeEvent
    public static void onLivingHeal(net.minecraftforge.event.entity.living.LivingHealEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.level().dimension() == ROGUE_DIM) {
            float medicBonus = sumPerkEffect(player, "perk:MEDIC") / 100.0f;
            if (medicBonus > 0) {
                event.setAmount(event.getAmount() * (1.0f + medicBonus));
            }
        }
    }


    private static ServerPlayer findAttacker(LivingHurtEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer p) return p;
        if (event.getSource().getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile proj) {
            if (proj.getOwner() instanceof ServerPlayer p) return p;
        }
        if (event.getEntity().level() instanceof ServerLevel sl) {
            ServerPlayer owner = RunManager.findPlayerForDungeonPosition(sl, event.getEntity().getX(), event.getEntity().getZ());
            if (owner != null) return owner;
            var nearest = sl.getNearestPlayer(event.getEntity(), 32);
            if (nearest instanceof ServerPlayer p) return p;
        }
        return null;
    }

    private static boolean isMeleeWeapon(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        net.minecraft.nbt.CompoundTag tag = stack.getTag();
        return (tag != null && tag.contains("MeleeWeaponId"))
            || com.levanilla.rogue.core.registry.LrTacticalRegistry.isMeleeWeapon(stack);
    }

    private static boolean isMeleeCriticalState(ServerPlayer player) {
        return player.fallDistance > 0.0F
            && !player.onGround()
            && !player.onClimbable()
            && !player.isInWater()
            && !player.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS)
            && !player.isPassenger();
    }

    private static ServerPlayer findKiller(LivingDeathEvent event, ServerLevel level) {
        if (event.getSource().getEntity() instanceof ServerPlayer p) return p;
        if (event.getSource().getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile proj) {
            if (proj.getOwner() instanceof ServerPlayer p) return p;
        }
        return null;
    }

    public static float getResistanceEffect(ServerPlayer player) {
        return sumPerkEffect(player, "perk:RESISTANCE");
    }

    public static float getSpecialResistanceMultiplier(ServerPlayer player) {
        float resist = Math.min(getResistanceEffect(player) / 100.0f, GameConstants.SPECIAL_RESISTANCE_MAX);
        return 1.0f - Math.max(0.0f, resist);
    }

    public static int reduceNegativeEffectDuration(ServerPlayer player, int ticks) {
        if (ticks <= 0) return ticks;
        return Math.max(20, Math.round(ticks * getSpecialResistanceMultiplier(player)));
    }

    private static boolean isSpecialResistanceSource(DamageSource source) {
        if (source == null) return false;
        String msgId = source.getMsgId().toLowerCase(Locale.ROOT);
        return msgId.contains("explosion")
            || msgId.contains("fireworks")
            || msgId.contains("poison")
            || msgId.contains("wither")
            || msgId.contains("fire")
            || msgId.contains("burn")
            || msgId.contains("lava")
            || msgId.contains("hotfloor")
            || msgId.contains("infire")
            || msgId.contains("onfire")
            || msgId.contains("magic")
            || msgId.contains("indirectmagic")
            || msgId.contains("wither")
            || msgId.contains("poison")
            || msgId.contains("freeze")
            || msgId.contains("sonic");
    }

    private static float sumPerkEffect(ServerPlayer player, String perkPrefix) {
        return PerkDefinition.sumEffect(player, perkPrefix);
    }

    // Selects one roguelike drop category, then delegates category-specific item creation.
    private static net.minecraft.world.item.ItemStack selectRoguelikeDrop(net.minecraft.util.RandomSource random, int floor) {
        float diffMul = DifficultyManager.getDropMultiplier();
        int roll = random.nextInt(100);
        int rareBonus = Math.min(floor, 20) + (int)(diffMul * 5);

        int wProb = GameConstants.DROP_WEAPON_BASE;
        int aProb = GameConstants.DROP_ATTACHMENT_BASE;

        if (roll < wProb) {
            return generateWeaponDrop(random, floor);
        }
        roll -= wProb;
        if (roll < aProb) {
            return generateAttachmentDrop(random, floor);
        }
        roll -= aProb;

        if (roll < GameConstants.DROP_RARE_BASE + rareBonus / 2) {
            return selectRareUtilityDrop(random, floor);
        } else if (roll < GameConstants.DROP_UNCOMMON_BASE + rareBonus) {
            return selectUncommonUtilityDrop(random, floor);
        } else if (roll < GameConstants.DROP_RATION_BASE + (int)(diffMul * 3)) {
            return random.nextFloat() < 0.20F
                ? com.levanilla.rogue.core.service.RogueItemFactory.createRecoveryItem("rogue:bandage")
                : com.levanilla.rogue.core.service.RogueItemFactory.createRecoveryItem("rogue:field_ration");
        } else if (roll < GameConstants.DROP_GOLD_CACHE_BASE + (int)(diffMul * 5)) {
            var stack = new net.minecraft.world.item.ItemStack(Items.RAW_GOLD, 1);
            applyRogueLore(stack,
                net.minecraft.network.chat.Component.translatable("item.tac_rogue.gold_cache"),
                39004,
                net.minecraft.network.chat.Component.translatable("item.tac_rogue.gold_cache.lore.0"),
                net.minecraft.network.chat.Component.translatable("item.tac_rogue.gold_cache.lore.1", GameConstants.GOLD_CACHE_VALUE),
                net.minecraft.network.chat.Component.translatable("item.tac_rogue.rarity.common"));
            return stack;
        } else if (roll < GameConstants.DROP_STAMINA_BASE + rareBonus / 2) {
            return com.levanilla.rogue.core.service.RogueItemFactory.createRecoveryItem("rogue:stamina_shot");
        } else {
            var stack = new net.minecraft.world.item.ItemStack(Items.RAW_IRON, 1 + random.nextInt(3));
            applyRogueLore(stack,
                net.minecraft.network.chat.Component.translatable("item.tac_rogue.scrap_metal"),
                39006,
                net.minecraft.network.chat.Component.translatable("item.tac_rogue.scrap_metal.lore.0"),
                net.minecraft.network.chat.Component.translatable("item.tac_rogue.scrap_metal.lore.1", GameConstants.SCRAP_SELL_VALUE),
                net.minecraft.network.chat.Component.translatable("item.tac_rogue.rarity.common"));
            return stack;
        }
    }

    private static net.minecraft.world.item.ItemStack selectUncommonUtilityDrop(net.minecraft.util.RandomSource random, int floor) {
        float armorPlateChance = floor >= 3 ? 0.32F : 0.18F;
        float roll = random.nextFloat();
        if (roll < armorPlateChance) {
            return com.levanilla.rogue.core.service.RogueItemFactory.createRecoveryItem("rogue:armor_plate");
        }
        if (floor >= 8 && roll < armorPlateChance + 0.08F) {
            return com.levanilla.rogue.core.service.RogueItemFactory.createRecoveryItem("rogue:emp_device");
        }
        return com.levanilla.rogue.core.service.GearService.createMedkitStack(1);
    }

    private static net.minecraft.world.item.ItemStack selectRareUtilityDrop(net.minecraft.util.RandomSource random, int floor) {
        if (floor >= 7 && random.nextFloat() < 0.28F) {
            return com.levanilla.rogue.core.service.RogueItemFactory.createRecoveryItem("rogue:adrenaline");
        }
        if (floor >= 5 && random.nextFloat() < 0.36F) {
            return com.levanilla.rogue.core.service.RogueItemFactory.createRecoveryItem("rogue:armor_plate");
        }
        var stack = new net.minecraft.world.item.ItemStack(Items.GOLDEN_APPLE);
        applyRogueLore(stack,
            net.minecraft.network.chat.Component.translatable("item.tac_rogue.emergency_ration"),
            39001,
            net.minecraft.network.chat.Component.translatable("item.tac_rogue.emergency_ration.lore.0"),
            net.minecraft.network.chat.Component.translatable("item.tac_rogue.emergency_ration.lore.1"),
            net.minecraft.network.chat.Component.translatable("item.tac_rogue.rarity.rare"));
        stack.enchant(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING, 1);
        stack.getOrCreateTag().putInt("HideFlags", 1);
        return stack;
    }

    private static void applyRogueLore(net.minecraft.world.item.ItemStack stack, net.minecraft.network.chat.Component name,
                                       int customModelData, net.minecraft.network.chat.Component... lore) {
        stack.setHoverName(name);
        net.minecraft.nbt.CompoundTag display = stack.getOrCreateTagElement("display");
        net.minecraft.nbt.ListTag loreList = new net.minecraft.nbt.ListTag();
        for (net.minecraft.network.chat.Component line : lore) {
            loreList.add(net.minecraft.nbt.StringTag.valueOf(
                net.minecraft.network.chat.Component.Serializer.toJson(line)));
        }
        display.put("Lore", loreList);
        stack.getOrCreateTag().putBoolean("rogue_item", true);
        stack.getOrCreateTag().putInt("CustomModelData", customModelData);
    }

    private static net.minecraft.world.item.ItemStack generateWeaponDrop(net.minecraft.util.RandomSource random, int floor) {
        java.util.List<com.levanilla.rogue.core.registry.ShopCatalog.ShopItem> weapons = getWeaponDropCandidates(floor);
        if (weapons.isEmpty()) return new net.minecraft.world.item.ItemStack(Items.AIR);

        var chosen = weapons.get(random.nextInt(weapons.size()));
        WeaponRarity.Rarity rarity = WeaponRarity.rollRarity(floor, random);
        net.minecraft.world.item.ItemStack stack = chosen.category == com.levanilla.rogue.core.registry.ShopCatalog.Category.MELEE
            ? com.levanilla.rogue.core.service.RogueItemFactory.createMeleeStack(chosen.id, rarity)
            : com.levanilla.rogue.core.service.ShopService.createItemStack(null, chosen.id);

        if (stack.isEmpty()) return stack;

        // Weapon drops receive rarity and may roll attachments based on floor depth.
        if (chosen.category != com.levanilla.rogue.core.registry.ShopCatalog.Category.MELEE) {
            WeaponRarity.applyRarity(stack, rarity);
        }

        if (stack.hasTag() && stack.getTag().contains("GunId")) {
            net.minecraft.nbt.CompoundTag attTag = new net.minecraft.nbt.CompoundTag();
            
            float attachmentChance = 0.2f + Math.min(0.5f, floor * 0.005f);
            
            if (random.nextFloat() < attachmentChance + 0.1f) {
                String id = getRandomAttachmentOfType("sight", random);
                if (id != null) attTag.putString("Sight", id);
            }
            if (random.nextFloat() < attachmentChance) {
                String id = getRandomAttachmentOfType("muzzle", random);
                if (id != null) attTag.putString("Muzzle", id);
            }
            if (random.nextFloat() < attachmentChance) {
                String id = getRandomAttachmentOfType("stock", random);
                if (id != null) attTag.putString("Stock", id);
            }
            if (random.nextFloat() < attachmentChance * 0.6f) {
                String id = getRandomAttachmentOfType("extended_mag", random);
                if (id != null) attTag.putString("ExtendedMag", id);
            }
            if (!attTag.isEmpty()) {
                stack.getTag().put("Attachments", attTag);
            }
        }
        return stack;
    }

    private static java.util.List<com.levanilla.rogue.core.registry.ShopCatalog.ShopItem> getWeaponDropCandidates(int floor) {
        int band = floor <= 5 ? 5 : floor <= 15 ? 15 : floor <= 30 ? 30 : floor <= 50 ? 50 : 100;
        return weaponDropCandidatesByBand.computeIfAbsent(band, ignored -> {
            java.util.List<com.levanilla.rogue.core.registry.ShopCatalog.ShopItem> weapons = new java.util.ArrayList<>();
            for (var item : TacZRegistryHelper.getAllShopItems()) {
                if (item.category.isWeapon() && isWeaponAllowedForFloorBand(item.category, band)) {
                    weapons.add(item);
                }
            }
            return java.util.List.copyOf(weapons);
        });
    }

    private static boolean isWeaponAllowedForFloorBand(
            com.levanilla.rogue.core.registry.ShopCatalog.Category category, int band) {
        if (band <= 5) {
            return category == com.levanilla.rogue.core.registry.ShopCatalog.Category.PISTOL
                || category == com.levanilla.rogue.core.registry.ShopCatalog.Category.SMG
                || category == com.levanilla.rogue.core.registry.ShopCatalog.Category.MELEE;
        }
        if (band <= 15) {
            return category == com.levanilla.rogue.core.registry.ShopCatalog.Category.PISTOL
                || category == com.levanilla.rogue.core.registry.ShopCatalog.Category.SMG
                || category == com.levanilla.rogue.core.registry.ShopCatalog.Category.MELEE
                || category == com.levanilla.rogue.core.registry.ShopCatalog.Category.SHOTGUN;
        }
        if (band <= 30) {
            return category == com.levanilla.rogue.core.registry.ShopCatalog.Category.PISTOL
                || category == com.levanilla.rogue.core.registry.ShopCatalog.Category.SMG
                || category == com.levanilla.rogue.core.registry.ShopCatalog.Category.MELEE
                || category == com.levanilla.rogue.core.registry.ShopCatalog.Category.SHOTGUN
                || category == com.levanilla.rogue.core.registry.ShopCatalog.Category.RIFLE;
        }
        if (band <= 50) {
            return category == com.levanilla.rogue.core.registry.ShopCatalog.Category.PISTOL
                || category == com.levanilla.rogue.core.registry.ShopCatalog.Category.SMG
                || category == com.levanilla.rogue.core.registry.ShopCatalog.Category.MELEE
                || category == com.levanilla.rogue.core.registry.ShopCatalog.Category.SHOTGUN
                || category == com.levanilla.rogue.core.registry.ShopCatalog.Category.RIFLE
                || category == com.levanilla.rogue.core.registry.ShopCatalog.Category.LMG;
        }
        return category.isWeapon();
    }

    private static String getRandomAttachmentOfType(String targetType, net.minecraft.util.RandomSource random) {
        java.util.List<String> valid = attachmentIdsBySlot.computeIfAbsent(targetType, CombatEventHandler::buildAttachmentIdsForSlot);
        if (valid.isEmpty()) return null;
        return valid.get(random.nextInt(valid.size()));
    }

    private static java.util.List<String> buildAttachmentIdsForSlot(String targetType) {
        java.util.List<String> valid = new java.util.ArrayList<>();
        for (String id : TacZRegistryHelper.getAllAttachmentIds()) {
            String type = com.levanilla.rogue.core.registry.AttachmentDatabase.getSlotType(id);
            if (type == null) type = com.levanilla.rogue.core.registry.AttachmentDatabase.guessSlotType(id);
            if (targetType.equals(type)) {
                valid.add(id);
            }
        }
        return java.util.List.copyOf(valid);
    }

    private static void cleanupPendingGunContexts() {
        long now = System.currentTimeMillis();
        pendingGunContext.entrySet().removeIf(entry -> now - entry.getValue().createdAtMs > PENDING_GUN_CONTEXT_TTL_MS);
    }

    private static net.minecraft.world.item.ItemStack selectBossWeaponDrop(net.minecraft.util.RandomSource random, int floor) {
        return generateWeaponDrop(random, floor + 10);
    }

    private static net.minecraft.world.item.ItemStack generateAttachmentDrop(net.minecraft.util.RandomSource random, int floor) {
        java.util.List<String> allAtt = TacZRegistryHelper.getAllAttachmentIds();
        if (allAtt.isEmpty()) return new net.minecraft.world.item.ItemStack(Items.AIR);
        String chosen = allAtt.get(random.nextInt(allAtt.size()));
        return com.levanilla.rogue.core.service.ShopService.createAttachmentStack(chosen);
    }


    public static void clearMemory() {
        lastDamageTickMap.clear();
        lastKillTick.clear();
        lastStealthHintTarget.clear();
        pendingGunContext.clear();
        weaponDropCandidatesByBand.clear();
        attachmentIdsBySlot.clear();
    }
}
