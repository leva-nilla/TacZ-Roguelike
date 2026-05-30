package com.levanilla.rogue.mixin;

import com.levanilla.rogue.core.PerkDefinition;
import com.levanilla.rogue.core.WeaponRarity;
import com.levanilla.rogue.core.service.RogueUtilityItemService;
import com.tacz.guns.api.client.animation.ObjectAnimation;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = com.tacz.guns.api.client.animation.ObjectAnimationRunner.class, remap = false)
public abstract class MixinObjectAnimationRunner {
    @Shadow @Final private ObjectAnimation animation;

    @ModifyVariable(method = "updateProgress", at = @At("HEAD"), argsOnly = true, remap = false)
    private long tacRogue$speedUpReloadAnimation(long deltaNs) {
        if (animation == null || animation.name == null) {
            return deltaNs;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return deltaNs;

        ItemStack stack = mc.player.getMainHandItem();
        if (animation.name.startsWith("melee_")) {
            float meleeMult = WeaponRarity.getEffectiveMeleeFireRateMult(stack, mc.player);
            if (meleeMult <= 1.005f) return deltaNs;

            double accelerated = deltaNs * meleeMult;
            return accelerated >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(1L, Math.round(accelerated));
        }

        if (isDrawAnimation(animation.name)) {
            float effect = PerkDefinition.sumClientCategoryEffect(PerkDefinition.Category.HANDLING);
            float utilityEffect = RogueUtilityItemService.getDrawSpeedBonusPercent(mc.player);
            if (effect > 0.0f || utilityEffect != 0.0f) {
                double speed = 1.0D + Math.max(-0.35D, Math.min(0.95D, (effect + utilityEffect) / 100.0D));
                double accelerated = deltaNs * speed;
                return accelerated >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(1L, Math.round(accelerated));
            }
        }

        if (!animation.name.startsWith("reload")) {
            return deltaNs;
        }

        if (stack.isEmpty() || !stack.hasTag() || !stack.getTag().contains("GunId")) {
            return deltaNs;
        }

        float reloadMult = WeaponRarity.getEffectiveReloadMult(stack, mc.player);
        if (reloadMult >= 0.995f) return deltaNs;

        double accelerated = deltaNs / reloadMult;
        return accelerated >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(1L, Math.round(accelerated));
    }

    private static boolean isDrawAnimation(String name) {
        return name.startsWith("draw") || name.startsWith("put_away") || name.startsWith("putaway");
    }
}
