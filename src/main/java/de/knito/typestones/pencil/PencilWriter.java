package de.knito.typestones.pencil;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;

import de.knito.typestones.ModBlocks;
import de.knito.typestones.ModComponents;
import de.knito.typestones.ModPencil;
import de.knito.typestones.block.Dyes;
import de.knito.typestones.block.TypestoneBlock;
import de.knito.typestones.block.TypestoneBlockEntity;
import de.knito.typestones.block.TypestoneItem;
import de.knito.typestones.picture.PictureTile;
import de.knito.typestones.typewriter.TypewriterBlockEntity;

/** What a pencil does on the server: place the next stone of its line, or take the last one back. */
public final class PencilWriter {
	private PencilWriter() {
	}

	/**
	 * Takes the next stone of the line from the Typewriter's output and places it. Clicking the stone
	 * placed last puts the next one to its right, in reading direction; clicking anything else places it
	 * there like a block, to start a new row.
	 */
	public static InteractionResult write(ServerLevel level, Player player, UseOnContext context, ItemStack pencil, PencilLine line) {
		if (line.done()) {
			return InteractionResult.PASS;
		}
		Storage<ItemVariant> output = output(level, player, line);
		if (output == null) {
			return InteractionResult.FAIL;
		}
		ItemStack stone = stone(pencil, line, line.next());
		ItemVariant variant = ItemVariant.of(stone);
		if (StorageUtil.simulateExtract(output, variant, 1, null) < 1) {
			player.sendOverlayMessage(Component.translatable("item.typestones.pencil.missing", ModBlocks.TYPESTONE_ITEM.getName(stone)));
			return InteractionResult.FAIL;
		}

		BlockPlaceContext place = line.picture().isPresent() && line.next() > 0
				? pictureContext(level, player, context, stone, line.picture().get(), line)
				: lineContext(level, player, context, stone, line);
		if (place == null) {
			return InteractionResult.FAIL;
		}
		BlockPos target = place.getClickedPos();
		if (!ModBlocks.TYPESTONE_ITEM.place(place).consumesAction()) {
			return InteractionResult.FAIL;
		}
		try (Transaction transaction = Transaction.openOuter()) {
			output.extract(variant, 1, transaction);
			transaction.commit();
		}

		PencilLine next = line.withPlaced(target);
		if (next.done()) {
			pencil.remove(ModComponents.PENCIL_LINE);
			player.sendOverlayMessage(Component.translatable(line.picture().isPresent()
					? "item.typestones.pencil.picture_written" : "item.typestones.pencil.written"));
		} else {
			pencil.set(ModComponents.PENCIL_LINE, next);
		}
		return InteractionResult.SUCCESS;
	}

	/** Clicking the stone placed last goes on to its right, in reading direction; anything else is placed as clicked. */
	private static BlockPlaceContext lineContext(ServerLevel level, Player player, UseOnContext context, ItemStack stone, PencilLine line) {
		if (!line.placed().isEmpty() && context.getClickedPos().equals(line.placed().getLast())
				&& level.getBlockState(context.getClickedPos()).getBlock() instanceof TypestoneBlock) {
			Direction right = level.getBlockState(context.getClickedPos()).getValue(TypestoneBlock.FACING).getClockWise();
			return against(player, context, stone, context.getClickedPos(), right);
		}
		return new BlockPlaceContext(player, context.getHand(), stone.copy(),
				new BlockHitResult(context.getClickLocation(), context.getClickedFace(), context.getClickedPos(), context.isInside()));
	}

	/**
	 * After the first stone, a picture goes on by itself wherever the player clicks: to the right of the
	 * stone before, or for a new row below the first stone of the row above (towards the player for a
	 * picture lying flat). Placed against a stone of the same picture, each stone lies as that one does.
	 */
	private static @Nullable BlockPlaceContext pictureContext(ServerLevel level, Player player, UseOnContext context, ItemStack stone,
			TypewriterBlockEntity.PictureJob picture, PencilLine line) {
		int next = line.next();
		boolean newRow = next % picture.cols() == 0;
		BlockPos neighbour = line.placed().get(newRow ? next - picture.cols() : next - 1);
		BlockState state = level.getBlockState(neighbour);
		if (!(state.getBlock() instanceof TypestoneBlock) || !(level.getBlockEntity(neighbour) instanceof TypestoneBlockEntity placed)
				|| placed.picture() == null || !placed.picture().hash().equals(picture.hash())) {
			player.sendOverlayMessage(Component.translatable("item.typestones.pencil.neighbour_gone"));
			return null;
		}
		Direction facing = state.getValue(TypestoneBlock.FACING);
		Direction side = !newRow ? facing.getClockWise() : state.getValue(TypestoneBlock.FLAT) ? facing.getOpposite() : Direction.DOWN;
		BlockPlaceContext place = against(player, context, stone, neighbour, side);
		if (!place.canPlace() || !place.getClickedPos().equals(neighbour.relative(side))) {
			player.sendOverlayMessage(Component.translatable("item.typestones.pencil.blocked"));
			return null;
		}
		if (!player.isWithinBlockInteractionRange(place.getClickedPos(), 1.0)) {
			player.sendOverlayMessage(Component.translatable("item.typestones.pencil.too_far"));
			return null;
		}
		return place;
	}

	/** Placing as if the player clicked the given side of the block at pos. */
	private static BlockPlaceContext against(Player player, UseOnContext context, ItemStack stone, BlockPos pos, Direction side) {
		return new BlockPlaceContext(player, context.getHand(), stone.copy(),
				new BlockHitResult(Vec3.atCenterOf(pos.relative(side)), side, pos, false));
	}

	/** Takes the stone placed last back into the Typewriter's output, or at the player's feet when it's full. */
	public static InteractionResult undo(ServerLevel level, Player player, ItemStack pencil, PencilLine line) {
		if (line.placed().isEmpty()) {
			return InteractionResult.PASS;
		}
		BlockPos last = line.placed().getLast();
		ItemStack back = stone(pencil, line, line.next() - 1);
		pencil.set(ModComponents.PENCIL_LINE, line.withoutLast());
		if (!(level.getBlockEntity(last) instanceof TypestoneBlockEntity stone) || !isStone(stone, back)) {
			// Broken or replaced since: forget it, so the next undo goes on with the one before.
			player.sendOverlayMessage(Component.translatable("item.typestones.pencil.gone"));
			return InteractionResult.SUCCESS;
		}
		level.removeBlock(last, false);
		Storage<ItemVariant> output = output(level, player, line);
		long inserted = 0;
		if (output != null) {
			try (Transaction transaction = Transaction.openOuter()) {
				inserted = output.insert(ItemVariant.of(back), 1, transaction);
				transaction.commit();
			}
		}
		if (inserted < 1) {
			Block.popResource(level, player.blockPosition(), back);
		}
		return InteractionResult.SUCCESS;
	}

	/** The stone at index of the pencil's line or picture, as an item of the Typewriter's output. */
	private static ItemStack stone(ItemStack pencil, PencilLine line, int index) {
		return line.picture()
				.map(picture -> ModBlocks.TYPESTONE_ITEM.pictureStack(picture.tile(index)))
				.orElseGet(() -> ModBlocks.TYPESTONE_ITEM.stack(line.letters().get(index), dyes(pencil), 1));
	}

	/** True when the placed stone is still the one the item makes. */
	private static boolean isStone(TypestoneBlockEntity placed, ItemStack item) {
		PictureTile tile = item.get(ModComponents.PICTURE);
		if (tile != null) {
			PictureTile there = placed.picture();
			return there != null && there.hash().equals(tile.hash()) && there.col() == tile.col() && there.row() == tile.row();
		}
		return placed.picture() == null && placed.letter().equals(TypestoneItem.letter(item))
				&& placed.dyes().equals(TypestoneItem.dyes(item));
	}

	/** The output of the pencil's Typewriter; keeps its chunk loaded for a while. Tells the player when it fails. */
	private static @Nullable Storage<ItemVariant> output(ServerLevel level, Player player, PencilLine line) {
		if (!line.typewriter().dimension().equals(level.dimension())) {
			player.sendOverlayMessage(Component.translatable("item.typestones.pencil.other_dimension"));
			return null;
		}
		BlockPos pos = line.typewriter().pos();
		level.getChunkSource().addTicketWithRadius(ModPencil.TICKET, ChunkPos.containing(pos), 1);
		if (!(level.getBlockEntity(pos) instanceof TypewriterBlockEntity typewriter)) {
			player.sendOverlayMessage(Component.translatable("item.typestones.pencil.no_typewriter"));
			return null;
		}
		Storage<ItemVariant> output = typewriter.output();
		if (output == null) {
			player.sendOverlayMessage(Component.translatable("gui.typestones.status.no_output"));
		}
		return output;
	}

	private static Dyes dyes(ItemStack pencil) {
		return pencil.getOrDefault(ModComponents.DYES, Dyes.DEFAULT);
	}
}
