package de.knito.typestones.test;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import de.knito.typestones.ModBlocks;
import de.knito.typestones.ModTypewriter;
import de.knito.typestones.block.Dyes;
import de.knito.typestones.letter.LetterLibrary;
import de.knito.typestones.letter.Letters;
import de.knito.typestones.typewriter.FontSettings;
import de.knito.typestones.typewriter.TypewriterBlock;
import de.knito.typestones.typewriter.TypewriterBlockEntity;

/** Typestones in the inputs are used again: a stone just like the wanted one is moved, others are wiped. */
public class TypewriterRecycleTest {
	@GameTest
	public void oldStonesAreUsedAgain(GameTestHelper helper) {
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, ModTypewriter.BLOCK.defaultBlockState().setValue(TypewriterBlock.FACING, Direction.NORTH));
		helper.setBlock(pos.west(), Blocks.CHEST);
		helper.setBlock(pos.south(), Blocks.CHEST);
		if (!(helper.getBlockEntity(pos, TypewriterBlockEntity.class) instanceof TypewriterBlockEntity typewriter)
				|| !(helper.getBlockEntity(pos.west(), ChestBlockEntity.class) instanceof ChestBlockEntity input)
				|| !(helper.getBlockEntity(pos.south(), ChestBlockEntity.class) instanceof ChestBlockEntity output)) {
			throw helper.assertionException(Component.literal("Typewriter or chests missing"));
		}
		Dyes red = new Dyes(DyeColor.RED, java.util.Optional.empty());
		String a = Letters.classic('A');
		String b = Letters.classic('B');
		typewriter.dyeSamples().setItem(TypewriterBlockEntity.SLOT_LETTER, new ItemStack(TypewriterBlockEntity.dyeItem(DyeColor.RED)));
		// An A just like the wanted one, a Z to wipe for the B, one red dye, no smooth stone at all.
		input.setItem(0, ModBlocks.TYPESTONE_ITEM.stack(a, red, 1));
		input.setItem(1, ModBlocks.TYPESTONE_ITEM.stack(Letters.classic('Z'), red, 1));
		input.setItem(2, new ItemStack(TypewriterBlockEntity.dyeItem(DyeColor.RED)));

		typewriter.applySettings("AB", 1, FontSettings.DEFAULT);
		typewriter.start(List.of(), new LetterLibrary());
		helper.runAfterDelay(TypewriterBlockEntity.INTERVAL * 3 + 2, () -> {
			helper.assertTrue(ItemStack.isSameItemSameComponents(output.getItem(0), ModBlocks.TYPESTONE_ITEM.stack(a, red, 1))
							&& ItemStack.isSameItemSameComponents(output.getItem(1), ModBlocks.TYPESTONE_ITEM.stack(b, red, 1)),
					"Output: " + output.getItem(0) + ", " + output.getItem(1) + "; status " + typewriter.status());
			for (int slot = 0; slot < 3; slot++) {
				helper.assertTrue(input.getItem(slot).isEmpty(), "Input slot " + slot + " still holds " + input.getItem(slot));
			}
			helper.succeed();
		});
	}
}
