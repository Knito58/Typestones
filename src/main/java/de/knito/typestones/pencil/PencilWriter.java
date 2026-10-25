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
import de.knito.typestones.letter.Letters;
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
		String letter = line.letters().get(line.next());
		ItemStack stone = ModBlocks.TYPESTONE_ITEM.stack(letter, dyes(pencil), 1);
		ItemVariant variant = ItemVariant.of(stone);
		if (StorageUtil.simulateExtract(output, variant, 1, null) < 1) {
			player.sendOverlayMessage(Component.translatable("item.typestones.pencil.missing", stoneName(letter)));
			return InteractionResult.FAIL;
		}

		BlockPlaceContext place;
		if (!line.placed().isEmpty() && context.getClickedPos().equals(line.placed().getLast())
				&& level.getBlockState(context.getClickedPos()).getBlock() instanceof TypestoneBlock) {
			Direction right = level.getBlockState(context.getClickedPos()).getValue(TypestoneBlock.FACING).getClockWise();
			BlockPos last = context.getClickedPos();
			place = new BlockPlaceContext(player, context.getHand(), stone.copy(),
					new BlockHitResult(Vec3.atCenterOf(last.relative(right)), right, last, false));
		} else {
			place = new BlockPlaceContext(player, context.getHand(), stone.copy(),
					new BlockHitResult(context.getClickLocation(), context.getClickedFace(), context.getClickedPos(), context.isInside()));
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
			player.sendOverlayMessage(Component.translatable("item.typestones.pencil.written"));
		} else {
			pencil.set(ModComponents.PENCIL_LINE, next);
		}
		return InteractionResult.SUCCESS;
	}

	/** Breaks the stone placed last back into the Typewriter's output, or at the player's feet when it's full. */
	public static InteractionResult undo(ServerLevel level, Player player, ItemStack pencil, PencilLine line) {
		if (line.placed().isEmpty()) {
			return InteractionResult.PASS;
		}
		BlockPos last = line.placed().getLast();
		String letter = line.letters().get(line.next() - 1);
		Dyes dyes = dyes(pencil);
		pencil.set(ModComponents.PENCIL_LINE, line.withoutLast());
		if (!(level.getBlockEntity(last) instanceof TypestoneBlockEntity stone) || !stone.letter().equals(letter) || !stone.dyes().equals(dyes)) {
			// Broken or replaced since: forget it, so the next undo goes on with the one before.
			player.sendOverlayMessage(Component.translatable("item.typestones.pencil.gone"));
			return InteractionResult.SUCCESS;
		}
		level.removeBlock(last, false);
		ItemStack back = ModBlocks.TYPESTONE_ITEM.stack(letter, dyes, 1);
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

	private static Component stoneName(String letter) {
		return ModBlocks.TYPESTONE_ITEM.getName(ModBlocks.TYPESTONE_ITEM.stack(letter, Dyes.DEFAULT, 1));
	}
}
