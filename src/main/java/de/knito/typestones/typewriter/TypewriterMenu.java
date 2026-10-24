package de.knito.typestones.typewriter;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

import de.knito.typestones.ModTypewriter;

public class TypewriterMenu extends AbstractContainerMenu {
	/** Sent to the client when the screen opens. */
	public record OpenData(BlockPos pos, String text) {
		public static final StreamCodec<ByteBuf, OpenData> STREAM_CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC, OpenData::pos,
				ByteBufCodecs.stringUtf8(TypewriterBlockEntity.MAX_TEXT_LENGTH * 4), OpenData::text,
				OpenData::new
		);
	}

	public static final int LETTER_X = 8;
	public static final int BACKGROUND_X = 30;
	public static final int DYES_Y = 66;
	public static final int INVENTORY_Y = 130;

	private final Container dyeSamples;
	private final ContainerData data;
	private final String initialText;
	/** Only set on the server. */
	private final @Nullable TypewriterBlockEntity typewriter;

	/** Client side. */
	public TypewriterMenu(int containerId, Inventory inventory, OpenData openData) {
		this(containerId, inventory, new SimpleContainer(2), new SimpleContainerData(4), null, openData.text());
	}

	/** Server side. */
	public TypewriterMenu(int containerId, Inventory inventory, Container dyeSamples, ContainerData data, TypewriterBlockEntity typewriter) {
		this(containerId, inventory, dyeSamples, data, typewriter, "");
	}

	private TypewriterMenu(int containerId, Inventory inventory, Container dyeSamples, ContainerData data,
			@Nullable TypewriterBlockEntity typewriter, String initialText) {
		super(ModTypewriter.MENU, containerId);
		checkContainerSize(dyeSamples, 2);
		checkContainerDataCount(data, 4);
		this.dyeSamples = dyeSamples;
		this.data = data;
		this.typewriter = typewriter;
		this.initialText = initialText;
		addSlot(new SampleSlot(dyeSamples, TypewriterBlockEntity.SLOT_LETTER, LETTER_X, DYES_Y));
		addSlot(new SampleSlot(dyeSamples, TypewriterBlockEntity.SLOT_BACKGROUND, BACKGROUND_X, DYES_Y));
		addStandardInventorySlots(inventory, 8, INVENTORY_Y);
		addDataSlots(data);
	}

	/** Shows a dye as a setting; items never go in or out. See {@link #clicked}. */
	private static class SampleSlot extends Slot {
		SampleSlot(Container container, int index, int x, int y) {
			super(container, index, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return false;
		}

		@Override
		public boolean mayPickup(Player player) {
			return false;
		}
	}

	public @Nullable TypewriterBlockEntity typewriter() {
		return typewriter;
	}

	public String initialText() {
		return initialText;
	}

	public TypewriterStatus status() {
		return TypewriterStatus.of(data.get(0));
	}

	public int made() {
		return data.get(1);
	}

	public int total() {
		return data.get(2);
	}

	public int repeat() {
		return data.get(3);
	}

	@Override
	public boolean stillValid(Player player) {
		return dyeSamples.stillValid(player);
	}

	/** A dye sets the sample, anything else clears it. The background may stay empty: plain smooth stone. */
	private void setSample(int slot, ItemStack stack) {
		ItemStack sample = TypewriterBlockEntity.dyeOf(stack).map(c -> new ItemStack(TypewriterBlockEntity.dyeItem(c))).orElse(ItemStack.EMPTY);
		dyeSamples.setItem(slot, sample);
		dyeSamples.setChanged();
	}

	@Override
	public void clicked(int slotIndex, int button, ContainerInput input, Player player) {
		if (slotIndex != TypewriterBlockEntity.SLOT_LETTER && slotIndex != TypewriterBlockEntity.SLOT_BACKGROUND) {
			super.clicked(slotIndex, button, input, player);
			return;
		}
		// Runs on both sides, so the client shows the change right away. No item ever moves.
		switch (input) {
			case PICKUP -> setSample(slotIndex, getCarried());
			case QUICK_MOVE -> setSample(slotIndex, ItemStack.EMPTY);
			case SWAP -> setSample(slotIndex, player.getInventory().getItem(button));
			default -> {
			}
		}
	}

	@Override
	public boolean canDragTo(Slot slot) {
		return slot.index > TypewriterBlockEntity.SLOT_BACKGROUND && super.canDragTo(slot);
	}

	/** Shift-click on a dye in the inventory sets the letter dye; the stack stays where it is. */
	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		Slot slot = slots.get(slotIndex);
		if (slotIndex > TypewriterBlockEntity.SLOT_BACKGROUND && slot.hasItem()
				&& TypewriterBlockEntity.dyeOf(slot.getItem()).isPresent()) {
			setSample(TypewriterBlockEntity.SLOT_LETTER, slot.getItem());
		}
		return ItemStack.EMPTY;
	}
}
