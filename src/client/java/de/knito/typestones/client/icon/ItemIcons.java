package de.knito.typestones.client.icon;

import java.awt.image.BufferedImage;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import de.knito.typestones.TypestonesConfig;
import de.knito.typestones.client.mixin.SpriteContentsAccessor;
import de.knito.typestones.picture.Picture;

/**
 * Makes the picture of an item: its main texture from this player's resource pack, as one picture
 * stone. Flat items (an iron ingot, a sword, a potion bottle) come out as their texture, pixel for
 * pixel. Block items show the texture the block's model names as its particle, which is flat too; it
 * is not the tilted cube of the inventory. The first frame of an animated texture is used, and tints
 * (dyed leather, potion color) are not applied. The result is plain pixels, so the server and
 * the other players need no resource pack.
 */
public final class ItemIcons {
	private ItemIcons() {
	}

	/** The one-stone picture of the item at the configured resolution, or null when it has no texture to take. */
	public static @Nullable Picture of(ItemStack stack) {
		Minecraft minecraft = Minecraft.getInstance();
		if (stack.isEmpty() || minecraft.player == null) {
			return null;
		}
		ItemStackRenderState state = new ItemStackRenderState();
		minecraft.getItemModelResolver().updateForNonLiving(state, stack, ItemDisplayContext.GUI, minecraft.player);
		if (state.isEmpty()) {
			return null;
		}
		SpriteContents contents = state.pickParticleMaterial(RandomSource.create()).sprite().contents();
		Identifier missing = MissingTextureAtlasSprite.getLocation();
		if (contents.name().equals(missing)) {
			return null;
		}
		try {
			int size = TypestonesConfig.get().pictureResolution();
			return Picture.of(framed(scale(((SpriteContentsAccessor) contents).typestones$originalImage(), contents.width(), contents.height(),
					size - 2 * margin(size)), size), 1, 1, size, Picture.Mode.STRETCH);
		} catch (IllegalStateException e) {
			// The game already freed the sprite's image.
			return null;
		}
	}

	/** Transparent pixels on each side, so the icon doesn't touch the edge of the stone: an eighth. */
	static int margin(int size) {
		return size / 8;
	}

	/** The image in the middle of a transparent size x size image. */
	static BufferedImage framed(BufferedImage image, int size) {
		BufferedImage framed = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		int offset = (size - image.getWidth()) / 2;
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				framed.setRGB(offset + x, offset + y, image.getRGB(x, y));
			}
		}
		return framed;
	}

	/**
	 * The top left width x height pixels of the image (the first frame of an animation) as size x size
	 * pixels. Smaller textures are enlarged without blur; bigger ones are averaged, weighted by alpha, so
	 * the edge of a 128 px texture doesn't turn dark.
	 */
	static BufferedImage scale(NativeImage image, int width, int height, int size) {
		int[] argb = new int[width * height];
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				argb[y * width + x] = image.getPixel(x, y);
			}
		}
		return scale(argb, width, height, size);
	}

	static BufferedImage scale(int[] argb, int width, int height, int size) {
		BufferedImage target = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < size; y++) {
			int y0 = y * height / size;
			int y1 = Math.max(y0 + 1, (y + 1) * height / size);
			for (int x = 0; x < size; x++) {
				int x0 = x * width / size;
				int x1 = Math.max(x0 + 1, (x + 1) * width / size);
				long alpha = 0;
				long red = 0;
				long green = 0;
				long blue = 0;
				for (int sy = y0; sy < y1; sy++) {
					for (int sx = x0; sx < x1; sx++) {
						int pixel = argb[sy * width + sx];
						int a = pixel >>> 24;
						alpha += a;
						red += (long) a * (pixel >> 16 & 0xFF);
						green += (long) a * (pixel >> 8 & 0xFF);
						blue += (long) a * (pixel & 0xFF);
					}
				}
				int count = (x1 - x0) * (y1 - y0);
				if (alpha == 0) {
					target.setRGB(x, y, 0);
				} else {
					target.setRGB(x, y, (int) (alpha / count) << 24 | (int) (red / alpha) << 16 | (int) (green / alpha) << 8 | (int) (blue / alpha));
				}
			}
		}
		return target;
	}
}
