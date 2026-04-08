package com.levanilla.rogue.core.event;

import com.levanilla.rogue.core.*;
import com.levanilla.rogue.core.RestrictedSlot.SlotType;
import com.levanilla.rogue.networking.TacRogueNetworking;
import com.levanilla.rogue.world.LobbyGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.levanilla.rogue.core.CommonEventHandler.LOBBY_DIM;
import static com.levanilla.rogue.core.CommonEventHandler.ROGUE_DIM;

/**
 * アイテム使用、インタラクト、ログイン、リスポーン、クローンなどのイベント。
 */
@Mod.EventBusSubscriber(modid = "tac_rogue")
public class ItemAndLifecycleHandler {

    // ===== 村人インタラクト（ショップ / NPC） =====

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof net.minecraft.world.entity.npc.Villager villager)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // 既存の商人 NPC
        if (villager.getTags().contains("rogue:merchant")) {
            TacRogueNetworking.openShop(player);
            event.setCanceled(true);
            return;
        }

        // NpcManager 管理の NPC（Commander, Quartermaster, Intel, Medic）
        if (com.levanilla.rogue.world.NpcManager.handleInteraction(player, villager)) {
            event.setCanceled(true);
        }
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

        // rogue_always_eat タグ付きステーキ: 満腹でも食べられるようにする
        if (stack.getItem() == Items.COOKED_BEEF
                && stack.hasTag() && stack.getTag().getBoolean("rogue_always_eat")
                && event.getEntity() instanceof ServerPlayer sp
                && !sp.canEat(false)) {
            sp.getFoodData().setFoodLevel(sp.getFoodData().getFoodLevel() - 1);
        }

        if (event.getEntity() instanceof ServerPlayer player) {
            if (stack.hasTag() && stack.getTag().getBoolean("rogue_item")) {
                if (stack.is(Items.HONEY_BOTTLE)) {
                    // STAMINA BOOST
                    StaminaManager.setStamina(player, StaminaManager.getMaxStamina(player));
                    player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 300, 1, false, true));
                    player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.tac_rogue.stamina_boost"));
                    stack.shrink(1);
                    event.setCanceled(true);
                } else if (stack.is(Items.RAW_IRON)) {
                    // SCRAP METAL — ロビー限定換金
                    if (player.level().dimension() != LOBBY_DIM) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                            "message.tac_rogue.lobby_only_exchange"));
                        event.setCanceled(true);
                    } else {
                        CurrencyManager.addGold(player, GameConstants.SCRAP_SELL_VALUE);
                        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                            "message.tac_rogue.scrap_sold", GameConstants.SCRAP_SELL_VALUE));
                        stack.shrink(1);
                        RunManager.sync();
                        event.setCanceled(true);
                    }
                } else if (stack.is(Items.RAW_GOLD)) {
                    // GOLD CACHE — ロビー限定換金
                    if (player.level().dimension() != LOBBY_DIM) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                            "message.tac_rogue.lobby_only_exchange"));
                        event.setCanceled(true);
                    } else {
                        CurrencyManager.addGold(player, GameConstants.GOLD_CACHE_VALUE);
                        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                            "message.tac_rogue.gold_cache", GameConstants.GOLD_CACHE_VALUE));
                        stack.shrink(1);
                        RunManager.sync();
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
                }
            }
        }
    }

    // ===== ログイン =====

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RunManager.loadFromPlayerNbt(player);

            ServerLevel lobbyLevel = player.server.getLevel(LOBBY_DIM);
            if (lobbyLevel != null && player.level().dimension() != LOBBY_DIM) {
                player.teleportTo(lobbyLevel,
                    GameConstants.LOBBY_X, GameConstants.LOBBY_Y, GameConstants.LOBBY_Z, 0, 0);
                LobbyGenerator.buildLobby(lobbyLevel, new BlockPos(0, 201, 0));
            }
            applySlotRestrictions(player);
            RunManager.sync();
        }
    }

    // ===== リスポーン =====

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerLevel lobbyLevel = player.server.getLevel(LOBBY_DIM);
            if (lobbyLevel != null) {
                player.teleportTo(lobbyLevel,
                    GameConstants.LOBBY_X, GameConstants.LOBBY_Y, GameConstants.LOBBY_Z, 0, 0);
            }
            applySlotRestrictions(player);
        }
    }

    // ===== クローン（死亡後インベントリ引き継ぎ） =====

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            event.getEntity().getInventory().replaceWith(event.getOriginal().getInventory());
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
