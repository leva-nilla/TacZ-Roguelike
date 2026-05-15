package com.levanilla.rogue.core;

import com.levanilla.rogue.world.TacRogueNpcEntity;
import com.levanilla.rogue.world.TacRogueBossEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
        DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "tac_rogue");

    public static final RegistryObject<EntityType<TacRogueNpcEntity>> TAC_ROGUE_NPC =
        ENTITIES.register("npc", () -> EntityType.Builder
            .of(TacRogueNpcEntity::new, MobCategory.MISC)
            .sized(0.62f, 1.95f)
            .clientTrackingRange(10)
            .updateInterval(3)
            .build("tac_rogue:npc"));

    public static final RegistryObject<EntityType<TacRogueBossEntity>> TAC_ROGUE_BOSS =
        ENTITIES.register("boss", () -> EntityType.Builder
            .of(TacRogueBossEntity::new, MobCategory.MONSTER)
            .sized(0.95f, 2.7f)
            .clientTrackingRange(12)
            .updateInterval(2)
            .build("tac_rogue:boss"));

    public static void register(IEventBus eventBus) {
        ENTITIES.register(eventBus);
    }

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(TAC_ROGUE_NPC.get(), TacRogueNpcEntity.createAttributes().build());
        event.put(TAC_ROGUE_BOSS.get(), TacRogueBossEntity.createAttributes().build());
    }
}
