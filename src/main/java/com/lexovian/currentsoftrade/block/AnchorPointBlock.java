package com.lexovian.currentsoftrade.block;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.block.entity.AnchorPointBlockEntity;
import com.lexovian.currentsoftrade.world.inventory.AnchorPointMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class AnchorPointBlock extends Block implements EntityBlock {

    public AnchorPointBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AnchorPointBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable net.minecraft.world.entity.LivingEntity placer, net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof AnchorPointBlockEntity anchor) {
                try {
                    if (stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
                        anchor.setHarborName(stack.getHoverName().getString());
                    } else {
                        anchor.setHarborName(AnchorPointBlockEntity.generateDynamicHarborName(level, pos));
                    }
                } catch (Throwable t) {
                    CurrentsofTrade.LOGGER.error("Failed to initialize harbor name for anchor point at {}", pos, t);
                }

                AnchorPointBlockEntity.HarborStatus status = anchor.getHarborStatus();
                if (status != AnchorPointBlockEntity.HarborStatus.VALID) {
                    Component warnMsg = switch (status) {
                        case TOO_SMALL -> Component.translatable("message.currents_of_trade.harbor_too_small");
                        case UNDERGROUND -> Component.translatable("message.currents_of_trade.harbor_underground");
                        case TOO_SHALLOW -> Component.translatable("message.currents_of_trade.harbor_too_shallow");
                        case WRONG_DIMENSION -> Component.translatable("message.currents_of_trade.overworld_only");
                        default -> Component.translatable("message.currents_of_trade.anchor_placed_no_water");
                    };
                    player.displayClientMessage(warnMsg, true);
                } else {
                    player.displayClientMessage(Component.translatable("message.currents_of_trade.harbor_established", anchor.getHarborName()), true);
                }
            }
        }
    }

    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hitResult) {
        if (stack.is(net.minecraft.world.item.Items.NAME_TAG) && stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
            if (!level.isClientSide) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof AnchorPointBlockEntity anchor) {
                    String newName = stack.getHoverName().getString();
                    anchor.setHarborName(newName);
                    if (!player.getAbilities().instabuild) {
                        stack.shrink(1);
                    }
                    level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ANVIL_USE, net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.2F);
                    player.displayClientMessage(Component.translatable("message.currents_of_trade.harbor_renamed", newName), true);
                }
            }
            return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            int harborLevel = 1;
            if (level.getBlockEntity(pos) instanceof AnchorPointBlockEntity anchor) {
                harborLevel = anchor.getTradeLevel();
            }
            final int finalLevel = harborLevel;
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (containerId, playerInventory, p) -> new AnchorPointMenu(containerId, playerInventory, ContainerLevelAccess.create(level, pos), pos),
                    Component.translatable("gui.currents_of_trade.anchor_point")
            ), buf -> {
                buf.writeBlockPos(pos);
                buf.writeVarInt(finalLevel);
            });
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
