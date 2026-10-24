package de.knito.typestones;

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
import de.knito.typestones.block.TypestoneBlock;
import de.knito.typestones.block.TypestoneBlockEntity;
import de.knito.typestones.block.TypestoneItem;
import de.knito.typestones.letter.Letters;

public class ModBlocks {
	private static final ResourceKey<Block> TYPESTONE_KEY = ResourceKey.create(Registries.BLOCK, Typestones.id("typestone"));
	private static final ResourceKey<Item> TYPESTONE_ITEM_KEY = ResourceKey.create(Registries.ITEM, Typestones.id("typestone"));

	public static final TypestoneBlock TYPESTONE = Registry.register(BuiltInRegistries.BLOCK, TYPESTONE_KEY,
			new TypestoneBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SMOOTH_STONE).setId(TYPESTONE_KEY)));

	public static final TypestoneItem TYPESTONE_ITEM = Registry.register(BuiltInRegistries.ITEM, TYPESTONE_ITEM_KEY,
			new TypestoneItem(TYPESTONE, new Item.Properties().useBlockDescriptionPrefix().setId(TYPESTONE_ITEM_KEY)));

	public static final BlockEntityType<TypestoneBlockEntity> TYPESTONE_ENTITY = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE,
			Typestones.id("typestone"),
			FabricBlockEntityTypeBuilder.create(TypestoneBlockEntity::new, TYPESTONE).build()
	);

	/** Samples for the creative tab: a plain block, black letters and digits, and two colored ones. */
	public static void initialize() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS).register(tab -> {
			tab.accept(TYPESTONE_ITEM.stack(Letters.NONE, Dyes.DEFAULT, 1));
			for (char c : "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray()) {
				tab.accept(TYPESTONE_ITEM.stack(Letters.classic(c), Dyes.DEFAULT, 1));
			}
			tab.accept(TYPESTONE_ITEM.stack(Letters.classic('A'), new Dyes(DyeColor.WHITE, Optional.of(DyeColor.BLUE)), 1));
			tab.accept(TYPESTONE_ITEM.stack(Letters.classic('a'), new Dyes(DyeColor.YELLOW, Optional.of(DyeColor.BLACK)), 1));
		});
	}
}
