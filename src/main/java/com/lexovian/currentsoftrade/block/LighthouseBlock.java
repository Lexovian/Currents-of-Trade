package com.lexovian.currentsoftrade.block;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.block.entity.LighthouseBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Lighthouse Lantern (Deniz Feneri Bloğu).
 *
 * <p>Emits light level 15 and projects a rotating nautical beam across coastal waters.
 * Ships within 128 blocks receive the Tailwind effect, granting +25% cruising speed
 * and immunity to ocean tempest resistance.
 */
public class LighthouseBlock extends Block implements EntityBlock {

    private static final VoxelShape BASE = Block.box(2.0, 0.0, 2.0, 14.0, 3.0, 14.0);
    private static final VoxelShape PILLAR = Block.box(3.0, 3.0, 3.0, 13.0, 13.0, 13.0);
    private static final VoxelShape CAP = Block.box(2.0, 13.0, 2.0, 14.0, 16.0, 14.0);
    public static final VoxelShape SHAPE = Shapes.or(BASE, PILLAR, CAP);

    public LighthouseBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LighthouseBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return type == CurrentsofTrade.LIGHTHOUSE_BE.get()
                ? (lvl, p, st, be) -> LighthouseBlockEntity.tick(lvl, p, st, (LighthouseBlockEntity) be)
                : null;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double centerX = pos.getX() + 0.5;
        double centerY = pos.getY() + 0.65;
        double centerZ = pos.getZ() + 0.5;

        // Rotating beacon glow
        long time = level.getGameTime();
        double angle = (time * 0.08) % (Math.PI * 2);
        double beamRadius = 1.2;

        double px = centerX + Math.cos(angle) * beamRadius;
        double pz = centerZ + Math.sin(angle) * beamRadius;

        level.addParticle(ParticleTypes.END_ROD, px, centerY, pz, Math.cos(angle) * 0.05, 0.01, Math.sin(angle) * 0.05);

        if (random.nextFloat() < 0.35F) {
            level.addParticle(ParticleTypes.GLOW, centerX + (random.nextDouble() - 0.5) * 0.8, centerY + (random.nextDouble() - 0.5) * 0.6, centerZ + (random.nextDouble() - 0.5) * 0.8, 0, 0.02, 0);
        }

        // Night or storm ambient foghorn/bell chime
        if (random.nextInt(120) == 0 && (level.isNight() || level.isRaining())) {
            level.playLocalSound(centerX, centerY, centerZ, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 0.7F, 0.6F, false);
        }
    }

    @Override
    public void appendHoverText(net.minecraft.world.item.ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, java.util.List<net.minecraft.network.chat.Component> tooltipComponents, net.minecraft.world.item.TooltipFlag tooltipFlag) {
        tooltipComponents.add(net.minecraft.network.chat.Component.translatable("tooltip.currents_of_trade.lighthouse.desc"));
        tooltipComponents.add(net.minecraft.network.chat.Component.translatable("tooltip.currents_of_trade.lighthouse.tailwind"));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}

