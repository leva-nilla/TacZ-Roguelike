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
    private static final int MIN_MELEE_ATTACK_DELAY_TICKS = 1;
    private static final int MIN_MELEE_COOLDOWN_TICKS = 4;

    @Inject(method = "getAttackCoolDown", at = @At("RETURN"), cancellable = true, remap = false)
    private void tacRogue$scaleMeleeCooldown(ItemStack stack, MeleeAction action, int actionCount,
                                             CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(tacRogue$scaleCooldownTicks(cir.getReturnValue(), stack));
    }

    @Inject(method = "getAttackDelay", at = @At("RETURN"), cancellable = true, remap = false)
    private void tacRogue$scaleMeleeAttackDelay(Player player, ItemStack stack, MeleeAction action, int actionCount,
                                                CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(tacRogue$scaleAttackDelayTicks(cir.getReturnValue(), stack, player));
    }

    private int tacRogue$scaleCooldownTicks(int originalTicks, ItemStack stack) {
        if (originalTicks <= 0 || stack == null || stack.isEmpty()) return originalTicks;
        float mult = WeaponRarity.getFireRateMult(stack);
        if (mult <= 1.005f) return originalTicks;
        int min = Math.max(1, Math.min(originalTicks, MIN_MELEE_COOLDOWN_TICKS));
        return Math.max(min, Math.min(originalTicks, Math.round(originalTicks / mult)));
    }

    private int tacRogue$scaleAttackDelayTicks(int originalTicks, ItemStack stack, Player player) {
        if (stack == null || stack.isEmpty() || player == null) return originalTicks;
        if (!tacRogue$isRogueContext(player)) return originalTicks;
        int baseTicks = originalTicks <= 0 ? MIN_MELEE_ATTACK_DELAY_TICKS : originalTicks;
        float mult = WeaponRarity.getEffectiveMeleeFireRateMult(stack, player);
        if (mult <= 1.005f) return baseTicks;
        int min = Math.max(1, Math.min(baseTicks, MIN_MELEE_ATTACK_DELAY_TICKS));
        return Math.max(min, Math.min(baseTicks, Math.round(baseTicks / mult)));
    }

    private boolean tacRogue$isRogueContext(Player player) {
        return player.level().dimension() == CommonEventHandler.ROGUE_DIM
            || player.level().dimension() == CommonEventHandler.LOBBY_DIM;
    }
}
