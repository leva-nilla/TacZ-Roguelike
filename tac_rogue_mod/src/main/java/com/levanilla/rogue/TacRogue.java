package com.levanilla.rogue;

import com.levanilla.rogue.core.CommonEventHandler;
import com.levanilla.rogue.core.ModBlocks;
import com.levanilla.rogue.core.ModEntities;
import com.levanilla.rogue.core.ModItems;
import com.levanilla.rogue.core.RogueConfig;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.ModList;

@Mod(TacRogue.MOD_ID)
public class TacRogue {
    public static final String MOD_ID = "tac_rogue";
    public static final String DISPLAY_NAME = "TacZ: Rogue Protocol";

    public TacRogue() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModEntities.register(modEventBus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, RogueConfig.COMMON_SPEC);

        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(CommonEventHandler.class);
        com.levanilla.rogue.networking.TacRogueNetworking.register();

        modEventBus.addListener(this::onCommonSetup);
        modEventBus.addListener(ModEntities::registerAttributes);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // Setup Tasks
        });
    }

    public static String version() {
        return ModList.get()
            .getModContainerById(MOD_ID)
            .map(container -> container.getModInfo().getVersion().toString())
            .orElse("dev");
    }

    public static String displayNameWithVersion() {
        return DISPLAY_NAME + " v" + version();
    }
}
