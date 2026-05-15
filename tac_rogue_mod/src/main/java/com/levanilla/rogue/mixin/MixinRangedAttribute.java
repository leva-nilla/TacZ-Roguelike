package com.levanilla.rogue.mixin;

import com.levanilla.rogue.core.GameConstants;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RangedAttribute.class)
public abstract class MixinRangedAttribute {
    @Inject(method = "sanitizeValue", at = @At("HEAD"), cancellable = true)
    private void tacRogue$raiseArmorAttributeCap(double value, CallbackInfoReturnable<Double> cir) {
        Attribute attribute = (Attribute) (Object) this;
        if ("attribute.name.generic.armor".equals(attribute.getDescriptionId())) {
            cir.setReturnValue(Mth.clamp(value, 0.0D, GameConstants.ROGUE_ARMOR_ATTRIBUTE_MAX));
        }
    }
}
