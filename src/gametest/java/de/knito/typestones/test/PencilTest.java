package de.knito.typestones.test;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import de.knito.typestones.ModComponents;
import de.knito.typestones.ModPencil;
import de.knito.typestones.ModTypewriter;
import de.knito.typestones.block.Dyes;
import de.knito.typestones.letter.LetterLibrary;
import de.knito.typestones.letter.Letters;
import de.knito.typestones.pencil.PencilLine;
import de.knito.typestones.typewriter.FontSettings;
import de.knito.typestones.typewriter.TypewriterBlock;
import de.knito.typestones.typewriter.TypewriterBlockEntity;
import de.knito.typestones.typewriter.TypewriterStatus;

/** Getting a pencil from the Typewriter. */
public class PencilTest {
	@GameTest
	public void pencilCostsStickAndStone(GameTestHelper helper) {
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, ModTypewriter.BLOCK.defaultBlockState().setValue(TypewriterBlock.FACING, Direction.NORTH));
		helper.setBlock(pos.west(), Blocks.CHEST);
		if (!(helper.getBlockEntity(pos, TypewriterBlockEntity.class) instanceof TypewriterBlockEntity typewriter)
				|| !(helper.getBlockEntity(pos.west(), ChestBlockEntity.class) instanceof ChestBlockEntity chest)) {
			throw helper.assertionException(Component.literal("Typewriter or chest missing"));
		}
		typewriter.dyeSamples().setItem(TypewriterBlockEntity.SLOT_LETTER, new ItemStack(TypewriterBlockEntity.dyeItem(DyeColor.RED)));

		helper.assertTrue(typewriter.makePencil().isEmpty() && typewriter.status() == TypewriterStatus.NO_LINE,
				"A pencil without a line: " + typewriter.status());

		typewriter.applySettings("A B", 1, FontSettings.DEFAULT);
		typewriter.start(List.of(), new LetterLibrary());
		typewriter.stop();
		helper.assertTrue(typewriter.makePencil().isEmpty() && typewriter.status() == TypewriterStatus.NEEDS_STICK,
				"A pencil without a stick: " + typewriter.status());

		chest.setItem(0, new ItemStack(Items.STICK));
		chest.setItem(1, new ItemStack(Items.SMOOTH_STONE, 2));
		ItemStack pencil = typewriter.makePencil();
		PencilLine line = pencil.get(ModComponents.PENCIL_LINE);
		helper.assertTrue(line != null && line.next() == 0
						&& line.letters().equals(List.of(Letters.classic('A'), Letters.NONE, Letters.classic('B')))
						&& line.typewriter().equals(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(pos))),
				"Wrong pencil line: " + line);
		helper.assertTrue(pencil.getOrDefault(ModComponents.DYES, Dyes.DEFAULT).letter() == DyeColor.RED, "The pencil lost its dye");
		helper.assertTrue(chest.getItem(0).isEmpty() && chest.getItem(1).getCount() == 1, "The pencil didn't cost a stick and a stone");

		ByteBuf buf = Unpooled.buffer();
		PencilLine.STREAM_CODEC.encode(buf, line);
		helper.assertTrue(line.equals(PencilLine.STREAM_CODEC.decode(buf)), "The pencil line changed on the way");
		helper.assertTrue(ModPencil.ITEM.getDefaultMaxStackSize() == 1, "Pencils must not stack");
		helper.succeed();
	}
}
