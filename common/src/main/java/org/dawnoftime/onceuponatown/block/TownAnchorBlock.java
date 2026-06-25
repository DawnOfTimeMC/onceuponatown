package org.dawnoftime.onceuponatown.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import org.dawnoftime.onceuponatown.blockentity.TownAnchorBlockEntity;
import org.dawnoftime.onceuponatown.network.NetworkHelper;
import org.dawnoftime.onceuponatown.registry.BlockEntityRegistry;
import org.dawnoftime.onceuponatown.town.LevelTowns;
import org.dawnoftime.onceuponatown.town.Town;
import org.jetbrains.annotations.Nullable;

public class TownAnchorBlock extends BaseEntityBlock {

    public static Properties defaultProperties() {
        return BlockBehaviour.Properties.of()
            .noOcclusion()                     // campfire model is not a full cube visually
            .strength(-1.0f, Float.MAX_VALUE)  // indestructible except by creative players
            .lightLevel(s -> 15);              // emits warm light like a lit campfire
    }

    public TownAnchorBlock(Properties props) { super(props); }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TownAnchorBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                  Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            TownAnchorBlockEntity be = (TownAnchorBlockEntity) level.getBlockEntity(pos);
            if (be != null) {
                Town town = LevelTowns.get((net.minecraft.server.level.ServerLevel) level).getTownAt(pos).orElse(null);
                if (town == null) return InteractionResult.FAIL;
                net.minecraft.nbt.CompoundTag hubData = town.getHubData(pos);
                hubData.putBoolean("ChatSubscribed", town.isChatSubscriber(player.getUUID()));
                NetworkHelper.sendTownHubPacket.accept((ServerPlayer) player, hubData);
                player.openMenu(be);
            }
        }
        return InteractionResult.SUCCESS;
    }

    // Prevent survival destruction - only creative players can remove it
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!player.isCreative()) return;
        super.playerWillDestroy(level, pos, state, player);
    }

    // Register a client-side ticker so particles/sounds run every tick like vanilla CampfireBlockEntity
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
            ? createTickerHelper(type, BlockEntityRegistry.TOWN_ANCHOR, TownAnchorBlockEntity::clientTick)
            : null;
    }
}
