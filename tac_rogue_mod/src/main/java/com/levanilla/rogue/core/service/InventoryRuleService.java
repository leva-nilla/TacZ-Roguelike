package com.levanilla.rogue.core.service;

import com.levanilla.rogue.core.GameConstants;
import com.levanilla.rogue.core.RunManager;
import com.levanilla.rogue.core.TacZRegistryHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class InventoryRuleService {

    private InventoryRuleService() {}
    private static final int FULL_ENFORCE_INTERVAL_TICKS = 40;
    private static final java.util.Map<java.util.UUID, InventoryRuleSnapshot> ruleSnapshots = new java.util.HashMap<>();

    private record InventoryRuleSnapshot(int signature, int lastFullTick) {}

    public static void enforce(ServerPlayer player) {
        clearLockedVisualsFromCombatSlots(player);
        enforceSlotRestrictions(player);
        enforceAmmoSlotRestrictions(player);
        enforceArmorAndOffhand(player);

        if (!shouldRunFullEnforce(player)) {
            return;
        }

        prioritizeAmmoSlots(player);
        enforceInventoryLimits(player);
        enforceAmmoStackSizes(player);
        rememberFullEnforce(player);
    }

    private static boolean shouldRunFullEnforce(ServerPlayer player) {
        java.util.UUID uuid = player.getUUID();
        int signature = buildRuleSignature(player);
        int tick = player.tickCount;
        InventoryRuleSnapshot snapshot = ruleSnapshots.get(uuid);

        return snapshot == null
            || snapshot.signature != signature
            || tick < snapshot.lastFullTick
            || tick - snapshot.lastFullTick >= FULL_ENFORCE_INTERVAL_TICKS;
    }

    private static void rememberFullEnforce(ServerPlayer player) {
        ruleSnapshots.put(player.getUUID(), new InventoryRuleSnapshot(buildRuleSignature(player), player.tickCount));
    }

    private static int buildRuleSignature(ServerPlayer player) {
        int hash = 17;
        hash = 31 * hash + player.getPersistentData().getInt("TacRogue_InvLevel");
        hash = 31 * hash + RunManager.getData(player).getAmmoCapacityLevel();

        int size = Math.min(41, player.getInventory().getContainerSize());
        hash = 31 * hash + size;
        for (int slot = 0; slot < size; slot++) {
            hash = 31 * hash + slot;
            hash = 31 * hash + stackRuleSignature(player.getInventory().getItem(slot));
        }
        return hash;
    }

    private static int stackRuleSignature(ItemStack stack) {
        if (stack.isEmpty()) return 0;

        int hash = System.identityHashCode(stack.getItem());
        net.minecraft.nbt.CompoundTag tag = stack.getTag();
        if (tag == null) return hash;

        hash = addTagString(hash, tag, "GunId");
        hash = addTagString(hash, tag, "MeleeWeaponId");
        hash = addTagString(hash, tag, "AmmoId");
        if (tag.contains("rogue_item_locked")) {
            hash = 31 * hash + (tag.getBoolean("rogue_item_locked") ? 1 : 2);
        }
        return hash;
    }

    private static int addTagString(int hash, net.minecraft.nbt.CompoundTag tag, String key) {
        if (!tag.contains(key)) return 31 * hash;
        return 31 * hash + tag.getString(key).hashCode();
    }

    private static void enforceSlotRestrictions(ServerPlayer player) {
        for (int slot = GameConstants.SLOT_GUN_START; slot <= GameConstants.SLOT_GUN_END; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && !isGunItem(stack)) {
                ejectFromSlot(player, slot);
            }
        }

        ItemStack meleeStack = player.getInventory().getItem(GameConstants.SLOT_MELEE);
        if (!meleeStack.isEmpty() && !isMeleeItem(meleeStack)) {
            ejectFromSlot(player, GameConstants.SLOT_MELEE);
        }

        for (int slot = GameConstants.SLOT_ITEM_START; slot <= GameConstants.SLOT_ITEM_END; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;

            if (isGunItem(stack)) {
                moveWeaponOrEject(player, slot, true);
            } else if (isMeleeItem(stack)) {
                moveWeaponOrEject(player, slot, false);
            }
        }

        for (int slot = GameConstants.SLOT_AMMO_GUN1_START; slot <= GameConstants.SLOT_AMMO_GUN2_END; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && (isGunItem(stack) || isMeleeItem(stack))) {
                moveWeaponOrEject(player, slot, isGunItem(stack));
            }
        }
    }

    public static boolean isGunItem(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains("GunId");
    }

    public static boolean isMeleeItem(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains("MeleeWeaponId");
    }

    private static void ejectFromSlot(ServerPlayer player, int fromSlot) {
        ItemStack stack = player.getInventory().getItem(fromSlot);
        if (stack.isEmpty()) return;

        for (int slot = GameConstants.SLOT_ITEM_START; slot <= GameConstants.SLOT_ITEM_END; slot++) {
            if (player.getInventory().getItem(slot).isEmpty()) {
                player.getInventory().setItem(slot, stack.copy());
                player.getInventory().setItem(fromSlot, ItemStack.EMPTY);
                return;
            }
        }

        int invLevel = player.getPersistentData().getInt("TacRogue_InvLevel");
        int maxAllowed = Math.min(GameConstants.SLOT_AMMO_GUN2_END + (invLevel * 2), 35);
        for (int slot = GameConstants.SLOT_AMMO_GUN2_END + 1; slot <= maxAllowed; slot++) {
            ItemStack existing = player.getInventory().getItem(slot);
            if (isLockedSlotVisual(existing)) continue;
            if (existing.isEmpty()) {
                player.getInventory().setItem(slot, stack.copy());
                player.getInventory().setItem(fromSlot, ItemStack.EMPTY);
                return;
            }
        }

        if (!ShopPlacementService.sendToStash(player, stack)) {
            player.drop(stack.copy(), true, false);
        }
        player.getInventory().setItem(fromSlot, ItemStack.EMPTY);
    }

    private static void enforceInventoryLimits(ServerPlayer player) {
        int invLevel = player.getPersistentData().getInt("TacRogue_InvLevel");
        int maxAllowedIndex = Math.min(GameConstants.SLOT_AMMO_GUN2_END + (invLevel * 2), 35);

        ItemStack lockedSlotItem = new ItemStack(Items.BARRIER);
        lockedSlotItem.setHoverName(Component.literal("\u00A7c[LOCKED]"));
        lockedSlotItem.getOrCreateTag().putBoolean("rogue_item_locked", true);

        for (int slot = GameConstants.SLOT_AMMO_GUN2_END + 1; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            boolean isLockedVisual = isLockedSlotVisual(stack);

            if (slot > maxAllowedIndex) {
                if (!isLockedVisual) {
                    if (!stack.isEmpty()) {
                        boolean returned = false;
                        for (int itemSlot = GameConstants.SLOT_ITEM_START; itemSlot <= GameConstants.SLOT_ITEM_END; itemSlot++) {
                            if (player.getInventory().getItem(itemSlot).isEmpty()) {
                                player.getInventory().setItem(itemSlot, stack.copy());
                                returned = true;
                                break;
                            }
                        }
                        if (!returned) {
                            if (!ShopPlacementService.sendToStash(player, stack)) {
                                player.drop(stack.copy(), true, false);
                            }
                        }
                    }
                    player.getInventory().setItem(slot, lockedSlotItem.copy());
                }
            } else {
                if (isLockedVisual) {
                    player.getInventory().setItem(slot, ItemStack.EMPTY);
                }
                if (!stack.isEmpty() && !isLockedVisual && (isGunItem(stack) || isMeleeItem(stack))) {
                    moveWeaponOrEject(player, slot, isGunItem(stack));
                }
            }
        }
    }

    private static void enforceArmorAndOffhand(ServerPlayer player) {
        for (int armorSlot = 36; armorSlot <= 39; armorSlot++) {
            ItemStack armorStack = player.getInventory().getItem(armorSlot);
            if (!armorStack.isEmpty()) {
                player.drop(armorStack.copy(), true, false);
                player.getInventory().setItem(armorSlot, ItemStack.EMPTY);
            }
        }

        ItemStack offhand = player.getInventory().getItem(40);
        if (!offhand.isEmpty()) {
            player.drop(offhand.copy(), true, false);
            player.getInventory().setItem(40, ItemStack.EMPTY);
        }
    }

    private static void enforceAmmoSlotRestrictions(ServerPlayer player) {
        for (int slot = GameConstants.SLOT_AMMO_GUN1_START; slot <= GameConstants.SLOT_AMMO_GUN2_END; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty() || isLockedSlotVisual(stack)) continue;

            if (!stack.hasTag() || !stack.getTag().contains("AmmoId")) {
                ejectFromSlot(player, slot);
            }
        }
    }

    private static void prioritizeAmmoSlots(ServerPlayer player) {
        for (int slot = 0; slot < Math.min(36, player.getInventory().getContainerSize()); slot++) {
            if (slot >= GameConstants.SLOT_AMMO_GUN1_START && slot <= GameConstants.SLOT_AMMO_GUN2_END) continue;
            if (slot >= GameConstants.SLOT_ITEM_START && slot <= GameConstants.SLOT_ITEM_END) continue;
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty() || !stack.hasTag() || !stack.getTag().contains("AmmoId")) continue;

            ItemStack moving = stack.copy();
            placeIntoAmmoSlots(player, moving, slot);
            if (moving.isEmpty()) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
            } else if (moving.getCount() != stack.getCount()) {
                player.getInventory().setItem(slot, moving);
            }
        }
    }

    private static void enforceAmmoStackSizes(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack ammoStack = player.getInventory().getItem(slot);
            if (ammoStack.isEmpty() || !ammoStack.hasTag() || !ammoStack.getTag().contains("AmmoId")) continue;

            String ammoId = ammoStack.getTag().getString("AmmoId");
            int baseMax = TacZRegistryHelper.getAmmoStackSize(ammoId);
            boolean isAmmoSlot = slot >= GameConstants.SLOT_AMMO_GUN1_START && slot <= GameConstants.SLOT_AMMO_GUN2_END;
            boolean isCombatItemSlot = slot >= GameConstants.SLOT_ITEM_START && slot <= GameConstants.SLOT_ITEM_END;
            int maxStack = (isAmmoSlot || isCombatItemSlot)
                ? RunManager.getAmmoReserveStackLimit(player, baseMax)
                : RunManager.getAmmoStackLimit(player, baseMax);

            if (ammoStack.getCount() <= maxStack) continue;

            int excess = ammoStack.getCount() - maxStack;
            ammoStack.setCount(maxStack);
            ItemStack overflow = ammoStack.copy();
            overflow.setCount(excess);

            boolean placed = mergeIntoAmmoSlots(player, slot, overflow, baseMax);
            if (!placed && !overflow.isEmpty()) {
                placeAmmoOverflow(player, overflow);
            }
        }
    }

    private static boolean mergeIntoAmmoSlots(ServerPlayer player, int sourceSlot, ItemStack overflow, int baseMax) {
        for (int ammoSlot = GameConstants.SLOT_AMMO_GUN1_START; ammoSlot <= GameConstants.SLOT_AMMO_GUN2_END; ammoSlot++) {
            if (ammoSlot == sourceSlot) continue;
            ItemStack target = player.getInventory().getItem(ammoSlot);
            if (!target.isEmpty() && ItemStack.isSameItemSameTags(target, overflow)) {
                int space = RunManager.getAmmoReserveStackLimit(player, baseMax) - target.getCount();
                if (space > 0) {
                    int move = Math.min(space, overflow.getCount());
                    target.grow(move);
                    overflow.shrink(move);
                    if (overflow.isEmpty()) return true;
                }
            }
        }

        for (int ammoSlot = GameConstants.SLOT_AMMO_GUN1_START; ammoSlot <= GameConstants.SLOT_AMMO_GUN2_END; ammoSlot++) {
            if (player.getInventory().getItem(ammoSlot).isEmpty()) {
                int move = Math.min(RunManager.getAmmoReserveStackLimit(player, baseMax), overflow.getCount());
                ItemStack split = overflow.copy();
                split.setCount(move);
                player.getInventory().setItem(ammoSlot, split);
                overflow.shrink(move);
                if (overflow.isEmpty()) return true;
            }
        }

        return false;
    }

    private static void placeAmmoOverflow(ServerPlayer player, ItemStack overflow) {
        for (int slot = GameConstants.SLOT_ITEM_START; slot <= GameConstants.SLOT_ITEM_END; slot++) {
            ItemStack existing = player.getInventory().getItem(slot);
            if (!existing.isEmpty() && ItemStack.isSameItemSameTags(existing, overflow)) {
                int space = getGeneralAmmoLimit(player, existing) - existing.getCount();
                if (space > 0) {
                    int move = Math.min(space, overflow.getCount());
                    existing.grow(move);
                    overflow.shrink(move);
                    if (overflow.isEmpty()) return;
                }
            }
        }

        for (int slot = GameConstants.SLOT_ITEM_START; slot <= GameConstants.SLOT_ITEM_END; slot++) {
            if (player.getInventory().getItem(slot).isEmpty()) {
                int move = Math.min(getGeneralAmmoLimit(player, overflow), overflow.getCount());
                ItemStack split = overflow.copy();
                split.setCount(move);
                player.getInventory().setItem(slot, split);
                overflow.shrink(move);
                if (overflow.isEmpty()) return;
            }
        }

        int invLevel = player.getPersistentData().getInt("TacRogue_InvLevel");
        int maxAllowed = Math.min(GameConstants.SLOT_AMMO_GUN2_END + (invLevel * 2), 35);
        for (int slot = GameConstants.SLOT_AMMO_GUN2_END + 1; slot <= maxAllowed; slot++) {
            ItemStack existing = player.getInventory().getItem(slot);
            if (isLockedSlotVisual(existing)) continue;
            if (existing.isEmpty()) {
                int move = Math.min(getGeneralAmmoLimit(player, overflow), overflow.getCount());
                ItemStack split = overflow.copy();
                split.setCount(move);
                player.getInventory().setItem(slot, split);
                overflow.shrink(move);
                if (overflow.isEmpty()) return;
            }
        }

        if (!ShopPlacementService.sendToStash(player, overflow)) {
            player.drop(overflow.copy(), true, false);
        }
    }

    private static int getGeneralAmmoLimit(ServerPlayer player, ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains("AmmoId")) {
            int baseMax = TacZRegistryHelper.getAmmoStackSize(stack.getTag().getString("AmmoId"));
            return RunManager.getAmmoStackLimit(player, baseMax);
        }
        return stack.getMaxStackSize();
    }

    private static void clearLockedVisualsFromCombatSlots(ServerPlayer player) {
        for (int slot = 0; slot <= GameConstants.SLOT_AMMO_GUN2_END; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (isLockedSlotVisual(stack)) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
            }
        }
    }

    private static boolean placeIntoAmmoSlots(ServerPlayer player, ItemStack moving, int sourceSlot) {
        int baseMax = TacZRegistryHelper.getAmmoStackSize(moving.getTag().getString("AmmoId"));
        int reserveLimit = RunManager.getAmmoReserveStackLimit(player, baseMax);
        for (int ammoSlot = GameConstants.SLOT_AMMO_GUN1_START; ammoSlot <= GameConstants.SLOT_AMMO_GUN2_END; ammoSlot++) {
            if (ammoSlot == sourceSlot) continue;
            ItemStack target = player.getInventory().getItem(ammoSlot);
            if (!target.isEmpty() && ItemStack.isSameItemSameTags(target, moving)) {
                int space = reserveLimit - target.getCount();
                if (space > 0) {
                    int move = Math.min(space, moving.getCount());
                    target.grow(move);
                    moving.shrink(move);
                    if (moving.isEmpty()) return true;
                }
            }
        }
        for (int ammoSlot = GameConstants.SLOT_AMMO_GUN1_START; ammoSlot <= GameConstants.SLOT_AMMO_GUN2_END; ammoSlot++) {
            if (player.getInventory().getItem(ammoSlot).isEmpty()) {
                int move = Math.min(reserveLimit, moving.getCount());
                ItemStack split = moving.copy();
                split.setCount(move);
                player.getInventory().setItem(ammoSlot, split);
                moving.shrink(move);
                if (moving.isEmpty()) return true;
            }
        }
        return false;
    }

    private static boolean isLockedSlotVisual(ItemStack stack) {
        return stack.is(Items.BARRIER)
            && stack.hasTag()
            && stack.getOrCreateTag().getBoolean("rogue_item_locked");
    }

    private static void moveWeaponOrEject(ServerPlayer player, int fromSlot, boolean gun) {
        ItemStack stack = player.getInventory().getItem(fromSlot);
        if (stack.isEmpty()) return;
        int start = gun ? GameConstants.SLOT_GUN_START : GameConstants.SLOT_MELEE;
        int end = gun ? GameConstants.SLOT_GUN_END : GameConstants.SLOT_MELEE;
        for (int slot = start; slot <= end; slot++) {
            if (slot == fromSlot) continue;
            if (player.getInventory().getItem(slot).isEmpty()) {
                player.getInventory().setItem(slot, stack.copy());
                player.getInventory().setItem(fromSlot, ItemStack.EMPTY);
                return;
            }
        }
        if (!ShopPlacementService.sendToStash(player, stack)) {
            player.drop(stack.copy(), true, false);
        }
        player.getInventory().setItem(fromSlot, ItemStack.EMPTY);
    }

    public static void clearMemory() {
        ruleSnapshots.clear();
    }
}
