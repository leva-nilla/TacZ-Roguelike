package com.levanilla.rogue.core.event;

import com.levanilla.rogue.core.*;
import com.levanilla.rogue.core.service.RoguePickupService;
import com.levanilla.rogue.networking.TacRogueNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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

    // TacZ damage context is registered by TacZEventHandler and consumed by LivingHurtEvent.
    public static class GunDamageContext {
        public final boolean isHeadShot;
        public final boolean isShotgun;
        public final boolean isPerkCrit;
        public final net.minecraft.world.phys.Vec3 impactPos;

        public GunDamageContext(boolean isHeadShot, boolean isShotgun, boolean isPerkCrit, net.minecraft.world.phys.Vec3 impactPos) {
            this.isHeadShot = isHeadShot;
            this.isShotgun = isShotgun;
            this.isPerkCrit = isPerkCrit;
            this.impactPos = impactPos;
        }
    }
    private static final java.util.Map<Integer, GunDamageContext> pendingGunContext = new java.util.concurrent.ConcurrentHashMap<>();

    public static void registerGunDamageContext(int entityId, boolean isHeadShot, boolean isShotgun, boolean isPerkCrit, net.minecraft.world.phys.Vec3 impactPos) {
        pendingGunContext.put(entityId, new GunDamageContext(isHeadShot, isShotgun, isPerkCrit, impactPos));
    }

    public static long getLastDamageTick(java.util.UUID playerId) {
        return lastDamageTickMap.getOrDefault(playerId, 0L);
    }

    public static void markPlayerDamaged(ServerPlayer player) {
        lastDamageTickMap.put(player.getUUID(), player.level().getGameTime());
    }

    // Stealth target filtering: mobs do not acquire players outside their forward cone unless recently alerted.
    @SubscribeEvent
    public static void onLivingChangeTarget(net.minecraftforge.event.entity.living.LivingChangeTargetEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (event.getNewTarget() instanceof ServerPlayer player && event.getEntity() instanceof Mob mob) {
            if (mob.getTags().contains("rogue:boss")) return;

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

        if ((event.getEntity().level().dimension() == ROGUE_DIM || event.getEntity().level().dimension() == LOBBY_DIM)
                && event.getSource().is(DamageTypes.STARVE)) {
            event.setCanceled(true);
            return;
        }

        if (event.getEntity() instanceof ServerPlayer adsFatiguePlayer
                && StaminaManager.isAdsExhaustDamage(adsFatiguePlayer)) {
            lastDamageTickMap.put(adsFatiguePlayer.getUUID(), adsFatiguePlayer.level().getGameTime());
            return;
        }

        if (event.getSource().getDirectEntity() instanceof ServerPlayer srcPlayer) {
            net.minecraft.world.item.ItemStack hand = srcPlayer.getMainHandItem();
            net.minecraft.nbt.CompoundTag handTag = hand.getTag();
            if (handTag != null && handTag.contains("MeleeWeaponId")) {
                float dmgMul = 1.0f;
                if ("lrtactical:dagger".equals(handTag.getString("MeleeWeaponId"))) {
                    dmgMul *= GameConstants.DAGGER_DAMAGE_MULT;
                }
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
            mob.getPersistentData().putLong("LastAlertTick", mob.level().getGameTime());
            
            if (event.getSource().getDirectEntity() instanceof ServerPlayer srcPlayer && event.getSource().getDirectEntity() == event.getSource().getEntity()) {
                if (mob.getTarget() == null) {
                    double dist = mob.distanceTo(srcPlayer);
                    if (dist <= 2.8) {
                        net.minecraft.world.phys.Vec3 lookVec = mob.getViewVector(1.0F).normalize();
                        net.minecraft.world.phys.Vec3 toPlayer = srcPlayer.position().subtract(mob.position()).normalize();
                        double dotProduct = lookVec.dot(toPlayer);
                        if (dotProduct < -0.5) {
                            event.setAmount((event.getAmount() * 10.0f) + 50.0f);
                            srcPlayer.displayClientMessage(net.minecraft.network.chat.Component.literal("\u00A7c\u00A7l* STEALTH TAKEDOWN *"), true);
                            srcPlayer.level().playSound(null, mob.blockPosition(), net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_CRIT, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.8f);
                            com.levanilla.rogue.core.QuestManager.advanceQuest(srcPlayer, com.levanilla.rogue.core.QuestManager.QuestType.STEALTH_KILL, 1);
                        }
                    }
                }
            }
        }

        if (event.getEntity() instanceof ServerPlayer damagedPlayer) {
            // DODGE stacks multiplicatively through player perk tags and caps at 75%.
            float hitChance = 1.0f;
            for (String tag : damagedPlayer.getTags()) {
                if (tag.startsWith("perk:DODGE")) {
                    PerkDefinition perk = PerkDefinition.fromTag(tag);
                    hitChance *= (1.0f - perk.calculateEffect() / 100.0f);
                }
            }
            float dodgeChance = Math.min(0.60f, 1.0f - hitChance);
            if (dodgeChance > 0 && damagedPlayer.getRandom().nextFloat() < dodgeChance) {
                event.setCanceled(true);
                damagedPlayer.displayClientMessage(net.minecraft.network.chat.Component.literal("\u00A7a* DODGE! *"), true);
                return;
            }

            int volatilePerks = 0;
            for (String tag : damagedPlayer.getTags()) {
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
            lastDamageTickMap.put(damagedPlayer.getUUID(), damagedPlayer.level().getGameTime());
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

                float damageBonus = sumPerkEffect(attacker, "perk:DAMAGE") / 100.0f;
                damage *= (1.0f + damageBonus);

                float rawCritChance = sumPerkEffect(attacker, "perk:FORTUNE") / 100.0f;
                float critChance = Math.min(0.70f, rawCritChance);
                float overCritBonus = Math.min(0.35f, Math.max(0, rawCritChance - 0.70f) * 0.25f);
                boolean perkCrit = critChance > 0 && attacker.getRandom().nextFloat() < critChance;
                if (perkCrit) damage *= (GameConstants.CRITICAL_DAMAGE_MULT + overCritBonus);

                float explosiveBonus = sumPerkEffect(attacker, "perk:EXPLOSIVE") / 100.0f;
                if (explosiveBonus > 0) {
                    String dmgType = event.getSource().getMsgId();
                    if (dmgType.contains("explosion") || dmgType.contains("fireworks")) {
                        damage *= (1.0f + explosiveBonus);
                    }
                }

                boolean isSneaking = attacker.isShiftKeyDown();
                boolean isCrawling = attacker.isSwimming();
                if (isCrawling) damage *= GameConstants.CRAWL_DAMAGE_MULT;
                else if (isSneaking) damage *= GameConstants.SNEAK_DAMAGE_MULT;

                event.setAmount(damage);

                double x = event.getEntity().getX();
                double y = event.getEntity().getY() + event.getEntity().getBbHeight();
                double z = event.getEntity().getZ();
                boolean isCritical = perkCrit || damage > GameConstants.CRITICAL_DAMAGE_THRESHOLD;
                String data = String.format(java.util.Locale.US, "%.1f:%.2f:%.2f:%.2f:%b:%b:%b", damage, x, y, z, isCritical, false, false);
                TacRogueNetworking.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> attacker),
                    new com.levanilla.rogue.networking.SyncDataMessage("dmg:" + data));
            } else {
                GunDamageContext ctx = pendingGunContext.remove(event.getEntity().getId());
                if (ctx != null) {
                    float finalDamage = event.getAmount();
                    net.minecraft.world.phys.Vec3 impact = ctx.impactPos != null
                        ? ctx.impactPos
                        : event.getEntity().position().add(0.0D, event.getEntity().getBbHeight() * 0.72D, 0.0D);
                    double x = impact.x;
                    double y = impact.y;
                    double z = impact.z;
                    boolean isCritical = ctx.isPerkCrit;
                    String data = String.format(java.util.Locale.US, "%.1f:%.2f:%.2f:%.2f:%b:%b:%b",
                        finalDamage, x, y, z, isCritical, ctx.isHeadShot, ctx.isShotgun);
                    TacRogueNetworking.CHANNEL.send(
                        net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> attacker),
                        new com.levanilla.rogue.networking.SyncDataMessage("dmg:" + data));
                }
            }
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
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.tac_rogue.death_penalty", penalty));
            }

            DifficultyManager.Difficulty diff = DifficultyManager.getDifficulty();
            int perksToLose = switch (diff) {
                case HARD -> 1;
                case EXTREME -> 3;
                default -> 0;
            };
            if (perksToLose > 0) {
                java.util.List<String> perkTags = new java.util.ArrayList<>();
                for (String tag : player.getTags()) {
                    if (tag.startsWith("perk:")) perkTags.add(tag);
                }
                java.util.Collections.shuffle(perkTags);
                int removed = 0;
                for (String tag : perkTags) {
                    if (removed >= perksToLose) break;
                    player.removeTag(tag);
                    removed++;
                }
                if (removed > 0) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                        "message.tac_rogue.perk_lost", removed));
                }
            }

            if (diff == DifficultyManager.Difficulty.EXTREME) {
                int[] protectedSlots = {
                    GameConstants.SLOT_GUN_START, GameConstants.SLOT_GUN_END,
                    GameConstants.SLOT_MELEE,
                    GameConstants.SLOT_AMMO_GUN1_START, GameConstants.SLOT_AMMO_GUN1_END,
                    GameConstants.SLOT_AMMO_GUN2_START, GameConstants.SLOT_AMMO_GUN2_END
                };
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    boolean isProtected = false;
                    for (int pSlot : protectedSlots) {
                        if (i == pSlot) {
                            isProtected = true;
                            break;
                        }
                    }
                    if (!isProtected) {
                        net.minecraft.world.item.ItemStack stack = player.getInventory().getItem(i);
                        if (!stack.isEmpty()) {
                            String regName = "";
                            net.minecraft.resources.ResourceLocation rl = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
                            if (rl != null) regName = rl.toString();
                            if (!regName.startsWith("tacz:")) {
                                player.getInventory().setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
                            }
                        }
                    }
                }
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                    "message.tac_rogue.items_lost"));
            }

            PlayerRunData data = RunManager.getData(player);
            if (data.isRunActive() && player.level().dimension() == ROGUE_DIM) {
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
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.tac_rogue.mission_failed"));
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

                    int reward = PriceManager.getKillReward(RunManager.getData(killer).getCurrentFloor());
                    float goldBonus = sumPerkEffect(killer, "perk:GOLD_RUSH") / 100.0f;
                    reward = (int)(reward * (1.0f + goldBonus));
                    CurrencyManager.addGold(killer, (int)(reward * DifficultyManager.getGoldMultiplier()));
                    RunManager.syncPlayer(killer);

                    float totalVampHeal = sumPerkEffect(killer, "perk:VAMPIRE") / 10.0f;
                    if (totalVampHeal > 0) killer.heal(totalVampHeal);

                    float bloodlust = sumPerkEffect(killer, "perk:BLOODLUST") / 10.0f;
                    if (bloodlust > 0) {
                        killer.heal(bloodlust * 0.5f);
                        killer.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 60, 0, false, false));
                    }

                    QuestManager.advanceQuest(killer, QuestManager.QuestType.KILL_COUNT, 1);
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
            applyRogueLore(stack, "\u00a7e* GOLD CACHE", new String[]{
                "\u00a77A small cache of gold.",
                "\u00a77Right click to gain \u00a7e250 G\u00a77.",
                "\u00a78\u00a7oRarity: \u00a77COMMON"
            }, 39004);
            return stack;
        } else if (roll < GameConstants.DROP_STAMINA_BASE + rareBonus / 2) {
            return com.levanilla.rogue.core.service.RogueItemFactory.createRecoveryItem("rogue:stamina_shot");
        } else {
            var stack = new net.minecraft.world.item.ItemStack(Items.RAW_IRON, 1 + random.nextInt(3));
            applyRogueLore(stack, "\u00a78* SCRAP METAL", new String[]{
                "\u00a77Usable scrap material.",
                "\u00a77Right click to gain \u00a7e50 G\u00a77.",
                "\u00a78\u00a7oRarity: \u00a77COMMON"
            }, 39006);
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
        applyRogueLore(stack, "\u00a76* EMERGENCY RATION", new String[]{
            "\u00a77Restores health in an emergency.",
            "\u00a77Right click to recover HP.",
            "\u00a78\u00a7oRarity: \u00a7eRARE"
        }, 39001);
        stack.enchant(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING, 1);
        stack.getOrCreateTag().putInt("HideFlags", 1);
        return stack;
    }

    private static void applyRogueLore(net.minecraft.world.item.ItemStack stack, String name, String[] lore, int customModelData) {
        stack.setHoverName(net.minecraft.network.chat.Component.literal(name));
        net.minecraft.nbt.CompoundTag display = stack.getOrCreateTagElement("display");
        net.minecraft.nbt.ListTag loreList = new net.minecraft.nbt.ListTag();
        for (String line : lore) {
            loreList.add(net.minecraft.nbt.StringTag.valueOf(
                net.minecraft.network.chat.Component.Serializer.toJson(
                    net.minecraft.network.chat.Component.literal(line))));
        }
        display.put("Lore", loreList);
        stack.getOrCreateTag().putBoolean("rogue_item", true);
        stack.getOrCreateTag().putInt("CustomModelData", customModelData);
    }

    private static net.minecraft.world.item.ItemStack generateWeaponDrop(net.minecraft.util.RandomSource random, int floor) {
        java.util.List<com.levanilla.rogue.core.registry.ShopCatalog.ShopItem> weapons = new java.util.ArrayList<>();
        for (var item : TacZRegistryHelper.getAllShopItems()) {
            if (item.category.isWeapon()) {
                boolean allow = false;
                if (floor <= 5) { // 1~5
                    allow = (item.category == com.levanilla.rogue.core.registry.ShopCatalog.Category.PISTOL ||
                             item.category == com.levanilla.rogue.core.registry.ShopCatalog.Category.SMG ||
                             item.category == com.levanilla.rogue.core.registry.ShopCatalog.Category.MELEE);
                } else if (floor <= 15) {
                    allow = (item.category == com.levanilla.rogue.core.registry.ShopCatalog.Category.PISTOL ||
                             item.category == com.levanilla.rogue.core.registry.ShopCatalog.Category.SMG ||
                             item.category == com.levanilla.rogue.core.registry.ShopCatalog.Category.MELEE ||
                             item.category == com.levanilla.rogue.core.registry.ShopCatalog.Category.SHOTGUN);
                } else if (floor <= 30) {
                    allow = (item.category == com.levanilla.rogue.core.registry.ShopCatalog.Category.PISTOL ||
                             item.category == com.levanilla.rogue.core.registry.ShopCatalog.Category.SMG ||
                             item.category == com.levanilla.rogue.core.registry.ShopCatalog.Category.MELEE ||
                             item.category == com.levanilla.rogue.core.registry.ShopCatalog.Category.SHOTGUN ||
                             item.category == com.levanilla.rogue.core.registry.ShopCatalog.Category.RIFLE);
                } else if (floor <= 50) {
                    allow = (item.category == com.levanilla.rogue.core.registry.ShopCatalog.Category.PISTOL ||
                             item.category == com.levanilla.rogue.core.registry.ShopCatalog.Category.SMG ||
                             item.category == com.levanilla.rogue.core.registry.ShopCatalog.Category.MELEE ||
                             item.category == com.levanilla.rogue.core.registry.ShopCatalog.Category.SHOTGUN ||
                             item.category == com.levanilla.rogue.core.registry.ShopCatalog.Category.RIFLE ||
                             item.category == com.levanilla.rogue.core.registry.ShopCatalog.Category.LMG);
                } else {
                    allow = true;
                }
                if (allow) weapons.add(item);
            }
        }
        if (weapons.isEmpty()) return new net.minecraft.world.item.ItemStack(Items.AIR);

        var chosen = weapons.get(random.nextInt(weapons.size()));
        net.minecraft.world.item.ItemStack stack = com.levanilla.rogue.core.service.ShopService.createItemStack(null, chosen.id);

        if (stack.isEmpty()) return stack;

        // Weapon drops receive rarity and may roll attachments based on floor depth.
        WeaponRarity.Rarity rarity = WeaponRarity.rollRarity(floor, random);
        WeaponRarity.applyRarity(stack, rarity);

        if (stack.hasTag() && stack.getTag().contains("GunId")) {
            java.util.List<String> allAtt = TacZRegistryHelper.getAllAttachmentIds();
            net.minecraft.nbt.CompoundTag attTag = new net.minecraft.nbt.CompoundTag();
            
            float attachmentChance = 0.2f + Math.min(0.5f, floor * 0.005f);
            
            if (random.nextFloat() < attachmentChance + 0.1f) {
                String id = getRandomAttachmentOfType(allAtt, "sight", random);
                if (id != null) attTag.putString("Sight", id);
            }
            if (random.nextFloat() < attachmentChance) {
                String id = getRandomAttachmentOfType(allAtt, "muzzle", random);
                if (id != null) attTag.putString("Muzzle", id);
            }
            if (random.nextFloat() < attachmentChance) {
                String id = getRandomAttachmentOfType(allAtt, "stock", random);
                if (id != null) attTag.putString("Stock", id);
            }
            if (random.nextFloat() < attachmentChance * 0.6f) {
                String id = getRandomAttachmentOfType(allAtt, "extended_mag", random);
                if (id != null) attTag.putString("ExtendedMag", id);
            }
            if (!attTag.isEmpty()) {
                stack.getTag().put("Attachments", attTag);
            }
        }
        return stack;
    }

    private static String getRandomAttachmentOfType(java.util.List<String> allAtt, String targetType, net.minecraft.util.RandomSource random) {
        java.util.List<String> valid = new java.util.ArrayList<>();
        for (String id : allAtt) {
            String type = com.levanilla.rogue.core.registry.AttachmentDatabase.getSlotType(id);
            if (type == null) type = com.levanilla.rogue.core.registry.AttachmentDatabase.guessSlotType(id);
            if (targetType.equals(type)) {
                valid.add(id);
            }
        }
        if (valid.isEmpty()) return null;
        return valid.get(random.nextInt(valid.size()));
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
        pendingGunContext.clear();
    }
}
