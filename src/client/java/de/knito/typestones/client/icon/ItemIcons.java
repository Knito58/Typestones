package de.knito.typestones.client.icon;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import de.knito.typestones.block.Dyes;
import de.knito.typestones.client.mixin.SpriteContentsAccessor;
import de.knito.typestones.picture.Picture;

/**
 * Makes the picture of an item: its main texture from this player's resource pack, painted on the
 * background the stone will have, as one opaque 64 px picture (see {@link IconPainter}). The background
 * is the flat color of the background dye, or without one the smooth stone's texture, so the icon's
 * edges blend into what is behind it. Flat items (an iron ingot, a sword) come out as their texture.
 * Block items show the texture the block's model names as its particle, which is flat too; it is not
 * the tilted cube of the inventory. The first frame of an animated texture is used, and tints (dyed
 * leather, potion color) are not applied. The result is plain pixels, so the server and the other
 * players need no resource pack.
 */
public final class ItemIcons {
	/** Used when the smooth stone's texture can't be read. */
	private static final int STONE_GRAY = 0xFF7D7D7D;

	private ItemIcons() {
	}

	/** The one-stone picture of the item on that background (none: smooth stone), or null when it has no texture to take. */
	public static @Nullable Picture of(ItemStack stack, Optional<DyeColor> background) {
		Sprite icon = sprite(stack);
		if (icon == null) {
			return null;
		}
		int[] canvas = new int[IconPainter.SIZE * IconPainter.SIZE];
		if (background.isPresent()) {
			java.util.Arrays.fill(canvas, new Dyes(DyeColor.BLACK, background).backgroundTint());
		} else {
			Sprite stone = sprite(new ItemStack(Items.SMOOTH_STONE));
			if (stone == null) {
				java.util.Arrays.fill(canvas, STONE_GRAY);
			} else {
				canvas = IconPainter.average(stone.argb, stone.width, stone.height, IconPainter.SIZE);
			}
		}
		return Picture.of(IconPainter.paint(canvas, icon.argb, icon.width, icon.height), 1, 1, IconPainter.SIZE, Picture.Mode.STRETCH);
	}

	private record Sprite(int[] argb, int width, int height) {
	}

	/** The first frame of the item's main texture, or null when there is none. */
	private static @Nullable Sprite sprite(ItemStack stack) {
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
		if (contents.name().equals(MissingTextureAtlasSprite.getLocation())) {
			return null;
		}
		try {
			NativeImage image = ((SpriteContentsAccessor) contents).typestones$originalImage();
			int width = contents.width();
			int height = contents.height();
			int[] argb = new int[width * height];
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					argb[y * width + x] = image.getPixel(x, y);
				}
			}
			return new Sprite(argb, width, height);
		} catch (IllegalStateException e) {
			// The game already freed the sprite's image.
			return null;
		}
	}
}
