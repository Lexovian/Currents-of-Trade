package com.lexovian.currentsoftrade.block;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.item.NauticalChartItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Navigation Buoy — floating illuminated sea marker placed directly on open water.
 * Right-click with a bound Nautical Chart to append this buoy's water tile as a sea route waypoint.
 */
public class NavigationBuoyBlock extends Block {

    private static final VoxelShape FLOAT_BASE = Block.box(2.0, 0.0, 2.0, 14.0, 5.0, 14.0);
    private static final VoxelShape MAST = Block.box(6.0, 5.0, 6.0, 10.0, 12.0, 10.0);
    private static final VoxelShape LANTERN = Block.box(4.5, 11.0, 4.5, 11.5, 16.0, 11.5);
    public static final VoxelShape SHAPE = Shapes.or(FLOAT_BASE, MAST, LANTERN);

    public NavigationBuoyBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getFluidState(pos.below()).is(FluidTags.WATER) && level.getFluidState(pos).isEmpty();
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess scheduledTickAccess,
                                     BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && placer instanceof ServerPlayer sp && level.getServer() != null) {
            var adv = level.getServer().getAdvancements().get(
                    Identifier.fromNamespaceAndPath(CurrentsofTrade.MODID, "star_navigator"));
            if (adv != null) {
                for (String c : adv.value().criteria().keySet()) {
                    sp.getAdvancements().award(adv, c);
                }
            }
        }
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof NauticalChartItem && NauticalChartItem.isBound(stack)) {
            if (!level.isClientSide()) {
                NauticalChartItem.addWaypointAt(level, player, stack, pos.below());
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextFloat() < 0.28F) {
            double cx = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.35;
            double cy = pos.getY() + 0.92;
            double cz = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.35;
            level.addParticle(ParticleTypes.GLOW, cx, cy, cz, 0.0, 0.015, 0.0);
        }
        if (random.nextInt(180) == 0 && (level.isDarkOutside() || level.isRaining())) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5,
                    SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 0.35F, 1.35F, false);
        }
    }
}
