package com.levanilla.rogue.core.event;

import com.levanilla.rogue.core.*;
import com.levanilla.rogue.networking.TacRogueNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

import static com.levanilla.rogue.core.CommonEventHandler.ROGUE_DIM;
import static com.levanilla.rogue.core.CommonEventHandler.LOBBY_DIM;

/**
 * スポーン制御、場外引き戻し、フロアクリア判定、ブロック/ガラス保護。
 */
@Mod.EventBusSubscriber(modid = "tac_rogue")
public class SpawnAndWorldHandler {

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
        if (Math.abs(tx) > GameConstants.TELEPORT_BOUNDARY || Math.abs(tz) > GameConstants.TELEPORT_BOUNDARY) {
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
        if (dim == ROGUE_DIM && event.getEntity() instanceof Mob mob) {
            mob.setPersistenceRequired();

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
        }

        // ダンジョン・ロビーでのブロック破片ドロップを無効化
        if (dim == ROGUE_DIM || dim == LOBBY_DIM) {
            if (event.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity itemEntity) {
                if (itemEntity.getItem().getItem() instanceof net.minecraft.world.item.BlockItem) {
                    event.setCanceled(true);
                }
            }
        }
    }

    // ===== ブロック破壊禁止 =====

    @SubscribeEvent
    public static void onBlockBreak(net.minecraftforge.event.level.BlockEvent.BreakEvent event) {
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

            // 雪玉のステルス誘導処理
            if (event.getEntity() instanceof net.minecraft.world.entity.projectile.Snowball) {
                net.minecraft.world.phys.HitResult hit = event.getRayTraceResult();
                net.minecraft.world.phys.Vec3 hitPos = hit.getLocation();
                
                // 着弾地点から半径24ブロックの敵を誘導
                AABB area = new AABB(hitPos.x - 24, hitPos.y - 12, hitPos.z - 24, hitPos.x + 24, hitPos.y + 12, hitPos.z + 24);
                List<Mob> mobs = level.getEntitiesOfClass(Mob.class, area, m -> m.isAlive() && m.getTags().contains("tac_rogue_spawned"));
                
                for (Mob targetMob : mobs) {
                    // すでにプレイヤーをターゲットしていないか、ターゲットからの距離が遠い場合のみ誘導
                    if (targetMob.getTarget() == null || targetMob.getTarget().distanceToSqr(targetMob) > 400) {
                        targetMob.getNavigation().moveTo(hitPos.x, hitPos.y, hitPos.z, 1.3);
                    }
                }
            }

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
        long decoyEndTime = mob.getPersistentData().getLong("DecoyEndTime");
        if (decoyEndTime > 0) {
            if (level.getGameTime() < decoyEndTime && mob.getTarget() == null) {
                double sx = mob.getPersistentData().getDouble("DecoyX");
                double sy = mob.getPersistentData().getDouble("DecoyY");
                double sz = mob.getPersistentData().getDouble("DecoyZ");
                
                mob.getLookControl().setLookAt(sx, sy, sz, 100.0F, 100.0F); // 強い力で視点固定
                // バニラのRandomLookAroundGoalによる首振りを強制的に上書き固定する
                float targetYaw = (float)(Math.atan2(sz - mob.getZ(), sx - mob.getX()) * (180D / Math.PI)) - 90.0F;
                mob.setYHeadRot(targetYaw);
                mob.setYBodyRot(targetYaw);
            } else {
                mob.getPersistentData().remove("DecoyEndTime");
            }
        }

        // ダンジョン内Mob全般の壁抜け・場外脱出対策
        if (mob.getTags().contains("tac_rogue_spawned")) {
            net.minecraft.world.entity.player.Player nearest = level.getNearestPlayer(mob, 300.0);
            
            // 亡霊（前回のフロア、または過去のリトライ前に湧いた敵）の即時消去システム
            int mobFloor = mob.getPersistentData().getInt("TacRogueSpawnFloor");
            long mobSpawnTick = mob.getPersistentData().getLong("TacRogueSpawnTick");
            if (nearest instanceof net.minecraft.server.level.ServerPlayer sp) {
                com.levanilla.rogue.core.PlayerRunData data = com.levanilla.rogue.core.RunManager.getData(sp);
                if (data.isRunActive()) {
                    boolean isResidue = false;
                    // フロア番号が違うなら過去の遺物
                    if (mobFloor > 0 && data.getCurrentFloor() != mobFloor) {
                        isResidue = true;
                    }
                    // リトライ等で同じフロアだとしても、プレイヤーの挑戦開始時刻よりも前に生み出されたなら過去の遺物
                    if (mobSpawnTick > 0 && data.getFloorStartTick() > 0 && mobSpawnTick < data.getFloorStartTick()) {
                        isResidue = true;
                    }
                    if (isResidue) {
                        mob.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                        return;
                    }
                }
            }

            // 地形生成直後のラグ等による誤爆（新しく湧いた直後に壁と判定される）を防ぐため、スポーン後3秒(60tick)は猶予
            if (mob.tickCount > 60) {
                // ボスや巨体が壁にめり込んだり押し出されたりするのを防ぐ
                // Vex の壁すり抜け対策も兼ねる
                if (mob instanceof net.minecraft.world.entity.monster.Vex vex) {
                    vex.noPhysics = false;
                }
                
                BlockPos mobPos = mob.blockPosition();
                boolean inWall = level.getBlockState(mobPos).isSolid() && level.getBlockState(mobPos.above()).isSolid();
                
                // ダンジョン外 or 完全に壁の中にいる場合 → 最寄りプレイヤーの近くに引き戻す
                if (inWall || isOutsideDungeon(mob, level)) {
                    if (nearest instanceof net.minecraft.server.level.ServerPlayer sp) {
                        double ox = (level.random.nextFloat() - 0.5) * 6.0;
                        double oz = (level.random.nextFloat() - 0.5) * 6.0;
                        BlockPos target = nearest.blockPosition().offset((int)ox, 0, (int)oz);
                        // 足元が空気で頭上も空気の場所を探す
                        if (!level.getBlockState(target).isSolid() && !level.getBlockState(target.above()).isSolid()) {
                            mob.teleportTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
                        } else {
                            mob.teleportTo(nearest.getX(), nearest.getY(), nearest.getZ());
                        }
                        mob.setDeltaMovement(0, 0, 0);
                    } else if (isOutsideDungeon(mob, level)) {
                        // プレイヤーがいないのに場外にいる場合はフェイルセーフとして完全に削除する
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
                warden.increaseAngerAt(nearest, 10, true);
            }
        }

        // 蜘蛛の天井/壁埋まり修正
        if (mob instanceof net.minecraft.world.entity.monster.Spider ||
            mob instanceof net.minecraft.world.entity.monster.CaveSpider) {
            mob.setNoGravity(false);
            mob.noPhysics = false;
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
                com.levanilla.rogue.world.NpcManager.tickNpcLookAt(lobbyLevel, new net.minecraft.core.BlockPos(0, 201, 0));
            }
        }

        // フロアクリア判定 (既存ロジック)
        if (tick % GameConstants.FLOOR_CLEAR_CHECK_INTERVAL != 0) return;

        ServerLevel rogueLevel = server.getLevel(ROGUE_DIM);
        if (rogueLevel == null) return;

        List<ServerPlayer> playersCopy = new ArrayList<>(rogueLevel.players());
        for (ServerPlayer player : playersCopy) {
            com.levanilla.rogue.core.PlayerRunData data = RunManager.getData(player);
            if (!data.isRunActive()) continue;

            // フロア開始から5秒(100tick)はクリア判定をスキップ（モブスポーン猶予）
            long currentTick = server.getTickCount();
            if (currentTick - data.getFloorStartTick() < 100) continue;

            // プレイヤー固有の基準座標で判定
            AABB area = new AABB(data.getDungeonOrigin())
                .inflate(GameConstants.FLOOR_CLEAR_RADIUS);
            List<Mob> alive = rogueLevel.getEntitiesOfClass(Mob.class, area, Mob::isAlive);
            if (alive.isEmpty()) {
                // フロア制圧完了。抽出用の情報将校をスポーンさせる
                com.levanilla.rogue.world.NpcManager.spawnExtractionOfficer(rogueLevel, player.blockPosition());
                
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.tac_rogue.extraction_arrived"));
                
                data.setFloorCleared(true);
                RunManager.syncPlayer(player);

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

    // ===== 専用スロット強制 =====

    @SubscribeEvent
    public static void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (player.tickCount % GameConstants.INV_CHECK_INTERVAL != 0) return;
        if (player.level().dimension() != ROGUE_DIM && player.level().dimension() != LOBBY_DIM) return;

        enforceSlotRestrictions(player);
    }

    private static void enforceSlotRestrictions(ServerPlayer player) {
        net.minecraft.world.entity.player.Inventory inv = player.getInventory();

        // Slot 0-1: Gun only — evict non-guns to item slots
        for (int s = GameConstants.SLOT_GUN_START; s <= GameConstants.SLOT_GUN_END; s++) {
            net.minecraft.world.item.ItemStack stack = inv.getItem(s);
            if (!stack.isEmpty() && !isGun(stack)) {
                if (!moveToFirstEmpty(inv, stack, GameConstants.SLOT_ITEM_START, GameConstants.SLOT_ITEM_END)) {
                    player.drop(stack.copy(), false);
                }
                inv.setItem(s, net.minecraft.world.item.ItemStack.EMPTY);
            }
        }

        // Slot 2: Melee only — evict non-melee
        net.minecraft.world.item.ItemStack meleeItem = inv.getItem(GameConstants.SLOT_MELEE);
        if (!meleeItem.isEmpty() && !isMelee(meleeItem)) {
            if (!moveToFirstEmpty(inv, meleeItem, GameConstants.SLOT_ITEM_START, GameConstants.SLOT_ITEM_END)) {
                player.drop(meleeItem.copy(), false);
            }
            inv.setItem(GameConstants.SLOT_MELEE, net.minecraft.world.item.ItemStack.EMPTY);
        }

        // Slot 3-8: Items only — evict guns and melee
        for (int s = GameConstants.SLOT_ITEM_START; s <= GameConstants.SLOT_ITEM_END; s++) {
            net.minecraft.world.item.ItemStack stack = inv.getItem(s);
            if (!stack.isEmpty()) {
                if (isGun(stack)) {
                    if (!moveToFirstEmpty(inv, stack, GameConstants.SLOT_GUN_START, GameConstants.SLOT_GUN_END)) {
                        player.drop(stack.copy(), false);
                    }
                    inv.setItem(s, net.minecraft.world.item.ItemStack.EMPTY);
                } else if (isMelee(stack)) {
                    if (inv.getItem(GameConstants.SLOT_MELEE).isEmpty()) {
                        inv.setItem(GameConstants.SLOT_MELEE, stack.copy());
                    } else {
                        player.drop(stack.copy(), false);
                    }
                    inv.setItem(s, net.minecraft.world.item.ItemStack.EMPTY);
                }
            }
        }

        // Slot 9-12: Ammo only — evict non-ammo
        for (int s = GameConstants.SLOT_AMMO_GUN1_START; s <= GameConstants.SLOT_AMMO_GUN2_END; s++) {
            net.minecraft.world.item.ItemStack stack = inv.getItem(s);
            if (!stack.isEmpty() && !isAmmo(stack)) {
                if (!moveToFirstEmpty(inv, stack, GameConstants.SLOT_ITEM_START, GameConstants.SLOT_ITEM_END)) {
                    player.drop(stack.copy(), false);
                }
                inv.setItem(s, net.minecraft.world.item.ItemStack.EMPTY);
            }
        }

        // Also evict guns/melee from slots 9-12
        for (int s = GameConstants.SLOT_AMMO_GUN1_START; s <= GameConstants.SLOT_AMMO_GUN2_END; s++) {
            net.minecraft.world.item.ItemStack stack = inv.getItem(s);
            if (!stack.isEmpty() && (isGun(stack) || isMelee(stack))) {
                player.drop(stack.copy(), false);
                inv.setItem(s, net.minecraft.world.item.ItemStack.EMPTY);
            }
        }
    }

    // --- Slot helper methods ---

    private static boolean isGun(net.minecraft.world.item.ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains("GunId");
    }

    private static boolean isMelee(net.minecraft.world.item.ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains("MeleeWeaponId");
    }

    private static boolean isAmmo(net.minecraft.world.item.ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains("AmmoId");
    }

    /** Move stack to the first empty slot within [start, end]. Returns true if successful. */
    private static boolean moveToFirstEmpty(net.minecraft.world.entity.player.Inventory inv,
            net.minecraft.world.item.ItemStack stack, int start, int end) {
        for (int s = start; s <= end; s++) {
            if (inv.getItem(s).isEmpty()) {
                inv.setItem(s, stack.copy());
                return true;
            }
        }
        return false;
    }

    /** Mobがダンジョンの有効範囲外にいるかどうかを判定 */
    private static boolean isOutsideDungeon(Mob mob, net.minecraft.world.level.Level level) {
        // プレイヤーまでの距離で判定するか、直近プレイヤーのダンジョン原点を使う
        net.minecraft.world.entity.player.Player nearest = level.getNearestPlayer(mob, 200.0);
        if (nearest instanceof net.minecraft.server.level.ServerPlayer sp) {
            com.levanilla.rogue.core.PlayerRunData data = com.levanilla.rogue.core.RunManager.getData(sp);
            net.minecraft.core.BlockPos origin = data.getDungeonOrigin();
            if (origin != null) {
                double dx = Math.abs(mob.getX() - origin.getX());
                double dz = Math.abs(mob.getZ() - origin.getZ());
                // ダンジョン半径は最大78なので、85以上なら壁の外
                // Y座標は origin (ダンジョン床) から少し下～天井少し上を許容
                return dx > 85 || dz > 85 || mob.getY() < origin.getY() - 10 || mob.getY() > origin.getY() + 30;
            }
        }
        return false;
    }

    // ===== サーバー停止時のメモリクリア =====

    @SubscribeEvent
    public static void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        // シングルプレイワールド退店時など、別ワールドへのデータ汚染を防ぐためメモリをリセット
        // ※ NBT には保存済みなのでクリアしても問題ない
        com.levanilla.rogue.core.RunManager.clearMemory();
    }
}
