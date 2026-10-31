package de.knito.typestones.test;

import java.util.List;
import java.util.Optional;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
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
import de.knito.typestones.picture.PictureTile;
import de.knito.typestones.pencil.PencilToss;
import de.knito.typestones.pencil.PottedPencilBlock;
import de.knito.typestones.typewriter.FontSettings;
import de.knito.typestones.typewriter.TypewriterActionPayload;
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
	public void emptyPencilIsRefilled(GameTestHelper helper) {
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, ModTypewriter.BLOCK.defaultBlockState().setValue(TypewriterBlock.FACING, Direction.NORTH));
		helper.setBlock(pos.west(), Blocks.CHEST);
		if (!(helper.getBlockEntity(pos, TypewriterBlockEntity.class) instanceof TypewriterBlockEntity typewriter)
				|| !(helper.getBlockEntity(pos.west(), ChestBlockEntity.class) instanceof ChestBlockEntity chest)) {
			throw helper.assertionException(Component.literal("Typewriter or chest missing"));
		}
		typewriter.dyeSamples().setItem(TypewriterBlockEntity.SLOT_LETTER, new ItemStack(TypewriterBlockEntity.dyeItem(DyeColor.RED)));
		typewriter.applySettings("A", 1, FontSettings.DEFAULT);
		typewriter.start(List.of(), new LetterLibrary());
		typewriter.stop();
		helper.assertTrue(typewriter.makePencil().isEmpty() && typewriter.status() == TypewriterStatus.NEEDS_STICK,
				"Expected a line and no stick yet: " + typewriter.status());

		ItemStack empty = new ItemStack(ModPencil.ITEM);
		empty.set(DataComponents.CUSTOM_NAME, Component.literal("Old Faithful"));
		chest.setItem(0, new ItemStack(Items.STICK));
		chest.setItem(1, new ItemStack(Items.SMOOTH_STONE));
		chest.setItem(2, empty);
		ItemStack pencil = typewriter.makePencil();
		helper.assertTrue(pencil.has(ModComponents.PENCIL_LINE), "No pencil from the empty one: " + typewriter.status());
		helper.assertTrue(chest.getItem(2).isEmpty(), "The empty pencil is still in the chest");
		helper.assertTrue(chest.getItem(0).getCount() == 1 && chest.getItem(1).getCount() == 1, "A refill cost a stick or a stone");
		helper.assertTrue(Component.literal("Old Faithful").equals(pencil.get(DataComponents.CUSTOM_NAME)), "The refilled pencil lost its name");

		// A pencil with a line left is not empty and stays where it is.
		chest.setItem(2, pencil.copy());
		ItemStack fresh = typewriter.makePencil();
		helper.assertTrue(fresh.has(ModComponents.PENCIL_LINE) && chest.getItem(2).has(ModComponents.PENCIL_LINE)
						&& chest.getItem(0).isEmpty() && chest.getItem(1).isEmpty(),
				"A pencil with a line was taken for a refill");
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
	public void picturePencilLaysRowByRow(GameTestHelper helper) {
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, ModTypewriter.BLOCK.defaultBlockState().setValue(TypewriterBlock.FACING, Direction.NORTH));
		helper.setBlock(pos.south(), Blocks.CHEST);
		BlockPos wall = new BlockPos(4, 4, 4);
		helper.setBlock(wall, Blocks.STONE);
		if (!(helper.getBlockEntity(pos.south(), ChestBlockEntity.class) instanceof ChestBlockEntity chest)) {
			throw helper.assertionException(Component.literal("Output chest missing"));
		}
		TypewriterBlockEntity.PictureJob picture = new TypewriterBlockEntity.PictureJob("cafe", 2, 2, PictureTile.Plane.WALL);
		for (int i = 0; i < picture.stones(); i++) {
			chest.setItem(i, ModBlocks.TYPESTONE_ITEM.pictureStack(picture.tile(i)));
		}
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos start = helper.absolutePos(wall.north());
		player.snapTo(Vec3.atCenterOf(start.north(2)), 0, 0);
		player.setItemInHand(InteractionHand.MAIN_HAND,
				ModPencil.picturePencil(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(pos)), picture));
		helper.assertTrue(player.getMainHandItem().get(ModComponents.PENCIL_LINE).fromBottom(), "A picture pencil should start at the bottom");
		// Used in the air: start at the top left instead.
		player.getMainHandItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		helper.assertFalse(player.getMainHandItem().get(ModComponents.PENCIL_LINE).fromBottom(), "Using it in the air didn't switch the corner");

		click(helper, player, helper.absolutePos(wall), Direction.NORTH);
		helper.assertTrue(tile(helper, start) != null && tile(helper, start).col() == 0 && tile(helper, start).row() == 0,
				"The first stone isn't the top left tile");
		Direction right = helper.getLevel().getBlockState(start).getValue(TypestoneBlock.FACING).getClockWise();
		// Wherever the player clicks, the picture goes on in its own order.
		click(helper, player, helper.absolutePos(wall), Direction.UP);
		BlockPos topRight = start.relative(right);
		helper.assertTrue(tile(helper, topRight) != null && tile(helper, topRight).col() == 1 && tile(helper, topRight).row() == 0,
				"The second stone isn't right of the first");
		click(helper, player, helper.absolutePos(wall), Direction.UP);
		helper.assertTrue(tile(helper, start.below()) != null && tile(helper, start.below()).row() == 1
				&& tile(helper, start.below()).col() == 0, "The second row didn't start below the first");

		player.setShiftKeyDown(true);
		click(helper, player, start, Direction.NORTH);
		player.setShiftKeyDown(false);
		helper.assertTrue(helper.getLevel().getBlockState(start.below()).isAir() && chest.countItem(ModBlocks.TYPESTONE_ITEM) == 2,
				"Undo didn't take the picture stone back");

		click(helper, player, start, Direction.NORTH);
		click(helper, player, start, Direction.NORTH);
		helper.assertTrue(tile(helper, start.below().relative(right)) != null && tile(helper, start.below().relative(right)).col() == 1,
				"The last tile is missing");
		helper.assertTrue(player.getMainHandItem().get(ModComponents.PENCIL_LINE) == null && chest.isEmpty(),
				"The picture pencil isn't done");
		for (BlockPos stone : List.of(start, topRight, start.below())) {
			helper.assertTrue(helper.getLevel().getBlockState(stone).getValue(TypestoneBlock.FACING)
					== helper.getLevel().getBlockState(start).getValue(TypestoneBlock.FACING)
					&& !helper.getLevel().getBlockState(stone).getValue(TypestoneBlock.FLAT), "A picture stone lies the wrong way");
		}

		ByteBuf buf = Unpooled.buffer();
		PencilLine line = PencilLine.of(GlobalPos.of(helper.getLevel().dimension(), BlockPos.ZERO), picture).withPlaced(BlockPos.ZERO);
		PencilLine.STREAM_CODEC.encode(buf, line);
		helper.assertTrue(line.equals(PencilLine.STREAM_CODEC.decode(buf)), "The picture pencil changed on the way");
		helper.succeed();
	}

	@GameTest
	public void picturePencilBuildsUpFromTheBottom(GameTestHelper helper) {
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, ModTypewriter.BLOCK.defaultBlockState().setValue(TypewriterBlock.FACING, Direction.NORTH));
		helper.setBlock(pos.south(), Blocks.CHEST);
		BlockPos ground = new BlockPos(4, 1, 4);
		helper.setBlock(ground, Blocks.STONE);
		if (!(helper.getBlockEntity(pos.south(), ChestBlockEntity.class) instanceof ChestBlockEntity chest)) {
			throw helper.assertionException(Component.literal("Output chest missing"));
		}
		TypewriterBlockEntity.PictureJob picture = new TypewriterBlockEntity.PictureJob("b0770m", 1, 2, PictureTile.Plane.WALL);
		chest.setItem(0, ModBlocks.TYPESTONE_ITEM.pictureStack(picture.tile(0)));
		chest.setItem(1, ModBlocks.TYPESTONE_ITEM.pictureStack(picture.tile(1)));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos bottom = helper.absolutePos(ground.above());
		player.snapTo(Vec3.atCenterOf(bottom.north(2)), 0, 0);
		player.setItemInHand(InteractionHand.MAIN_HAND,
				ModPencil.picturePencil(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(pos)), picture));

		click(helper, player, helper.absolutePos(ground), Direction.UP);
		helper.assertTrue(tile(helper, bottom) != null && tile(helper, bottom).row() == 1, "The first stone isn't the bottom tile");
		click(helper, player, helper.absolutePos(ground), Direction.UP);
		helper.assertTrue(tile(helper, bottom.above()) != null && tile(helper, bottom.above()).row() == 0, "The top tile isn't above the bottom one");
		helper.assertTrue(chest.isEmpty() && player.getMainHandItem().get(ModComponents.PENCIL_LINE) == null, "The pencil isn't done");
		helper.succeed();
	}

	@GameTest
	public void pencilForAPictureInTheOutput(GameTestHelper helper) {
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, ModTypewriter.BLOCK.defaultBlockState().setValue(TypewriterBlock.FACING, Direction.NORTH));
		helper.setBlock(pos.west(), Blocks.CHEST);
		helper.setBlock(pos.south(), Blocks.CHEST);
		if (!(helper.getBlockEntity(pos, TypewriterBlockEntity.class) instanceof TypewriterBlockEntity typewriter)
				|| !(helper.getBlockEntity(pos.west(), ChestBlockEntity.class) instanceof ChestBlockEntity input)
				|| !(helper.getBlockEntity(pos.south(), ChestBlockEntity.class) instanceof ChestBlockEntity output)) {
			throw helper.assertionException(Component.literal("Typewriter or chests missing"));
		}
		TypewriterBlockEntity.PictureJob fruits = new TypewriterBlockEntity.PictureJob("f00d", 3, 2, PictureTile.Plane.WALL);
		TypewriterBlockEntity.PictureJob sun = new TypewriterBlockEntity.PictureJob("5a1", 2, 1, PictureTile.Plane.FLAT);
		output.setItem(0, ModBlocks.TYPESTONE_ITEM.stack(Letters.classic('A'), Dyes.DEFAULT, 1));
		output.setItem(1, ModBlocks.TYPESTONE_ITEM.pictureStack(fruits.tile(4)));
		output.setItem(2, ModBlocks.TYPESTONE_ITEM.pictureStack(sun.tile(0)));
		output.setItem(3, ModBlocks.TYPESTONE_ITEM.pictureStack(fruits.tile(0)));
		List<String> found = typewriter.outputPictures().stream().map(PictureTile::hash).toList();
		helper.assertTrue(found.equals(List.of("f00d", "5a1")), "Pictures in the output: " + found);
		List<TypewriterActionPayload.PencilChoice> choices = typewriter.pencilChoices();
		helper.assertTrue(choices.equals(List.of(new TypewriterActionPayload.PencilChoice("f00d", "", 3, 2),
				new TypewriterActionPayload.PencilChoice("5a1", "", 2, 1))), "Pencil choices: " + choices);
		ByteBuf buf = Unpooled.buffer();
		TypewriterActionPayload.PencilChoices.STREAM_CODEC.encode(buf, new TypewriterActionPayload.PencilChoices(choices));
		helper.assertTrue(choices.equals(TypewriterActionPayload.PencilChoices.STREAM_CODEC.decode(buf).choices()),
				"The pencil choices changed on the way");

		input.setItem(0, new ItemStack(Items.STICK, 2));
		input.setItem(1, new ItemStack(Items.SMOOTH_STONE, 2));
		ItemStack pencil = typewriter.makePencil("f00d");
		PencilLine line = pencil.get(ModComponents.PENCIL_LINE);
		helper.assertTrue(line != null && line.picture().equals(Optional.of(fruits)), "Wrong picture pencil: " + line);
		helper.assertTrue(typewriter.makePencil("beef").isEmpty() && typewriter.status() == TypewriterStatus.PICTURE_GONE,
				"A pencil for a picture that isn't in the output: " + typewriter.status());
		helper.assertTrue(input.getItem(0).getCount() == 1, "A refused pencil cost a stick");
		helper.succeed();
	}

	private static @Nullable PictureTile tile(GameTestHelper helper, BlockPos absolute) {
		return helper.getLevel().getBlockEntity(absolute) instanceof TypestoneBlockEntity stone ? stone.picture() : null;
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
		// Like flower pot plants, the pencil stays in a creative player's hand.
		helper.assertTrue(player.hasInfiniteMaterials() || player.getMainHandItem().isEmpty(), "The pencil is still in the hand");

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
