package com.lexovian.currentsoftrade.block;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.entity.CargoBoatEntity;
import com.lexovian.currentsoftrade.entity.SloopEntity;
import com.lexovian.currentsoftrade.entity.TradeBoatEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Harbor Brass Bell — rings automatically with a 30-tick Redstone signal whenever a Trade Ship
 * arrives at a nearby harbor. Right-click to ring manually and inspect nearby fleet activity.
 */
public class HarborBellBlock extends Block {

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 15.0, 14.0);

    public HarborBellBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) ? 15 : 0;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, false), 3);
            level.updateNeighborsAt(pos, this);
        }
    }

    @Override
    protected InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (!player.isSecondaryUseActive()) {
            useWithoutItem(state, level, pos, player, hit);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
            ringBell(serverLevel, pos, true);
            AABB area = new AABB(pos).inflate(64.0);
            int tradeShips = serverLevel.getEntitiesOfClass(TradeBoatEntity.class, area, e -> !e.isWanderingTraderShip()).size();
            int cargoShips = serverLevel.getEntitiesOfClass(CargoBoatEntity.class, area).size();
            int sloops = serverLevel.getEntitiesOfClass(SloopEntity.class, area, e -> !e.isMerchantShip()).size();
            if (player instanceof ServerPlayer sp) {
                sp.sendOverlayMessage(Component.translatable("message.currents_of_trade.harbor_bell_status",
                        tradeShips, cargoShips, sloops));
            }
        }
        return InteractionResult.SUCCESS;
    }

    public static void ringBell(ServerLevel level, BlockPos pos, boolean withHorn) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof HarborBellBlock bell)) return;
        level.setBlock(pos, state.setValue(POWERED, true), 3);
        level.updateNeighborsAt(pos, bell);
        level.scheduleTick(pos, bell, 30);

        level.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.4F, 0.92F);
        if (withHorn) {
            level.playSound(null, pos, CurrentsofTrade.SHIP_HORN_SOUND.get(), SoundSource.BLOCKS, 0.65F, 1.05F);
        }
        level.sendParticles(ParticleTypes.NOTE, pos.getX() + 0.5, pos.getY() + 1.15, pos.getZ() + 0.5, 5, 0.25, 0.15, 0.25, 0.05);
    }

    public static void ringNearbyBells(ServerLevel level, BlockPos harborPos) {
        BlockPos.MutableBlockPos scan = new BlockPos.MutableBlockPos();
        for (int dx = -12; dx <= 12; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dz = -12; dz <= 12; dz++) {
                    scan.set(harborPos.getX() + dx, harborPos.getY() + dy, harborPos.getZ() + dz);
                    if (level.isLoaded(scan) && level.getBlockState(scan).getBlock() instanceof HarborBellBlock) {
                        ringBell(level, scan.immutable(), false);
                    }
                }
            }
        }
    }
}
