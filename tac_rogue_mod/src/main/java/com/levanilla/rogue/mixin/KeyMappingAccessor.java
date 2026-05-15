package com.levanilla.rogue.mixin;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
    @Accessor("clickCount")
    int tacRogue$getClickCount();

    @Accessor("clickCount")
    void tacRogue$setClickCount(int value);
}
