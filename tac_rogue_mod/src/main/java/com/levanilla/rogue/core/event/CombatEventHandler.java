package com.levanilla.rogue.core.event;

import com.levanilla.rogue.core.*;
import com.levanilla.rogue.networking.TacRogueNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.ArrayList;

import static com.levanilla.rogue.core.CommonEventHandler.ROGUE_DIM;
import static com.levanilla.rogue.core.CommonEventHandler.LOBBY_DIM;

/**
 * 戦闘関連のイベント: 近接ダメージ計算、デスペナルティ、ドロップ。
 * v0.4.0: 銃撃ダメージ計算は {@link TacZEventHandler} に移行。
 * 本ハンドラは近接攻撃・爆発・その他のダメージソースを処理する。
 */
@Mod.EventBusSubscriber(modid = "tac_rogue")
public class CombatEventHandler {

    /** ダメージ後の回復クールダウン追跡 */
    private static final java.util.Map<java.util.UUID, Long> lastDamageTickMap = new java.util.concurrent.ConcurrentHashMap<>();

    /** プレイヤーのダメージクールダウンtickを取得 */
    public static long getLastDamageTick(java.util.UUID playerId) {
        return lastDamageTickMap.getOrDefault(playerId, 0L);
    }

    // ===== ステルス方向検知 =====

    @SubscribeEvent
    public static void onLivingChangeTarget(net.minecraftforge.event.entity.living.LivingChangeTargetEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (event.getNewTarget() instanceof ServerPlayer player && event.getEntity() instanceof Mob mob) {
            // すでにアラート状態（攻撃を受けた、または銃声を聞いた）なら180度以上見える
            long lastAlertTick = mob.getPersistentData().getLong("LastAlertTick");
            long currentTick = mob.level().getGameTime();
            if (currentTick - lastAlertTick < 100) return; // 5秒間は360度感知

            // プレイヤーがモブの視界（前方90度強、dotProduct > 0.3）に入っていないならターゲット変更をキャンセル
            net.minecraft.world.phys.Vec3 lookVec = mob.getViewVector(1.0F).normalize();
            net.minecraft.world.phys.Vec3 toPlayer = player.position().subtract(mob.position()).normalize();
            double dotProduct = lookVec.dot(toPlayer);
            if (dotProduct < 0.3) {
                event.setCanceled(true); // 横・後ろからの接近では気づかない
            }
        }
    }

    // ===== 環境制御 (地形破壊・EXP) =====

    @SubscribeEvent
    public static void onExplosionDetonate(net.minecraftforge.event.level.ExplosionEvent.Detonate event) {
        if (event.getLevel().dimension() == ROGUE_DIM || event.getLevel().dimension() == LOBBY_DIM) {
            // 爆発によるブロック破壊リストを空にして、地形は壊れないがダメージは通るようにする
            event.getAffectedBlocks().clear();
        }
    }

    @SubscribeEvent
    public static void onExperienceDrop(net.minecraftforge.event.entity.living.LivingExperienceDropEvent event) {
        if (event.getEntity().level().dimension() == ROGUE_DIM || event.getEntity().level().dimension() == LOBBY_DIM) {
            // 経験値ドロップをキャンセル
            event.setCanceled(true);
        }
    }

    // ===== ダメージ処理 =====

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;

        // ダガーのダメージ半減
        if (event.getSource().getEntity() instanceof ServerPlayer srcPlayer) {
            net.minecraft.world.item.ItemStack hand = srcPlayer.getMainHandItem();
            net.minecraft.nbt.CompoundTag handTag = hand.getTag();
            if (handTag != null && "lrtactical:dagger".equals(handTag.getString("MeleeWeaponId"))) {
                event.setAmount(event.getAmount() * GameConstants.DAGGER_DAMAGE_MULT);
            }
        }

        // ロビー内のNPC・merchant へのダメージ無効化
        if (event.getEntity().level().dimension() == LOBBY_DIM || event.getEntity().getTags().contains("rogue:merchant")) {
            event.setCanceled(true);
            return;
        }

        // 敵へのアグロ（ダメージを受けた時）
        if (event.getEntity() instanceof Mob mob) {
            mob.getPersistentData().putLong("LastAlertTick", mob.level().getGameTime());
            
            // ===== 真後ろからの近接ステルステイクダウン =====
            if (event.getSource().getDirectEntity() instanceof ServerPlayer srcPlayer && event.getSource().getDirectEntity() == event.getSource().getEntity()) {
                if (mob.getTarget() == null) { // バレていない
                    double dist = mob.distanceTo(srcPlayer);
                    if (dist <= 2.8) { // 2.8ブロック以内 (密着近接)
                        net.minecraft.world.phys.Vec3 lookVec = mob.getViewVector(1.0F).normalize();
                        net.minecraft.world.phys.Vec3 toPlayer = srcPlayer.position().subtract(mob.position()).normalize();
                        double dotProduct = lookVec.dot(toPlayer);
                        if (dotProduct < -0.5) { // 真後ろ (角度的に約120度〜180度の背後空間)
                            event.setAmount((event.getAmount() * 10.0f) + 50.0f); // 確殺レベルの大ダメージ (基礎ダメージx10 + 50)
                            srcPlayer.displayClientMessage(net.minecraft.network.chat.Component.literal("\u00A7c\u00A7l* STEALTH TAKEDOWN *"), true);
                            srcPlayer.level().playSound(null, mob.blockPosition(), net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_CRIT, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.8f);
                            com.levanilla.rogue.core.QuestManager.advanceQuest(srcPlayer, com.levanilla.rogue.core.QuestManager.QuestType.STEALTH_KILL, 1);
                        }
                    }
                }
            }
        }

        // VOLATILE: プレイヤーが被ダメ時、VOLATILEパーク×20%ダメージ増加
        if (event.getEntity() instanceof ServerPlayer damagedPlayer) {
            // === パーク: DODGE (回避) ===
            float dodgeChance = sumPerkEffect(damagedPlayer, "perk:DODGE") / 100.0f;
            if (dodgeChance > 0 && damagedPlayer.getRandom().nextFloat() < Math.min(0.5f, dodgeChance)) {
                event.setCanceled(true);
                damagedPlayer.displayClientMessage(net.minecraft.network.chat.Component.literal("\u00A7a* DODGE! *"), true);
                return;
            }

            lastDamageTickMap.put(damagedPlayer.getUUID(), damagedPlayer.level().getGameTime());
            int volatilePerks = 0;
            for (String tag : damagedPlayer.getTags()) {
                if (tag.startsWith("perk:") && tag.contains(":VOLATILE:")) {
                    volatilePerks++;
                }
            }
            if (volatilePerks > 0) {
                event.setAmount(event.getAmount() * (1.0f + volatilePerks * 0.2f));
            }
        }

        if (!(event.getEntity() instanceof Mob)) return;
        if (event.getEntity().level().dimension() != ROGUE_DIM) return;

        // ダメージソースからプレイヤーを特定（TacZ 銃弾対応）
        ServerPlayer attacker = findAttacker(event);

        if (attacker != null) {
            // v0.4.0+: TacZ DamageType tag for bullet damage detection (正規 API)
            boolean isTaczGunDamage = event.getSource().is(
                com.tacz.guns.init.ModDamageTypes.BULLETS_TAG);

            if (!isTaczGunDamage) {
                // 非銃弾ダメージのパーク適用（近接・爆発等）
                float damage = event.getAmount();

                float damageBonus = sumPerkEffect(attacker, "perk:DAMAGE") / 100.0f;
                damage *= (1.0f + damageBonus);

                float critChance = sumPerkEffect(attacker, "perk:FORTUNE") / 100.0f;
                float overCritBonus = Math.max(0, critChance - 1.0f);
                boolean perkCrit = critChance > 0 && attacker.getRandom().nextFloat() < critChance;
                if (perkCrit) damage *= (GameConstants.CRITICAL_DAMAGE_MULT + overCritBonus);

                // パーク: EXPLOSIVE (爆発ダメージ強化)
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

                // ダメージ表記
                double x = event.getEntity().getX();
                double y = event.getEntity().getY() + event.getEntity().getBbHeight();
                double z = event.getEntity().getZ();
                boolean isCritical = perkCrit || damage > GameConstants.CRITICAL_DAMAGE_THRESHOLD;
                String data = String.format("%.1f:%.2f:%.2f:%.2f:%b", damage, x, y, z, isCritical);
                TacRogueNetworking.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.ALL.noArg(),
                    new com.levanilla.rogue.networking.SyncDataMessage("dmg:" + data));
            }
        }

        // パーク: RESISTANCE (被ダメージ軽減 — プレイヤーが攻撃を受けた場合)
        if (event.getEntity() instanceof ServerPlayer victim && !event.isCanceled()) {
            float resistBonus = sumPerkEffect(victim, "perk:RESISTANCE") / 100.0f;
            if (resistBonus > 0) {
                event.setAmount(event.getAmount() * (1.0f - Math.min(resistBonus, 0.75f)));
            }
            // ダメージクールダウン記録（自然回復の遅延用）
            lastDamageTickMap.put(victim.getUUID(), victim.level().getGameTime());
        }
    }

    // v0.4.0: 射撃音アラートは TacZEventHandler.alertNearbyMobsByGunshot() に移行済み

    // ===== 死亡処理 =====

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        // プレイヤー死亡: デスペナルティ
        if (event.getEntity() instanceof ServerPlayer player && !player.level().isClientSide) {
            int currentGold = CurrencyManager.getGold(player);
            int penalty = (int)(currentGold * DifficultyManager.getDeathPenaltyRate());
            if (penalty > 0) {
                CurrencyManager.consumeGold(player, penalty);
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.tac_rogue.death_penalty", penalty));
            }

            // === HARD / EXTREME 追加ペナルティ: パーク喪失 ===
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

            // === EXTREME 追加ペナルティ: 携帯アイテム全喪失 (武器・弾薬以外) ===
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
                            // tacZの銃や弾薬は保護
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

            // ローグライク走行中の死亡処理
            PlayerRunData data = RunManager.getData(player);
            if (data.isRunActive() && player.level().dimension() == ROGUE_DIM) {
                event.setCanceled(true);
                player.setHealth(player.getMaxHealth());
                data.setRunActive(false);

                ServerLevel lobbyLevel = player.server.getLevel(LOBBY_DIM);
                if (lobbyLevel != null) {
                    player.teleportTo(lobbyLevel,
                        GameConstants.LOBBY_X, GameConstants.LOBBY_Y, GameConstants.LOBBY_Z, 0, 0);
                    com.levanilla.rogue.world.LobbyGenerator.buildLobby(lobbyLevel, new BlockPos(0, 201, 0));
                }
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.tac_rogue.mission_failed"));
                RunManager.syncPlayer(player);
            }
        }

        // 敵殺害時の報酬処理
        if (event.getEntity().level() instanceof ServerLevel level && level.dimension() == ROGUE_DIM) {
            ServerPlayer killer = findKiller(event, level);

            if (killer != null) {
                // === 二重支給防止: TacZEventHandler で既に報酬処理されたか確認 ===
                // Primary: TacZEventHandler.onEntityKillByGun が処理済みなら UUID が記録されている
                boolean alreadyRewardedByTacz = TacZEventHandler.wasGunKill(event.getEntity().getUUID());
                // Secondary: DamageType tag による確認 (フォールバック)
                boolean isBulletDamage = event.getSource().is(
                    com.tacz.guns.init.ModDamageTypes.BULLETS_TAG);

                if (!alreadyRewardedByTacz && !isBulletDamage) {
                    // 非銃弾キル（近接・爆発・落下等）: キル報酬を付与
                    int reward = PriceManager.getKillReward(RunManager.getData(killer).getCurrentFloor());
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
                }

                // ドロップ判定（銃弾キル・非銃弾キル共通 — TacZ イベントにドロップ機能がないため）
                float dropChance = GameConstants.DROP_BASE_CHANCE * DifficultyManager.getDropMultiplier();
                dropChance *= (1.0f + sumPerkEffect(killer, "perk:SCAVENGER") / 100.0f);

                if (level.random.nextFloat() < dropChance) {
                    net.minecraft.world.item.ItemStack dropItem = selectRoguelikeDrop(level.random, RunManager.getData(killer).getCurrentFloor());
                    level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level,
                        event.getEntity().getX(), event.getEntity().getY(), event.getEntity().getZ(), dropItem));

                    String dropName = dropItem.getHoverName().getString().replaceAll("§.", "");
                    String dropData = String.format("%s:%.2f:%.2f:%.2f", dropName,
                        event.getEntity().getX(), event.getEntity().getY() + 1.0, event.getEntity().getZ());
                    TacRogueNetworking.CHANNEL.send(
                        net.minecraftforge.network.PacketDistributor.ALL.noArg(),
                        new com.levanilla.rogue.networking.SyncDataMessage("drop:" + dropData));
                }
            }
        }
    }

    // ===== バニラドロップの禁止 =====

    @SubscribeEvent
    public static void onLivingDrops(net.minecraftforge.event.entity.living.LivingDropsEvent event) {
        if (!event.getEntity().level().isClientSide && event.getEntity().level().dimension() == ROGUE_DIM) {
            if (!(event.getEntity() instanceof net.minecraft.world.entity.player.Player)) {
                event.setCanceled(true);
            }
        }
    }

    // ===== スタッシュ自動回収 (拾得制御) =====
    
    @SubscribeEvent
    public static void onItemPickup(net.minecraftforge.event.entity.player.EntityItemPickupEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().dimension() != ROGUE_DIM) return;

        net.minecraft.world.item.ItemStack stack = event.getItem().getItem();
        if (stack.hasTag() && (stack.getTag().contains("GunId") || stack.getTag().contains("MeleeWeaponId"))) {
            boolean canPickup = false;
            if (stack.getTag().contains("GunId")) {
                if (player.getInventory().getItem(0).isEmpty() || player.getInventory().getItem(1).isEmpty()) {
                    canPickup = true;
                }
            } else if (stack.getTag().contains("MeleeWeaponId")) {
                if (player.getInventory().getItem(2).isEmpty()) {
                    canPickup = true;
                }
            }

            // 所持枠に空きがある場合は、バニラの拾得処理を行い手持ちに入れる
            if (canPickup) {
                return;
            }

            // 武器の所持枠が満杯の場合のみ、直接スタッシュに転送する
            com.levanilla.rogue.core.StashSavedData data = com.levanilla.rogue.core.StashSavedData.get(player.serverLevel());
            com.levanilla.rogue.core.StashSavedData.PlayerStash stash = data.getStash(player.getUUID());
            
            boolean stored = false;
            int maxSlots = stash.unlockedLines * 9;
            for (int i = 0; i < maxSlots; i++) {
                if (stash.getItem(i).isEmpty()) {
                    stash.setItem(i, stack.copy());
                    stored = true;
                    break;
                }
            }

            if (stored) {
                event.getItem().discard();
                event.setCanceled(true);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§b[STASH] §a" + stack.getHoverName().getString() + " §fをスタッシュに回収しました！"));
            } else {
                event.setCanceled(true); // スタッシュがいっぱいの場合は、拾わずに足元に残す
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("§cスタッシュが満杯で回収できません！"), true);
            }
        }
    }

    // ===== 回復処理 (MEDIC) =====
    
    @SubscribeEvent
    public static void onLivingHeal(net.minecraftforge.event.entity.living.LivingHealEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.level().dimension() == ROGUE_DIM) {
            float medicBonus = sumPerkEffect(player, "perk:MEDIC") / 100.0f;
            if (medicBonus > 0) {
                event.setAmount(event.getAmount() * (1.0f + medicBonus));
            }
        }
    }

    // ===== ヘルパー =====

    private static ServerPlayer findAttacker(LivingHurtEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer p) return p;
        if (event.getSource().getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile proj) {
            if (proj.getOwner() instanceof ServerPlayer p) return p;
        }
        if (event.getEntity().level() instanceof ServerLevel sl) {
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
        var nearest = level.getNearestPlayer(event.getEntity(), 32);
        if (nearest instanceof ServerPlayer p) return p;
        return null;
    }

    /** パーク効果の合算 */
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

    // ===== ドロップテーブル =====

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
            var stack = new net.minecraft.world.item.ItemStack(Items.GOLDEN_APPLE);
            applyRogueLore(stack, "§6✦ EMERGENCY RATION", new String[]{
                "§7即時 HP 回復アイテム", "§7金リンゴの効果で大幅 HP 回復",
                "§7右クリックで使用", "§8§oRarity: §e★★★ RARE"
            }, 39001);
            stack.enchant(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING, 1);
            stack.getOrCreateTag().putInt("HideFlags", 1);
            return stack;
        } else if (roll < GameConstants.DROP_UNCOMMON_BASE + rareBonus) {
            return com.levanilla.rogue.core.service.GearService.createMedkitStack(1);
        } else if (roll < GameConstants.DROP_RATION_BASE + (int)(diffMul * 3)) {
            var stack = new net.minecraft.world.item.ItemStack(Items.COOKED_BEEF, 2 + random.nextInt(3));
            applyRogueLore(stack, "§f✦ FIELD RATION", new String[]{
                "§7右クリックで即時使用", "§7HP +6 / 満腹度回復",
                "§8§oRarity: §7★ COMMON"
            }, 39003);
            return stack;
        } else if (roll < GameConstants.DROP_GOLD_CACHE_BASE + (int)(diffMul * 5)) {
            var stack = new net.minecraft.world.item.ItemStack(Items.RAW_GOLD, 1);
            applyRogueLore(stack, "§e✦ GOLD CACHE", new String[]{
                "§7大量のゴールドの塊", "§7右クリック → §e250 G §7獲得",
                "§8§oRarity: §7★ COMMON"
            }, 39004);
            return stack;
        } else if (roll < GameConstants.DROP_STAMINA_BASE + rareBonus / 2) {
            var stack = new net.minecraft.world.item.ItemStack(Items.HONEY_BOTTLE, 1);
            applyRogueLore(stack, "§b✦ STAMINA BOOST", new String[]{
                "§7右クリックで使用", "§7スタミナが全回復し最大値が上昇",
                "§8§oRarity: §b★★ UNCOMMON"
            }, 39005);
            stack.enchant(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING, 1);
            stack.getOrCreateTag().putInt("HideFlags", 1);
            return stack;
        } else {
            var stack = new net.minecraft.world.item.ItemStack(Items.RAW_IRON, 1 + random.nextInt(3));
            applyRogueLore(stack, "§8✦ SCRAP METAL", new String[]{
                "§7換金用のスクラップ金属", "§7右クリック → §e50 G §7獲得",
                "§8§oRarity: §7★ COMMON"
            }, 39006);
            return stack;
        }
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
                if (floor <= 10) { // 1~10階: Pistol, SMG, Melee
                    allow = (item.category == com.levanilla.rogue.core.registry.ShopCatalog.Category.PISTOL ||
                             item.category == com.levanilla.rogue.core.registry.ShopCatalog.Category.SMG ||
                             item.category == com.levanilla.rogue.core.registry.ShopCatalog.Category.MELEE);
                } else if (floor <= 25) { // 11~25階: + Shotgun
                    allow = (item.category == com.levanilla.rogue.core.registry.ShopCatalog.Category.PISTOL ||
                             item.category == com.levanilla.rogue.core.registry.ShopCatalog.Category.SMG ||
                             item.category == com.levanilla.rogue.core.registry.ShopCatalog.Category.MELEE ||
                             item.category == com.levanilla.rogue.core.registry.ShopCatalog.Category.SHOTGUN);
                } else if (floor <= 45) { // 26~45階: + Rifle
                    allow = (item.category == com.levanilla.rogue.core.registry.ShopCatalog.Category.PISTOL ||
                             item.category == com.levanilla.rogue.core.registry.ShopCatalog.Category.SMG ||
                             item.category == com.levanilla.rogue.core.registry.ShopCatalog.Category.MELEE ||
                             item.category == com.levanilla.rogue.core.registry.ShopCatalog.Category.SHOTGUN ||
                             item.category == com.levanilla.rogue.core.registry.ShopCatalog.Category.RIFLE);
                } else if (floor <= 70) { // 46~70階: + LMG
                    allow = (item.category == com.levanilla.rogue.core.registry.ShopCatalog.Category.PISTOL ||
                             item.category == com.levanilla.rogue.core.registry.ShopCatalog.Category.SMG ||
                             item.category == com.levanilla.rogue.core.registry.ShopCatalog.Category.MELEE ||
                             item.category == com.levanilla.rogue.core.registry.ShopCatalog.Category.SHOTGUN ||
                             item.category == com.levanilla.rogue.core.registry.ShopCatalog.Category.RIFLE ||
                             item.category == com.levanilla.rogue.core.registry.ShopCatalog.Category.LMG);
                } else { // 71階以上: + Sniper (All)
                    allow = true;
                }
                if (allow) weapons.add(item);
            }
        }
        if (weapons.isEmpty()) return new net.minecraft.world.item.ItemStack(Items.AIR);

        var chosen = weapons.get(random.nextInt(weapons.size()));
        net.minecraft.world.item.ItemStack stack = com.levanilla.rogue.core.service.ShopService.createItemStack(chosen.id);

        if (stack.isEmpty()) return stack;

        // レアリティ付与 (独立抽選)
        WeaponRarity.Rarity rarity = WeaponRarity.rollRarity(floor, random);
        WeaponRarity.applyRarity(stack, rarity);

        // 銃のみアタッチメント抽選 (独立)
        if (stack.hasTag() && stack.getTag().contains("GunId")) {
            java.util.List<String> allAtt = TacZRegistryHelper.getAllAttachmentIds();
            net.minecraft.nbt.CompoundTag attTag = new net.minecraft.nbt.CompoundTag();
            
            // アタッチメントが付属する確率 (階層が上がれば上がるほど少しずつ増える)
            float attachmentChance = 0.2f + Math.min(0.5f, floor * 0.005f); // 1階につき0.5%上昇し、最大70%まで
            
            if (random.nextFloat() < attachmentChance + 0.1f) { // サイトは少し出やすくする
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
            if (random.nextFloat() < attachmentChance * 0.6f) { // 拡張マガジンは強いため出にくくする
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

    private static net.minecraft.world.item.ItemStack generateAttachmentDrop(net.minecraft.util.RandomSource random, int floor) {
        java.util.List<String> allAtt = TacZRegistryHelper.getAllAttachmentIds();
        if (allAtt.isEmpty()) return new net.minecraft.world.item.ItemStack(Items.AIR);
        String chosen = allAtt.get(random.nextInt(allAtt.size()));
        return com.levanilla.rogue.core.service.ShopService.createAttachmentStack(chosen);
    }
}
