package de.knito.typestones;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;

import de.knito.typestones.block.Dyes;
import de.knito.typestones.block.FontFace;
import de.knito.typestones.block.TypestoneBlock;
import de.knito.typestones.block.TypestoneBlockEntity;
import de.knito.typestones.block.TypestoneItem;
import de.knito.typestones.glyph.GlyphSet;

public class ModBlocks {
	/** One block per font face. */
	public static final Map<FontFace, TypestoneBlock> TYPESTONES = new EnumMap<>(FontFace.class);
	public static final Map<FontFace, TypestoneItem> TYPESTONE_ITEMS = new EnumMap<>(FontFace.class);

	static {
		for (FontFace face : FontFace.values()) {
			ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, Typestones.id(face.blockName));
			TypestoneBlock block = Registry.register(BuiltInRegistries.BLOCK, blockKey,
					new TypestoneBlock(face, BlockBehaviour.Properties.ofFullCopy(Blocks.SMOOTH_STONE).setId(blockKey)));
			ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Typestones.id(face.blockName));
			TypestoneItem item = Registry.register(BuiltInRegistries.ITEM, itemKey,
					new TypestoneItem(block, new Item.Properties().useBlockDescriptionPrefix().setId(itemKey)));
			TYPESTONES.put(face, block);
			TYPESTONE_ITEMS.put(face, item);
		}
	}

	public static final BlockEntityType<TypestoneBlockEntity> TYPESTONE_ENTITY = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE,
			Typestones.id("typestone"),
			FabricBlockEntityTypeBuilder.create(TypestoneBlockEntity::new, TYPESTONES.values().toArray(Block[]::new)).build()
	);

	/** Samples for the creative tab: a plain block, black letters and digits, and two colored ones. */
	public static void initialize() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS).register(tab -> {
			for (TypestoneItem item : TYPESTONE_ITEMS.values()) {
				tab.accept(item.stack(0, Dyes.DEFAULT, 1));
				for (char c : "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray()) {
					tab.accept(item.stack(GlyphSet.indexOf(c), Dyes.DEFAULT, 1));
				}
				tab.accept(item.stack(GlyphSet.indexOf('A'), new Dyes(DyeColor.WHITE, Optional.of(DyeColor.BLUE)), 1));
				tab.accept(item.stack(GlyphSet.indexOf('a'), new Dyes(DyeColor.YELLOW, Optional.of(DyeColor.BLACK)), 1));
			}
		});
	}
}
