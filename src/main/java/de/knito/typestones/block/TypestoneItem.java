package de.knito.typestones.block;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import de.knito.typestones.ModComponents;
import de.knito.typestones.letter.Letters;

/**
 * The Typestone item. Its letter is in the typestones:letter component and its dyes are in the
 * typestones:dyes component; the plain stone has neither, so all plain stones stack.
 */
public class TypestoneItem extends BlockItem {
	public TypestoneItem(Block block, Properties properties) {
		super(block, properties);
	}

	/** A Typestone with the given letter hash ({@link Letters#NONE} for none) and dyes. */
	public ItemStack stack(String letter, Dyes dyes, int count) {
		ItemStack stack = new ItemStack(this, count);
		if (!letter.equals(Letters.NONE)) {
			stack.set(ModComponents.LETTER, letter);
		}
		if (!dyes.equals(Dyes.DEFAULT)) {
			stack.set(ModComponents.DYES, dyes);
		}
		return stack;
	}

	public static String letter(ItemStack stack) {
		return stack.getOrDefault(ModComponents.LETTER, Letters.NONE);
	}

	public static Dyes dyes(ItemStack stack) {
		return stack.getOrDefault(ModComponents.DYES, Dyes.DEFAULT);
	}

	/** "Typestone "A"", or just "Typestone" for the plain block and unknown letters. */
	@Override
	public Component getName(ItemStack stack) {
		String text = Letters.text(letter(stack));
		if (text.isEmpty()) {
			return super.getName(stack);
		}
		return Component.translatable("item.typestones.typestone.with_glyph", super.getName(stack), text);
	}
}
