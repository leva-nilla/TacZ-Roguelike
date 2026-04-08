package com.levanilla.rogue.core;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.core.registries.Registries;

/**
 * ディメンション定数の定義。
 * イベント処理はサブハンドラ (core.event パッケージ) に委譲済み。
 *
 * @see com.levanilla.rogue.core.event.PlayerTickHandler
 * @see com.levanilla.rogue.core.event.CombatEventHandler
 * @see com.levanilla.rogue.core.event.ItemAndLifecycleHandler
 * @see com.levanilla.rogue.core.event.SpawnAndWorldHandler
 */
public class CommonEventHandler {

    public static final ResourceKey<Level> LOBBY_DIM = ResourceKey.create(
        Registries.DIMENSION, new ResourceLocation("tac_rogue", "lobby_dimension"));

    public static final ResourceKey<Level> ROGUE_DIM = ResourceKey.create(
        Registries.DIMENSION, new ResourceLocation("tac_rogue", "rogue_dimension"));
}
