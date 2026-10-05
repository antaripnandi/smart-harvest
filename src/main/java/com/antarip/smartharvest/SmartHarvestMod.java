package com.antarip.smartharvest;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TorchflowerCropBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

public class SmartHarvestMod implements ModInitializer {
    public static final String MOD_ID = "smartharvest";

    @Override
    public void onInitialize() {
        UseBlockCallback.EVENT.register(SmartHarvestMod::onUseBlock);
    }

    private static InteractionResult onUseBlock(Player player, Level world, InteractionHand hand, BlockHitResult hitResult) {
        BlockPos pos = hitResult.getBlockPos();
        if (hand != InteractionHand.MAIN_HAND || player.isSpectator() || !player.mayBuild() || !world.mayInteract(player, pos)) {
            return InteractionResult.PASS;
        }

        // Allow players sneaking with an item to perform block placement or secondary actions
        if (player.isShiftKeyDown() && !player.getItemInHand(hand).isEmpty()) {
            return InteractionResult.PASS;
        }

        BlockState state = world.getBlockState(pos);
        Block block = state.getBlock();

        boolean isMature = false;
        BlockState resetState = null;

        if (block instanceof CropBlock cropBlock) {
            if (cropBlock.isMaxAge(state)) {
                isMature = true;
                resetState = cropBlock.getStateForAge(0);
            }
        } else if (block instanceof NetherWartBlock) {
            if (state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE) {
                isMature = true;
                resetState = state.setValue(NetherWartBlock.AGE, 0);
            }
        } else if (block instanceof CocoaBlock) {
            if (state.getValue(CocoaBlock.AGE) >= CocoaBlock.MAX_AGE) {
                isMature = true;
                resetState = state.setValue(CocoaBlock.AGE, 0);
            }
        }

        if (!isMature || resetState == null) {
            return InteractionResult.PASS;
        }

        Item seedItem = getSeedItem(block);
        if (seedItem == null || seedItem == Items.AIR) {
            return InteractionResult.PASS;
        }

        if (world.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        ServerLevel serverLevel = (ServerLevel) world;
        ItemStack held = player.getItemInHand(hand);
        List<ItemStack> drops = Block.getDrops(state, serverLevel, pos, null, player, held);

        boolean seedAvailable = player.getAbilities().instabuild;

        // Try to consume 1 seed from harvested drops to replant
        if (!seedAvailable) {
            for (ItemStack drop : drops) {
                if (drop.is(seedItem)) {
                    drop.shrink(1);
                    seedAvailable = true;
                    break;
                }
            }
        }

        // If drops didn't provide a seed (e.g. Torchflower), check player inventory
        if (!seedAvailable && seedItem != Items.AIR) {
            int slot = player.getInventory().findSlotMatchingItem(new ItemStack(seedItem));
            if (slot >= 0) {
                player.getInventory().removeItem(slot, 1);
                seedAvailable = true;
            }
        }

        // If still no seed available to replant, break naturally without duplication
        if (!seedAvailable) {
            world.destroyBlock(pos, true, player);
            return InteractionResult.SUCCESS;
        }

        // Spawn remaining harvested crops and bonus seeds
        for (ItemStack drop : drops) {
            if (!drop.isEmpty()) {
                Block.popResource(world, pos, drop);
            }
        }

        // Replant crop at age 0
        world.setBlock(pos, resetState, 3);

        // Visual and auditory feedback: break particles + break sound + replant sound
        world.levelEvent(2001, pos, Block.getId(state));
        world.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0f, 1.0f);
        world.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);

        return InteractionResult.SUCCESS;
    }

    private static Item getSeedItem(Block block) {
        if (block == Blocks.WHEAT) {
            return Items.WHEAT_SEEDS;
        } else if (block == Blocks.BEETROOTS) {
            return Items.BEETROOT_SEEDS;
        } else if (block == Blocks.CARROTS) {
            return Items.CARROT;
        } else if (block == Blocks.POTATOES) {
            return Items.POTATO;
        } else if (block == Blocks.NETHER_WART) {
            return Items.NETHER_WART;
        } else if (block == Blocks.COCOA) {
            return Items.COCOA_BEANS;
        } else if (block instanceof TorchflowerCropBlock) {
            return Items.TORCHFLOWER_SEEDS;
        }
        return block.asItem();
    }
}
