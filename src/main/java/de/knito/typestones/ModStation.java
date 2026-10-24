package de.knito.typestones;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;

import de.knito.typestones.station.AlphaStationBlock;
import de.knito.typestones.station.AlphaStationBlockEntity;
import de.knito.typestones.station.AlphaStationMenu;
import de.knito.typestones.station.StationActionPayload;

/** The Alpha Station: block, block entity, menu and the packet of its buttons. */
public class ModStation {
	private static final ResourceKey<Block> BLOCK_KEY = ResourceKey.create(Registries.BLOCK, Typestones.id("alpha_station"));
	private static final ResourceKey<Item> ITEM_KEY = ResourceKey.create(Registries.ITEM, Typestones.id("alpha_station"));

	public static final AlphaStationBlock BLOCK = Registry.register(BuiltInRegistries.BLOCK, BLOCK_KEY,
			new AlphaStationBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SMOOTH_STONE).setId(BLOCK_KEY)));

	public static final Item ITEM = Registry.register(BuiltInRegistries.ITEM, ITEM_KEY,
			new BlockItem(BLOCK, new Item.Properties().useBlockDescriptionPrefix().setId(ITEM_KEY)));

	public static final BlockEntityType<AlphaStationBlockEntity> ENTITY = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE,
			Typestones.id("alpha_station"),
			FabricBlockEntityTypeBuilder.create(AlphaStationBlockEntity::new, BLOCK).build()
	);

	public static final MenuType<AlphaStationMenu> MENU = Registry.register(
			BuiltInRegistries.MENU,
			Typestones.id("alpha_station"),
			new ExtendedMenuType<>(AlphaStationMenu::new, AlphaStationMenu.OpenData.STREAM_CODEC)
	);

	public static void initialize() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(tab -> tab.accept(ITEM));

		PayloadTypeRegistry.serverboundPlay().register(StationActionPayload.TYPE, StationActionPayload.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(StationActionPayload.TYPE, (payload, context) -> {
			// Only act on the station whose screen this player really has open.
			if (!(context.player().containerMenu instanceof AlphaStationMenu menu)
					|| menu.station() == null
					|| !menu.stillValid(context.player())) {
				return;
			}
			AlphaStationBlockEntity station = menu.station();
			station.applySettings(payload.text(), payload.repeat());
			switch (payload.action()) {
				case SAVE -> {
				}
				case START -> station.start();
				case STOP -> station.stop();
			}
		});
	}
}
