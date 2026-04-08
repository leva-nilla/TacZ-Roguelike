package com.levanilla.rogue.world;

import com.levanilla.rogue.core.DifficultyManager;
import com.levanilla.rogue.core.QuestManager;
import com.levanilla.rogue.core.RunManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

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
        List<Villager> existing = level.getEntitiesOfClass(Villager.class, area,
            e -> e.getTags().contains(NPC_TAG));
        
        // 正しく4人いる場合はそのまま再利用
        if (existing.size() == 4) return;

        // 数がおかしい（増殖している、または欠けている）場合は一度全消去してリセットする
        for (Villager v : existing) {
            v.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        }

        // コマンダー: NE 作戦室 (center +9, -8)
        spawnNpc(level,
            lobbyCenter.offset(9, 0, -8),
            NpcRole.COMMANDER,
            "§6[COMMANDER] §fCol. Graves");

        // 補給官: SE 武装ショップ (center +9, +8)
        spawnNpc(level,
            lobbyCenter.offset(9, 0, 8),
            NpcRole.QUARTERMASTER,
            "§a[SUPPLY] §fSgt. Knox");

        // 情報将校: NW 情報分析室 (center -9, -8)
        spawnNpc(level,
            lobbyCenter.offset(-9, 0, -8),
            NpcRole.INTEL_OFFICER,
            "§b[INTEL] §fLt. Hayes");

        // 衛生兵: SW 医療ベイ (center -9, +8)
        spawnNpc(level,
            lobbyCenter.offset(-9, 0, 8),
            NpcRole.MEDIC,
            "§d[MEDIC] §fDoc Rivera");
    }

    /** ダンジョンに脱出用NPC（情報将校）を配置 */
    public static void spawnExtractionOfficer(ServerLevel level, BlockPos pos) {
        spawnNpc(level, pos, NpcRole.INTEL_OFFICER, net.minecraft.network.chat.Component.translatable("npc.tac_rogue.extraction_officer").getString());
    }

    /** NPC（Villagerベース）を生成 */
    private static void spawnNpc(ServerLevel level, BlockPos pos, NpcRole role, String name) {
        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null) return;

        villager.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        villager.setCustomName(Component.literal(name));
        villager.setCustomNameVisible(true);
        villager.setInvulnerable(true);
        villager.setNoAi(true);  // 通常AIは停止 — lookAtPlayer は tick で処理
        villager.setSilent(true);
        villager.setPersistenceRequired();
        villager.addTag(NPC_TAG);
        villager.addTag("npc_role:" + role.id);
        villager.setNoGravity(false);

        level.addFreshEntity(villager);

        // 待機場所の装飾生成
        buildNpcStation(level, pos, role);
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
        List<Villager> npcs = level.getEntitiesOfClass(Villager.class, area,
            e -> e.getTags().contains(NPC_TAG));

        for (Villager npc : npcs) {
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
    public static boolean handleInteraction(ServerPlayer player, Villager npc) {
        if (!npc.getTags().contains(NPC_TAG)) return false;

        String roleTag = npc.getTags().stream()
            .filter(t -> t.startsWith("npc_role:"))
            .findFirst().orElse("");

        switch (roleTag) {
            case "npc_role:commander" -> {
                // クエストターミナルを開く
                showQuestTerminal(player);
                return true;
            }
            case "npc_role:quartermaster" -> {
                // ショップを開く (専用パケットで確実にGUIを開く)
                com.levanilla.rogue.networking.TacRogueNetworking.openShop(player);
                // ゴールド同期も送信
                RunManager.syncPlayer(player);
                return true;
            }
            case "npc_role:intel" -> {
                if (player.level().dimension() == com.levanilla.rogue.core.CommonEventHandler.ROGUE_DIM) {
                    // ダンジョン内: フロア脱出処理
                    com.levanilla.rogue.core.PlayerRunData data = RunManager.getData(player);
                    if (data.isRunActive() && data.isFloorCleared()) {
                        boolean isFarming = data.getCurrentFloor() < data.getMaxReachedFloor();
                        com.levanilla.rogue.networking.TacRogueNetworking.CHANNEL.send(
                            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                            new com.levanilla.rogue.networking.OpenFloorClearScreenMessage(isFarming));
                        data.setRunActive(false);
                        RunManager.syncPlayer(player);
                    }
                } else {
                    // ロビー内: フロア情報を表示
                    showIntelBriefing(player);
                }
                return true;
            }
            case "npc_role:medic" -> {
                // HP/スタミナ全回復
                player.setHealth(player.getMaxHealth());
                com.levanilla.rogue.core.StaminaManager.setStamina(
                    player, com.levanilla.rogue.core.StaminaManager.getMaxStamina(player));
                player.sendSystemMessage(Component.translatable("message.tac_rogue.medic_heal"));
                return true;
            }
        }
        return false;
    }

    /** クエストターミナル表示 — クライアントにクエストデータを送信してGUIを開く */
    private static void showQuestTerminal(ServerPlayer player) {
        QuestManager.QuestProgress progress = QuestManager.getProgress(player);
        int chapter = progress.currentChapter;
        List<QuestManager.Quest> quests = QuestManager.getChapterQuests(chapter);

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
              .append(completed).append(",");
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
    private static void showIntelBriefing(ServerPlayer player) {
        com.levanilla.rogue.core.PlayerRunData data = RunManager.getData(player);
        int floor = data.getCurrentFloor();
        player.sendSystemMessage(Component.literal("§b══════════════════════════════════"));
        player.sendSystemMessage(Component.translatable("message.tac_rogue.intel_header"));
        player.sendSystemMessage(Component.literal("§f  Current Floor: §e" + floor));
        player.sendSystemMessage(Component.literal("§f  Difficulty: §e" + DifficultyManager.getDifficulty().displayName));
        player.sendSystemMessage(Component.literal("§f  Enemy HP Scale: §c" + String.format("%.1fx", DifficultyManager.getHpScale(floor))));
        player.sendSystemMessage(Component.literal("§f  Gold Mult: §a" + String.format("%.1fx", DifficultyManager.getGoldMultiplier())));
        player.sendSystemMessage(Component.literal("§b══════════════════════════════════"));

        // フロア選択UIを開くパケットを送信
        long seed = player.server.getWorldData().worldGenOptions().seed();
        com.levanilla.rogue.networking.TacRogueNetworking.CHANNEL.send(
            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
            new com.levanilla.rogue.networking.SyncDataMessage("open_floor_selection:" + data.getMaxReachedFloor() + ":" + seed)
        );
    }
}
