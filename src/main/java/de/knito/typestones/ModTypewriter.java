package de.knito.typestones;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;

import de.knito.typestones.letter.LetterLibrary;
import de.knito.typestones.typewriter.TypewriterBlock;
import de.knito.typestones.typewriter.TypewriterBlockEntity;
import de.knito.typestones.typewriter.TypewriterMenu;
import de.knito.typestones.typewriter.TypewriterActionPayload;

/** The Typewriter: block, block entity, menu and the packet of its buttons. */
public class ModTypewriter {
	private static final ResourceKey<Block> BLOCK_KEY = ResourceKey.create(Registries.BLOCK, Typestones.id("typewriter"));
	private static final ResourceKey<Item> ITEM_KEY = ResourceKey.create(Registries.ITEM, Typestones.id("typewriter"));

	public static final TypewriterBlock BLOCK = Registry.register(BuiltInRegistries.BLOCK, BLOCK_KEY,
			new TypewriterBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SMOOTH_STONE).setId(BLOCK_KEY)));

	public static final Item ITEM = Registry.register(BuiltInRegistries.ITEM, ITEM_KEY,
			new BlockItem(BLOCK, new Item.Properties().useBlockDescriptionPrefix().setId(ITEM_KEY)));

	public static final BlockEntityType<TypewriterBlockEntity> ENTITY = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE,
			Typestones.id("typewriter"),
			FabricBlockEntityTypeBuilder.create(TypewriterBlockEntity::new, BLOCK).build()
	);

	public static final MenuType<TypewriterMenu> MENU = Registry.register(
			BuiltInRegistries.MENU,
			Typestones.id("typewriter"),
			new ExtendedMenuType<>(TypewriterMenu::new, TypewriterMenu.OpenData.STREAM_CODEC)
	);

	public static void initialize() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(tab -> tab.accept(ITEM));

		PayloadTypeRegistry.serverboundPlay().register(TypewriterActionPayload.TYPE, TypewriterActionPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(TypewriterActionPayload.PencilChoices.TYPE,
				TypewriterActionPayload.PencilChoices.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TypewriterActionPayload.TYPE, (payload, context) -> {
			// Only act on the typewriter whose screen this player really has open.
			if (!(context.player().containerMenu instanceof TypewriterMenu menu)
					|| menu.typewriter() == null
					|| !menu.stillValid(context.player())) {
				return;
			}
			TypewriterBlockEntity typewriter = menu.typewriter();
			// A program holds it: the screen only watches.
			if (typewriter.isClaimed()) {
				return;
			}
			typewriter.applySettings(payload.text(), payload.repeat(), payload.font());
			switch (payload.action()) {
				case SAVE -> {
				}
				case START -> typewriter.start(payload.letters(), LetterLibrary.get(context.player().level().getServer()));
				case STOP -> typewriter.stop();
				case GET_PENCIL -> {
					ItemStack pencil = typewriter.makePencil(payload.pencilPicture());
					if (!pencil.isEmpty() && !context.player().getInventory().add(pencil)) {
						Block.popResource(context.player().level(), context.player().blockPosition(), pencil);
					}
				}
			}
		});
	}
}
