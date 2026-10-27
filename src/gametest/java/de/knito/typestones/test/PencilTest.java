package de.knito.typestones.test;

import java.util.List;
import java.util.Optional;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import de.knito.typestones.ModBlocks;
import de.knito.typestones.ModComponents;
import de.knito.typestones.ModPencil;
import de.knito.typestones.ModTypewriter;
import de.knito.typestones.block.Dyes;
import de.knito.typestones.block.TypestoneBlock;
import de.knito.typestones.block.TypestoneBlockEntity;
import de.knito.typestones.letter.LetterLibrary;
import de.knito.typestones.letter.Letters;
import de.knito.typestones.pencil.PencilLine;
import de.knito.typestones.pencil.PencilToss;
import de.knito.typestones.pencil.PottedPencilBlock;
import de.knito.typestones.typewriter.FontSettings;
import de.knito.typestones.typewriter.TypewriterBlock;
import de.knito.typestones.typewriter.TypewriterBlockEntity;
import de.knito.typestones.typewriter.TypewriterStatus;

/** Getting a pencil from the Typewriter, writing a line with it, and retiring it. */
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
		helper.assertTrue(line != null && line.next() == 0 && line.placed().isEmpty()
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

	@GameTest
	public void pencilWritesAndUndoes(GameTestHelper helper) {
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, ModTypewriter.BLOCK.defaultBlockState().setValue(TypewriterBlock.FACING, Direction.NORTH));
		helper.setBlock(pos.south(), Blocks.CHEST);
		helper.setBlock(new BlockPos(4, 1, 4), Blocks.STONE);
		if (!(helper.getBlockEntity(pos.south(), ChestBlockEntity.class) instanceof ChestBlockEntity chest)) {
			throw helper.assertionException(Component.literal("Output chest missing"));
		}
		List<String> letters = List.of(Letters.classic('A'), Letters.NONE, Letters.classic('B'));
		for (int i = 0; i < letters.size(); i++) {
			chest.setItem(i, ModBlocks.TYPESTONE_ITEM.stack(letters.get(i), Dyes.DEFAULT, 1));
		}
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setItemInHand(InteractionHand.MAIN_HAND, ModPencil.pencil(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(pos)), letters, Dyes.DEFAULT));

		BlockPos first = helper.absolutePos(new BlockPos(4, 2, 4));
		click(helper, player, first.below(), Direction.UP);
		helper.assertTrue(helper.getLevel().getBlockEntity(first) instanceof TypestoneBlockEntity stone && stone.letter().equals(letters.get(0)),
				"The first stone isn't an A");
		helper.assertTrue(chest.getItem(0).isEmpty(), "The A is still in the chest");

		click(helper, player, first, Direction.UP);
		Direction right = helper.getLevel().getBlockState(first).getValue(TypestoneBlock.FACING).getClockWise();
		BlockPos second = first.relative(right);
		helper.assertTrue(helper.getLevel().getBlockState(second).is(ModBlocks.TYPESTONE), "The space didn't go right of the A");

		player.setShiftKeyDown(true);
		click(helper, player, second, Direction.UP);
		player.setShiftKeyDown(false);
		PencilLine line = player.getMainHandItem().get(ModComponents.PENCIL_LINE);
		helper.assertTrue(helper.getLevel().getBlockState(second).isAir() && chest.countItem(ModBlocks.TYPESTONE_ITEM) == 2 && line != null && line.next() == 1,
				"Undo didn't put the space back: " + line);

		click(helper, player, first, Direction.UP);
		click(helper, player, second, Direction.UP);
		helper.assertTrue(player.getMainHandItem().get(ModComponents.PENCIL_LINE) == null, "The pencil isn't empty after the line");
		helper.assertTrue(helper.getLevel().getBlockEntity(second.relative(right)) instanceof TypestoneBlockEntity stone
				&& stone.letter().equals(letters.get(2)), "The B is missing");
		helper.succeed();
	}

	@GameTest
	public void emptyPencilGoesIntoPot(GameTestHelper helper) {
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, Blocks.FLOWER_POT);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Dyes dyes = new Dyes(DyeColor.RED, Optional.of(DyeColor.BLUE));
		ItemStack pencil = new ItemStack(ModPencil.ITEM);
		pencil.set(ModComponents.DYES, dyes);
		player.setItemInHand(InteractionHand.MAIN_HAND, pencil);

		ItemStack writing = ModPencil.pencil(GlobalPos.of(helper.getLevel().dimension(), BlockPos.ZERO), List.of(Letters.classic('A')), dyes);
		player.setItemInHand(InteractionHand.OFF_HAND, writing);
		helper.assertFalse(PottedPencilBlock.pot(helper.getLevel(), helper.absolutePos(pos), player, InteractionHand.OFF_HAND),
				"A pencil with an unwritten line went into the pot");

		helper.assertTrue(PottedPencilBlock.pot(helper.getLevel(), helper.absolutePos(pos), player, InteractionHand.MAIN_HAND),
				"The empty pencil didn't go into the pot");
		helper.assertBlockPresent(ModPencil.POTTED, pos);
		helper.assertTrue(player.getMainHandItem().isEmpty(), "The pencil is still in the hand");

		helper.getLevel().destroyBlock(helper.absolutePos(pos), true);
		List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(pos)).inflate(2));
		helper.assertTrue(drops.stream().anyMatch(d -> d.getItem().is(Items.FLOWER_POT)), "No flower pot dropped");
		helper.assertTrue(drops.stream().anyMatch(d -> PencilToss.isEmptyPencil(d.getItem())
				&& dyes.equals(d.getItem().get(ModComponents.DYES))), "The pencil didn't drop in its colors");
		helper.succeed();
	}

	@GameTest
	public void onlyEmptyPencilBursts(GameTestHelper helper) {
		BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
		ItemEntity empty = new ItemEntity(helper.getLevel(), at.getX(), at.getY(), at.getZ(), new ItemStack(ModPencil.ITEM));
		ItemEntity writing = new ItemEntity(helper.getLevel(), at.getX(), at.getY(), at.getZ(),
				ModPencil.pencil(GlobalPos.of(helper.getLevel().dimension(), BlockPos.ZERO), List.of(Letters.classic('A')), Dyes.DEFAULT));
		helper.getLevel().addFreshEntity(empty);
		helper.getLevel().addFreshEntity(writing);
		PencilToss.thrown(empty);
		PencilToss.thrown(writing);
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(empty.isRemoved(), "The empty pencil didn't burst");
			helper.assertFalse(writing.isRemoved(), "A pencil with an unwritten line burst");
			helper.succeed();
		});
	}

	private static void click(GameTestHelper helper, ServerPlayer player, BlockPos absolute, Direction face) {
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute).relative(face, 0.5), face, absolute, false);
		player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
	}
}
