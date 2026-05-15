package com.levanilla.rogue.world;

import com.levanilla.rogue.core.DifficultyManager;
import com.levanilla.rogue.core.ModEntities;
import com.levanilla.rogue.core.QuestManager;
import com.levanilla.rogue.core.RunManager;
import com.levanilla.rogue.networking.PopupNotificationMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ロビーNPCマネージャ — コマンダーNPCの配置・AI・インタラクション管理
 *
 * コマンダーNPCはロビーに常駐し:
 * - プレイヤーが近づくと視線で追従する
 * - 右クリックでクエストターミナルを開く
 * - 待機場所にはコマンドデスク装飾を配置
 */
public class NpcManager {

    /** NPCの種類 */
    public enum NpcRole {
        COMMANDER("§6Commander", "commander"),
        QUARTERMASTER("§aQuartermaster", "quartermaster"),
        INTEL_OFFICER("§bIntelligence Officer", "intel"),
        MEDIC("§dField Medic", "medic");

        public final String displayName;
        public final String id;
        NpcRole(String name, String id) { this.displayName = name; this.id = id; }
    }

    // NPC 識別用タグ
    private static final String NPC_TAG = "tac_rogue_npc";
    private static final String NPC_VISUAL_TAG = "tac_rogue_npc_visual";
    private static final int MENU_SESSION_TICKS = 20 * 30;
    private static final double MENU_ACTION_MAX_DISTANCE_SQR = 36.0;
    private static final Map<UUID, MenuSession> MENU_SESSIONS = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> ALLOWED_ACTIONS = Map.of(
        "commander", Set.of("quest", "talk"),
        "quartermaster", Set.of("shop", "talk"),
        "intel", Set.of("intel", "extract", "floor_select", "talk"),
        "medic", Set.of("heal", "talk")
    );

    private record MenuSession(int npcId, String role, String dimension, long expiresAtTick) {}

    /** ロビーにNPCを配置（既存NPCがなければ生成） */
    public static void ensureNpcsSpawned(ServerLevel level, BlockPos lobbyCenter) {
        // アンロードによるNPC検索漏れと増殖を防ぐため、ロビー周辺のチャンクを強制ロード
        int cx = lobbyCenter.getX() >> 4;
        int cz = lobbyCenter.getZ() >> 4;
        for (int i = -1; i <= 1; i++) {
            for (int k = -1; k <= 1; k++) {
                level.getChunk(cx + i, cz + k);
            }
        }

        // 既存NPCを検索
        AABB area = new AABB(lobbyCenter).inflate(30);
        List<TacRogueNpcEntity> existingCustom = level.getEntitiesOfClass(TacRogueNpcEntity.class, area,
            e -> e.getTags().contains(NPC_TAG));
        List<Villager> existing = level.getEntitiesOfClass(Villager.class, area,
            e -> e.getTags().contains(NPC_TAG));

        // 旧方式の不可視Villager/ArmorStandは新NPCへ移行したので掃除する
        for (Villager v : existing) {
            v.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        }
        for (ArmorStand stand : level.getEntitiesOfClass(ArmorStand.class, area, e -> e.getTags().contains(NPC_VISUAL_TAG))) {
            stand.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        }

        // 正しく4人いて、新ロビーの各区画に配置済みの場合はそのまま再利用
        if (existingCustom.size() == 4
            && hasNpcAt(existingCustom, lobbyCenter, NpcRole.COMMANDER, 13, -13)
            && hasNpcAt(existingCustom, lobbyCenter, NpcRole.QUARTERMASTER, 13, 13)
            && hasNpcAt(existingCustom, lobbyCenter, NpcRole.INTEL_OFFICER, -13, -13)
            && hasNpcAt(existingCustom, lobbyCenter, NpcRole.MEDIC, -13, 13)) {
            return;
        }

        // 数がおかしい（増殖している、または欠けている）場合は一度全消去してリセットする
        for (TacRogueNpcEntity npc : existingCustom) {
            npc.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        }

        // コマンダー: NE 作戦室
        spawnNpc(level,
            lobbyCenter.offset(13, 0, -13),
            NpcRole.COMMANDER,
            "§6[COMMANDER] §fCol. Graves");

        // 補給官: SE 武装ショップ
        spawnNpc(level,
            lobbyCenter.offset(13, 0, 13),
            NpcRole.QUARTERMASTER,
            "§a[SUPPLY] §fSgt. Knox");

        // 情報将校: NW 情報分析室
        spawnNpc(level,
            lobbyCenter.offset(-13, 0, -13),
            NpcRole.INTEL_OFFICER,
            "§b[INTEL] §fLt. Hayes");

        // 衛生兵: SW 医療ベイ
        spawnNpc(level,
            lobbyCenter.offset(-13, 0, 13),
            NpcRole.MEDIC,
            "§d[MEDIC] §fDoc Rivera");
    }

    private static boolean hasNpcAt(List<TacRogueNpcEntity> npcs, BlockPos center, NpcRole role, int dx, int dz) {
        double targetX = center.getX() + dx + 0.5;
        double targetZ = center.getZ() + dz + 0.5;
        for (TacRogueNpcEntity npc : npcs) {
            if (npc.getRole() != role) continue;
            if (Math.abs(npc.getX() - targetX) <= 1.25 && Math.abs(npc.getZ() - targetZ) <= 1.25) {
                return true;
            }
        }
        return false;
    }

    /** ダンジョンに脱出用NPC（情報将校）を配置 */
    public static TacRogueNpcEntity spawnExtractionOfficer(ServerLevel level, BlockPos pos, int floor) {
        TacRogueNpcEntity npc = spawnNpc(level, pos, NpcRole.INTEL_OFFICER, net.minecraft.network.chat.Component.translatable("npc.tac_rogue.extraction_officer").getString());
        if (npc != null) {
            npc.getPersistentData().putInt("TacRogueSpawnFloor", floor);
            npc.getPersistentData().putLong("TacRogueSpawnTick", level.getServer().getTickCount());
        }
        return npc;
    }

    public static BlockPos findExtractionSpawnNear(ServerLevel level, ServerPlayer player) {
        BlockPos base = player.blockPosition();
        Vec3 look = player.getLookAngle();
        Vec3 forward = new Vec3(look.x, 0.0D, look.z);
        if (forward.lengthSqr() < 0.001D) {
            forward = Vec3.directionFromRotation(0.0F, player.getYRot()).multiply(1.0D, 0.0D, 1.0D);
        }
        forward = forward.normalize();
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);

        double[][] preferred = {
            {forward.x * 2.0D, forward.z * 2.0D},
            {forward.x * 2.0D + right.x, forward.z * 2.0D + right.z},
            {forward.x * 2.0D - right.x, forward.z * 2.0D - right.z},
            {right.x * 2.0D, right.z * 2.0D},
            {-right.x * 2.0D, -right.z * 2.0D},
            {-forward.x * 2.0D, -forward.z * 2.0D}
        };
        for (double[] offset : preferred) {
            BlockPos pos = findSafeExtractionY(level, base.offset((int)Math.round(offset[0]), 0, (int)Math.round(offset[1])), base.getY());
            if (pos != null) return pos;
        }

        for (int radius = 1; radius <= 5; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    BlockPos pos = findSafeExtractionY(level, base.offset(dx, 0, dz), base.getY());
                    if (pos != null) return pos;
                }
            }
        }
        return base;
    }

    private static BlockPos findSafeExtractionY(ServerLevel level, BlockPos pos, int playerY) {
        for (int dy = 1; dy >= -4; dy--) {
            BlockPos candidate = new BlockPos(pos.getX(), playerY + dy, pos.getZ());
            if (isExtractionSpawnSafe(level, candidate)) return candidate;
        }
        return null;
    }

    private static boolean isExtractionSpawnSafe(ServerLevel level, BlockPos pos) {
        if (!level.getWorldBorder().isWithinBounds(pos)) return false;
        if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return false;
        if (!level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) return false;
        return !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty();
    }

    /** 独自NPCを生成 */
    private static TacRogueNpcEntity spawnNpc(ServerLevel level, BlockPos pos, NpcRole role, String name) {
        TacRogueNpcEntity npc = ModEntities.TAC_ROGUE_NPC.get().create(level);
        if (npc == null) return null;

        npc.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        npc.setCustomName(Component.literal(name));
        npc.setCustomNameVisible(true);
        npc.setInvulnerable(true);
        npc.setNoAi(true);
        npc.addTag(NPC_TAG);
        npc.setRole(role);

        level.addFreshEntity(npc);

        // 待機場所の装飾生成
        buildNpcStation(level, pos, role);
        return npc;
    }

    /** NPC 待機場所の装飾 — 部屋自体は LobbyGenerator で構築済み、ここでは足元のみ */
    private static void buildNpcStation(ServerLevel level, BlockPos pos, NpcRole role) {
        // NPC足元のカーペット (役割ごとの色分け)
        var carpet = switch (role) {
            case COMMANDER -> net.minecraft.world.level.block.Blocks.YELLOW_CARPET;
            case QUARTERMASTER -> net.minecraft.world.level.block.Blocks.GREEN_CARPET;
            case INTEL_OFFICER -> net.minecraft.world.level.block.Blocks.BLUE_CARPET;
            case MEDIC -> net.minecraft.world.level.block.Blocks.PINK_CARPET;
        };
        level.setBlockAndUpdate(pos, carpet.defaultBlockState());
    }

    /** NPCの視線追従（ServerTickで呼び出し） */
    public static void tickNpcLookAt(ServerLevel level, BlockPos lobbyCenter) {
        AABB area = new AABB(lobbyCenter).inflate(30);
        List<TacRogueNpcEntity> npcs = level.getEntitiesOfClass(TacRogueNpcEntity.class, area,
            e -> e.getTags().contains(NPC_TAG));

        for (TacRogueNpcEntity npc : npcs) {
            // 最も近いプレイヤーを探す (8ブロック以内)
            ServerPlayer nearest = null;
            double nearestDist = 8.0;
            for (ServerPlayer player : level.players()) {
                double dist = npc.distanceTo(player);
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = player;
                }
            }

            if (nearest != null) {
                // プレイヤーの方を向く
                Vec3 toPlayer = nearest.position().subtract(npc.position());
                double yaw = Math.toDegrees(Math.atan2(-toPlayer.x, toPlayer.z));
                double pitch = Math.toDegrees(-Math.atan2(toPlayer.y - 1.0, Math.sqrt(toPlayer.x * toPlayer.x + toPlayer.z * toPlayer.z)));
                npc.setYRot((float) yaw);
                npc.setYHeadRot((float) yaw);
                npc.setXRot((float) pitch);
                npc.yBodyRot = (float) yaw;
            }
        }
    }

    /** NPC インタラクション処理 */
    public static void handleCustomNpcInteraction(ServerPlayer player, TacRogueNpcEntity npc) {
        NpcRole role = npc.getRole();
        MENU_SESSIONS.put(player.getUUID(), new MenuSession(
            npc.getId(),
            role.id,
            player.level().dimension().location().toString(),
            player.server.getTickCount() + MENU_SESSION_TICKS
        ));
        if (role == NpcRole.INTEL_OFFICER && player.level().dimension() == com.levanilla.rogue.core.CommonEventHandler.ROGUE_DIM) {
            com.levanilla.rogue.core.PlayerRunData data = RunManager.getData(player);
            com.levanilla.rogue.networking.TacRogueNetworking.openNpcMenu(player, role.id, true, data.isFloorCleared());
        } else {
            com.levanilla.rogue.networking.TacRogueNetworking.openNpcMenu(player, role.id, false, false);
        }
    }

    public static void handleMenuAction(ServerPlayer player, String role, String action) {
        if (!validateMenuAction(player, role, action)) {
            PopupNotificationMessage.send(player, PopupNotificationMessage.PopupType.WARNING,
                Component.translatable("popup.tac_rogue.npc.intel.title"),
                Component.translatable("gui.tac_rogue.npc_menu.too_far"), 70);
            return;
        }

        if ("commander".equals(role)) {
            if ("quest".equals(action)) {
                showQuestTerminal(player);
            } else {
                PopupNotificationMessage.send(player, PopupNotificationMessage.PopupType.NPC,
                    Component.translatable("popup.tac_rogue.npc.commander.title"),
                    Component.translatable("popup.tac_rogue.npc.commander.body", QuestManager.getProgress(player).currentChapter), 90);
            }
            return;
        }
        if ("quartermaster".equals(role)) {
            if ("shop".equals(action)) {
                com.levanilla.rogue.networking.TacRogueNetworking.openShop(player);
                RunManager.syncPlayer(player);
            } else {
                PopupNotificationMessage.send(player, PopupNotificationMessage.PopupType.NPC,
                    Component.translatable("popup.tac_rogue.npc.quartermaster.title"),
                    Component.translatable("popup.tac_rogue.npc.quartermaster.body"), 90);
            }
            return;
        }
        if ("intel".equals(role)) {
            if ("extract".equals(action)) {
                com.levanilla.rogue.core.PlayerRunData data = RunManager.getData(player);
                if (player.level().dimension() == com.levanilla.rogue.core.CommonEventHandler.ROGUE_DIM
                    && data.isRunActive() && data.isFloorCleared()) {
                    boolean isFarming = data.getCurrentFloor() + 1 < data.getMaxReachedFloor();
                    com.levanilla.rogue.networking.OpenFloorClearScreenMessage.sendFloorClear(player, isFarming);
                    data.setRunActive(false);
                    RunManager.syncPlayer(player);
                } else {
                    PopupNotificationMessage.send(player, PopupNotificationMessage.PopupType.WARNING,
                        Component.translatable("popup.tac_rogue.npc.intel.title"),
                        Component.translatable("gui.tac_rogue.npc_menu.not_ready"), 80);
                }
            } else if ("floor_select".equals(action)) {
                openFloorSelection(player);
            } else {
                showIntelBriefing(player, false);
            }
            return;
        }
        if ("medic".equals(role)) {
            if ("heal".equals(action)) {
                player.setHealth(player.getMaxHealth());
                com.levanilla.rogue.core.StaminaManager.setStamina(player, com.levanilla.rogue.core.StaminaManager.getMaxStamina(player));
                PopupNotificationMessage.send(player, PopupNotificationMessage.PopupType.NPC,
                    Component.translatable("popup.tac_rogue.npc.medic.title"),
                    Component.translatable("message.tac_rogue.medic_heal"), 90);
            } else {
                PopupNotificationMessage.send(player, PopupNotificationMessage.PopupType.NPC,
                    Component.translatable("popup.tac_rogue.npc.medic.title"),
                    Component.translatable("gui.tac_rogue.npc_menu.medic.body"), 90);
            }
        }
    }

    public static boolean canUseQuartermaster(ServerPlayer player) {
        return validateMenuAction(player, "quartermaster", "shop");
    }

    public static void clearMemory() {
        MENU_SESSIONS.clear();
    }

    private static boolean validateMenuAction(ServerPlayer player, String role, String action) {
        if (role == null || action == null) return false;
        Set<String> allowed = ALLOWED_ACTIONS.get(role);
        if (allowed == null || !allowed.contains(action)) return false;

        MenuSession session = MENU_SESSIONS.get(player.getUUID());
        if (session == null) return false;
        if (!role.equals(session.role())) return false;
        if (player.server.getTickCount() > session.expiresAtTick()) {
            MENU_SESSIONS.remove(player.getUUID());
            return false;
        }
        if (!player.level().dimension().location().toString().equals(session.dimension())) return false;

        Entity entity = player.serverLevel().getEntity(session.npcId());
        if (!(entity instanceof TacRogueNpcEntity npc)) return false;
        if (!npc.getTags().contains(NPC_TAG)) return false;
        if (!npc.getRole().id.equals(role)) return false;
        if (player.distanceToSqr(npc) > MENU_ACTION_MAX_DISTANCE_SQR) return false;

        boolean inDungeon = player.level().dimension() == com.levanilla.rogue.core.CommonEventHandler.ROGUE_DIM;
        if ("extract".equals(action)) return inDungeon;
        if ("floor_select".equals(action)) return !inDungeon;
        return true;
    }

    /** クエストターミナル表示 — クライアントにクエストデータを送信してGUIを開く */
    private static void showQuestTerminal(ServerPlayer player) {
        QuestManager.QuestProgress progress = QuestManager.getProgress(player);
        int chapter = progress.currentChapter;
        List<QuestManager.Quest> quests = QuestManager.getChapterQuests(chapter);
        PopupNotificationMessage.send(
            player,
            PopupNotificationMessage.PopupType.NPC,
            Component.translatable("popup.tac_rogue.npc.commander.title"),
            Component.translatable("popup.tac_rogue.npc.commander.body", chapter),
            110
        );

        // クエストデータをクライアントへ送信
        StringBuilder sb = new StringBuilder();
        sb.append("quest_data:").append(chapter).append("|");
        for (QuestManager.Quest quest : quests) {
            boolean completed = progress.completedQuests.contains(quest.id);
            int current = progress.questProgress.getOrDefault(quest.id, 0);
            // format: id;typeLangKey;target;progress;goldReward;completed
            sb.append(quest.id).append(";")
              .append(quest.type.langKey).append(";")
              .append(quest.targetAmount).append(";")
              .append(current).append(";")
              .append(quest.goldReward).append(";")
              .append(completed).append(";")
              .append(quest.role.langKey).append(";")
              .append(quest.rareWeaponReward).append(";")
              .append(quest.type.langKey).append(".desc").append(",");
        }

        com.levanilla.rogue.networking.TacRogueNetworking.CHANNEL.send(
            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
            new com.levanilla.rogue.networking.SyncDataMessage(sb.toString()));

        // クライアント側でQUESTタブを開く
        com.levanilla.rogue.networking.TacRogueNetworking.CHANNEL.send(
            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
            new com.levanilla.rogue.networking.SyncDataMessage("open_quest_tab"));
    }

    /** インテル情報 */
    private static void showIntelBriefing(ServerPlayer player, boolean openFloorSelection) {
        com.levanilla.rogue.core.PlayerRunData data = RunManager.getData(player);
        int floor = data.getCurrentFloor();
        PopupNotificationMessage.send(
            player,
            PopupNotificationMessage.PopupType.NPC,
            Component.translatable("popup.tac_rogue.npc.intel.title"),
            Component.translatable(
                "popup.tac_rogue.npc.intel.body",
                floor,
                DifficultyManager.getDifficulty().displayName,
                String.format(java.util.Locale.ROOT, "%.1fx", DifficultyManager.getHpScale(floor)),
                String.format(java.util.Locale.ROOT, "%.1fx", DifficultyManager.getGoldMultiplier())
            ),
            160
        );

        if (openFloorSelection) {
            openFloorSelection(player);
        }
    }

    private static void openFloorSelection(ServerPlayer player) {
        com.levanilla.rogue.core.PlayerRunData data = RunManager.getData(player);
        long seed = player.server.getWorldData().worldGenOptions().seed();
        com.levanilla.rogue.networking.TacRogueNetworking.CHANNEL.send(
            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
            new com.levanilla.rogue.networking.SyncDataMessage("open_floor_selection:" + data.getMaxReachedFloor() + ":" + seed)
        );
    }
}
