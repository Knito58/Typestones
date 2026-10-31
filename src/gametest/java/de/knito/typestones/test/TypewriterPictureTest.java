package de.knito.typestones.test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import javax.imageio.ImageIO;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import de.knito.typestones.ModComponents;
import de.knito.typestones.ModPictures;
import de.knito.typestones.ModTypewriter;
import de.knito.typestones.picture.PictureTile;
import de.knito.typestones.script.TypewriterScript;
import de.knito.typestones.typewriter.TypewriterBlock;
import de.knito.typestones.typewriter.TypewriterBlockEntity;
import de.knito.typestones.typewriter.TypewriterStatus;

/** Programs typing pictures: options, image checks, cost preview, and a job that waits for dye. */
public class TypewriterPictureTest {
	private static byte[] png(int width, int height) {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				image.setRGB(x, y, x < width / 2 ? 0x2040C0 : 0xE0E0E0);
			}
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			ImageIO.write(image, "png", out);
		} catch (IOException e) {
			throw new AssertionError(e);
		}
		return out.toByteArray();
	}

	@GameTest
	public void optionsAndImagesAreChecked(GameTestHelper helper) {
		TypewriterScript.Options options = TypewriterScript.Options.of(Map.of("width", 3L, "height", 2L, "fit", "fill", "facing", "ceiling"));
		helper.assertTrue(options.cols() == 3 && options.rows() == 2 && options.plane() == PictureTile.Plane.FLAT, "Options: " + options);
		helper.assertTrue(TypewriterScript.Options.of(Map.of("width", 1L, "height", 1L)).plane() == PictureTile.Plane.WALL,
				"A picture without facing should stand like a painting");
		for (Map<String, ?> wrong : java.util.List.<Map<String, ?>>of(Map.of("width", 2L), Map.of("width", 0L, "height", 1L),
				Map.of("width", 1.5, "height", 1L), Map.of("width", 1L, "height", 1L, "fit", "crop"),
				Map.of("width", 1L, "height", 1L, "facing", "sky"))) {
			try {
				TypewriterScript.Options.of(wrong);
				throw helper.assertionException(Component.literal("Wrong options passed: " + wrong));
			} catch (IllegalArgumentException expected) {
				// The program gets this as a Lua error.
			}
		}
		helper.assertTrue(reason(new byte[] {1, 2, 3}).equals("bad_image"), "Garbage read as an image");
		helper.assertTrue(reason(png(TypewriterScript.MAX_IMAGE_SIDE + 1, 1)).equals("image_too_large"), "A huge image was decoded");
		helper.assertTrue(reason(new byte[(int) TypewriterScript.MAX_FILE_BYTES + 1]).equals("file_too_large"), "A huge file was read");
		helper.assertTrue(reason(png(40, 20)).isEmpty(), "A plain PNG was refused");
		helper.succeed();
	}

	private static String reason(byte[] bytes) {
		try {
			TypewriterScript.read(bytes);
			return "";
		} catch (TypewriterScript.ImageProblem problem) {
			return problem.reason;
		}
	}

	/**
	 * A 2 x 1 floor picture with dye for one stone: it starts with a warning, makes one stone, waits,
	 * and finishes when the dye is refilled. Each stone costs one smooth stone and one RGB dye.
	 */
	@GameTest(maxTicks = 200)
	public void pictureJobWaitsForDye(GameTestHelper helper) {
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, ModTypewriter.BLOCK.defaultBlockState().setValue(TypewriterBlock.FACING, Direction.NORTH));
		helper.setBlock(pos.west(), Blocks.CHEST);
		helper.setBlock(pos.south(), Blocks.CHEST);
		if (!(helper.getBlockEntity(pos, TypewriterBlockEntity.class) instanceof TypewriterBlockEntity typewriter)
				|| !(helper.getBlockEntity(pos.west(), ChestBlockEntity.class) instanceof ChestBlockEntity input)
				|| !(helper.getBlockEntity(pos.south(), ChestBlockEntity.class) instanceof ChestBlockEntity output)) {
			throw helper.assertionException(Component.literal("Typewriter or chests missing"));
		}
		input.setItem(0, new ItemStack(Items.SMOOTH_STONE, 5));
		input.setItem(1, new ItemStack(ModPictures.RGB_DYE, 1));

		TypewriterScript script = new TypewriterScript(typewriter);
		Map<String, ?> options = Map.of("width", 2L, "height", 1L, "facing", "floor");
		Map<String, Object> cost = script.getCost(options);
		helper.assertTrue(cost.get("stones").equals(2) && cost.get("rgbDye").equals(2) && cost.get("haveStones").equals(5L)
						&& cost.get("haveRgbDye").equals(1L) && cost.get("ok").equals(false) && "needs_rgb_dye".equals(cost.get("missing")),
				"Wrong cost preview: " + cost);
		helper.assertTrue(script.getCost(Map.of("width", 9L, "height", 1L)).get("missing").equals("too_big"), "9 stones wide passed the 8 x 8 cap");

		byte[] image = png(64, 32);
		CompletableFuture<Object[]> started = script.drawImage("half blue", () -> image, options, "test");
		boolean[] refilled = {false};
		helper.succeedWhen(() -> {
			helper.assertTrue(started.isDone(), "drawImage still reading");
			Object[] result = started.join();
			helper.assertTrue(result.length == 2 && result[0].equals(true) && result[1].equals("needs_rgb_dye"),
					"drawImage should start with a warning: " + java.util.Arrays.toString(result));
			if (!refilled[0]) {
				helper.assertTrue(typewriter.made() == 1 && typewriter.status() == TypewriterStatus.NEEDS_RGB_DYE && typewriter.isPainting(),
						"Not waiting for dye after one stone: " + typewriter.status() + " " + typewriter.made());
				Object[] busy = script.drawImage("again", () -> image, options, "test").join();
				helper.assertTrue(busy[0].equals(false) && busy[1].equals("busy"), "A second picture started while the first waits");
				input.setItem(1, new ItemStack(ModPictures.RGB_DYE, 1));
				refilled[0] = true;
			}
			helper.assertTrue(typewriter.status() == TypewriterStatus.DONE && !typewriter.isPainting(), "Not done: " + typewriter.status());
			PictureTile first = output.getItem(0).get(ModComponents.PICTURE);
			PictureTile second = output.getItem(1).get(ModComponents.PICTURE);
			helper.assertTrue(first != null && second != null && first.col() == 0 && second.col() == 1
					&& first.plane() == PictureTile.Plane.FLAT && first.hash().equals(second.hash()), "Wrong stones: " + first + ", " + second);
			helper.assertTrue(input.getItem(0).getCount() == 3 && input.getItem(1).isEmpty(), "Wrong cost taken");
		});
	}
}
