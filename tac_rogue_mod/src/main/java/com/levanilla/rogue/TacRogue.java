package com.levanilla.rogue;

import com.levanilla.rogue.core.CommonEventHandler;
import com.levanilla.rogue.core.ModBlocks;
import com.levanilla.rogue.core.ModItems;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import java.lang.reflect.Field;
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

        modEventBus.addListener(this::onCommonSetup);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // AmmoDatabase の設定に合わせて弾薬のインベントリスタックサイズを動的にオーバーライドする
            for (Item item : ForgeRegistries.ITEMS) {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
                if (id != null && "tacz".equals(id.getNamespace())) {
                    int expectedSize = com.levanilla.rogue.core.registry.AmmoDatabase.getAmmoStackSize(id.toString());
                    if (item.getMaxStackSize() != expectedSize) {
                        try {
                            Field targetField = null;
                            for (Field f : Item.class.getDeclaredFields()) {
                                if (f.getType() == int.class) {
                                    f.setAccessible(true);
                                    int val = f.getInt(item);
                                    if (val == item.getMaxStackSize()) {
                                        targetField = f;
                                        break;
                                    }
                                }
                            }
                            if (targetField != null) {
                                targetField.setInt(item, expectedSize);
                            }
                        } catch (Exception e) {
                            System.err.println("TacRogue: Failed to override stack size for " + id);
                        }
                    }
                }
            }
        });
    }
}
