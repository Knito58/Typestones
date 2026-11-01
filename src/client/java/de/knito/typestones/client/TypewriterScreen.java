package de.knito.typestones.client;

import java.util.ArrayList;
import java.util.Collections;
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
	private static final int STATUS_Y = 130;
	private static final int PANEL_GAP = 2;
	private static final int PANEL_WIDTH = 120;
	private static final int PREVIEW = 64;
	private static final int PREVIEW_Y = 142;
	private static final int MESSAGE_GAP = 3;
	private static final int MESSAGE_HEIGHT = 12;
	private static final SystemToast.SystemToastId BAKED_TOAST = new SystemToast.SystemToastId();
	private static final String PREVIEW_SAMPLE = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

	private EditBox textBox;
	private EditBox filterBox;
	private Button sizeButton;
	private Button alignButton;
	private Button closeButton;
	private final List<Button> joinedOnly = new ArrayList<>();
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
		super(menu, inventory, title, 176, 232);
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

		addRenderableWidget(Button.builder(Component.literal("-"), b -> changeRepeat(-1))
				.bounds(leftPos + 8, topPos + 88, 14, 18).build());
		addRenderableWidget(Button.builder(Component.literal("+"), b -> changeRepeat(1))
				.bounds(leftPos + 48, topPos + 88, 16, 18).build());
		sizeButton = Button.builder(sizeLabel(), b -> changeSize())
				.bounds(leftPos + 68, topPos + 88, 34, 18).build();
		styleButton = Button.builder(styleLabel(), b -> changeStyle())
				.bounds(leftPos + 104, topPos + 88, 64, 18).build();
		fontButtons.clear();
		fontButtons.add(sizeButton);
		fontButtons.add(styleButton);
		addRenderableWidget(Button.builder(Component.translatable("gui.typestones.start"), b -> send(TypewriterActionPayload.Action.START))
				.bounds(leftPos + 8, topPos + 108, 78, 18).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.typestones.stop"), b -> send(TypewriterActionPayload.Action.STOP))
				.bounds(leftPos + 90, topPos + 108, 78, 18).build());
		closeButton = addRenderableWidget(new CloseButton(leftPos + imageWidth - 15, topPos + 3));

		int px = panelX();
		filterBox = new EditBox(font, px + 6, topPos + 18, PANEL_WIDTH - 12, 16, Component.translatable("gui.typestones.font_filter"));
		filterBox.setHint(Component.translatable("gui.typestones.font_filter"));
		filterBox.setValue(oldFilter);
		filterBox.setResponder(this::filterChanged);
		addRenderableWidget(filterBox);
		addRenderableWidget(Button.builder(Component.translatable("gui.typestones.font_classic_button"), b -> setFont(FontSettings.CLASSIC))
				.bounds(px + PANEL_WIDTH - 56, topPos + 2, 50, 14).build());
		addRenderableWidget(Button.builder(Component.literal("<"), b -> changeFont(-1))
				.bounds(px + 6, topPos + 38, 16, 18).build());
		addRenderableWidget(Button.builder(Component.literal(">"), b -> changeFont(1))
				.bounds(px + PANEL_WIDTH - 22, topPos + 38, 16, 18).build());

		joinedButton = Button.builder(joinedLabel(), b -> toggleJoined())
				.bounds(px + 6, topPos + 60, 56, 18).build();
		joinedButton.setTooltip(Tooltip.create(Component.translatable("gui.typestones.joined.tooltip")));
		fontButtons.add(joinedButton);
		alignButton = Button.builder(alignLabel(), b -> changeAlign())
				.bounds(px + PANEL_WIDTH - 56, topPos + 60, 50, 18).build();
		alignButton.setTooltip(Tooltip.create(Component.translatable("gui.typestones.align.tooltip")));
		fontButtons.add(alignButton);
		Button widthDown = Button.builder(Component.literal("-"), b -> changeWidth(-1)).bounds(px + 6, topPos + 80, 16, 18).build();
		Button widthUp = Button.builder(Component.literal("+"), b -> changeWidth(1)).bounds(px + PANEL_WIDTH - 22, topPos + 80, 16, 18).build();
		widthDown.setTooltip(Tooltip.create(Component.translatable("gui.typestones.width.tooltip")));
		widthUp.setTooltip(Tooltip.create(Component.translatable("gui.typestones.width.tooltip")));
		fontButtons.add(widthDown);
		fontButtons.add(widthUp);
		joinedOnly.clear();
		joinedOnly.addAll(List.of(alignButton, widthDown, widthUp));
		fontButtons.add(Button.builder(Component.literal("-"), b -> changeScale(-1)).bounds(px + 6, topPos + 100, 16, 18).build());
		fontButtons.add(Button.builder(Component.literal("+"), b -> changeScale(1)).bounds(px + PANEL_WIDTH - 22, topPos + 100, 16, 18).build());
		fontButtons.add(Button.builder(Component.literal("-"), b -> changeBaseline(-1)).bounds(px + 6, topPos + 120, 16, 18).build());
		fontButtons.add(Button.builder(Component.literal("+"), b -> changeBaseline(1)).bounds(px + PANEL_WIDTH - 22, topPos + 120, 16, 18).build());
		fontButtons.forEach(this::addRenderableWidget);
		addRenderableWidget(Button.builder(Component.literal("<"), b -> previewIndex--)
				.bounds(px + 6, topPos + PREVIEW_Y + PREVIEW / 2 - 9, 16, 18).build());
		addRenderableWidget(Button.builder(Component.literal(">"), b -> previewIndex++)
				.bounds(px + PANEL_WIDTH - 22, topPos + PREVIEW_Y + PREVIEW / 2 - 9, 16, 18).build());
		updateFontButtons();
	}

	/** Size, scale and baseline mean nothing for the built-in classic face. */
	private void updateFontButtons() {
		fontButtons.forEach(b -> b.active = !settings.isClassic() && (settings.isJoined() || !joinedOnly.contains(b)));
		sizeButton.setMessage(sizeLabel());
		styleButton.setMessage(styleLabel());
		if (joinedButton != null) {
			joinedButton.setMessage(joinedLabel());
			alignButton.setMessage(alignLabel());
		}
	}

	private Component joinedLabel() {
		return Component.translatable(settings.joined() ? "gui.typestones.joined" : "gui.typestones.spaced");
	}

	private Component alignLabel() {
		return Component.translatable("gui.typestones.align." + settings.align());
	}

	private void changeAlign() {
		settings = settings.withAlign((settings.align() + 1) % 3);
		updateFontButtons();
		send(TypewriterActionPayload.Action.SAVE);
	}

	private void changeWidth(int delta) {
		settings = settings.withWidth(settings.width() + delta * step());
		send(TypewriterActionPayload.Action.SAVE);
	}

	/** The first font matching what was typed becomes the chosen one, so the preview follows every character. */
	private void filterChanged(String filter) {
		if (filter.isBlank()) {
			return;
		}
		List<String> choices = fontChoices();
		if (choices.isEmpty() || choices.contains(settings.font()) && !settings.isClassic()) {
			return;
		}
		String lower = filter.toLowerCase(Locale.ROOT);
		String pick = choices.stream().filter(f -> !f.equals(FontSettings.CLASSIC) && f.toLowerCase(Locale.ROOT).startsWith(lower)).findFirst()
				.orElseGet(() -> choices.stream().filter(f -> !f.equals(FontSettings.CLASSIC)).findFirst().orElse(choices.get(0)));
		if (!pick.equals(settings.font())) {
			setFont(pick);
		}
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

	/**
	 * The arrows walk the whole list in order, from the current font on. The filter only picks where to
	 * start (see {@link #filterChanged}); walking inside the matches would circle between a few fonts.
	 */
	private void changeFont(int delta) {
		List<String> choices = allFonts();
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
	 * On Start, characters a Typestone can't show are dropped from the box, so it shows what the typewriter
	 * will make; with a font, the letters are baked and uploaded first.
	 */
	private void send(TypewriterActionPayload.Action action) {
		// Only Start drops what the font can't draw. Saving a setting (scrolling through fonts) must not
		// eat the text: the next font may have the characters; the typewriter cleans its own copy.
		String text = textBox.getValue();
		if (action == TypewriterActionPayload.Action.START) {
			text = cleanText(text);
			if (!text.equals(textBox.getValue())) {
				textBox.setValue(text);
			}
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
					int padding = settings.padding(cells.size());
					if (cells.size() + padding > TypewriterBlockEntity.MAX_TEXT_LENGTH) {
						toast(Component.translatable("gui.typestones.toast.too_long", cells.size() + padding, TypewriterBlockEntity.MAX_TEXT_LENGTH));
						action = TypewriterActionPayload.Action.SAVE;
					} else {
						// Blank stones in front put the line where the alignment says, within the width.
						letters = new ArrayList<>(Collections.nCopies(padding, Letters.NONE));
						letters.addAll(uploadCells(cells));
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
		graphics.centeredText(font, Component.translatable("gui.typestones.scale", settings.scale()), px + PANEL_WIDTH / 2, py + 105, 0xFFFFFFFF);
		graphics.centeredText(font, Component.translatable(settings.width() == 0 ? "gui.typestones.width_auto" : "gui.typestones.width", settings.width()),
				px + PANEL_WIDTH / 2, py + 85, settings.isJoined() ? 0xFFFFFFFF : 0xFFA0A0A0);
		String baseline = (settings.baseline() > 0 ? "+" : "") + settings.baseline();
		graphics.centeredText(font, Component.translatable("gui.typestones.baseline", baseline), px + PANEL_WIDTH / 2, py + 125, 0xFFFFFFFF);

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

	/** The close button: a white cross drawn from pixels on a red tile, not a letter. */
	private final class CloseButton extends Button {
		CloseButton(int x, int y) {
			super(x, y, 11, 11, Component.translatable("gui.typestones.close"), b -> onClose(), DEFAULT_NARRATION);
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			int x = getX();
			int y = getY();
			graphics.fill(x, y, x + 11, y + 11, isHoveredOrFocused() ? 0xFFD05050 : 0xFF9A3C3C);
			frame(graphics, x, y, 11, 11, 0xFF000000);
			for (int i = 0; i < 7; i++) {
				graphics.fill(x + 2 + i, y + 2 + i, x + 3 + i, y + 3 + i, 0xFFFFFFFF);
				graphics.fill(x + 8 - i, y + 2 + i, x + 9 - i, y + 3 + i, 0xFFFFFFFF);
			}
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
				widget.active = widget == closeButton || free && !(settings.isClassic() && fontButtons.contains(widget))
						&& !(widget instanceof Button button && joinedOnly.contains(button) && !settings.isJoined());
			}
		}
		if (menu.pencilChoices().isEmpty()) {
			pencilButton.active = false;
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
		super.extractLabels(graphics, xm, ym);
		graphics.centeredText(font, Component.translatable("gui.typestones.repeat", currentRepeat()), 35, 93, 0xFFFFFFFF);
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
