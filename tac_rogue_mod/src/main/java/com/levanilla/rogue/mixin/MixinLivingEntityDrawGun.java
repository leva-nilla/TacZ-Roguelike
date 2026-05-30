package com.levanilla.rogue.mixin;

import com.levanilla.rogue.core.PerkDefinition;
import com.levanilla.rogue.core.service.RogueUtilityItemService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = com.tacz.guns.entity.shooter.LivingEntityDrawGun.class, remap = false)
public abstract class MixinLivingEntityDrawGun {
    @Shadow @Final private LivingEntity shooter;

    @Inject(method = "getDrawCoolDown", at = @At("RETURN"), cancellable = true, remap = false)
    private void tacRogue$applyHandlingDrawSpeed(CallbackInfoReturnable<Long> cir) {
        long cooldown = cir.getReturnValue();
        if (cooldown <= 0L || !(shooter instanceof ServerPlayer player)) return;

        float effect = PerkDefinition.sumCategoryEffect(player, PerkDefinition.Category.HANDLING);
        float utilityEffect = RogueUtilityItemService.getDrawSpeedBonusPercent(player);
        if (effect <= 0.0f && utilityEffect == 0.0f) return;

        double speedBonus = Math.max(-0.35D, Math.min(0.95D, (effect + utilityEffect) / 100.0D));
        long adjusted = Math.round(cooldown / (1.0D + speedBonus));
        cir.setReturnValue(Math.max(0L, adjusted));
    }
}
