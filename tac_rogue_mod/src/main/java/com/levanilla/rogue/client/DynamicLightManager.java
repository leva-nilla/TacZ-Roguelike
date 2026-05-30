package com.levanilla.rogue.client;

import net.minecraft.core.BlockPos;

/**
 * Legacy compatibility stub.
 *
 * Flashlight lighting is server-authoritative in FlashlightManager using minecraft:light
 * blocks. The old client-side pseudo light path is intentionally disabled because
 * MixinClientLevel is no longer registered.
 */
public final class DynamicLightManager {
    private DynamicLightManager() {}

    public static int getActiveLightCount() {
        return 0;
    }

    public static boolean toggle() {
        return false;
    }

    public static boolean isEnabled() {
        return false;
    }

    public static void tick() {
    }

    public static int getDynamicLightAt(BlockPos pos) {
        return 0;
    }

    public static boolean hasActiveLights() {
        return false;
    }

    public static void cleanup() {
    }
}
