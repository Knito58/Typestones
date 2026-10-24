package de.knito.typestones.block;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.Block;

import de.knito.typestones.ModComponents;
import de.knito.typestones.glyph.GlyphSet;

/**
 * The Typestone item. Its glyph is in the vanilla block_state component, which also places the block
 * with that glyph; its dyes are in the typestones:dyes component.
 */
public class TypestoneItem extends BlockItem {
	public TypestoneItem(Block block, Properties properties) {
		super(block, properties);
	}

	/** A Typestone with the given glyph (index into {@link GlyphSet}) and dyes. */
	public ItemStack stack(int glyph, Dyes dyes, int count) {
		ItemStack stack = new ItemStack(this, count);
		stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY
				.with(TypestoneBlock.GLYPH, glyph)
				.with(TypestoneBlock.DYED, dyes.dyed()));
		stack.set(ModComponents.DYES, dyes);
		return stack;
	}

	public static int glyph(ItemStack stack) {
		BlockItemStateProperties state = stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY);
		Integer glyph = state.get(TypestoneBlock.GLYPH);
		return glyph == null ? 0 : glyph;
	}

	/** "Typestone "A"", or just "Typestone" for the plain block. */
	@Override
	public Component getName(ItemStack stack) {
		int glyph = glyph(stack);
		if (glyph <= 0 || glyph >= GlyphSet.size()) {
			return super.getName(stack);
		}
		return Component.translatable("item.typestones.typestone.with_glyph", super.getName(stack),
				String.valueOf(GlyphSet.charAt(glyph)));
	}
}
