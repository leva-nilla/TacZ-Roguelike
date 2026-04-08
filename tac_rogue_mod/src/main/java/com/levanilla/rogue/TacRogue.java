package com.levanilla.rogue;

import com.levanilla.rogue.core.CommonEventHandler;
import com.levanilla.rogue.core.ModBlocks;
import com.levanilla.rogue.core.ModItems;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod("tac_rogue")
public class TacRogue {
    public TacRogue() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);

        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(CommonEventHandler.class);
        com.levanilla.rogue.networking.TacRogueNetworking.register();

        // TacZ JAR からアタッチメント互換性データを読み込み
        com.levanilla.rogue.core.registry.AttachmentDatabase.init();
    }
}
