package com.levanilla.rogue.mixin;

import com.levanilla.rogue.core.PerkDefinition;
import com.levanilla.rogue.core.PlayerRunData;
import com.levanilla.rogue.core.RunManager;
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
        PlayerRunData data = RunManager.getDataOrNull(player.getUUID());
        if (data == null || !data.isRunActive()) return;

        float effect = PerkDefinition.sumCategoryEffect(player, PerkDefinition.Category.HANDLING);
        if (effect <= 0.0f) return;

        double speedBonus = Math.min(0.85D, effect / 100.0D);
        long adjusted = Math.round(cooldown / (1.0D + speedBonus));
        cir.setReturnValue(Math.max(0L, adjusted));
    }
}
