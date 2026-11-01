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
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import de.knito.typestones.ModPencil;
import de.knito.typestones.Typestones;
import de.knito.typestones.client.bake.LetterBaker;
import de.knito.typestones.client.bake.LineBaker;
import de.knito.typestones.client.network.LetterSync;
import de.knito.typestones.glyph.GlyphSet;
import de.knito.typestones.letter.Letter;
import de.knito.typestones.letter.LetterInfo;
import de.knito.typestones.letter.Letters;
import de.knito.typestones.letter.LibraryLetter;
import de.knito.typestones.typewriter.FontSettings;
import de.knito.typestones.typewriter.TypewriterBlockEntity;
import de.knito.typestones.typewriter.TypewriterMenu;
import de.knito.typestones.client.picture.PictureSync;
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
	private static final int MESSAGE_GAP = 3;
	private static final int MESSAGE_HEIGHT = 12;
	private static final SystemToast.SystemToastId BAKED_TOAST = new SystemToast.SystemToastId();
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
	private @Nullable Button joinedButton;
	/** The cut line the preview shows in joined mode, baked again only when the settings or the text change. */
	private @Nullable PreviewLine previewLine;

	private record PreviewLine(FontSettings settings, String text, List<LineBaker.Cell> cells) {
	}
	/** The pencil choice shown, by hash; empty for the first choice, the last job. */
	private String pencilHash = "";
	private PencilButton pencilButton;
	/** Until this tick the status line says what the pencil is for, after a choice. */
	private int pencilNoteUntil;
	private int ticks;

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
		topPos = Math.max(0, (height - imageHeight - MESSAGE_GAP - MESSAGE_HEIGHT) / 2);
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

		pencilButton = addRenderableWidget(new PencilButton(leftPos + 52, topPos + 64, 100, 18));
		addRenderableWidget(Button.builder(Component.literal("▲"), b -> stepPencil(-1))
				.bounds(leftPos + 154, topPos + 64, 14, 9).build());
		addRenderableWidget(Button.builder(Component.literal("▼"), b -> stepPencil(1))
				.bounds(leftPos + 154, topPos + 73, 14, 9).build());
		updatePencilButton();

		addRenderableWidget(Button.builder(Component.translatable("gui.typestones.start"), b -> send(TypewriterActionPayload.Action.START))
				.bounds(leftPos + 8, topPos + 88, 49, 18).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.typestones.stop"), b -> send(TypewriterActionPayload.Action.STOP))
				.bounds(leftPos + 60, topPos + 88, 49, 18).build());
		addRenderableWidget(Button.builder(Component.literal("-"), b -> changeRepeat(-1))
				.bounds(leftPos + 111, topPos + 88, 14, 18).build());
		addRenderableWidget(Button.builder(Component.literal("+"), b -> changeRepeat(1))
				.bounds(leftPos + 152, topPos + 88, 16, 18).build());

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
		joinedButton = Button.builder(joinedLabel(), b -> toggleJoined())
				.bounds(px + 6, topPos + 2, 56, 14).build();
		joinedButton.setTooltip(Tooltip.create(Component.translatable("gui.typestones.joined.tooltip")));
		fontButtons.add(joinedButton);
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
		if (joinedButton != null) {
			joinedButton.setMessage(joinedLabel());
		}
	}

	private Component joinedLabel() {
		return Component.translatable(settings.joined() ? "gui.typestones.joined" : "gui.typestones.spaced");
	}

	/** Joined: the line is shaped as a whole and cut into stones. Spaced: one stone per character. */
	private void toggleJoined() {
		settings = settings.withJoined(!settings.joined());
		previewIndex = 0;
		updateFontButtons();
		send(TypewriterActionPayload.Action.SAVE);
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
				if (!settings.joined() && LetterBaker.needsShaping(text)) {
					// Arabic and the like can't be set one stone per character.
					settings = settings.withJoined(true);
					updateFontButtons();
					toast(Component.translatable("gui.typestones.toast.auto_joined"));
				}
				if (settings.isJoined()) {
					List<LineBaker.Cell> cells = LetterBaker.bakeLine(settings, text);
					if (cells.size() > TypewriterBlockEntity.MAX_TEXT_LENGTH) {
						toast(Component.translatable("gui.typestones.toast.too_long", cells.size(), TypewriterBlockEntity.MAX_TEXT_LENGTH));
						action = TypewriterActionPayload.Action.SAVE;
					} else {
						letters = uploadCells(cells);
					}
				} else {
					letters = bakeAndUpload(text);
				}
			}
		}
		ClientPlayNetworking.send(new TypewriterActionPayload(action, text, currentRepeat(), settings, letters, pencilHash));
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
		int[] fresh = {0};
		text.codePoints().forEach(c -> {
			Letter letter = LetterBaker.bake(settings, c);
			if (letter == null) {
				hashes.add(Letters.NONE);
				return;
			}
			hashes.add(letter.hash());
			if (!baked.containsKey(letter.hash()) && !Letters.isKnown(letter.hash())) {
				fresh[0]++;
			}
			baked.putIfAbsent(letter.hash(), new LibraryLetter(letter,
					new LetterInfo(new String(Character.toChars(c)), settings.fontName(), locale, "", "")));
		});
		LetterSync.upload(baked.values());
		if (fresh[0] > 0) {
			Minecraft minecraft = Minecraft.getInstance();
			SystemToast.addOrUpdate(minecraft.gui.toastManager(), BAKED_TOAST, Component.translatable("block.typestones.typewriter"),
					Component.translatable("gui.typestones.toast.baked", fresh[0], settings.fontName(), settings.size()));
		}
		return hashes;
	}

	private void toast(Component message) {
		SystemToast.addOrUpdate(Minecraft.getInstance().gui.toastManager(), BAKED_TOAST, Component.translatable("block.typestones.typewriter"), message);
	}

	/** The hash of each cell of a joined line, a blank stone for an empty cell; new letters go to the server once. */
	private List<String> uploadCells(List<LineBaker.Cell> cells) {
		String locale = Minecraft.getInstance().getLanguageManager().getSelected();
		List<String> hashes = new ArrayList<>();
		Map<String, LibraryLetter> baked = new LinkedHashMap<>();
		int fresh = 0;
		for (LineBaker.Cell cell : cells) {
			Letter letter = cell.letter();
			if (letter == null) {
				hashes.add(Letters.NONE);
				continue;
			}
			hashes.add(letter.hash());
			if (!baked.containsKey(letter.hash()) && !Letters.isKnown(letter.hash())) {
				fresh++;
			}
			baked.putIfAbsent(letter.hash(), new LibraryLetter(letter, new LetterInfo(cell.text(), settings.fontName(), locale, "", "")));
		}
		LetterSync.upload(baked.values());
		if (fresh > 0) {
			toast(Component.translatable("gui.typestones.toast.baked", fresh, settings.fontName(), settings.size()));
		}
		return hashes;
	}

	/** The cut line for the preview in joined mode; null when it isn't joined or the font can't draw it. */
	private @Nullable List<LineBaker.Cell> previewCells() {
		String text = textBox.getValue().strip();
		if (!settings.isJoined() || text.isEmpty() || !LetterBaker.has(settings.font())) {
			return null;
		}
		if (previewLine == null || !previewLine.settings().equals(settings) || !previewLine.text().equals(text)) {
			previewLine = new PreviewLine(settings, text, LetterBaker.bakeLine(settings, text));
		}
		return previewLine.cells().isEmpty() ? null : previewLine.cells();
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
		extractMessageLine(graphics);
	}

	private void extractFontPanel(GuiGraphicsExtractor graphics) {
		int px = panelX();
		int py = topPos;
		frame(graphics, px, py, PANEL_WIDTH, imageHeight, 0xFF555555);
		graphics.fill(px + 1, py + 1, px + PANEL_WIDTH - 1, py + imageHeight - 1, 0xFFC6C6C6);

		graphics.centeredText(font, shortFontName(), px + PANEL_WIDTH / 2, py + 43, 0xFFFFFFFF);
		graphics.centeredText(font, Component.translatable("gui.typestones.scale", settings.scale()), px + PANEL_WIDTH / 2, py + 85, 0xFFFFFFFF);
		String baseline = (settings.baseline() > 0 ? "+" : "") + settings.baseline();
		graphics.centeredText(font, Component.translatable("gui.typestones.baseline", baseline), px + PANEL_WIDTH / 2, py + 105, 0xFFFFFFFF);

		// The preview: the letter on stone gray, its cell framed, the baseline in red.
		int bx = px + (PANEL_WIDTH - PREVIEW) / 2;
		int by = py + PREVIEW_Y;
		List<LineBaker.Cell> cells = previewCells();
		LineBaker.Cell cell = cells == null ? null : cells.get(Math.floorMod(previewIndex, cells.size()));
		int c = cells == null ? previewChar() : ' ';
		frame(graphics, bx - 1, by - 1, PREVIEW + 2, PREVIEW + 2, 0xFF000000);
		graphics.fill(bx, by, bx + PREVIEW, by + PREVIEW, 0xFF8E8E8E);
		Letter letter = cell != null ? cell.letter() : previewLetter(c);
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
		String label = cell != null
				? (Math.floorMod(previewIndex, cells.size()) + 1) + "/" + cells.size() + "  " + cell.text()
				: new String(Character.toChars(c)) + String.format("  U+%04X", c);
		graphics.centeredText(font, label, px + PANEL_WIDTH / 2, by + PREVIEW + 4, 0xFF404040);
	}

	/** What the message line says now, or null. */
	private @Nullable Component message() {
		if (settings.isClassic()) {
			return null;
		}
		if (!LetterBaker.has(settings.font())) {
			return Component.translatable("gui.typestones.font_missing", settings.font());
		}
		if (settings.isJoined() && textBox.getValue().length() > 0 && previewCells() != null
				&& previewCells().size() > TypewriterBlockEntity.MAX_TEXT_LENGTH) {
			return Component.translatable("gui.typestones.toast.too_long", previewCells().size(), TypewriterBlockEntity.MAX_TEXT_LENGTH);
		}
		int c = previewChar();
		if (!LetterBaker.canDisplay(settings.font(), c)) {
			return Component.translatable("gui.typestones.font_lacks", settings.font(), String.format("U+%04X", c));
		}
		return null;
	}

	/** A line under both panels; a message wider than the line scrolls through it like a ticker. */
	private void extractMessageLine(GuiGraphicsExtractor graphics) {
		Component message = message();
		if (message == null) {
			return;
		}
		int x0 = leftPos;
		int x1 = panelX() + PANEL_WIDTH;
		int y = topPos + imageHeight + MESSAGE_GAP;
		graphics.fill(x0, y, x1, y + MESSAGE_HEIGHT, 0xC0000000);
		int room = x1 - x0 - 8;
		int textWidth = font.width(message);
		if (textWidth <= room) {
			graphics.centeredText(font, message, (x0 + x1) / 2, y + 2, 0xFFFF6060);
			return;
		}
		int loop = textWidth + 40;
		int offset = (int) (Util.getMillis() / 25 % loop);
		graphics.enableScissor(x0 + 4, y, x1 - 4, y + MESSAGE_HEIGHT);
		graphics.text(font, message, x0 + 4 - offset, y + 2, 0xFFFF6060, false);
		graphics.text(font, message, x0 + 4 - offset + loop, y + 2, 0xFFFF6060, false);
		graphics.disableScissor();
	}

	private String fontName() {
		return settings.isClassic() ? Component.translatable("gui.typestones.font_classic").getString() : settings.font();
	}

	/** The font name, cut with "…" when it is wider than the room between the arrows. */
	private String shortFontName() {
		String name = fontName();
		int room = PANEL_WIDTH - 52;
		return font.width(name) <= room ? name : font.plainSubstrByWidth(name, room - font.width("…")) + "…";
	}

	private static void frame(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int color) {
		graphics.fill(x, y, x + w, y + 1, color);
		graphics.fill(x, y + h - 1, x + w, y + h, color);
		graphics.fill(x, y, x + 1, y + h, color);
		graphics.fill(x + w - 1, y, x + w, y + h, color);
	}

	/** The Pencil button: a pencil icon and what the pencil will lay. */
	private final class PencilButton extends Button {
		private final ItemStack icon = new ItemStack(ModPencil.ITEM);

		PencilButton(int x, int y, int width, int height) {
			super(x, y, width, height, Component.empty(), b -> send(TypewriterActionPayload.Action.GET_PENCIL), DEFAULT_NARRATION);
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			extractDefaultSprite(graphics);
			// One pixel up and right of the corner: the pencil drawing leans to the bottom left.
			graphics.item(icon, getX() + 2, getY());
			String label = cut(getMessage().getString(), getWidth() - 24);
			graphics.text(font, label, getX() + 20, getY() + (getHeight() - 8) / 2, active ? 0xFFFFFFFF : 0xFFA0A0A0, true);
		}
	}

	/** The text cut to the width, with "…" when something is left out. */
	private String cut(String text, int width) {
		return font.width(text) <= width ? text : font.plainSubstrByWidth(text, width - font.width("…")) + "…";
	}

	private int pencilIndex() {
		List<TypewriterActionPayload.PencilChoice> choices = menu.pencilChoices();
		for (int i = 1; i < choices.size(); i++) {
			if (choices.get(i).hash().equals(pencilHash)) {
				return i;
			}
		}
		return 0;
	}

	/** The arrows and the mouse wheel go through the choices, and around. */
	private void stepPencil(int step) {
		List<TypewriterActionPayload.PencilChoice> choices = menu.pencilChoices();
		if (choices.isEmpty()) {
			return;
		}
		int index = Math.floorMod(pencilIndex() + step, choices.size());
		pencilHash = index == 0 ? "" : choices.get(index).hash();
		pencilNoteUntil = ticks + 60;
		updatePencilButton();
	}

	/** What a choice is called: a line's text in quotes, or the image name of a picture. */
	private Component pencilLabel(TypewriterActionPayload.PencilChoice choice, int index) {
		if (choice.isLine()) {
			return Component.literal("\"" + choice.text() + "\"");
		}
		String name = PictureSync.name(choice.hash());
		return name == null || name.isEmpty() ? Component.translatable("gui.typestones.pencil_picture", index + 1) : Component.literal(name);
	}

	/** Label and tooltip of the Pencil button; the names of pictures arrive with them. */
	private void updatePencilButton() {
		List<TypewriterActionPayload.PencilChoice> choices = menu.pencilChoices();
		int index = pencilIndex();
		if (index == 0) {
			pencilHash = "";
		}
		if (choices.isEmpty()) {
			pencilButton.setMessage(Component.translatable("gui.typestones.pencil_none"));
			pencilButton.setTooltip(Tooltip.create(Component.translatable("gui.typestones.pencil_none_tooltip")));
			return;
		}
		TypewriterActionPayload.PencilChoice choice = choices.get(index);
		Component label = pencilLabel(choice, index);
		Component size = choice.isLine()
				? Component.translatable("gui.typestones.pencil_size_line", choice.cols())
				: Component.translatable("gui.typestones.pencil_size_picture", choice.cols(), choice.rows());
		pencilButton.setMessage(label);
		pencilButton.setTooltip(Tooltip.create(Component.translatable("gui.typestones.pencil_tooltip", label, size)));
	}

	/** The mouse wheel over the Pencil button chooses what it lays. */
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (pencilButton.active && pencilButton.isMouseOver(mouseX, mouseY) && scrollY != 0) {
			stepPencil(scrollY > 0 ? -1 : 1);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	/** While a program holds the Typewriter, every control is greyed out: the screen only watches. */
	@Override
	protected void containerTick() {
		super.containerTick();
		ticks++;
		updatePencilButton();
		boolean free = !menu.claimed();
		for (var child : children()) {
			if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget) {
				// Size, scale and baseline stay off for the classic face, see updateFontButtons.
				widget.active = free && !(settings.isClassic() && fontButtons.contains(widget));
			}
		}
		if (menu.pencilChoices().isEmpty()) {
			pencilButton.active = false;
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
		super.extractLabels(graphics, xm, ym);
		graphics.centeredText(font, Component.translatable("gui.typestones.repeat", currentRepeat()), 139, 93, 0xFFFFFFFF);
		List<TypewriterActionPayload.PencilChoice> choices = menu.pencilChoices();
		if (ticks < pencilNoteUntil && !choices.isEmpty()) {
			int index = pencilIndex();
			Component note = Component.translatable("gui.typestones.pencil_for", pencilLabel(choices.get(index), index), index + 1, choices.size());
			graphics.text(font, cut(note.getString(), imageWidth - 16), 8, STATUS_Y, 0xFF404040, false);
			return;
		}
		TypewriterStatus status = menu.status();
		Component line = menu.claimed()
				? Component.translatable("gui.typestones.claimed", status.describe())
				: status.describe();
		if (menu.total() > 0) {
			line = Component.translatable("gui.typestones.status_progress", line,
					Component.translatable("gui.typestones.progress", menu.made(), menu.total()));
		}
		graphics.text(font, cut(line.getString(), imageWidth - 16), 8, STATUS_Y, menu.claimed() ? 0xFF2040A0 : status.color, false);
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
		// A cut font name shows in full when hovered.
		int px = panelX();
		if (mouseX >= px + 24 && mouseX < px + PANEL_WIDTH - 24 && mouseY >= topPos + 38 && mouseY < topPos + 56
				&& !shortFontName().equals(fontName())) {
			graphics.setTooltipForNextFrame(Component.literal(fontName()), mouseX, mouseY);
		}
		if (hoveredSlot != null && hoveredSlot.index <= TypewriterBlockEntity.SLOT_BACKGROUND && !hoveredSlot.hasItem()
				&& menu.getCarried().isEmpty()) {
			String key = hoveredSlot.index == TypewriterBlockEntity.SLOT_LETTER ? "gui.typestones.slot_letter" : "gui.typestones.slot_background";
			graphics.setTooltipForNextFrame(Component.translatable(key), mouseX, mouseY);
		}
	}
}
