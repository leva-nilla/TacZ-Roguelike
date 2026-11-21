package com.levanilla.rogue.core.service;

import com.levanilla.rogue.core.CommonEventHandler;
import com.levanilla.rogue.core.PlayerRunData;
import com.levanilla.rogue.core.QuestManager;
import com.levanilla.rogue.core.RunManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Supplier;

public final class SupplyCacheService {
    public static final String CACHE_MARKER = "TacRogueLootChest";
    public static final String CACHE_TITLE = "[ROGUE SUPPLY CACHE]";

    private SupplyCacheService() {}

    public static boolean handleOpen(ServerPlayer player, ChestBlockEntity chest) {
        if (!chest.getPersistentData().getBoolean(CACHE_MARKER)) return false;
        FloorInstanceManager.FloorInstance instance = FloorInstanceManager.getInstanceForPlayer(player);
        if (!canUse(player, chest, instance)) return true;

        BlockPos pos = chest.getBlockPos();
        UUID owner = player.getUUID();
        SimpleContainer inventory = instance.supplyCaches.getOrClaim(pos, owner, () -> claim(player, chest, instance));
        if (inventory == null) {
            player.displayClientMessage(Component.translatable("message.tac_rogue.supply_cache_claimed"), true);
        } else {
            player.openMenu(new SimpleMenuProvider(
                (id, playerInventory, ignored) -> new SupplyCacheMenu(id, playerInventory, inventory,
                    viewer -> viewer instanceof ServerPlayer serverPlayer
                        && owner.equals(serverPlayer.getUUID()) && canUse(serverPlayer, chest, instance)),
                Component.literal(CACHE_TITLE)));
        }
        return true;
    }

    private static boolean canUse(ServerPlayer player, ChestBlockEntity chest,
                                  FloorInstanceManager.FloorInstance instance) {
        if (instance == null || (instance.state != FloorInstanceManager.State.ACTIVE
                && instance.state != FloorInstanceManager.State.CLEARED)) return false;
        CompoundTag tag = chest.getPersistentData();
        return !player.isSpectator()
            && player.level().dimension() == CommonEventHandler.ROGUE_DIM
            && player.level() == chest.getLevel()
            && FloorInstanceManager.getInstanceForPlayer(player) == instance
            && instance.id.equals(tag.getString(FloorInstanceManager.INSTANCE_ID_KEY))
            && instance.floor == tag.getInt(FloorInstanceManager.FLOOR_KEY)
            && chest.stillValid(player);
    }

    private static SimpleContainer claim(ServerPlayer player, ChestBlockEntity chest,
                                         FloorInstanceManager.FloorInstance instance) {
        int floor = instance.floor;
        PlayerRunData data = RunManager.getData(player);
        int maxClaims = data.getSupplyChestMaxClaims(floor);
        if (maxClaims <= 0) return null;
        int claimIndex = data.claimNextSupplyChest(floor, maxClaims);
        if (claimIndex < 0) return null;

        CompoundTag tag = chest.getPersistentData();
        long lootSeed = tag.contains(ChestLootService.CHEST_LOOT_SEED_KEY)
            ? tag.getLong(ChestLootService.CHEST_LOOT_SEED_KEY)
            : instance.floorSeedSalt ^ chest.getBlockPos().asLong();
        Random random = new Random(lootSeed ^ player.getUUID().getLeastSignificantBits() ^ claimIndex);
        SimpleContainer inventory = new SimpleContainer(SupplyCacheMenu.SIZE);
        for (ItemStack stack : ChestLootService.generatePersonalChestLoot(player, floor, claimIndex, lootSeed)) {
            if (stack.isEmpty()) continue;
            int start = random.nextInt(inventory.getContainerSize());
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                int slot = (start + i) % inventory.getContainerSize();
                if (!inventory.getItem(slot).isEmpty()) continue;
                inventory.setItem(slot, stack);
                break;
            }
        }
        RunManager.saveToPlayerNbt(player);
        QuestManager.advanceQuest(player, QuestManager.QuestType.CHEST_RECOVERY, 1);
        return inventory;
    }

    /** Owned by one generated floor; even an emptied cache remains claimed until regeneration. */
    static final class Contents<T> {
        private record Key(BlockPos pos, UUID playerId) {}

        private final Map<Key, T> caches = new HashMap<>();

        T getOrClaim(BlockPos pos, UUID playerId, Supplier<T> claim) {
            return caches.computeIfAbsent(new Key(pos.immutable(), playerId), key -> claim.get());
        }
    }
}
