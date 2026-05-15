package com.levanilla.rogue.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * アイテム保管庫（スタッシュ）へのアクセス用ブロック。
 * 初期サイズは 9×2 (18スロット)、ショップで拡張可能。
 * ChestMenu を使用し、unlockedLines に応じて 3行 or 6行 を開く。
 */
public class StashBlock extends Block {
    public StashBlock(Properties props) {
        super(props);
    }

    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 16, 14);

    /** Oculus/Iris シェーダーとの互換性: カスタムVoxelShapeに光遮蔽を使わない */
    @Override
    public boolean useShapeForLightOcclusion(BlockState state) { return false; }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            openFor(serverPlayer);
        }
        return InteractionResult.SUCCESS;
    }

    public static void openFor(ServerPlayer serverPlayer) {
        com.levanilla.rogue.core.StashSavedData data = com.levanilla.rogue.core.StashSavedData.get(serverPlayer.serverLevel());
        com.levanilla.rogue.core.StashSavedData.PlayerStash stash = data.getStash(serverPlayer.getUUID());

        int rows = Math.max(2, Math.min(6, stash.unlockedLines));
        serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider(
            (id, inv, p) -> {
                switch (rows) {
                    case 2: return new net.minecraft.world.inventory.ChestMenu(net.minecraft.world.inventory.MenuType.GENERIC_9x2, id, inv, stash, 2);
                    case 3: return new net.minecraft.world.inventory.ChestMenu(net.minecraft.world.inventory.MenuType.GENERIC_9x3, id, inv, stash, 3);
                    case 4: return new net.minecraft.world.inventory.ChestMenu(net.minecraft.world.inventory.MenuType.GENERIC_9x4, id, inv, stash, 4);
                    case 5: return new net.minecraft.world.inventory.ChestMenu(net.minecraft.world.inventory.MenuType.GENERIC_9x5, id, inv, stash, 5);
                    default: return new net.minecraft.world.inventory.ChestMenu(net.minecraft.world.inventory.MenuType.GENERIC_9x6, id, inv, stash, 6);
                }
            },
            net.minecraft.network.chat.Component.literal("\u00A7b[STASH TERMINAL]")
        ));
    }
}
