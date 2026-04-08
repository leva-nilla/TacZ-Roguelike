package com.levanilla.rogue.mixin;

/**
 * TickAnimationEvent Mixin は不要になりました。
 * 三人称の回転同期・ADS一人称切り替えは全て ClientEventHandler.onClientTick で処理します。
 * このクラスは mixins.json の参照用に残しています（defaultRequire=0 のため無害）。
 */
@org.spongepowered.asm.mixin.Mixin(targets = "com.tacz.guns.client.event.TickAnimationEvent", remap = false)
public class MixinTickAnimationEvent {
    // 全ロジックを ClientEventHandler に移動 — このクラスは空
}
