package de.knito.typestones.pencil;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import de.knito.typestones.ModPencil;
import de.knito.typestones.Typestones;

/**
 * A flower pot holding a pencil whose line is written. Right-click with an empty hand takes it out
 * again; breaking drops the pot and the pencil.
 */
public class PottedPencilBlock extends BaseEntityBlock {
	private static final VoxelShape SHAPE = Block.box(5, 0, 5, 11, 6, 11);

	public PottedPencilBlock(Properties properties) {
		super(properties);
	}

	/** Puts an empty pencil into the empty flower pot at pos. True when it did. */
	public static boolean pot(Level level, BlockPos pos, Player player, InteractionHand hand) {
		ItemStack pencil = player.getItemInHand(hand);
		if (!PencilToss.isEmptyPencil(pencil) || !level.getBlockState(pos).is(Blocks.FLOWER_POT)) {
			return false;
		}
		if (level.isClientSide()) {
			return true;
		}
		level.setBlockAndUpdate(pos, ModPencil.POTTED.defaultBlockState());
		if (level.getBlockEntity(pos) instanceof PottedPencilBlockEntity potted) {
			potted.setPencil(pencil);
		}
		pencil.consume(1, player);
		level.playSound(null, pos, SoundEvents.DECORATED_POT_INSERT, SoundSource.BLOCKS, 1.0f, 1.0f);
		if (player instanceof ServerPlayer serverPlayer) {
			AdvancementHolder advancement = serverPlayer.level().getServer().getAdvancements().get(Typestones.id("potted_pencil"));
			if (advancement != null) {
				serverPlayer.getAdvancements().award(advancement, "potted");
			}
		}
		return true;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level.getBlockEntity(pos) instanceof PottedPencilBlockEntity potted)) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			ItemStack pencil = potted.pencil();
			if (!player.getInventory().add(pencil)) {
				Block.popResource(level, pos, pencil);
			}
			level.setBlockAndUpdate(pos, Blocks.FLOWER_POT.defaultBlockState());
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new PottedPencilBlockEntity(pos, state);
	}
}
