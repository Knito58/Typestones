package de.knito.typestones;

import java.util.List;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import de.knito.typestones.block.Dyes;
import de.knito.typestones.pencil.PencilItem;
import de.knito.typestones.pencil.PencilLine;

/** The pencil, which places a Typewriter's line stone by stone. */
public class ModPencil {
	private static final ResourceKey<Item> ITEM_KEY = ResourceKey.create(Registries.ITEM, Typestones.id("pencil"));

	public static final PencilItem ITEM = Registry.register(BuiltInRegistries.ITEM, ITEM_KEY,
			new PencilItem(new Item.Properties().stacksTo(1).setId(ITEM_KEY)));

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
	}
}
