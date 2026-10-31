package de.knito.typestones.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import de.knito.typestones.ModBlocks;
import de.knito.typestones.ModComponents;
import de.knito.typestones.ModPictures;
import de.knito.typestones.Typestones;
import de.knito.typestones.TypestonesConfig;
import de.knito.typestones.block.TypestoneBlock;
import de.knito.typestones.block.TypestoneBlockEntity;
import de.knito.typestones.picture.Picture;
import de.knito.typestones.picture.PictureInfo;
import de.knito.typestones.picture.PictureLibrary;
import de.knito.typestones.picture.PictureTile;

/** Pictures: their pixels and hash, the library, the stones and which way they lie when placed. */
public class PictureTest {
	@GameTest
	public void pictureSurvivesPng(GameTestHelper helper) {
		Picture picture = ModPictures.testPicture(3, 2);
		helper.assertTrue(picture.width() == 3 * Picture.PIXELS && picture.height() == 2 * Picture.PIXELS, "Wrong picture size");
		try {
			Picture back = Picture.fromPng(picture.toPng());
			helper.assertTrue(back.hash().equals(picture.hash()) && back.cols() == 3 && back.rows() == 2,
					"The picture changed in its PNG");
		} catch (IOException e) {
			throw new AssertionError("Can't read the picture's own PNG", e);
		}
		helper.assertTrue(!ModPictures.testPicture(2, 3).hash().equals(picture.hash()), "Different pictures, same hash");
		helper.succeed();
	}

	@GameTest
	public void backShowsOppositeTile(GameTestHelper helper) {
		PictureTile tile = new PictureTile("ab", 4, 3, 0, 2);
		helper.assertTrue(tile.backCol() == 3, "The back of the left column must show the right column");
		helper.assertTrue(tile.bottomRow() == 0, "Underneath, the last row must show the first row");

		ByteBuf buf = Unpooled.buffer();
		PictureTile.STREAM_CODEC.encode(buf, tile);
		helper.assertTrue(tile.equals(PictureTile.STREAM_CODEC.decode(buf)), "The tile changed on the way");
		helper.assertTrue(PictureTile.CODEC.parse(NbtOps.INSTANCE,
						PictureTile.CODEC.encodeStart(NbtOps.INSTANCE, new PictureTile("ab", 2, 2, 0, 0)).getOrThrow()
				).isSuccess(), "A good tile doesn't load");
		Tag outside = PictureTile.CODEC.encodeStart(NbtOps.INSTANCE, new PictureTile("ab", 2, 2, 1, 1)).getOrThrow();
		helper.assertTrue(PictureTile.CODEC.parse(NbtOps.INSTANCE, outside).isSuccess(), "The last tile doesn't load");
		helper.succeed();
	}

	@GameTest
	public void libraryKeepsPictures(GameTestHelper helper) {
		Path folder;
		try {
			folder = Files.createTempDirectory("typestones-pictures");
		} catch (IOException e) {
			throw new AssertionError(e);
		}
		PictureLibrary library = new PictureLibrary();
		Picture picture = ModPictures.testPicture(2, 2);
		PictureInfo info = PictureInfo.of(picture, "test", "Peter", "2026-10-27");
		try {
			helper.assertTrue(library.add(picture, info, folder) == PictureLibrary.Added.NEW, "First add is not NEW");
			helper.assertTrue(library.add(picture, info, folder) == PictureLibrary.Added.KNOWN, "Second add is not KNOWN");
			byte[] png = library.png(picture.hash(), folder);
			helper.assertTrue(png != null && Picture.fromPng(png).equals(picture), "The PNG file doesn't hold the picture");
		} catch (IOException e) {
			throw new AssertionError(e);
		}
		Tag saved = PictureLibrary.TYPE.codec().encodeStart(NbtOps.INSTANCE, library).getOrThrow();
		PictureLibrary loaded = PictureLibrary.TYPE.codec().parse(NbtOps.INSTANCE, saved).getOrThrow();
		helper.assertTrue(info.equals(loaded.find(picture.hash())), "The picture's entry didn't survive saving: " + saved);
		helper.assertTrue(loaded.remove(picture.hash(), folder) && loaded.png(picture.hash(), folder) == null,
				"Remove didn't remove");
		helper.assertTrue(library.png("../../etc", folder) == null, "Read a file that is no picture");
		helper.succeed();
	}

	@GameTest
	public void stonesCarryTiles(GameTestHelper helper) {
		Picture picture = ModPictures.testPicture(3, 2);
		List<ItemStack> stones = ModPictures.stones(picture);
		helper.assertTrue(stones.size() == 6, "Not one stone per tile");
		helper.assertTrue(new PictureTile(picture.hash(), 3, 2, 1, 1).equals(stones.get(4).get(ModComponents.PICTURE)),
				"The fifth stone isn't the middle of the bottom row");

		BlockPos absolute = helper.absolutePos(new BlockPos(1, 2, 1));
		helper.getLevel().setBlockAndUpdate(absolute, ModBlocks.TYPESTONE.defaultBlockState());
		PictureTile tile = PictureTile.of(picture, 1, 1);
		if (helper.getLevel().getBlockEntity(absolute) instanceof TypestoneBlockEntity entity) {
			entity.setPicture(tile);
		}
		helper.getLevel().destroyBlock(absolute, true);
		List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(2));
		helper.assertTrue(drops.size() == 1 && tile.equals(drops.getFirst().getItem().get(ModComponents.PICTURE)),
				"The drop lost its picture: " + drops);
		helper.succeed();
	}

	@GameTest
	public void rgbDyeRecipeOnlyWithScripting(GameTestHelper helper) {
		// Without Billboard API or CC: Tweaked nothing can make pictures, so the dye has no recipe.
		var key = ResourceKey.create(Registries.RECIPE, Typestones.id("rgb_dye"));
		helper.assertTrue(helper.getLevel().recipeAccess().byKey(key).isPresent() == ModPictures.scriptingAvailable(),
				"The RGB dye recipe should load exactly when a scripting mod is there");
		helper.assertTrue(ModPictures.RGB_DYE != null, "No RGB dye");
		helper.succeed();
	}

	/** Settings out of range fall back or are clamped instead of breaking pictures. */
	@GameTest
	public void configKeepsItsLimits(GameTestHelper helper) {
		TypestonesConfig config = new TypestonesConfig(48, 0, 99, 50);
		helper.assertTrue(config.pictureResolution() == 32, "Unknown resolution kept: " + config.pictureResolution());
		helper.assertTrue(config.maxCols() == 1 && config.maxRows() == Picture.MAX_STONES, "Size not clamped: " + config);
		helper.assertTrue(config.viewChunks() == TypestonesConfig.MAX_VIEW_CHUNKS, "View distance not clamped: " + config);
		helper.assertTrue(TypestonesConfig.DEFAULT.viewBlocks() == 64, "Default view distance is not 4 chunks");
		helper.succeed();
	}

	/**
	 * A stone clicked onto a floor lies flat; one on a wall stands; one placed against a stone of the
	 * same picture lies like that one, whatever side was clicked.
	 */
	@GameTest
	public void stonesLieLikeTheirPicture(GameTestHelper helper) {
		Picture picture = ModPictures.testPicture(2, 2);
		List<ItemStack> stones = ModPictures.stones(picture);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);

		BlockPos floor = helper.absolutePos(new BlockPos(2, 1, 2));
		place(helper, player, stones.get(0), floor, Direction.UP);
		BlockState first = helper.getLevel().getBlockState(floor.above());
		helper.assertTrue(first.is(ModBlocks.TYPESTONE) && first.getValue(TypestoneBlock.FLAT), "A stone on the floor doesn't lie flat");

		// The next one goes against the side of the first: still part of the floor picture.
		Direction side = first.getValue(TypestoneBlock.FACING).getClockWise();
		place(helper, player, stones.get(1), floor.above(), side);
		BlockState second = helper.getLevel().getBlockState(floor.above().relative(side));
		helper.assertTrue(second.is(ModBlocks.TYPESTONE) && second.getValue(TypestoneBlock.FLAT)
						&& second.getValue(TypestoneBlock.FACING) == first.getValue(TypestoneBlock.FACING),
				"The second stone doesn't lie like the first");

		BlockPos wall = helper.absolutePos(new BlockPos(2, 1, 2));
		place(helper, player, ModPictures.stones(ModPictures.testPicture(1, 1)).getFirst(), wall, Direction.NORTH);
		BlockState standing = helper.getLevel().getBlockState(wall.north());
		helper.assertTrue(standing.is(ModBlocks.TYPESTONE) && !standing.getValue(TypestoneBlock.FLAT), "A stone on a wall doesn't stand");
		helper.succeed();
	}

	private static void place(GameTestHelper helper, ServerPlayer player, ItemStack stone, BlockPos absolute, Direction face) {
		player.setItemInHand(InteractionHand.MAIN_HAND, stone.copy());
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute).relative(face, 0.5), face, absolute, false);
		player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
	}
}
