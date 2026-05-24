package com.levanilla.rogue.core.event;

import com.levanilla.rogue.core.*;
import com.levanilla.rogue.core.service.RogueMobAlertService;
import com.levanilla.rogue.networking.TacRogueNetworking;
import com.levanilla.rogue.world.goal.RogueMobGoalUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.levanilla.rogue.core.CommonEventHandler.ROGUE_DIM;
import static com.levanilla.rogue.core.CommonEventHandler.LOBBY_DIM;

/**
 * スポーン制御、場外引き戻し、フロアクリア判定、ブロック/ガラス保護。
 */
@Mod.EventBusSubscriber(modid = "tac_rogue")
public class SpawnAndWorldHandler {
    private static final List<PendingSlimeSplit> PENDING_SLIME_SPLITS = new ArrayList<>();

    // ===== バニラスポーン抑制 =====

    @SubscribeEvent
    public static void onCheckSpawn(net.minecraftforge.event.entity.living.MobSpawnEvent.FinalizeSpawn event) {
        if (event.getEntity().level().dimension() == ROGUE_DIM || event.getEntity().level().dimension() == LOBBY_DIM) {
            // ダンジョンジェネレーターがスポーンしたMobはタグで識別して除外
            if (event.getEntity().getTags().contains("tac_rogue_spawned")) return;
            if (event.getSpawnType() == net.minecraft.world.entity.MobSpawnType.NATURAL
                || event.getSpawnType() == net.minecraft.world.entity.MobSpawnType.CHUNK_GENERATION
                || event.getSpawnType() == net.minecraft.world.entity.MobSpawnType.STRUCTURE) {
                event.setSpawnCancelled(true);
            }
        }
    }

    // ===== テレポート制限 =====

    @SubscribeEvent
    public static void onEntityTeleport(net.minecraftforge.event.entity.EntityTeleportEvent event) {
        if (event.getEntity().level().dimension() != ROGUE_DIM) return;
        double tx = event.getTargetX(), tz = event.getTargetZ();
        BlockPos origin = null;
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerRunData data = RunManager.getData(player);
            origin = data.getDungeonOrigin();
        } else if (event.getEntity().level() instanceof ServerLevel sl) {
            PlayerRunData data = RunManager.findDataForDungeonPosition(
                sl, event.getEntity().getX(), event.getEntity().getZ(), GameConstants.FLOOR_CLEAR_RADIUS + 80.0D);
            if (data != null) origin = data.getDungeonOrigin();
        }
        if (origin != null) {
            if (Math.abs(tx - origin.getX()) > GameConstants.TELEPORT_BOUNDARY
                || Math.abs(tz - origin.getZ()) > GameConstants.TELEPORT_BOUNDARY) {
                event.setCanceled(true);
            }
        } else if (Math.abs(tx) > GameConstants.TELEPORT_BOUNDARY || Math.abs(tz) > GameConstants.TELEPORT_BOUNDARY) {
            event.setCanceled(true);
        }
        if (event.getEntity() instanceof net.minecraft.world.entity.monster.Shulker) {
            event.setCanceled(true);
        }
    }

    // ===== エンティティ参加時のデスポーン防止 =====

    @SubscribeEvent
    public static void onEntityJoinLevel(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        var dim = event.getLevel().dimension();
        boolean serverLevel = event.getLevel() instanceof ServerLevel;
        if (dim == ROGUE_DIM && event.getEntity() instanceof Mob mob) {
            if (!serverLevel) return;

            mob.setPersistenceRequired();
            inheritSlimeSplitTracking(event, mob);

            // 強化版亡霊処理: チャンクロード時に過去のエンティティを一掃する
            boolean isSpawnedOrNpc = mob.getTags().contains("tac_rogue_spawned") || mob.getTags().contains("tac_rogue_npc");
            if (!isSpawnedOrNpc) {
                event.setCanceled(true);
                return;
            }
            if (mob.getTags().contains("tac_rogue_spawned")) {
                RogueMobGoalUtils.removePassivePlayerLookGoals(mob);
            }
            if (event.getLevel() instanceof ServerLevel sl) {
                PlayerRunData data = RunManager.findDataForDungeonPosition(sl, mob.getX(), mob.getZ());
                if (data != null && data.isRunActive()) {
                    int mobFloor = mob.getPersistentData().getInt("TacRogueSpawnFloor");
                    long mobSpawnTick = mob.getPersistentData().getLong("TacRogueSpawnTick");
                    boolean isResidue = false;
                    if (mobFloor > 0 && data.getCurrentFloor() != mobFloor) {
                        isResidue = true;
                    } else if (mobSpawnTick > 0 && data.getFloorStartTick() > 0 && mobSpawnTick < data.getFloorStartTick()) {
                        isResidue = true;
                    }
                    if (isResidue) {
                        event.setCanceled(true);
                        // CanceledするとEntityJoinLevelが行われないので自然消去される
                        return;
                    }
                }
            }

            // Vexのカスタムモブ化
            if (mob instanceof net.minecraft.world.entity.monster.Vex vex) {
                vex.setCustomName(net.minecraft.network.chat.Component.literal("\u00A7c\u00A7l[COMBAT DRONE]"));
                vex.setCustomNameVisible(true);
                
                var maxHp = vex.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                if (maxHp != null) maxHp.setBaseValue(40.0D); // ドローンの体力調整
                vex.setHealth(vex.getMaxHealth());
                
                net.minecraft.world.entity.player.Player nearest = event.getLevel().getNearestPlayer(vex, 64.0);
                if (nearest != null) {
                    vex.setTarget(nearest);
                }
            }

            // ステルス仕様: デフォルトの索敵範囲(40等)を物理的に24(VISION_RANGE)に制限
            var followRange = mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
            if (followRange != null && followRange.getBaseValue() > 24.0) {
                followRange.setBaseValue(24.0);
            }
        } else if (dim == LOBBY_DIM && event.getEntity() instanceof Mob mob) {
            if (!serverLevel) return;

            boolean isNpc = mob.getTags().contains("tac_rogue_npc");
            if (!isNpc) {
                event.setCanceled(true);
                return;
            }
        }

        // ダンジョン・ロビーでのブロック破片ドロップを無効化
        if (dim == ROGUE_DIM || dim == LOBBY_DIM) {
            if (!serverLevel) return;

            if (event.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity itemEntity) {
                if (itemEntity.getItem().getItem() instanceof net.minecraft.world.item.BlockItem) {
                    event.setCanceled(true);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDeathTrackSlimeSplit(LivingDeathEvent event) {
        if (event.getEntity().level().dimension() != ROGUE_DIM) return;
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (!(mob instanceof net.minecraft.world.entity.monster.Slime
                || mob instanceof net.minecraft.world.entity.monster.MagmaCube)) return;
        if (!mob.getTags().contains("tac_rogue_spawned")) return;

        var data = mob.getPersistentData();
        String instanceId = data.getString(com.levanilla.rogue.core.service.FloorInstanceManager.INSTANCE_ID_KEY);
        if (instanceId.isBlank()) return;
        PENDING_SLIME_SPLITS.add(new PendingSlimeSplit(
            instanceId,
            data.getInt(com.levanilla.rogue.core.service.FloorInstanceManager.FLOOR_KEY),
            data.getString(com.levanilla.rogue.core.service.FloorInstanceManager.MODE_KEY),
            data.getString(com.levanilla.rogue.core.service.FloorInstanceManager.OWNER_KEY),
            mob.getX(), mob.getY(), mob.getZ(),
            mob.level().getGameTime()));
        prunePendingSlimeSplits(mob.level().getGameTime());
    }

    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        if (event.getEntity().level().dimension() != ROGUE_DIM) return;
        if (!(event.getEntity() instanceof net.minecraft.world.entity.monster.Spider
                || event.getEntity() instanceof net.minecraft.world.entity.monster.CaveSpider)) return;
        if (!event.getEntity().getTags().contains("tac_rogue_spawned")) return;
        event.setCanceled(true);
        event.getEntity().fallDistance = 0.0F;
    }

    // ===== ブロック破壊禁止 =====

    @SubscribeEvent
    public static void onBlockBreak(net.minecraftforge.event.level.BlockEvent.BreakEvent event) {
        if (event.getPlayer().level().dimension() != ROGUE_DIM && event.getPlayer().level().dimension() != LOBBY_DIM) return;
        if (!event.getPlayer().isCreative()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMobGriefing(net.minecraftforge.event.entity.EntityMobGriefingEvent event) {
        if (event.getEntity() != null && event.getEntity().level().dimension() == ROGUE_DIM) {
            // ウィザーの壁破壊やクリーパーの爆発破壊など、モブによる一切の地形破壊を無効化
            event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
        }
    }

    // ===== 銃弾によるブロック損壊防止 =====

    @SubscribeEvent
    public static void onProjectileImpact(net.minecraftforge.event.entity.ProjectileImpactEvent event) {
        if (!event.getEntity().level().isClientSide) {
            Level level = event.getEntity().level();

            // MAINT-5: 雪玉デコイ処理は TacZEventHandler.onProjectileImpact() に統合済み

            if (event.getRayTraceResult().getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                net.minecraft.world.phys.BlockHitResult hit = (net.minecraft.world.phys.BlockHitResult) event.getRayTraceResult();
                if (event.getEntity().level().getBlockState(hit.getBlockPos()).getBlock()
                        instanceof net.minecraft.world.level.block.AbstractGlassBlock) {
                    event.setCanceled(true);
                }
            }
        }
    }

    // ロビー射撃禁止: TacZEventHandler.onGunFire() で GunFireEvent.setCanceled() により正規 API で処理

    // ===== 場外モブ引き戻し =====

    @SubscribeEvent
    public static void onLivingUpdate(LivingEvent.LivingTickEvent event) {
        if (event.getEntity().level().isClientSide) return;
        Level level = event.getEntity().level();

        if (level.dimension() != ROGUE_DIM || !(event.getEntity() instanceof Mob mob)) return;

        // デコイ注視状態 (雪玉誘導) の強制
        long decoyEndTime = mob.getPersistentData().getLong(RogueMobAlertService.DECOY_END_TIME);
        if (decoyEndTime > 0) {
            if (level.getGameTime() < decoyEndTime && mob.getTarget() == null) {
                double sx = mob.getPersistentData().getDouble(RogueMobAlertService.DECOY_X);
                double sy = mob.getPersistentData().getDouble(RogueMobAlertService.DECOY_Y);
                double sz = mob.getPersistentData().getDouble(RogueMobAlertService.DECOY_Z);
                
                mob.getLookControl().setLookAt(sx, sy, sz, 100.0F, 100.0F); // 強い力で視点固定
                // バニラのRandomLookAroundGoalによる首振りを強制的に上書き固定する
                float targetYaw = (float)(Math.atan2(sz - mob.getZ(), sx - mob.getX()) * (180D / Math.PI)) - 90.0F;
                mob.setYHeadRot(targetYaw);
                mob.setYBodyRot(targetYaw);
            } else {
                mob.getPersistentData().remove(RogueMobAlertService.DECOY_END_TIME);
                mob.getPersistentData().remove(RogueMobAlertService.DECOY_X);
                mob.getPersistentData().remove(RogueMobAlertService.DECOY_Y);
                mob.getPersistentData().remove(RogueMobAlertService.DECOY_Z);
                mob.getPersistentData().remove(RogueMobAlertService.DECOY_OWNER);
            }
        }

        // ダンジョン内Mob全般の壁抜け・場外脱出対策とフェイルセーフ亡霊処理
        if (mob.getTags().contains("tac_rogue_spawned") || mob.getTags().contains("tac_rogue_npc")) {
            ServerPlayer runOwner = level instanceof ServerLevel sl
                ? RunManager.findPlayerForDungeonPosition(sl, mob.getX(), mob.getZ())
                : null;
            net.minecraft.world.entity.player.Player nearest = runOwner != null
                ? runOwner
                : level.getNearestPlayer(mob, 300.0);
            
            // 亡霊（前回のフロア、または過去のリトライ前に湧いた敵）の即時消去システム
            int mobFloor = mob.getPersistentData().getInt("TacRogueSpawnFloor");
            long mobSpawnTick = mob.getPersistentData().getLong("TacRogueSpawnTick");
            if (runOwner != null) {
                PlayerRunData data = RunManager.getData(runOwner);
                if (data.isRunActive()) {
                    boolean isResidue = false;
                    if (mobFloor > 0 && data.getCurrentFloor() != mobFloor) {
                        isResidue = true;
                    }
                    if (mobSpawnTick > 0 && data.getFloorStartTick() > 0 && mobSpawnTick < data.getFloorStartTick()) {
                        isResidue = true;
                    }
                    if (isResidue) {
                        mob.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                        return;
                    }
                }
            }

            // スポーン後5秒(100tick)は壁判定・場外判定をスキップ（地形生成ラグ対策）
            if (mob.tickCount > 100) {
                // Vex の壁すり抜け対策
                if (mob instanceof net.minecraft.world.entity.monster.Vex vex) {
                    vex.noPhysics = false;
                }

                // 壁埋まり判定: 4方向+上下すべてソリッドならば真に閉じ込められている
                // （ハーフブロック・階段・ドア枠での誤判定を排除）
                BlockPos mobPos = mob.blockPosition();
                boolean trulyTrapped = isTrulyTrappedInWall(mob, level, mobPos);
                boolean outside = isOutsideDungeon(mob, level);

                if (trulyTrapped || outside) {
                    // 連続TPを防ぐ: 5秒(100tick)クールダウン
                    long lastRelocateTick = mob.getPersistentData().getLong("TacRogueLastRelocate");
                    long currentGameTime = level.getGameTime();
                    if (currentGameTime - lastRelocateTick < 100) return;

                    if (outside && nearest == null) {
                        // プレイヤーがいない場外モブは即削除
                        mob.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                        return;
                    }

                    if (nearest instanceof net.minecraft.server.level.ServerPlayer sp2) {
                        // プレイヤーの **背後** 12-18ブロック先に着地可能な場所を探す
                        BlockPos relocateTarget = findSafeRelocatePos(sp2, level);
                        if (relocateTarget != null) {
                            mob.teleportTo(relocateTarget.getX() + 0.5, relocateTarget.getY(), relocateTarget.getZ() + 0.5);
                            mob.setDeltaMovement(0, 0, 0);
                            mob.getPersistentData().putLong("TacRogueLastRelocate", currentGameTime);
                        } else {
                            // 安全な再配置先が見つからない場合は削除（プレイヤーの目の前にTPさせない）
                            mob.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                        }
                    } else {
                        mob.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                    }
                }
            }
        }

        // 以降は20tickごとの処理
        if (mob.tickCount % 20 != 0) return;

        // Wardenのデスポーン(地中へ潜る)防止処理
        if (mob instanceof net.minecraft.world.entity.monster.warden.Warden warden) {
            net.minecraft.world.entity.player.Player nearest = level.getNearestPlayer(warden, 64.0);
            if (nearest != null) {
                warden.increaseAngerAt(nearest, 80, true);
                warden.setTarget(nearest);
            }
            // 常にDIG_COOLDOWNを維持し、地中に潜るルーチン(DIGアクティビティ)への移行を阻止する
            warden.getBrain().setMemoryWithExpiry(net.minecraft.world.entity.ai.memory.MemoryModuleType.DIG_COOLDOWN, net.minecraft.util.Unit.INSTANCE, 1200L);
        }

        // 蜘蛛の天井/壁埋まり修正
        if (mob instanceof net.minecraft.world.entity.monster.Spider ||
            mob instanceof net.minecraft.world.entity.monster.CaveSpider) {
            mob.getPersistentData().putBoolean("TacRogueNoSpiderClimb", true);
            mob.setNoGravity(false);
            mob.noPhysics = false;
            mob.fallDistance = 0.0F;
            BlockPos spPos = mob.blockPosition();
            boolean stuck = level.getBlockState(spPos).isSolid()
                || level.getBlockState(spPos.above()).isSolid()
                || level.getBlockState(spPos.above(2)).isSolid();
            if (stuck) {
                double pushX = (level.random.nextFloat() - 0.5) * 0.5;
                double pushZ = (level.random.nextFloat() - 0.5) * 0.5;
                mob.setDeltaMovement(pushX, -0.3, pushZ);
            }
        }
    }

    private static void inheritSlimeSplitTracking(net.minecraftforge.event.entity.EntityJoinLevelEvent event, Mob mob) {
        if (mob.getTags().contains("tac_rogue_spawned")) return;
        if (!(mob instanceof net.minecraft.world.entity.monster.Slime
                || mob instanceof net.minecraft.world.entity.monster.MagmaCube)) return;
        long now = event.getLevel().getGameTime();
        prunePendingSlimeSplits(now);
        PendingSlimeSplit match = null;
        double best = Double.MAX_VALUE;
        for (PendingSlimeSplit pending : PENDING_SLIME_SPLITS) {
            double dx = mob.getX() - pending.x;
            double dy = mob.getY() - pending.y;
            double dz = mob.getZ() - pending.z;
            double dist = dx * dx + dy * dy + dz * dz;
            if (dist < best && dist <= 64.0D) {
                best = dist;
                match = pending;
            }
        }
        if (match == null) return;

        mob.addTag("tac_rogue_spawned");
        mob.getPersistentData().putInt("TacRogueSpawnFloor", match.floor);
        var server = mob.level().getServer();
        mob.getPersistentData().putLong("TacRogueSpawnTick", server == null ? now : server.getTickCount());
        UUID owner = parseUuid(match.ownerUuid);
        com.levanilla.rogue.core.service.FloorInstanceManager.stampEntity(
            mob,
            match.instanceId,
            match.floor,
            com.levanilla.rogue.core.service.FloorInstanceManager.EntryMode.parse(match.mode),
            owner);
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static void prunePendingSlimeSplits(long now) {
        PENDING_SLIME_SPLITS.removeIf(pending -> now - pending.createdTick > 60L);
    }

    private record PendingSlimeSplit(String instanceId, int floor, String mode, String ownerUuid,
                                     double x, double y, double z, long createdTick) {}

    // ===== フロアクリア判定 (ServerTickEvent で最適化) =====

    @SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        long tick = server.getTickCount();

        // NPC視線追従 (5tickごと — ロビー内のみ)
        if (tick % 5 == 0) {
            ServerLevel lobbyLevel = server.getLevel(LOBBY_DIM);
            if (lobbyLevel != null && !lobbyLevel.players().isEmpty()) {
                com.levanilla.rogue.world.NpcManager.tickLobbyMaintenance(lobbyLevel, new net.minecraft.core.BlockPos(0, 201, 0));
            }
        }

        com.levanilla.rogue.core.service.FloorInstanceManager.tick(server);

        // フロアクリア判定 (既存ロジック)
        if (tick % GameConstants.FLOOR_CLEAR_CHECK_INTERVAL != 0) return;

        ServerLevel rogueLevel = server.getLevel(ROGUE_DIM);
        if (rogueLevel == null) return;

        List<ServerPlayer> playersCopy = new ArrayList<>(rogueLevel.players());
        for (ServerPlayer player : playersCopy) {
            com.levanilla.rogue.core.PlayerRunData data = RunManager.getData(player);
            if (!data.isRunActive()) continue;
            if (data.isFloorCleared()) continue;
            if (com.levanilla.rogue.core.service.FloorInstanceManager.getInstanceForPlayer(player) != null) continue;

            // フロア開始から5秒(100tick)はクリア判定をスキップ（モブスポーン猶予）
            long currentTick = server.getTickCount();
            if (currentTick - data.getFloorStartTick() < 100) continue;

            // プレイヤー固有の基準座標で判定
            AABB area = new AABB(data.getDungeonOrigin())
                .inflate(GameConstants.FLOOR_CLEAR_RADIUS);
            List<Mob> alive = rogueLevel.getEntitiesOfClass(Mob.class, area, mob ->
                mob.isAlive()
                    && mob.getTags().contains("tac_rogue_spawned")
                    && mob.getPersistentData().getInt("TacRogueSpawnFloor") == data.getCurrentFloor());
            boolean bossFloor = com.levanilla.rogue.world.ThemeManager.isBossFloor(data.getCurrentFloor());
            boolean bossAlive = alive.stream().anyMatch(mob -> mob.getTags().contains("rogue:boss"));
            if (alive.isEmpty() || (bossFloor && !bossAlive)) {
                // フロア制圧完了。抽出用の情報将校をスポーンさせる
                com.levanilla.rogue.world.TacRogueNpcEntity npc = com.levanilla.rogue.world.NpcManager.spawnExtractionOfficer(
                    rogueLevel,
                    com.levanilla.rogue.world.NpcManager.findExtractionSpawnNear(rogueLevel, player),
                    data.getCurrentFloor());
                if (npc != null) {
                    npc.getPersistentData().putString(
                        com.levanilla.rogue.core.service.FloorInstanceManager.OWNER_KEY,
                        player.getUUID().toString());
                }
                
                com.levanilla.rogue.networking.PopupNotificationMessage.send(
                    player,
                    com.levanilla.rogue.networking.PopupNotificationMessage.PopupType.SYSTEM,
                    net.minecraft.network.chat.Component.translatable("popup.tac_rogue.extraction.title"),
                    net.minecraft.network.chat.Component.translatable("message.tac_rogue.extraction_arrived"),
                    150
                );
                
                data.setFloorCleared(true);
                RunManager.syncPlayer(player);

                boolean questEligible = com.levanilla.rogue.core.service.FloorInstanceManager.isQuestEligibleFloor(
                    data.getCurrentFloor(), data.getMaxReachedFloor());
                if (!questEligible) continue;

                // === クエスト進捗: フロアクリア系 ===
                com.levanilla.rogue.core.QuestManager.advanceQuest(player, com.levanilla.rogue.core.QuestManager.QuestType.FLOOR_CLEAR, 1);

                // SPEEDRUN: 階層ごとに変動するタイムリミット (敵の増加に合わせて時間を増やす)
                // Base=120秒, 1フロアごとに+10秒
                int speedrunLimitTicks = (120 + data.getCurrentFloor() * 10) * 20;
                long elapsedTicks = currentTick - data.getFloorStartTick();
                if (elapsedTicks <= speedrunLimitTicks) {
                    com.levanilla.rogue.core.QuestManager.advanceQuest(player, com.levanilla.rogue.core.QuestManager.QuestType.SPEEDRUN, 1);
                }

                // SURVIVE: フロアを死亡せずにクリア (HPが最大値の50%以上)
                if (player.getHealth() >= player.getMaxHealth() * 0.5f) {
                    com.levanilla.rogue.core.QuestManager.advanceQuest(player, com.levanilla.rogue.core.QuestManager.QuestType.SURVIVE, 1);
                }

                // LOW_HEALTH_CLEAR: 危険域のHPでフロアを制圧する
                if (player.getHealth() <= player.getMaxHealth() * 0.35f) {
                    com.levanilla.rogue.core.QuestManager.advanceQuest(player, com.levanilla.rogue.core.QuestManager.QuestType.LOW_HEALTH_CLEAR, 1);
                }

                // NO_DAMAGE: ダメージを受けずにクリア (5秒間のクールダウン未使用 = ダメージなし)
                long lastDmg = CombatEventHandler.getLastDamageTick(player.getUUID());
                if (lastDmg <= data.getFloorStartTick()) {
                    com.levanilla.rogue.core.QuestManager.advanceQuest(player, com.levanilla.rogue.core.QuestManager.QuestType.NO_DAMAGE, 1);
                }
            }
        }
    }

    // ===== 爆発地形保護 =====

    /** ローグダンジョン/ロビーで爆発によるブロック破壊を無効化（エンティティダメージは維持） */
    @SubscribeEvent
    public static void onExplosion(net.minecraftforge.event.level.ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            if (serverLevel.dimension() == ROGUE_DIM || serverLevel.dimension() == LOBBY_DIM) {
                event.getAffectedBlocks().clear();
            }
        }
    }

    // BUG-2: スロット制約は InventoryRuleService.enforce() に統合済み

    /**
     * 真に壁に閉じ込められているかを判定する。
     * 6方向(上下東西南北)のうち5方向以上がソリッドなら完全に埋まっていると判定。
     * ハーフブロック・階段・ドア枠での誤検知を防止する。
     */
    private static boolean isTrulyTrappedInWall(Mob mob, net.minecraft.world.level.Level level, BlockPos mobPos) {
        BlockPos head = mobPos.above();
        // 足元と頭の両方がソリッドでなければ埋まっているとは言えない
        if (!level.getBlockState(mobPos).isSolid() || !level.getBlockState(head).isSolid()) {
            return false;
        }
        // 6方向チェック: 上下東西南北のうち5方向以上がソリッドなら真に閉じ込められている
        int solidCount = 0;
        if (level.getBlockState(mobPos.above(2)).isSolid()) solidCount++;
        if (level.getBlockState(mobPos.below()).isSolid()) solidCount++;
        if (level.getBlockState(mobPos.north()).isSolid()) solidCount++;
        if (level.getBlockState(mobPos.south()).isSolid()) solidCount++;
        if (level.getBlockState(mobPos.east()).isSolid()) solidCount++;
        if (level.getBlockState(mobPos.west()).isSolid()) solidCount++;
        return solidCount >= 5;
    }

    /**
     * プレイヤーから離れた安全な再配置先を探す。
     * プレイヤーの視線の反対側 12-18ブロック地点で、空気2ブロック+足場がある場所。
     * 見つからなければ null を返す（呼び出し側でモブ削除）。
     */
    private static BlockPos findSafeRelocatePos(ServerPlayer player, net.minecraft.world.level.Level level) {
        net.minecraft.world.phys.Vec3 look = player.getLookAngle().normalize();
        // プレイヤーの視線の反対方向をベースにする
        double baseAngle = Math.atan2(-look.z, -look.x);

        for (int attempt = 0; attempt < 15; attempt++) {
            // ±45度の範囲でランダムに散らす
            double angle = baseAngle + (level.random.nextFloat() - 0.5) * Math.PI * 0.5;
            double dist = 12.0 + level.random.nextFloat() * 6.0; // 12-18ブロック
            int tx = (int)(player.getX() + Math.cos(angle) * dist);
            int tz = (int)(player.getZ() + Math.sin(angle) * dist);
            int ty = player.blockPosition().getY();

            // Y方向に±5ブロック探索して着地可能地点を見つける
            for (int dy = -3; dy <= 5; dy++) {
                BlockPos candidate = new BlockPos(tx, ty + dy, tz);
                boolean feetClear = !level.getBlockState(candidate).isSolid();
                boolean headClear = !level.getBlockState(candidate.above()).isSolid();
                boolean hasFloor = level.getBlockState(candidate.below()).isSolid();
                if (feetClear && headClear && hasFloor) {
                    return candidate;
                }
            }
        }
        return null; // 安全な場所が見つからない
    }

    /** Mobがダンジョンの有効範囲外にいるかどうかを判定 */
    private static boolean isOutsideDungeon(Mob mob, net.minecraft.world.level.Level level) {
        if (level instanceof ServerLevel sl) {
            PlayerRunData data = RunManager.findDataForDungeonPosition(
                sl, mob.getX(), mob.getZ(), GameConstants.FLOOR_CLEAR_RADIUS + 80.0D);
            BlockPos origin = data == null ? null : data.getDungeonOrigin();
            if (origin != null) {
                double dx = Math.abs(mob.getX() - origin.getX());
                double dz = Math.abs(mob.getZ() - origin.getZ());
                // ダンジョン半径は最大78 → 100以上は明らかに壁の外 (余裕を持たせて誤検知を減らす)
                return dx > 100 || dz > 100 || mob.getY() < origin.getY() - 10 || mob.getY() > origin.getY() + 30;
            }
        }
        return false;
    }

    // ===== サーバー起動時のデータ復元 =====

    @SubscribeEvent
    public static void onServerStarted(net.minecraftforge.event.server.ServerStartedEvent event) {
        // SavedData からワールドに保存された難易度を復元
        net.minecraft.server.level.ServerLevel lobbyLevel = event.getServer().getLevel(LOBBY_DIM);
        if (lobbyLevel != null) {
            com.levanilla.rogue.core.DifficultyManager.loadFromSavedData(lobbyLevel);
        }
    }

    // ===== サーバー停止時のメモリクリア =====

    @SubscribeEvent
    public static void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        // シングルプレイワールド退店時など、別ワールドへのデータ汚染を防ぐためメモリをリセット
        // ※ NBT には保存済みなのでクリアしても問題ない
        com.levanilla.rogue.core.RunManager.clearMemory();
        CombatEventHandler.clearMemory();
        TacZEventHandler.clearMemory();
        com.levanilla.rogue.core.service.BossRewardService.clearMemory();
        com.levanilla.rogue.core.StaminaManager.clearMemory();
        com.levanilla.rogue.networking.RogueActionMessage.clearMemory();
        com.levanilla.rogue.core.QuestManager.clearMemory();
        com.levanilla.rogue.world.NpcManager.clearMemory();
    }
}
