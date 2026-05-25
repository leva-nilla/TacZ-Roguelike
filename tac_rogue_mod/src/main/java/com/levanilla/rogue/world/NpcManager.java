package com.levanilla.rogue.world;

import com.levanilla.rogue.core.DifficultyManager;
import com.levanilla.rogue.core.CurrencyManager;
import com.levanilla.rogue.core.GameConstants;
import com.levanilla.rogue.core.ModEntities;
import com.levanilla.rogue.core.PriceManager;
import com.levanilla.rogue.core.QuestManager;
import com.levanilla.rogue.core.RunManager;
import com.levanilla.rogue.core.StashSavedData;
import com.levanilla.rogue.core.TacZRegistryHelper;
import com.levanilla.rogue.core.WeaponRarity;
import com.levanilla.rogue.core.registry.AttachmentDatabase;
import com.levanilla.rogue.core.registry.ShopCatalog;
import com.levanilla.rogue.core.service.GoldGainService;
import com.levanilla.rogue.core.service.RogueItemFactory;
import com.levanilla.rogue.core.service.ShopPlacementService;
import com.levanilla.rogue.networking.PopupNotificationMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.EnumMap;
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
    private static final String LOBBY_NPC_TAG = "tac_rogue_lobby_npc";
    private static final String NPC_VISUAL_TAG = "tac_rogue_npc_visual";
    private static final String LOADOUT_PRESET_KEY = "TacRogueLoadoutPresetV1";
    private static final String MEDICAL_BUFF_UNTIL_KEY = "TacRogueMedicalBuffUntil";
    private static final int MENU_SESSION_TICKS = 20 * 60 * 5;
    private static final double MENU_ACTION_MAX_DISTANCE_SQR = 144.0;
    private static final int FIELD_BUFF_COST = 2000;
    private static final int MEDICAL_REFILL_COST = 850;
    private static final int BULK_AMMO_STACKS = 3;
    private static final Map<UUID, MenuSession> MENU_SESSIONS = new ConcurrentHashMap<>();
    private static final Map<String, Long> DELAYED_LOBBY_NORMALIZE_TICKS = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> ALLOWED_ACTIONS = Map.of(
        "commander", Set.of("quest", "talk", "operation_plan"),
        "quartermaster", Set.of("shop", "talk", "deep_operations", "save_loadout", "restore_loadout",
            "bulk_ammo", "attachment_check", "sell_loose_attachments", "sync_service_stash"),
        "intel", Set.of("intel", "extract", "floor_select", "talk", "deep_operations"),
        "medic", Set.of("heal", "talk", "field_buff", "medical_refill", "critical_briefing")
    );

    private record MenuSession(int npcId, String role, String dimension, long expiresAtTick) {}
    private record LoadoutSource(boolean stash, int slot) {}

    public static void scheduleLobbyNormalization(ServerLevel level, int delayTicks) {
        if (level == null) return;
        long dueTick = level.getServer().getTickCount() + Math.max(1, delayTicks);
        DELAYED_LOBBY_NORMALIZE_TICKS.merge(
            level.dimension().location().toString(),
            dueTick,
            Math::min
        );
    }

    public static void tickLobbyMaintenance(ServerLevel level, BlockPos lobbyCenter) {
        String dimension = level.dimension().location().toString();
        Long dueTick = DELAYED_LOBBY_NORMALIZE_TICKS.get(dimension);
        if (dueTick != null && level.getServer().getTickCount() >= dueTick) {
            DELAYED_LOBBY_NORMALIZE_TICKS.remove(dimension);
            ensureNpcsSpawned(level, lobbyCenter);
        }
        tickNpcLookAt(level, lobbyCenter);
    }

    /** ロビーにNPCを配置（既存NPCがなければ生成） */
    public static void ensureNpcsSpawned(ServerLevel level, BlockPos lobbyCenter) {
        // アンロードによるNPC検索漏れと増殖を防ぐため、ロビー周辺のチャンクを強制ロード
        int cx = lobbyCenter.getX() >> 4;
        int cz = lobbyCenter.getZ() >> 4;
        for (int i = -6; i <= 6; i++) {
            for (int k = -6; k <= 6; k++) {
                level.getChunk(cx + i, cz + k);
            }
        }

        // 既存NPCを広めに検索。タグ漏れ・旧位置・再入場後の残骸もここで正規化する。
        AABB area = new AABB(lobbyCenter).inflate(128.0D, 64.0D, 128.0D);
        List<TacRogueNpcEntity> existingCustom = level.getEntitiesOfClass(TacRogueNpcEntity.class, area,
            TacRogueNpcEntity::isAlive);
        List<Villager> existing = level.getEntitiesOfClass(Villager.class, area,
            e -> e.getTags().contains(NPC_TAG));

        // 旧方式の不可視Villager/ArmorStandは新NPCへ移行したので掃除する
        for (Villager v : existing) {
            v.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        }
        for (ArmorStand stand : level.getEntitiesOfClass(ArmorStand.class, area, e -> e.getTags().contains(NPC_VISUAL_TAG))) {
            stand.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        }

        Map<NpcRole, TacRogueNpcEntity> keepers = new EnumMap<>(NpcRole.class);
        for (NpcRole role : NpcRole.values()) {
            TacRogueNpcEntity best = null;
            double bestDistance = Double.MAX_VALUE;
            Vec3 target = Vec3.atBottomCenterOf(lobbyNpcPos(lobbyCenter, role));
            for (TacRogueNpcEntity npc : existingCustom) {
                if (resolveLobbyRole(npc) != role || !npc.isAlive()) continue;
                double distance = npc.position().distanceToSqr(target);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = npc;
                }
            }
            if (best != null) {
                keepers.put(role, best);
                normalizeLobbyNpc(best, role, target);
            }
        }

        for (TacRogueNpcEntity npc : existingCustom) {
            NpcRole role = resolveLobbyRole(npc);
            TacRogueNpcEntity keeper = keepers.get(role);
            if (keeper == null || keeper.getId() != npc.getId()) {
                npc.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            }
        }

        for (NpcRole role : NpcRole.values()) {
            if (!keepers.containsKey(role)) {
                spawnLobbyNpc(level, lobbyNpcPos(lobbyCenter, role), role, lobbyNpcName(role));
            }
        }
    }

    private static NpcRole resolveLobbyRole(TacRogueNpcEntity npc) {
        String stored = npc.getPersistentData().getString("TacRogueLobbyRole");
        if (stored == null || stored.isBlank()) {
            for (String tag : npc.getTags()) {
                if (tag.startsWith("npc_role:")) {
                    stored = tag.substring("npc_role:".length());
                    break;
                }
            }
        }
        if (stored != null && !stored.isBlank()) {
            for (NpcRole role : NpcRole.values()) {
                if (role.id.equals(stored)) return role;
            }
        }
        return npc.getRole();
    }

    private static void normalizeLobbyNpc(TacRogueNpcEntity npc, NpcRole role, Vec3 target) {
        npc.addTag(NPC_TAG);
        npc.addTag(LOBBY_NPC_TAG);
        npc.setRole(role);
        npc.markLobbyNpc(role);
        npc.setPos(target.x, target.y, target.z);
        npc.setCustomName(Component.literal(lobbyNpcName(role)));
        npc.setCustomNameVisible(true);
        npc.setInvulnerable(true);
        npc.setNoAi(true);
        npc.setPersistenceRequired();
    }

    private static BlockPos lobbyNpcPos(BlockPos center, NpcRole role) {
        return switch (role) {
            case COMMANDER -> center.offset(13, 0, -13);
            case QUARTERMASTER -> center.offset(13, 0, 13);
            case INTEL_OFFICER -> center.offset(-13, 0, -13);
            case MEDIC -> center.offset(-13, 0, 13);
        };
    }

    private static String lobbyNpcName(NpcRole role) {
        return switch (role) {
            case COMMANDER -> "§6[COMMANDER] §fCol. Graves";
            case QUARTERMASTER -> "§a[SUPPLY] §fSgt. Knox";
            case INTEL_OFFICER -> "§b[INTEL] §fLt. Hayes";
            case MEDIC -> "§d[MEDIC] §fDoc Rivera";
        };
    }

    /** ダンジョンに脱出用NPC（情報将校）を配置 */
    public static TacRogueNpcEntity spawnExtractionOfficer(ServerLevel level, BlockPos pos, int floor) {
        TacRogueNpcEntity npc = spawnNpc(level, pos, NpcRole.INTEL_OFFICER,
            net.minecraft.network.chat.Component.translatable("npc.tac_rogue.extraction_officer").getString(), false);
        if (npc != null) {
            npc.markExtractionOfficer();
            npc.getPersistentData().putInt("TacRogueSpawnFloor", floor);
            npc.getPersistentData().putLong("TacRogueSpawnTick", level.getServer().getTickCount());
        }
        return npc;
    }

    /** Objective completion support team visual. They are temporary and non-menu NPCs. */
    public static TacRogueNpcEntity spawnSupportOperator(ServerLevel level, BlockPos pos, int floor, int index) {
        String[] callsigns = {"Raptor", "Viper", "Specter", "Warden"};
        TacRogueNpcEntity npc = spawnNpc(level, pos, NpcRole.COMMANDER,
            "§8[SOF] §f" + callsigns[Math.floorMod(index, callsigns.length)] + "-" + (index + 1), false);
        if (npc != null) {
            npc.markSupportOperator(20 * 30);
            npc.getPersistentData().putInt("TacRogueSpawnFloor", floor);
            npc.getPersistentData().putLong("TacRogueSpawnTick", level.getServer().getTickCount());
            ItemStack gun = createSupportGun(index);
            if (!gun.isEmpty()) {
                npc.setItemSlot(EquipmentSlot.MAINHAND, gun);
                npc.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
            }
        }
        return npc;
    }

    private static ItemStack createSupportGun(int index) {
        List<ShopCatalog.ShopItem> candidates = new ArrayList<>();
        candidates.addAll(TacZRegistryHelper.getItemsByCategory(ShopCatalog.Category.SMG));
        candidates.addAll(TacZRegistryHelper.getItemsByCategory(ShopCatalog.Category.RIFLE));
        if (!candidates.isEmpty()) {
            String id = candidates.get(Math.floorMod(index, candidates.size())).id;
            return RogueItemFactory.createGunStack(id, WeaponRarity.Rarity.RARE);
        }
        List<String> allGuns = TacZRegistryHelper.getAllGunIds();
        if (!allGuns.isEmpty()) {
            return RogueItemFactory.createGunStack(allGuns.get(Math.floorMod(index, allGuns.size())), WeaponRarity.Rarity.RARE);
        }
        return ItemStack.EMPTY;
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
    private static TacRogueNpcEntity spawnLobbyNpc(ServerLevel level, BlockPos pos, NpcRole role, String name) {
        return spawnNpc(level, pos, role, name, true);
    }

    private static TacRogueNpcEntity spawnNpc(ServerLevel level, BlockPos pos, NpcRole role, String name, boolean lobbyNpc) {
        TacRogueNpcEntity npc = ModEntities.TAC_ROGUE_NPC.get().create(level);
        if (npc == null) return null;

        npc.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        npc.setCustomName(Component.literal(name));
        npc.setCustomNameVisible(true);
        npc.setInvulnerable(true);
        npc.setNoAi(true);
        npc.addTag(NPC_TAG);
        npc.setRole(role);
        if (lobbyNpc) {
            npc.markLobbyNpc(role);
        }

        level.addFreshEntity(npc);

        if (lobbyNpc) {
            buildNpcStation(level, pos, role);
        }
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
        if (npc.getTags().contains("tac_rogue_support_npc")) {
            PopupNotificationMessage.send(
                player,
                PopupNotificationMessage.PopupType.SYSTEM,
                Component.translatable("popup.tac_rogue.objective_support.title"),
                Component.translatable("message.tac_rogue.objective_support_ready"),
                80);
            return;
        }
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
            } else if ("operation_plan".equals(action)) {
                showOperationPlan(player);
            } else {
                PopupNotificationMessage.send(player, PopupNotificationMessage.PopupType.NPC,
                    Component.translatable("popup.tac_rogue.npc.commander.title"),
                    npcDialogue(player, "popup.tac_rogue.npc.commander.body", QuestManager.getProgress(player).currentChapter), 90);
            }
            return;
        }
        if ("quartermaster".equals(role)) {
            if ("shop".equals(action)) {
                com.levanilla.rogue.networking.TacRogueNetworking.openShop(player);
                RunManager.syncPlayer(player);
            } else if ("deep_operations".equals(action)) {
                openDeepOperations(player);
            } else if ("save_loadout".equals(action)) {
                saveLoadoutPreset(player);
            } else if ("restore_loadout".equals(action)) {
                restoreLoadoutPreset(player);
            } else if ("bulk_ammo".equals(action)) {
                bulkBuyAmmo(player);
            } else if ("attachment_check".equals(action)) {
                checkAttachmentCompatibility(player);
            } else if ("sell_loose_attachments".equals(action)) {
                sellLooseIncompatibleAttachments(player);
            } else if ("sync_service_stash".equals(action)) {
                sendServiceLoadoutSummary(player);
            } else {
                PopupNotificationMessage.send(player, PopupNotificationMessage.PopupType.NPC,
                    Component.translatable("popup.tac_rogue.npc.quartermaster.title"),
                    npcDialogue(player, "popup.tac_rogue.npc.quartermaster.body"), 90);
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
            } else if ("deep_operations".equals(action)) {
                openDeepOperations(player);
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
            } else if ("field_buff".equals(action)) {
                applyFieldBuff(player);
            } else if ("medical_refill".equals(action)) {
                refillMedicalSupplies(player);
            } else if ("critical_briefing".equals(action)) {
                PopupNotificationMessage.send(player, PopupNotificationMessage.PopupType.NPC,
                    Component.translatable("popup.tac_rogue.npc.medic.title"),
                    Component.translatable("message.tac_rogue.medic_critical_briefing"), 180);
            } else {
                PopupNotificationMessage.send(player, PopupNotificationMessage.PopupType.NPC,
                    Component.translatable("popup.tac_rogue.npc.medic.title"),
                    npcDialogue(player, "popup.tac_rogue.npc.medic.body"), 90);
            }
        }
    }

    public static boolean canUseQuartermaster(ServerPlayer player) {
        return validateMenuAction(player, "quartermaster", "shop");
    }

    public static boolean canUseDeepOperations(ServerPlayer player) {
        if (player == null || player.level().dimension() != com.levanilla.rogue.core.CommonEventHandler.LOBBY_DIM) {
            return false;
        }
        AABB area = player.getBoundingBox().inflate(Math.sqrt(MENU_ACTION_MAX_DISTANCE_SQR));
        return !player.serverLevel().getEntitiesOfClass(TacRogueNpcEntity.class, area, npc ->
            npc.isAlive()
                && npc.getTags().contains(NPC_TAG)
                && (npc.getRole() == NpcRole.QUARTERMASTER || npc.getRole() == NpcRole.INTEL_OFFICER)
                && player.distanceToSqr(npc) <= MENU_ACTION_MAX_DISTANCE_SQR
        ).isEmpty();
    }

    public static void swapServiceLoadoutSlot(ServerPlayer player, int inventorySlot, int stashSlot) {
        if (!validateMenuAction(player, "quartermaster", "sync_service_stash")) {
            PopupNotificationMessage.send(player, PopupNotificationMessage.PopupType.WARNING,
                Component.translatable("popup.tac_rogue.npc.quartermaster.title"),
                Component.translatable("gui.tac_rogue.npc_menu.too_far"), 70);
            return;
        }
        int maxSlot = maxManagedInventorySlot(player);
        if (inventorySlot < 0 || inventorySlot > maxSlot || inventorySlot >= player.getInventory().items.size()) {
            PopupNotificationMessage.send(player, PopupNotificationMessage.PopupType.WARNING,
                Component.translatable("popup.tac_rogue.npc.quartermaster.title"),
                Component.translatable("message.tac_rogue.loadout_swap_invalid"), 90);
            sendServiceLoadoutSummary(player);
            return;
        }
        StashSavedData.PlayerStash stash = StashSavedData.get(player.serverLevel()).getStash(player.getUUID());
        if (stashSlot < 0 || stashSlot >= stash.unlockedLines * 9) {
            PopupNotificationMessage.send(player, PopupNotificationMessage.PopupType.WARNING,
                Component.translatable("popup.tac_rogue.npc.quartermaster.title"),
                Component.translatable("message.tac_rogue.loadout_swap_invalid"), 90);
            sendServiceLoadoutSummary(player);
            return;
        }

        ItemStack inventoryStack = player.getInventory().items.get(inventorySlot);
        ItemStack stashStack = stash.getItem(stashSlot);
        player.getInventory().items.set(inventorySlot, stashStack.copy());
        stash.setItem(stashSlot, inventoryStack.copy());
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) {
            player.containerMenu.broadcastChanges();
        }
        PopupNotificationMessage.send(player, PopupNotificationMessage.PopupType.REWARD,
            Component.translatable("popup.tac_rogue.npc.quartermaster.title"),
            Component.translatable("message.tac_rogue.loadout_swap_done"), 80);
        sendServiceLoadoutSummary(player);
    }

    private static void showOperationPlan(ServerPlayer player) {
        com.levanilla.rogue.core.PlayerRunData data = RunManager.getData(player);
        int floor = Math.max(1, data.getCurrentFloor());
        int nextMilestone = ((floor - 1) / 5 + 1) * 5;
        int nextBoss = ((floor - 1) / 10 + 1) * 10;
        QuestManager.QuestProgress progress = QuestManager.getProgress(player);
        PopupNotificationMessage.send(player, PopupNotificationMessage.PopupType.NPC,
            Component.translatable("popup.tac_rogue.npc.commander.title"),
            Component.translatable("message.tac_rogue.commander_operation_plan",
                floor,
                data.getMaxReachedFloor(),
                nextMilestone,
                nextBoss,
                DifficultyManager.getDifficulty().displayName,
                progress.currentChapter),
            180);
    }

    private static void applyFieldBuff(ServerPlayer player) {
        if (!requireLobby(player, "message.tac_rogue.medic_lobby_only")) return;
        if (!consumeGoldOrWarn(player, FIELD_BUFF_COST, Component.translatable("popup.tac_rogue.npc.medic.title"))) return;
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 20 * 60 * 5, 0, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 60 * 4, 0, true, true));
        player.getPersistentData().putLong(MEDICAL_BUFF_UNTIL_KEY, player.level().getGameTime() + 20L * 60L * 5L);
        com.levanilla.rogue.core.StaminaManager.setStamina(player, com.levanilla.rogue.core.StaminaManager.getMaxStamina(player));
        RunManager.syncPlayer(player);
        PopupNotificationMessage.send(player, PopupNotificationMessage.PopupType.REWARD,
            Component.translatable("popup.tac_rogue.npc.medic.title"),
            Component.translatable("message.tac_rogue.medic_field_buff", FIELD_BUFF_COST), 120);
    }

    private static void refillMedicalSupplies(ServerPlayer player) {
        if (!consumeGoldOrWarn(player, MEDICAL_REFILL_COST, Component.translatable("popup.tac_rogue.npc.medic.title"))) return;
        ShopPlacementService.placeRewardItem(player, RogueItemFactory.createRecoveryItem("rogue:medkit"), "MEDKIT");
        ShopPlacementService.placeRewardItem(player, RogueItemFactory.createRecoveryItem("rogue:adrenaline"), "ADRENALINE");
        ShopPlacementService.placeRewardItem(player, RogueItemFactory.createRecoveryItem("rogue:bandage"), "BANDAGE");
        RunManager.syncPlayer(player);
        PopupNotificationMessage.send(player, PopupNotificationMessage.PopupType.REWARD,
            Component.translatable("popup.tac_rogue.npc.medic.title"),
            Component.translatable("message.tac_rogue.medic_refill", MEDICAL_REFILL_COST), 130);
    }

    private static void saveLoadoutPreset(ServerPlayer player) {
        ListTag preset = new ListTag();
        int maxSlot = maxManagedInventorySlot(player);
        for (int slot = 0; slot <= maxSlot && slot < player.getInventory().items.size(); slot++) {
            ItemStack stack = player.getInventory().items.get(slot);
            if (stack.isEmpty()) continue;
            String key = loadoutKey(stack);
            if (key.isBlank()) continue;
            CompoundTag entry = new CompoundTag();
            entry.putInt("Slot", slot);
            entry.putString("Key", key);
            preset.add(entry);
        }
        player.getPersistentData().put(LOADOUT_PRESET_KEY, preset);
        PopupNotificationMessage.send(player, PopupNotificationMessage.PopupType.NPC,
            Component.translatable("popup.tac_rogue.npc.quartermaster.title"),
            Component.translatable("message.tac_rogue.loadout_saved", preset.size()), 100);
        sendServiceLoadoutSummary(player);
    }

    private static void restoreLoadoutPreset(ServerPlayer player) {
        if (!player.getPersistentData().contains(LOADOUT_PRESET_KEY)) {
            PopupNotificationMessage.send(player, PopupNotificationMessage.PopupType.WARNING,
                Component.translatable("popup.tac_rogue.npc.quartermaster.title"),
                Component.translatable("message.tac_rogue.loadout_missing"), 90);
            return;
        }
        ListTag preset = player.getPersistentData().getList(LOADOUT_PRESET_KEY, 10);
        int restored = 0;
        int maxSlot = maxManagedInventorySlot(player);
        boolean[] lockedSlots = new boolean[player.getInventory().items.size()];
        StashSavedData data = StashSavedData.get(player.serverLevel());
        StashSavedData.PlayerStash stash = data.getStash(player.getUUID());
        for (int i = 0; i < preset.size(); i++) {
            CompoundTag entry = preset.getCompound(i);
            int targetSlot = entry.getInt("Slot");
            if (targetSlot < 0 || targetSlot > maxSlot || targetSlot >= player.getInventory().items.size()) continue;
            String key = entry.getString("Key");
            if (key.equals(loadoutKey(player.getInventory().items.get(targetSlot)))) {
                lockedSlots[targetSlot] = true;
                restored++;
                continue;
            }
            LoadoutSource sourceRef = findLoadoutSource(player, stash, key, maxSlot, lockedSlots);
            if (sourceRef == null) continue;
            if (sourceRef.stash()) {
                ItemStack source = stash.getItem(sourceRef.slot());
                ItemStack target = player.getInventory().items.get(targetSlot);
                player.getInventory().items.set(targetSlot, source);
                stash.setItem(sourceRef.slot(), target);
            } else if (sourceRef.slot() != targetSlot) {
                ItemStack source = player.getInventory().items.get(sourceRef.slot());
                ItemStack target = player.getInventory().items.get(targetSlot);
                player.getInventory().items.set(targetSlot, source);
                player.getInventory().items.set(sourceRef.slot(), target);
            }
            lockedSlots[targetSlot] = true;
            restored++;
        }
        player.getInventory().setChanged();
        PopupNotificationMessage.send(player, restored > 0 ? PopupNotificationMessage.PopupType.REWARD : PopupNotificationMessage.PopupType.WARNING,
            Component.translatable("popup.tac_rogue.npc.quartermaster.title"),
            Component.translatable("message.tac_rogue.loadout_restored", restored, preset.size()), 110);
        sendServiceLoadoutSummary(player);
    }

    private static void bulkBuyAmmo(ServerPlayer player) {
        Set<String> ammoIds = equippedAmmoIds(player);
        if (ammoIds.isEmpty()) {
            PopupNotificationMessage.send(player, PopupNotificationMessage.PopupType.WARNING,
                Component.translatable("popup.tac_rogue.npc.quartermaster.title"),
                Component.translatable("message.tac_rogue.bulk_ammo_no_gun"), 90);
            return;
        }
        int totalCost = 0;
        for (String ammoId : ammoIds) {
            totalCost += PriceManager.getAmmoBuyPrice(ammoId) * BULK_AMMO_STACKS;
        }
        if (!consumeGoldOrWarn(player, totalCost, Component.translatable("popup.tac_rogue.npc.quartermaster.title"))) return;
        int stacks = 0;
        for (String ammoId : ammoIds) {
            for (int i = 0; i < BULK_AMMO_STACKS; i++) {
                ItemStack stack = RogueItemFactory.createShopAmmoStack(ammoId);
                if (!stack.isEmpty()) {
                    ShopPlacementService.placePurchasedItem(player, stack, ammoId, 0);
                    stacks++;
                }
            }
        }
        RunManager.syncPlayer(player);
        PopupNotificationMessage.send(player, PopupNotificationMessage.PopupType.REWARD,
            Component.translatable("popup.tac_rogue.npc.quartermaster.title"),
            Component.translatable("message.tac_rogue.bulk_ammo_bought", stacks, totalCost), 120);
        sendServiceLoadoutSummary(player);
    }

    private static void checkAttachmentCompatibility(ServerPlayer player) {
        List<String> gunIds = equippedGunIds(player);
        int loose = 0;
        int compatible = 0;
        int incompatible = 0;
        for (ItemStack stack : player.getInventory().items) {
            String attId = attachmentId(stack);
            if (attId == null) continue;
            loose++;
            if (isCompatibleWithAnyGun(attId, gunIds)) compatible++;
            else incompatible++;
        }
        StashSavedData.PlayerStash stash = StashSavedData.get(player.serverLevel()).getStash(player.getUUID());
        for (int slot = 0; slot < stash.unlockedLines * 9; slot++) {
            String attId = attachmentId(stash.getItem(slot));
            if (attId == null) continue;
            loose++;
            if (isCompatibleWithAnyGun(attId, gunIds)) compatible++;
            else incompatible++;
        }
        PopupNotificationMessage.send(player, PopupNotificationMessage.PopupType.NPC,
            Component.translatable("popup.tac_rogue.npc.quartermaster.title"),
            Component.translatable("message.tac_rogue.attachment_check", loose, compatible, incompatible, gunIds.size()), 150);
        sendServiceLoadoutSummary(player);
    }

    private static void sellLooseIncompatibleAttachments(ServerPlayer player) {
        List<String> gunIds = equippedGunIds(player);
        if (gunIds.isEmpty()) {
            PopupNotificationMessage.send(player, PopupNotificationMessage.PopupType.WARNING,
                Component.translatable("popup.tac_rogue.npc.quartermaster.title"),
                Component.translatable("message.tac_rogue.sell_junk_no_gun"), 90);
            return;
        }
        int sold = 0;
        int total = 0;
        for (int slot = 0; slot < Math.min(36, player.getInventory().items.size()); slot++) {
            ItemStack stack = player.getInventory().items.get(slot);
            String attId = attachmentId(stack);
            if (attId == null || isCompatibleWithAnyGun(attId, gunIds)) continue;
            int price = PriceManager.getSellPrice(stack);
            if (price <= 0) continue;
            player.getInventory().items.set(slot, ItemStack.EMPTY);
            sold += stack.getCount();
            total += price;
        }
        StashSavedData data = StashSavedData.get(player.serverLevel());
        StashSavedData.PlayerStash stash = data.getStash(player.getUUID());
        for (int slot = 0; slot < stash.unlockedLines * 9; slot++) {
            ItemStack stack = stash.getItem(slot);
            String attId = attachmentId(stack);
            if (attId == null || isCompatibleWithAnyGun(attId, gunIds)) continue;
            int price = PriceManager.getSellPrice(stack);
            if (price <= 0) continue;
            stash.setItem(slot, ItemStack.EMPTY);
            sold += stack.getCount();
            total += price;
        }
        if (total > 0) {
            GoldGainService.award(player, total);
            player.getInventory().setChanged();
        }
        PopupNotificationMessage.send(player, total > 0 ? PopupNotificationMessage.PopupType.REWARD : PopupNotificationMessage.PopupType.WARNING,
            Component.translatable("popup.tac_rogue.npc.quartermaster.title"),
            Component.translatable("message.tac_rogue.sell_junk_done", sold, total), 120);
        sendServiceLoadoutSummary(player);
    }

    private static void sendServiceLoadoutSummary(ServerPlayer player) {
        List<com.levanilla.rogue.networking.SyncServiceStashMessage.Entry> hotbar = new ArrayList<>();
        int maxSlot = maxManagedInventorySlot(player);
        for (int slot = 0; slot <= maxSlot && slot < player.getInventory().items.size(); slot++) {
            ItemStack stack = player.getInventory().items.get(slot);
            hotbar.add(new com.levanilla.rogue.networking.SyncServiceStashMessage.Entry(slot, stack.copy(), classifyLoadoutStack(stack)));
        }

        List<com.levanilla.rogue.networking.SyncServiceStashMessage.Entry> stashEntries = new ArrayList<>();
        StashSavedData.PlayerStash stash = StashSavedData.get(player.serverLevel()).getStash(player.getUUID());
        for (int slot = 0; slot < stash.unlockedLines * 9; slot++) {
            ItemStack stack = stash.getItem(slot);
            stashEntries.add(new com.levanilla.rogue.networking.SyncServiceStashMessage.Entry(slot, stack.copy(), classifyLoadoutStack(stack)));
        }

        com.levanilla.rogue.networking.TacRogueNetworking.CHANNEL.send(
            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
            new com.levanilla.rogue.networking.SyncServiceStashMessage(hotbar, stashEntries));
    }

    private static String classifyLoadoutStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "empty";
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            if (tag.contains("GunId")) return "gun";
            if (tag.contains("AmmoId")) return "ammo";
            if (tag.contains("AttachmentId")) return "attachment";
            if (tag.contains("MeleeWeaponId")) return "melee";
            if (tag.getBoolean("rogue_item")) return "item";
        }
        return "item";
    }

    private static boolean requireLobby(ServerPlayer player, String messageKey) {
        if (player.level().dimension() == com.levanilla.rogue.core.CommonEventHandler.LOBBY_DIM) return true;
        PopupNotificationMessage.send(player, PopupNotificationMessage.PopupType.WARNING,
            Component.translatable("popup.tac_rogue.npc.medic.title"),
            Component.translatable(messageKey), 90);
        return false;
    }

    private static boolean consumeGoldOrWarn(ServerPlayer player, int amount, Component title) {
        if (amount <= 0 || CurrencyManager.consumeGold(player, amount)) return true;
        PopupNotificationMessage.send(player, PopupNotificationMessage.PopupType.WARNING,
            title,
            Component.translatable("message.tac_rogue.not_enough_gold", amount), 90);
        return false;
    }

    private static int maxManagedInventorySlot(ServerPlayer player) {
        int invLevel = player.getPersistentData().getInt("TacRogue_InvLevel");
        return Math.min(GameConstants.SLOT_AMMO_GUN2_END + invLevel * 2, 35);
    }

    private static LoadoutSource findLoadoutSource(ServerPlayer player, StashSavedData.PlayerStash stash, String key, int maxSlot, boolean[] lockedSlots) {
        for (int slot = 0; slot <= maxSlot && slot < player.getInventory().items.size(); slot++) {
            if (lockedSlots[slot]) continue;
            ItemStack stack = player.getInventory().items.get(slot);
            if (!stack.isEmpty() && key.equals(loadoutKey(stack))) return new LoadoutSource(false, slot);
        }
        for (int slot = 0; slot < stash.unlockedLines * 9; slot++) {
            ItemStack stack = stash.getItem(slot);
            if (!stack.isEmpty() && key.equals(loadoutKey(stack))) return new LoadoutSource(true, slot);
        }
        return null;
    }

    private static String loadoutKey(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        CompoundTag tag = stack.getTag();
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        String base = itemId == null ? stack.getItem().toString() : itemId.toString();
        if (tag == null) return "item:" + base;
        if (tag.contains("AmmoId")) return "ammo:" + tag.getString("AmmoId");
        if (tag.contains("AttachmentId")) return "attachment:" + tag.getString("AttachmentId");
        if (tag.contains("MeleeWeaponId")) return "melee:" + tag.getString("MeleeWeaponId") + ":" + stableLoadoutTag(tag);
        if (tag.contains("GunId")) return "gun:" + tag.getString("GunId") + ":" + stableLoadoutTag(tag);
        if (tag.getBoolean("rogue_item")) return "rogue_item:" + base + ":" + tag.getInt("CustomModelData");
        return "item:" + base + ":" + tag;
    }

    private static String stableLoadoutTag(CompoundTag tag) {
        List<String> parts = new ArrayList<>();
        if (tag.contains("RogueRarity")) parts.add("rarity=" + tag.getString("RogueRarity"));
        if (tag.contains("WeaponRarity")) parts.add("rarity2=" + tag.getString("WeaponRarity"));
        if (tag.contains("TacRogueRarity")) parts.add("rarity3=" + tag.getString("TacRogueRarity"));
        if (tag.contains("Attachments")) parts.add("attachments=" + tag.getCompound("Attachments"));
        if (tag.contains("DeepModifier")) parts.add("deep=" + tag.getString("DeepModifier"));
        return String.join("|", parts);
    }

    private static List<String> equippedGunIds(ServerPlayer player) {
        List<String> gunIds = new ArrayList<>();
        for (int slot = GameConstants.SLOT_GUN_START; slot <= GameConstants.SLOT_GUN_END; slot++) {
            if (slot >= player.getInventory().items.size()) continue;
            ItemStack stack = player.getInventory().items.get(slot);
            if (stack.isEmpty() || !stack.hasTag() || !stack.getTag().contains("GunId")) continue;
            gunIds.add(stack.getTag().getString("GunId"));
        }
        return gunIds;
    }

    private static Set<String> equippedAmmoIds(ServerPlayer player) {
        Set<String> ammoIds = new HashSet<>();
        for (String gunId : equippedGunIds(player)) {
            String ammoId = TacZRegistryHelper.getAmmoForGun(gunId);
            if (ammoId != null && !ammoId.isBlank()) ammoIds.add(ammoId);
        }
        return ammoIds;
    }

    private static String attachmentId(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTag()) return null;
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("AttachmentId")) return null;
        String id = tag.getString("AttachmentId");
        return id == null || id.isBlank() ? null : id;
    }

    private static boolean isCompatibleWithAnyGun(String attachmentId, List<String> gunIds) {
        for (String gunId : gunIds) {
            if (AttachmentDatabase.isCompatible(gunId, attachmentId)) return true;
        }
        return false;
    }

    private static void openDeepOperations(ServerPlayer player) {
        com.levanilla.rogue.core.PlayerRunData data = RunManager.getData(player);
        if (!com.levanilla.rogue.core.service.DeepProgressService.isUnlocked(data)) {
            PopupNotificationMessage.send(player, PopupNotificationMessage.PopupType.WARNING,
                Component.translatable("popup.tac_rogue.deep.title"),
                Component.translatable("message.tac_rogue.deep_locked"), 80);
            return;
        }
        com.levanilla.rogue.core.service.DeepProgressService.sync(player);
        com.levanilla.rogue.networking.TacRogueNetworking.CHANNEL.send(
            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
            new com.levanilla.rogue.networking.SyncDataMessage("open_deep_operations")
        );
    }

    private static Component npcDialogue(ServerPlayer player, String baseKey, Object... args) {
        int index = Math.floorMod(player.getUUID().hashCode() + player.server.getTickCount() / 20, 4);
        return Component.translatable(baseKey + "." + index, args);
    }

    public static void clearMemory() {
        MENU_SESSIONS.clear();
        DELAYED_LOBBY_NORMALIZE_TICKS.clear();
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
        QuestManager.ensureChapterPlan(player, progress);
        PopupNotificationMessage.send(
            player,
            PopupNotificationMessage.PopupType.NPC,
            Component.translatable("popup.tac_rogue.npc.commander.title"),
            npcDialogue(player, "popup.tac_rogue.npc.commander.body", chapter),
            110
        );

        // クエストデータをクライアントへ送信
        sendQuestData(player);

        // クライアント側でQUESTタブを開く
        com.levanilla.rogue.networking.TacRogueNetworking.CHANNEL.send(
            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
            new com.levanilla.rogue.networking.SyncDataMessage("open_quest_tab"));
    }

    public static void sendQuestData(ServerPlayer player) {
        QuestManager.QuestProgress progress = QuestManager.getProgress(player);
        int chapter = progress.currentChapter;
        QuestManager.ensureChapterPlan(player, progress);
        List<QuestManager.Quest> quests = QuestManager.getVisibleChapterQuests(player);

        StringBuilder sb = new StringBuilder();
        boolean locked = progress.selectionLockedChapter == chapter;
        sb.append("quest_data:").append(chapter)
          .append(":").append(locked)
          .append(":").append(String.join("~", progress.selectedQuestIds))
          .append(":").append(String.join("~", progress.candidateQuestIds))
          .append("|");
        for (QuestManager.Quest quest : quests) {
            boolean completed = progress.completedQuests.contains(quest.id);
            int current = progress.questProgress.getOrDefault(quest.id, 0);
            boolean candidate = progress.candidateQuestIds.contains(quest.id);
            boolean selected = progress.selectedQuestIds.contains(quest.id);
            boolean active = quest.role == QuestManager.QuestRole.STORY || selected;
            // format: id;typeLangKey;target;progress;goldReward;completed
            sb.append(quest.id).append(";")
              .append(quest.type.langKey).append(";")
              .append(quest.targetAmount).append(";")
              .append(current).append(";")
              .append(quest.goldReward).append(";")
              .append(completed).append(";")
              .append(quest.role.langKey).append(";")
              .append(quest.rareWeaponReward).append(";")
              .append(quest.type.langKey).append(".desc").append(";")
              .append(active).append(";")
              .append(selected).append(";")
              .append(candidate).append(";")
              .append(quest.role == QuestManager.QuestRole.STORY).append(",");
        }

        com.levanilla.rogue.networking.TacRogueNetworking.CHANNEL.send(
            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
            new com.levanilla.rogue.networking.SyncDataMessage(sb.toString()));
    }

    /** インテル情報 */
    private static void showIntelBriefing(ServerPlayer player, boolean openFloorSelection) {
        com.levanilla.rogue.core.PlayerRunData data = RunManager.getData(player);
        int floor = data.getCurrentFloor();
        PopupNotificationMessage.send(
            player,
            PopupNotificationMessage.PopupType.NPC,
            Component.translatable("popup.tac_rogue.npc.intel.title"),
            npcDialogue(
                player,
                "popup.tac_rogue.npc.intel.body",
                floor,
                DifficultyManager.getDifficulty().displayName,
                String.format(java.util.Locale.ROOT, "%.1fx", DifficultyManager.getHpScale(floor)),
                String.format(java.util.Locale.ROOT, "%.1fx", DifficultyManager.getGoldMultiplier())
            ),
            160
        );
        if (com.levanilla.rogue.core.service.DeepProgressService.isUnlocked(data)) {
            String taskType = data.getCurrentDeepTaskType().isBlank() ? "band_clear" : data.getCurrentDeepTaskType().toLowerCase(java.util.Locale.ROOT);
            PopupNotificationMessage.send(
                player,
                PopupNotificationMessage.PopupType.SYSTEM,
                Component.translatable("popup.tac_rogue.deep.title"),
                Component.translatable(
                    "message.tac_rogue.deep_status",
                    data.getDeepCore(),
                    data.getPrestigeLevel(),
                    data.getHighestEverFloor(),
                    Component.translatable("deep_task.tac_rogue." + taskType),
                    data.getDeepTaskProgress(),
                    data.getDeepTaskTarget()
                ),
                150
            );
        }

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
