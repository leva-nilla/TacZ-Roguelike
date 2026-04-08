package com.levanilla.rogue.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = "tac_rogue", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientKeyBinds {
    
    public static final String CATEGORY = "key.categories.tac_rogue";
    
    // ======== 新規登録キー ========
    public static final KeyMapping FLASHLIGHT = new KeyMapping(
            "key.tac_rogue.flashlight",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Q,
            CATEGORY
    );

    public static final KeyMapping CAMERA_TOGGLE = new KeyMapping(
            "key.tac_rogue.camera_toggle",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            CATEGORY
    );

    @SubscribeEvent
    public static void onKeyRegister(RegisterKeyMappingsEvent event) {
        event.register(FLASHLIGHT);
        event.register(CAMERA_TOGGLE);
    }
}
