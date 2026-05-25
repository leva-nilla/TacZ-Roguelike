package com.levanilla.rogue.mixin;

import com.levanilla.rogue.core.CommonEventHandler;
import com.levanilla.rogue.core.WeaponRarity;
import me.xjqsh.lrtactical.api.melee.MeleeAction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = me.xjqsh.lrtactical.item.MeleeItem.class, remap = false)
public abstract class MixinLrTacticalMeleeItem {
    @Inject(method = "getAttackCoolDown", at = @At("RETURN"), cancellable = true, remap = false)
    private void tacRogue$scaleMeleeCooldown(ItemStack stack, MeleeAction action, int actionCount,
                                             CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(tacRogue$scaleTicks(cir.getReturnValue(), stack, null));
    }

    @Inject(method = "getAttackDelay", at = @At("RETURN"), cancellable = true, remap = false)
    private void tacRogue$scaleMeleeAttackDelay(Player player, ItemStack stack, MeleeAction action, int actionCount,
                                                CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(tacRogue$scaleTicks(cir.getReturnValue(), stack, player));
    }

    private int tacRogue$scaleTicks(int originalTicks, ItemStack stack, Player player) {
        if (originalTicks <= 0 || stack == null || stack.isEmpty()) return originalTicks;
        if (player != null
                && player.level().dimension() != CommonEventHandler.ROGUE_DIM
                && player.level().dimension() != CommonEventHandler.LOBBY_DIM) {
            return originalTicks;
        }
        float mult = player == null
            ? WeaponRarity.getFireRateMult(stack)
            : WeaponRarity.getEffectiveMeleeFireRateMult(stack, player);
        if (mult <= 1.005f) return originalTicks;
        return Math.max(1, Math.min(originalTicks, Math.round(originalTicks / mult)));
    }
}
