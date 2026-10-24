package de.knito.typestones.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import de.knito.typestones.Typestones;
import de.knito.typestones.glyph.GlyphSet;
import de.knito.typestones.station.AlphaStationBlockEntity;
import de.knito.typestones.station.AlphaStationMenu;
import de.knito.typestones.station.StationActionPayload;
import de.knito.typestones.station.StationStatus;

public class AlphaStationScreen extends AbstractContainerScreen<AlphaStationMenu> {
	private static final Identifier BACKGROUND = Typestones.id("textures/gui/alpha_station.png");
	private static final int STATUS_Y = 110;

	private EditBox textBox;
	/** -1 until the player changes it: then the screen shows what the station has stored. */
	private int repeat = -1;

	public AlphaStationScreen(AlphaStationMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 176, 212);
		this.inventoryLabelY = AlphaStationMenu.INVENTORY_Y - 11;
	}

	@Override
	protected void init() {
		super.init();
		String oldText = textBox != null ? textBox.getValue() : menu.initialText();

		textBox = new EditBox(font, leftPos + 8, topPos + 18, 160, 18, Component.translatable("gui.typestones.text"));
		textBox.setMaxLength(AlphaStationBlockEntity.MAX_TEXT_LENGTH);
		textBox.setHint(Component.translatable("gui.typestones.text_hint"));
		textBox.setFilter(text -> AlphaStationBlockEntity.cleanText(text).equals(text));
		textBox.setValue(oldText);
		addRenderableWidget(textBox);

		addRenderableWidget(Button.builder(Component.translatable("gui.typestones.preset_letters"), b -> preset("ABCDEFGHIJKLMNOPQRSTUVWXYZ"))
				.bounds(leftPos + 8, topPos + 40, 36, 18).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.typestones.preset_digits"), b -> preset("0123456789"))
				.bounds(leftPos + 48, topPos + 40, 36, 18).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.typestones.preset_all"), b -> preset(GlyphSet.CHARS.substring(1)))
				.bounds(leftPos + 88, topPos + 40, 36, 18).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.typestones.clear"), b -> preset(""))
				.bounds(leftPos + 128, topPos + 40, 40, 18).build());

		addRenderableWidget(Button.builder(Component.literal("-"), b -> changeRepeat(-1))
				.bounds(leftPos + 100, topPos + 64, 16, 18).build());
		addRenderableWidget(Button.builder(Component.literal("+"), b -> changeRepeat(1))
				.bounds(leftPos + 152, topPos + 64, 16, 18).build());

		addRenderableWidget(Button.builder(Component.translatable("gui.typestones.start"), b -> send(StationActionPayload.Action.START))
				.bounds(leftPos + 8, topPos + 88, 50, 18).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.typestones.stop"), b -> send(StationActionPayload.Action.STOP))
				.bounds(leftPos + 63, topPos + 88, 50, 18).build());
	}

	@Override
	protected void setInitialFocus() {
		setInitialFocus(textBox);
	}

	private void preset(String text) {
		textBox.setValue(text);
		send(StationActionPayload.Action.SAVE);
	}

	private int currentRepeat() {
		return repeat < 0 ? Math.clamp(menu.repeat(), 1, AlphaStationBlockEntity.MAX_REPEAT) : repeat;
	}

	/** Shift steps by 10. */
	private void changeRepeat(int delta) {
		int step = Minecraft.getInstance().hasShiftDown() ? 10 : 1;
		repeat = Math.clamp(currentRepeat() + delta * step, 1, AlphaStationBlockEntity.MAX_REPEAT);
		send(StationActionPayload.Action.SAVE);
	}

	private void send(StationActionPayload.Action action) {
		ClientPlayNetworking.send(new StationActionPayload(action, textBox.getValue(), currentRepeat()));
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isEscape()) {
			return super.keyPressed(event);
		}
		// While typing, letters like E must go into the text box instead of closing the screen.
		if (textBox.isFocused() && (textBox.keyPressed(event) || textBox.canConsumeInput())) {
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		// Keep what was typed even if no button was pressed; must go out before the menu closes on the server.
		send(StationActionPayload.Action.SAVE);
		super.onClose();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
		super.extractLabels(graphics, xm, ym);
		graphics.centeredText(font, Component.translatable("gui.typestones.repeat", currentRepeat()), 134, 69, 0xFFFFFFFF);
		if (menu.total() > 0) {
			Component progress = Component.translatable("gui.typestones.progress", menu.made(), menu.total());
			graphics.text(font, progress, imageWidth - 8 - font.width(progress), 93, 0xFF404040, false);
		}
		StationStatus status = menu.status();
		graphics.text(font, status.describe(), 8, STATUS_Y, status.color, false);
	}

	@Override
	protected void extractSlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY) {
		super.extractSlot(graphics, slot, mouseX, mouseY);
		// Fade the dye samples like ghosts, so they don't look like real items in the station.
		if (slot.index <= AlphaStationBlockEntity.SLOT_BACKGROUND && slot.hasItem()) {
			graphics.nextStratum();
			graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x808B8B8B);
		}
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		if (hoveredSlot != null && hoveredSlot.index <= AlphaStationBlockEntity.SLOT_BACKGROUND && !hoveredSlot.hasItem()
				&& menu.getCarried().isEmpty()) {
			String key = hoveredSlot.index == AlphaStationBlockEntity.SLOT_LETTER ? "gui.typestones.slot_letter" : "gui.typestones.slot_background";
			graphics.setTooltipForNextFrame(Component.translatable(key), mouseX, mouseY);
		}
	}
}
