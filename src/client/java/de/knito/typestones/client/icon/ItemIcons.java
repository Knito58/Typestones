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

	/**
	 * Every target pixel is the average of exactly the part of the source it covers (fractions of
	 * source pixels count by their share), weighted by alpha. A whole-number enlargement (16 to 48)
	 * stays pixel for pixel; other ratios get soft edges instead of the uneven steps of picking
	 * whole source pixels.
	 */
	static BufferedImage scale(int[] argb, int width, int height, int size) {
		BufferedImage target = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < size; y++) {
			double top = (double) y * height / size;
			double bottom = (double) (y + 1) * height / size;
			for (int x = 0; x < size; x++) {
				double left = (double) x * width / size;
				double right = (double) (x + 1) * width / size;
				double weight = 0;
				double alpha = 0;
				double red = 0;
				double green = 0;
				double blue = 0;
				for (int sy = (int) Math.floor(top); sy < Math.min(height, (int) Math.ceil(bottom)); sy++) {
					double wy = Math.min(bottom, sy + 1) - Math.max(top, sy);
					for (int sx = (int) Math.floor(left); sx < Math.min(width, (int) Math.ceil(right)); sx++) {
						double w = wy * (Math.min(right, sx + 1) - Math.max(left, sx));
						int pixel = argb[sy * width + sx];
						double a = (pixel >>> 24) * w;
						weight += w;
						alpha += a;
						red += a * (pixel >> 16 & 0xFF);
						green += a * (pixel >> 8 & 0xFF);
						blue += a * (pixel & 0xFF);
					}
				}
				if (alpha <= 0 || weight <= 0) {
					target.setRGB(x, y, 0);
				} else {
					target.setRGB(x, y, (int) Math.round(alpha / weight) << 24 | (int) Math.round(red / alpha) << 16
							| (int) Math.round(green / alpha) << 8 | (int) Math.round(blue / alpha));
				}
			}
		}
		return target;
	}
}
