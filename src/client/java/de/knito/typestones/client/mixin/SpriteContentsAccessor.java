package de.knito.typestones.client.mixin;

import com.mojang.blaze3d.platform.NativeImage;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.renderer.texture.SpriteContents;

/** Opens the pixels of a sprite, which Minecraft keeps private: item icons are made from them. */
@Mixin(SpriteContents.class)
public interface SpriteContentsAccessor {
	@Accessor("originalImage")
	NativeImage typestones$originalImage();
}
