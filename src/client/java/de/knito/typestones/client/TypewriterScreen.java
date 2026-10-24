package de.knito.typestones.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;

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
import de.knito.typestones.client.bake.LetterBaker;
import de.knito.typestones.client.network.LetterSync;
import de.knito.typestones.glyph.GlyphSet;
import de.knito.typestones.letter.Letter;
import de.knito.typestones.letter.LetterInfo;
import de.knito.typestones.letter.Letters;
import de.knito.typestones.letter.LibraryLetter;
import de.knito.typestones.typewriter.FontSettings;
import de.knito.typestones.typewriter.TypewriterBlockEntity;
import de.knito.typestones.typewriter.TypewriterMenu;
import de.knito.typestones.typewriter.TypewriterActionPayload;
import de.knito.typestones.typewriter.TypewriterStatus;

/**
 * The Typewriter's screen, with a font panel on its right: a font of this computer with a filter,
 * letter size, scale and baseline, and a preview of one letter with its cell and baseline.
 */
public class TypewriterScreen extends AbstractContainerScreen<TypewriterMenu> {
	private static final Identifier BACKGROUND = Typestones.id("textures/gui/typewriter.png");
	private static final int STATUS_Y = 110;
	private static final int PANEL_GAP = 2;
	private static final int PANEL_WIDTH = 120;
	private static final int PREVIEW = 64;
	private static final int PREVIEW_Y = 122;
	private static final String PREVIEW_SAMPLE = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

	private EditBox textBox;
	private EditBox filterBox;
	private Button sizeButton;
	private Button styleButton;
	private final List<Button> fontButtons = new ArrayList<>();
	/** -1 until the player changes it: then the screen shows what the typewriter has stored. */
	private int repeat = -1;
	private FontSettings settings;
	/** Which character of the text the preview shows. */
	private int previewIndex;

	public TypewriterScreen(TypewriterMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 176, 212);
		this.inventoryLabelY = TypewriterMenu.INVENTORY_Y - 11;
		this.settings = menu.initialFont();
	}

	private int panelX() {
		return leftPos + imageWidth + PANEL_GAP;
	}

	@Override
	protected void init() {
		super.init();
		// Center the screen and its font panel together.
		leftPos = (width - imageWidth - PANEL_GAP - PANEL_WIDTH) / 2;
		String oldText = textBox != null ? textBox.getValue() : menu.initialText();
		String oldFilter = filterBox != null ? filterBox.getValue() : "";

		textBox = new EditBox(font, leftPos + 8, topPos + 18, 160, 18, Component.translatable("gui.typestones.text"));
		textBox.setMaxLength(TypewriterBlockEntity.MAX_TEXT_LENGTH);
		textBox.setHint(Component.translatable("gui.typestones.text_hint"));
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

		addRenderableWidget(Button.builder(Component.translatable("gui.typestones.start"), b -> send(TypewriterActionPayload.Action.START))
				.bounds(leftPos + 8, topPos + 88, 50, 18).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.typestones.stop"), b -> send(TypewriterActionPayload.Action.STOP))
				.bounds(leftPos + 63, topPos + 88, 50, 18).build());

		int px = panelX();
		filterBox = new EditBox(font, px + 6, topPos + 18, PANEL_WIDTH - 12, 16, Component.translatable("gui.typestones.font_filter"));
		filterBox.setHint(Component.translatable("gui.typestones.font_filter"));
		filterBox.setValue(oldFilter);
		addRenderableWidget(filterBox);
		addRenderableWidget(Button.builder(Component.translatable("gui.typestones.font_classic_button"), b -> setFont(FontSettings.CLASSIC))
				.bounds(px + PANEL_WIDTH - 56, topPos + 2, 50, 14).build());
		addRenderableWidget(Button.builder(Component.literal("<"), b -> changeFont(-1))
				.bounds(px + 6, topPos + 38, 16, 18).build());
		addRenderableWidget(Button.builder(Component.literal(">"), b -> changeFont(1))
				.bounds(px + PANEL_WIDTH - 22, topPos + 38, 16, 18).build());

		fontButtons.clear();
		sizeButton = Button.builder(sizeLabel(), b -> changeSize())
				.bounds(px + 6, topPos + 60, 40, 18).build();
		fontButtons.add(sizeButton);
		styleButton = Button.builder(styleLabel(), b -> changeStyle())
				.bounds(px + 50, topPos + 60, PANEL_WIDTH - 56, 18).build();
		fontButtons.add(styleButton);
		fontButtons.add(Button.builder(Component.literal("-"), b -> changeScale(-1)).bounds(px + 6, topPos + 80, 16, 18).build());
		fontButtons.add(Button.builder(Component.literal("+"), b -> changeScale(1)).bounds(px + PANEL_WIDTH - 22, topPos + 80, 16, 18).build());
		fontButtons.add(Button.builder(Component.literal("-"), b -> changeBaseline(-1)).bounds(px + 6, topPos + 100, 16, 18).build());
		fontButtons.add(Button.builder(Component.literal("+"), b -> changeBaseline(1)).bounds(px + PANEL_WIDTH - 22, topPos + 100, 16, 18).build());
		fontButtons.forEach(this::addRenderableWidget);
		addRenderableWidget(Button.builder(Component.literal("<"), b -> previewIndex--)
				.bounds(px + 6, topPos + PREVIEW_Y + PREVIEW / 2 - 9, 16, 18).build());
		addRenderableWidget(Button.builder(Component.literal(">"), b -> previewIndex++)
				.bounds(px + PANEL_WIDTH - 22, topPos + PREVIEW_Y + PREVIEW / 2 - 9, 16, 18).build());
		updateFontButtons();
	}

	/** Size, scale and baseline mean nothing for the built-in classic face. */
	private void updateFontButtons() {
		fontButtons.forEach(b -> b.active = !settings.isClassic());
		sizeButton.setMessage(sizeLabel());
		styleButton.setMessage(styleLabel());
	}

	private Component styleLabel() {
		return Component.translatable("gui.typestones.font_style." + settings.style());
	}

	/** Regular, Bold, Italic, Bold Italic. */
	private void changeStyle() {
		settings = settings.withStyle((settings.style() + 1) % 4);
		updateFontButtons();
		send(TypewriterActionPayload.Action.SAVE);
	}

	private Component sizeLabel() {
		return Component.translatable("gui.typestones.letter_size", settings.size());
	}

	/** Classic first, then this computer's fonts; only those matching the filter. */
	private List<String> fontChoices() {
		String filter = filterBox.getValue().toLowerCase(Locale.ROOT);
		List<String> choices = new ArrayList<>();
		for (String family : allFonts()) {
			if (family.toLowerCase(Locale.ROOT).contains(filter)) {
				choices.add(family);
			}
		}
		return choices;
	}

	private static List<String> allFonts() {
		List<String> all = new ArrayList<>();
		all.add(FontSettings.CLASSIC);
		all.addAll(LetterBaker.families());
		return all;
	}

	private void changeFont(int delta) {
		List<String> choices = fontChoices();
		if (choices.isEmpty()) {
			return;
		}
		int index = choices.indexOf(settings.font());
		index = index < 0 ? (delta > 0 ? 0 : choices.size() - 1) : Math.floorMod(index + delta, choices.size());
		setFont(choices.get(index));
	}

	private void setFont(String font) {
		settings = settings.withFont(font);
		updateFontButtons();
		send(TypewriterActionPayload.Action.SAVE);
	}

	private void changeSize() {
		int[] sizes = FontSettings.SIZES;
		int index = 0;
		while (index < sizes.length && sizes[index] != settings.size()) {
			index++;
		}
		settings = settings.withSize(sizes[(index + 1) % sizes.length]);
		updateFontButtons();
		send(TypewriterActionPayload.Action.SAVE);
	}

	private void changeScale(int delta) {
		settings = settings.withScale(settings.scale() + delta * step());
		send(TypewriterActionPayload.Action.SAVE);
	}

	private void changeBaseline(int delta) {
		settings = settings.withBaseline(settings.baseline() + delta * step());
		send(TypewriterActionPayload.Action.SAVE);
	}

	/** Shift steps by 10. */
	private static int step() {
		return Minecraft.getInstance().hasShiftDown() ? 10 : 1;
	}

	@Override
	protected void setInitialFocus() {
		setInitialFocus(textBox);
	}

	private void preset(String text) {
		textBox.setValue(text);
		send(TypewriterActionPayload.Action.SAVE);
	}

	private int currentRepeat() {
		return repeat < 0 ? Math.clamp(menu.repeat(), 1, TypewriterBlockEntity.MAX_REPEAT) : repeat;
	}

	private void changeRepeat(int delta) {
		repeat = Math.clamp(currentRepeat() + delta * step(), 1, TypewriterBlockEntity.MAX_REPEAT);
		send(TypewriterActionPayload.Action.SAVE);
	}

	/**
	 * Characters a Typestone can't show are dropped here, so the box shows what the typewriter will make.
	 * On Start with a font, the letters are baked and uploaded first.
	 */
	private void send(TypewriterActionPayload.Action action) {
		String text = cleanText(textBox.getValue());
		if (!text.equals(textBox.getValue())) {
			textBox.setValue(text);
		}
		List<String> letters = List.of();
		if (action == TypewriterActionPayload.Action.START && !settings.isClassic()) {
			if (!LetterBaker.has(settings.font())) {
				// Another player's font: this game can't bake it. Keep the settings, start nothing.
				action = TypewriterActionPayload.Action.SAVE;
			} else {
				letters = bakeAndUpload(text);
			}
		}
		ClientPlayNetworking.send(new TypewriterActionPayload(action, text, currentRepeat(), settings, letters));
	}

	/** Also drops what the chosen font can't draw, when this computer has the font. */
	private String cleanText(String typed) {
		String text = TypewriterBlockEntity.cleanText(typed, settings.isClassic());
		if (settings.isClassic() || !LetterBaker.has(settings.font())) {
			return text;
		}
		StringBuilder drawable = new StringBuilder();
		text.codePoints().filter(c -> c == ' ' || LetterBaker.canDisplay(settings.font(), c)).forEach(drawable::appendCodePoint);
		return drawable.toString();
	}

	/** The hash of each character's letter; every new letter goes to the server once. */
	private List<String> bakeAndUpload(String text) {
		String locale = Minecraft.getInstance().getLanguageManager().getSelected();
		List<String> hashes = new ArrayList<>();
		Map<String, LibraryLetter> baked = new LinkedHashMap<>();
		text.codePoints().forEach(c -> {
			Letter letter = LetterBaker.bake(settings, c);
			if (letter == null) {
				hashes.add(Letters.NONE);
				return;
			}
			hashes.add(letter.hash());
			baked.putIfAbsent(letter.hash(), new LibraryLetter(letter,
					new LetterInfo(new String(Character.toChars(c)), settings.fontName(), locale, "", "")));
		});
		LetterSync.upload(baked.values());
		return hashes;
	}

	/** The character the preview shows: one of the text, or of A–Z, a–z, 0–9 while there is no text. */
	private int previewChar() {
		int[] chars = textBox.getValue().codePoints().filter(c -> c != ' ').distinct().toArray();
		if (chars.length == 0) {
			chars = PREVIEW_SAMPLE.codePoints().toArray();
		}
		return chars[Math.floorMod(previewIndex, chars.length)];
	}

	private @Nullable Letter previewLetter(int c) {
		if (settings.isClassic()) {
			return Character.isBmpCodePoint(c) ? Letters.get(Letters.classic((char) c)) : null;
		}
		return LetterBaker.bake(settings, c);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isEscape()) {
			return super.keyPressed(event);
		}
		// While typing, letters like E must go into the text box instead of closing the screen.
		for (EditBox box : List.of(textBox, filterBox)) {
			if (box.isFocused() && (box.keyPressed(event) || box.canConsumeInput())) {
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		// Keep what was typed even if no button was pressed; must go out before the menu closes on the server.
		send(TypewriterActionPayload.Action.SAVE);
		super.onClose();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);
		extractFontPanel(graphics);
	}

	private void extractFontPanel(GuiGraphicsExtractor graphics) {
		int px = panelX();
		int py = topPos;
		frame(graphics, px, py, PANEL_WIDTH, imageHeight, 0xFF555555);
		graphics.fill(px + 1, py + 1, px + PANEL_WIDTH - 1, py + imageHeight - 1, 0xFFC6C6C6);
		graphics.text(font, Component.translatable("gui.typestones.font"), px + 6, py + 6, 0xFF404040, false);

		String name = settings.isClassic() ? Component.translatable("gui.typestones.font_classic").getString() : settings.font();
		graphics.centeredText(font, font.plainSubstrByWidth(name, PANEL_WIDTH - 52), px + PANEL_WIDTH / 2, py + 43, 0xFFFFFFFF);
		graphics.centeredText(font, Component.translatable("gui.typestones.scale", settings.scale()), px + PANEL_WIDTH / 2, py + 85, 0xFFFFFFFF);
		String baseline = (settings.baseline() > 0 ? "+" : "") + settings.baseline();
		graphics.centeredText(font, Component.translatable("gui.typestones.baseline", baseline), px + PANEL_WIDTH / 2, py + 105, 0xFFFFFFFF);

		// The preview: the letter on stone gray, its cell framed, the baseline in red.
		int bx = px + (PANEL_WIDTH - PREVIEW) / 2;
		int by = py + PREVIEW_Y;
		int c = previewChar();
		frame(graphics, bx - 1, by - 1, PREVIEW + 2, PREVIEW + 2, 0xFF000000);
		graphics.fill(bx, by, bx + PREVIEW, by + PREVIEW, 0xFF8E8E8E);
		Letter letter = previewLetter(c);
		if (letter != null) {
			int pixel = PREVIEW / letter.size();
			for (Letter.Rect r : letter.rectangles()) {
				graphics.fill(bx + r.x0() * pixel, by + r.y0() * pixel, bx + r.x1() * pixel, by + r.y1() * pixel, 0xFF1D1D21);
			}
		}
		if (!settings.isClassic()) {
			int line = by + (int) Math.round(LetterBaker.baseline(settings) * PREVIEW / settings.size());
			graphics.fill(bx - 4, line, bx + PREVIEW + 4, line + 1, 0xFFE03030);
		}
		String label = new String(Character.toChars(c)) + String.format("  U+%04X", c);
		graphics.centeredText(font, label, px + PANEL_WIDTH / 2, by + PREVIEW + 4, 0xFF404040);
		if (!settings.isClassic() && !LetterBaker.has(settings.font())) {
			graphics.centeredText(font, Component.translatable("gui.typestones.font_missing"), px + PANEL_WIDTH / 2, by + PREVIEW + 14, 0xFF802020);
		} else if (!settings.isClassic() && !LetterBaker.canDisplay(settings.font(), c)) {
			graphics.centeredText(font, Component.translatable("gui.typestones.font_lacks"), px + PANEL_WIDTH / 2, by + PREVIEW + 14, 0xFF802020);
		}
	}

	private static void frame(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int color) {
		graphics.fill(x, y, x + w, y + 1, color);
		graphics.fill(x, y + h - 1, x + w, y + h, color);
		graphics.fill(x, y, x + 1, y + h, color);
		graphics.fill(x + w - 1, y, x + w, y + h, color);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
		super.extractLabels(graphics, xm, ym);
		graphics.centeredText(font, Component.translatable("gui.typestones.repeat", currentRepeat()), 134, 69, 0xFFFFFFFF);
		if (menu.total() > 0) {
			Component progress = Component.translatable("gui.typestones.progress", menu.made(), menu.total());
			graphics.text(font, progress, imageWidth - 8 - font.width(progress), 93, 0xFF404040, false);
		}
		TypewriterStatus status = menu.status();
		graphics.text(font, status.describe(), 8, STATUS_Y, status.color, false);
	}

	@Override
	protected void extractSlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY) {
		super.extractSlot(graphics, slot, mouseX, mouseY);
		// Fade the dye samples like ghosts, so they don't look like real items in the typewriter.
		if (slot.index <= TypewriterBlockEntity.SLOT_BACKGROUND && slot.hasItem()) {
			graphics.nextStratum();
			graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x808B8B8B);
		}
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		if (hoveredSlot != null && hoveredSlot.index <= TypewriterBlockEntity.SLOT_BACKGROUND && !hoveredSlot.hasItem()
				&& menu.getCarried().isEmpty()) {
			String key = hoveredSlot.index == TypewriterBlockEntity.SLOT_LETTER ? "gui.typestones.slot_letter" : "gui.typestones.slot_background";
			graphics.setTooltipForNextFrame(Component.translatable(key), mouseX, mouseY);
		}
	}
}
