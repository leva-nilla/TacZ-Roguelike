package com.levanilla.rogue.core.event;

import com.levanilla.rogue.core.*;
import com.levanilla.rogue.core.RestrictedSlot.SlotType;
import com.levanilla.rogue.core.service.ArmorPlateService;
import com.levanilla.rogue.networking.TacRogueNetworking;
import com.levanilla.rogue.world.LobbyGenerator;
import com.levanilla.rogue.world.NpcManager;
import com.levanilla.rogue.world.TacRogueNpcEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.levanilla.rogue.core.CommonEventHandler.LOBBY_DIM;
import static com.levanilla.rogue.core.CommonEventHandler.ROGUE_DIM;

/**
 * アイテム使用、インタラクト、ログイン、リスポーン、クローンなどのイベント。
 */
@Mod.EventBusSubscriber(modid = "tac_rogue")
public class ItemAndLifecycleHandler {
    private static final String SAVED_HEALTH_KEY = "TacRogueSavedHealth";
    // ===== 旧商人インタラクト =====

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() instanceof TacRogueNpcEntity npc && event.getEntity() instanceof ServerPlayer player) {
            NpcManager.handleCustomNpcInteraction(player, npc);
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }

        if (!(event.getTarget() instanceof net.minecraft.world.entity.npc.Villager villager)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        if (villager.getTags().contains("rogue:merchant")) {
            TacRogueNetworking.openNpcMenu(player, "quartermaster", false, false);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!(event.getTarget() instanceof TacRogueNpcEntity npc)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        NpcManager.handleCustomNpcInteraction(player, npc);
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().dimension() != ROGUE_DIM) return;

        BlockPos pos = event.getPos();
        if (com.levanilla.rogue.core.service.FloorObjectiveService.handleObjectiveBlockInteract(player, pos)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (event.isCanceled()) return;
        if (!(event.getLevel().getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest)) {
            return;
        }
        if (!chest.getPersistentData().getBoolean("TacRogueLootChest")) return;
        int floor = chest.getPersistentData().contains("TacRogueFloor")
            ? chest.getPersistentData().getInt("TacRogueFloor")
            : RunManager.getData(player).getCurrentFloor();
        int chestIndex = Math.max(0, chest.getPersistentData().getInt(
            com.levanilla.rogue.core.service.ChestLootService.CHEST_INDEX_KEY));
        int generatedChestCount = chest.getPersistentData().contains(
            com.levanilla.rogue.core.service.ChestLootService.CHEST_TOTAL_KEY)
            ? chest.getPersistentData().getInt(com.levanilla.rogue.core.service.ChestLootService.CHEST_TOTAL_KEY)
            : chestIndex + 1;
        PlayerRunData data = RunManager.getData(player);
        int maxClaims = data.getSupplyChestMaxClaims(floor);
        if (maxClaims <= 0) {
            // Compatibility for already-generated caches from older builds. New floors stamp this at generation time.
            maxClaims = Math.max(1, generatedChestCount);
        }
        if (com.levanilla.rogue.core.service.FloorInstanceManager.hasChestClaimed(chest, player.getUUID())) {
            // Same generated chest, same floor session: allow reopening while any previously rolled loot remains.
            if (hasAnyContent(chest)) {
                return;
            }
            // Empty already-opened physical cache may still consume another floor claim
            // if this run previously generated more caches than the current layout exposes.
        }
        if (data.getClaimedSupplyChestCount(floor) >= maxClaims) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.tac_rogue.supply_cache_claimed"), true);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        int claimIndex = data.claimNextSupplyChest(floor, maxClaims);
        if (claimIndex < 0) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.tac_rogue.supply_cache_claimed"), true);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        refillPersonalSupplyChest(chest, player, Math.max(1, floor), claimIndex);
        com.levanilla.rogue.core.service.FloorInstanceManager.markChestClaimed(chest, player.getUUID());
        QuestManager.advanceQuest(player, QuestManager.QuestType.CHEST_RECOVERY, 1);
    }

    private static boolean hasAnyContent(net.minecraft.world.level.block.entity.ChestBlockEntity chest) {
        for (int i = 0; i < chest.getContainerSize(); i++) {
            if (!chest.getItem(i).isEmpty()) return true;
        }
        return false;
    }

    private static void refillPersonalSupplyChest(net.minecraft.world.level.block.entity.ChestBlockEntity chest,
                                                  ServerPlayer player,
                                                  int floor,
                                                  int chestIndex) {
        long chestLootSeed = ensureChestLootSeed(chest, player, floor, chestIndex);
        java.util.Random rand = new java.util.Random(player.getUUID().getMostSignificantBits()
            ^ Long.rotateLeft(player.getUUID().getLeastSignificantBits(), 21)
            ^ ((long) Math.max(1, floor) * 0x9E3779B97F4A7C15L)
            ^ ((long) Math.max(0, chestIndex) * 0xD1B54A32D192ED03L)
            ^ Long.rotateLeft(chestLootSeed, 13));
        chest.clearContent();
        java.util.List<ItemStack> loot =
            com.levanilla.rogue.core.service.ChestLootService.generatePersonalChestLoot(player, floor, chestIndex, chestLootSeed);

        for (ItemStack stack : loot) {
            if (stack.isEmpty()) continue;
            int slot = rand.nextInt(chest.getContainerSize());
            for (int tries = 0; tries < chest.getContainerSize() && !chest.getItem(slot).isEmpty(); tries++) {
                slot = (slot + 1) % chest.getContainerSize();
            }
            chest.setItem(slot, stack);
        }
        chest.setChanged();
    }

    private static long ensureChestLootSeed(net.minecraft.world.level.block.entity.ChestBlockEntity chest,
                                            ServerPlayer player,
                                            int floor,
                                            int chestIndex) {
        String key = com.levanilla.rogue.core.service.ChestLootService.CHEST_LOOT_SEED_KEY;
        if (chest.getPersistentData().contains(key)) {
            return chest.getPersistentData().getLong(key);
        }
        long seed = player.getRandom().nextLong()
            ^ player.serverLevel().getSeed()
            ^ ((long) Math.max(1, floor) * 0x9E3779B97F4A7C15L)
            ^ ((long) Math.max(0, chestIndex) * 0xD1B54A32D192ED03L)
            ^ Long.rotateLeft(player.getUUID().getMostSignificantBits(), 11)
            ^ Long.rotateLeft(player.getUUID().getLeastSignificantBits(), 37);
        chest.getPersistentData().putLong(key, seed);
        chest.setChanged();
        return seed;
    }

    // ===== ステーキ使用完了時の回復 =====

    @SubscribeEvent
    public static void onLivingItemFinish(net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getItem().is(Items.COOKED_BEEF)) {
            player.heal(GameConstants.STEAK_HEAL_AMOUNT);
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.tac_rogue.steak_heal"), true);
        }
    }

    // ===== ローグアイテムの右クリック使用 =====

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide) return;
        ItemStack stack = event.getItemStack();
        if (event.getEntity() instanceof ServerPlayer objectivePlayer
                && objectivePlayer.level().dimension() == ROGUE_DIM
                && tryObjectiveUseFromHeldItem(objectivePlayer)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        // rogue_always_eat タグ付きステーキ: 満腹でも食べられるようにする
        if (stack.getItem() == Items.COOKED_BEEF
                && stack.hasTag() && stack.getTag().getBoolean("rogue_always_eat")
                && event.getEntity() instanceof ServerPlayer sp
                && !sp.canEat(false)) {
            sp.getFoodData().setFoodLevel(sp.getFoodData().getFoodLevel() - 1);
        }

        if (event.getEntity() instanceof ServerPlayer player) {
            if ((stack.hasTag() && stack.getTag().getBoolean("rogue_item"))
                    || ArmorPlateService.isArmorPlate(stack)) {
                if (com.levanilla.rogue.core.service.RogueUtilityItemService.useUtilityItem(player, stack)) {
                    event.setCanceled(true);
                } else if (stack.is(Items.HONEY_BOTTLE)) {
                    // STAMINA SHOT: restore stamina and grant a short movement burst.
                    StaminaManager.setStamina(player, StaminaManager.getMaxStamina(player));
                    player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 300, 1, false, true));
                    player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.tac_rogue.stamina_boost"), true);
                    stack.shrink(1);
                    event.setCanceled(true);
                } else if (stack.is(Items.RAW_IRON)) {
                    // SCRAP METAL — ロビー限定換金
                    if (player.level().dimension() != LOBBY_DIM) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                            "message.tac_rogue.lobby_only_exchange"), true);
                        event.setCanceled(true);
                    } else {
                        com.levanilla.rogue.core.service.GoldGainService.award(player, GameConstants.SCRAP_SELL_VALUE);
                        stack.shrink(1);
                        event.setCanceled(true);
                    }
                } else if (stack.is(Items.RAW_GOLD)) {
                    // GOLD CACHE — ロビー限定換金
                    if (player.level().dimension() != LOBBY_DIM) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                            "message.tac_rogue.lobby_only_exchange"), true);
                        event.setCanceled(true);
                    } else {
                        com.levanilla.rogue.core.service.GoldGainService.award(player, GameConstants.GOLD_CACHE_VALUE);
                        stack.shrink(1);
                        event.setCanceled(true);
                    }
                } else if (stack.is(Items.COOKED_BEEF)) {
                    // FIELD RATION
                    if (player.getHealth() < player.getMaxHealth()) {
                        player.heal(GameConstants.STEAK_HEAL_AMOUNT);
                        player.getFoodData().eat(6, 0.6f);
                        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                            "message.tac_rogue.ration_heal"), true);
                        stack.shrink(1);
                        event.setCanceled(true);
                    } else {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                            "message.tac_rogue.hp_full"), true);
                        event.setCanceled(true);
                    }
                } else if (stack.is(Items.PAPER) && stack.getTag().getBoolean("rogue_medkit")) {
                    // MEDKIT
                    float maxHp = player.getMaxHealth();
                    float healAmount = maxHp * GameConstants.MEDKIT_HEAL_RATIO;
                    if (player.getHealth() < maxHp) {
                        player.heal(healAmount);
                        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                            "message.tac_rogue.medkit_heal", String.format("%.0f", healAmount)), true);
                        stack.shrink(1);
                    } else {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                            "message.tac_rogue.hp_full"), true);
                    }
                    event.setCanceled(true);
                } else if (stack.is(Items.STRING)) {
                    // BANDAGE — HP最大値の15%回復
                    float maxHp = player.getMaxHealth();
                    float healAmount = maxHp * 0.15f;
                    if (player.getHealth() < maxHp) {
                        player.heal(healAmount);
                        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                            "message.tac_rogue.bandage_heal", String.format("%.0f", healAmount)), true);
                        stack.shrink(1);
                    } else {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                            "message.tac_rogue.hp_full"), true);
                    }
                    event.setCanceled(true);
                } else if (ArmorPlateService.isArmorPlate(stack)) {
                    ArmorPlateService.use(player, stack);
                    event.setCanceled(true);
                } else if (stack.is(Items.GLASS_BOTTLE)) {
                    // ADRENALINE — ダメージ+30% & 速度+20% (20秒) → 終了後スロー2秒
                    RogueCombatEffects.activateAdrenaline(player);
                    player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 400, 0, false, true));
                    // 20秒後にスロー付与
                    player.getServer().tell(new net.minecraft.server.TickTask(
                        player.getServer().getTickCount() + 400, () -> {
                            if (player.isAlive()) {
                                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                                    net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 40, 1, false, true));
                            }
                        }));
                    player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                        "message.tac_rogue.adrenaline_used"), true);
                    stack.shrink(1);
                    event.setCanceled(true);
                } else if (stack.is(Items.REDSTONE)) {
                    // EMP DEVICE — 半径10ブロック内の全モブにスロー+弱体化 (5秒)
                    var mobs = player.level().getEntitiesOfClass(
                        net.minecraft.world.entity.Mob.class,
                        player.getBoundingBox().inflate(10.0));
                    for (var mob : mobs) {
                        mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 100, 2, false, true));
                        mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.WEAKNESS, 100, 1, false, true));
                    }
                    player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                        "message.tac_rogue.emp_used", mobs.size()), true);
                    stack.shrink(1);
                    event.setCanceled(true);
                }
            }
        }
    }

    private static boolean tryObjectiveUseFromHeldItem(ServerPlayer player) {
        if (player == null || player.level().dimension() != ROGUE_DIM) return false;
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 end = eye.add(player.getLookAngle().scale(5.75D));
        BlockHitResult hit = player.level().clip(new ClipContext(
            eye,
            end,
            ClipContext.Block.OUTLINE,
            ClipContext.Fluid.NONE,
            player));
        if (hit.getType() != HitResult.Type.BLOCK) return false;
        return com.levanilla.rogue.core.service.FloorObjectiveService.handleObjectiveBlockInteract(player, hit.getBlockPos());
    }

    // ===== ログイン =====

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RunManager.loadFromPlayerNbt(player);
            RunManager.getData(player).setRunActive(false);

            if (!RogueConfig.perfNeutralWorld()) {
                ServerLevel lobbyLevel = player.server.getLevel(LOBBY_DIM);
                if (lobbyLevel != null) {
                    boolean rebuilt = LobbyGenerator.ensureLobbyBuilt(lobbyLevel, LobbyGenerator.DEFAULT_CENTER);
                    if (player.level().dimension() != LOBBY_DIM || rebuilt) {
                        player.teleportTo(lobbyLevel,
                            GameConstants.LOBBY_X, GameConstants.LOBBY_Y, GameConstants.LOBBY_Z, 0, 0);
                    }
                }
            }
            applySlotRestrictions(player);
            restoreRoguePlayerState(player, true);
            RunManager.syncPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            saveRoguePlayerState(player);
            com.levanilla.rogue.core.service.FloorInstanceManager.leaveInstance(player, true);
            RunManager.saveToPlayerNbt(player);
        }
    }

    // ===== リスポーン =====

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            com.levanilla.rogue.core.service.FloorInstanceManager.leaveInstance(player, true);
            ServerLevel lobbyLevel = player.server.getLevel(LOBBY_DIM);
            if (lobbyLevel != null) {
                LobbyGenerator.ensureLobbyBuilt(lobbyLevel, LobbyGenerator.DEFAULT_CENTER);
                player.teleportTo(lobbyLevel,
                    GameConstants.LOBBY_X, GameConstants.LOBBY_Y, GameConstants.LOBBY_Z, 0, 0);
            }
            applySlotRestrictions(player);
            restoreRoguePlayerState(player, false);
            RunManager.syncPlayer(player);
        }
    }

    // ===== クローン（死亡後インベントリ引き継ぎ） =====

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            event.getEntity().getInventory().replaceWith(event.getOriginal().getInventory());
            event.getEntity().getPersistentData().merge(event.getOriginal().getPersistentData());
            if (event.getEntity() instanceof ServerPlayer cloned
                && event.getOriginal() instanceof ServerPlayer originalServer) {
                com.levanilla.rogue.core.service.PerkStorageService.setPerks(
                    cloned,
                    com.levanilla.rogue.core.service.PerkStorageService.getPerkTags(originalServer));
                RunManager.restorePerkTags(cloned);
            }
        }
    }

    private static void saveRoguePlayerState(ServerPlayer player) {
        if (player.level().dimension() == LOBBY_DIM || player.level().dimension() == ROGUE_DIM) {
            player.getPersistentData().putFloat(SAVED_HEALTH_KEY, Math.max(1.0f, player.getHealth()));
            StaminaManager.saveToPersistentData(player);
        }
    }

    private static void restoreRoguePlayerState(ServerPlayer player, boolean restoreHealth) {
        com.levanilla.rogue.core.service.PlayerPerkTickService.invalidateSnapshot(player);
        com.levanilla.rogue.core.service.PlayerPerkTickService.applyPerkStats(player);
        StaminaManager.restoreFromPersistentData(player);
        if (restoreHealth && player.getPersistentData().contains(SAVED_HEALTH_KEY)) {
            float savedHealth = player.getPersistentData().getFloat(SAVED_HEALTH_KEY);
            player.setHealth(Math.max(1.0f, Math.min(savedHealth, player.getMaxHealth())));
        }
    }

    // ===== InventoryMenu のスロット制約適用 =====

    /**
     * プレイヤーの InventoryMenu のホットバースロット 0,1,2 を
     * RestrictedSlot に差し替え、許可されたアイテム以外を配置不可にする。
     *
     * InventoryMenu のスロットインデックス:
     *   0=クラフト結果, 1-4=クラフトグリッド, 5-8=防具,
     *   9-35=メインインベントリ, 36-44=ホットバー(0-8)
     */
    public static void applySlotRestrictions(ServerPlayer player) {
        net.minecraft.world.inventory.InventoryMenu menu = player.inventoryMenu;

        // ホットバースロット 0, 1 → 銃のみ（メニューインデックス 36, 37）
        for (int hotbar = 0; hotbar <= 1; hotbar++) {
            int menuIdx = 36 + hotbar;
            net.minecraft.world.inventory.Slot original = menu.slots.get(menuIdx);
            RestrictedSlot restricted = new RestrictedSlot(
                original.container, hotbar, original.x, original.y, SlotType.GUN);
            restricted.index = menuIdx;
            menu.slots.set(menuIdx, restricted);
        }

        // ホットバースロット 2 → 近接のみ（メニューインデックス 38）
        {
            int menuIdx = 38;
            net.minecraft.world.inventory.Slot original = menu.slots.get(menuIdx);
            RestrictedSlot restricted = new RestrictedSlot(
                original.container, 2, original.x, original.y, SlotType.MELEE);
            restricted.index = menuIdx;
            menu.slots.set(menuIdx, restricted);
        }
    }
}
