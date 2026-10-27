package de.knito.typestones;

import java.util.List;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;

import de.knito.typestones.block.Dyes;
import de.knito.typestones.pencil.PencilItem;
import de.knito.typestones.pencil.PencilLine;
import de.knito.typestones.pencil.PencilToss;
import de.knito.typestones.pencil.PottedPencilBlock;
import de.knito.typestones.pencil.PottedPencilBlockEntity;

/** The pencil, which places a Typewriter's line stone by stone, and the flower pot it retires to. */
public class ModPencil {
	private static final ResourceKey<Item> ITEM_KEY = ResourceKey.create(Registries.ITEM, Typestones.id("pencil"));

	public static final PencilItem ITEM = Registry.register(BuiltInRegistries.ITEM, ITEM_KEY,
			new PencilItem(new Item.Properties().stacksTo(1).setId(ITEM_KEY)));

	private static final ResourceKey<Block> POTTED_KEY = ResourceKey.create(Registries.BLOCK, Typestones.id("potted_pencil"));

	/** A flower pot with an empty pencil in it; no item of its own, like vanilla potted plants. */
	public static final PottedPencilBlock POTTED = Registry.register(BuiltInRegistries.BLOCK, POTTED_KEY,
			new PottedPencilBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.FLOWER_POT).setId(POTTED_KEY)));

	public static final BlockEntityType<PottedPencilBlockEntity> POTTED_ENTITY = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE,
			Typestones.id("potted_pencil"),
			FabricBlockEntityTypeBuilder.create(PottedPencilBlockEntity::new, POTTED).build()
	);

	/**
	 * Keeps the chunk of a pencil's Typewriter loaded for 5 minutes after each use, so the pencil can
	 * reach its stones from afar. Loading only: the Typewriter doesn't run in that time.
	 */
	public static final TicketType TICKET = Registry.register(BuiltInRegistries.TICKET_TYPE, Typestones.id("pencil"),
			new TicketType(5 * 60 * 20, TicketType.FLAG_LOADING));

	/** A new pencil for the line of the Typewriter at typewriter, in its dyes. */
	public static ItemStack pencil(GlobalPos typewriter, List<String> letters, Dyes dyes) {
		ItemStack stack = new ItemStack(ITEM);
		stack.set(ModComponents.PENCIL_LINE, new PencilLine(typewriter, letters, List.of()));
		if (!dyes.equals(Dyes.DEFAULT)) {
			stack.set(ModComponents.DYES, dyes);
		}
		return stack;
	}

	public static void initialize() {
		PencilToss.initialize();
		// Vanilla's flower pot takes every click itself, so the pencil goes in before the pot sees it.
		UseBlockCallback.EVENT.register((player, level, hand, hit) ->
				!player.isSpectator() && PottedPencilBlock.pot(level, hit.getBlockPos(), player, hand) ? InteractionResult.SUCCESS : InteractionResult.PASS);
	}
}
