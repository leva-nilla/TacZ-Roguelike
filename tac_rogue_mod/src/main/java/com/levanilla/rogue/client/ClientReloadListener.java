package com.levanilla.rogue.client;

import com.levanilla.rogue.core.TacZRegistryHelper;
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
        LOGGER.info("[TacZ Roguelike] Resource Manager Reload triggered. Clearing caches...");
        
        // TacZRegistryHelperのキャッシュ（ショップの表示名など）をクリア
        TacZRegistryHelper.clearCache();
        
        LOGGER.info("[TacZ Roguelike] Cache cleared successfully.");
    }
}
