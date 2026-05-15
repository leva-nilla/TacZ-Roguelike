package com.levanilla.rogue.client;

import com.levanilla.rogue.core.TacZRegistryHelper;
import com.levanilla.rogue.core.registry.AttachmentDatabase;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * カスタムリソースリロードリスナー
 * ユーザーが言語を変更した際やF3+Tでリロードした際に呼び出され、
 * モッド内の古いキャッシュテキストや不要なデータをクリアします。
 */
public class ClientReloadListener implements ResourceManagerReloadListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientReloadListener.class);

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        LOGGER.debug("[TacZ Roguelike] Resource Manager Reload triggered. Clearing caches...");
        
        // 表示名/リスト系と、TacZ資産構造に依存する互換性DBを分けて破棄する。
        TacZRegistryHelper.clearRegistryCache();
        TacZRegistryHelper.clearShopItemCache();
        AttachmentDatabase.clearRuntimeCache();
        
        LOGGER.debug("[TacZ Roguelike] Cache cleared successfully.");
    }
}
