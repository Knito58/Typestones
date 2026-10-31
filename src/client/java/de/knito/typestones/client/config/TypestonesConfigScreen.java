package de.knito.typestones.client.config;

import java.util.List;
import java.util.stream.IntStream;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import de.knito.typestones.TypestonesConfig;
import de.knito.typestones.picture.Picture;

/**
 * The settings screen that Mod Menu opens: resolution and largest size of new pictures, and how far
 * picture stones are drawn. Done saves config/typestones.properties, Cancel leaves it as it was.
 */
public class TypestonesConfigScreen extends Screen {
	private static final List<Integer> VIEW_CHUNKS = List.of(1, 2, 3, 4, 6, 8, 12, 16);
	private static final int BUTTON_WIDTH = 200;
	private static final int ROW = 24;

	private final @Nullable Screen parent;
	private int resolution;
	private int maxCols;
	private int maxRows;
	private int viewChunks;

	public TypestonesConfigScreen(@Nullable Screen parent) {
		super(Component.translatable("config.typestones.title"));
		this.parent = parent;
		TypestonesConfig config = TypestonesConfig.get();
		resolution = config.pictureResolution();
		maxCols = config.maxCols();
		maxRows = config.maxRows();
		viewChunks = config.viewChunks();
	}

	@Override
	protected void init() {
		int x = (width - BUTTON_WIDTH) / 2;
		int y = height / 6 + 12;
		List<Integer> stones = IntStream.rangeClosed(1, Picture.MAX_STONES).boxed().toList();

		addRenderableWidget(CycleButton.builder((Integer value) -> Component.translatable("config.typestones.pixels", value), resolution)
				.withValues(TypestonesConfig.RESOLUTIONS)
				.withTooltip(value -> Tooltip.create(Component.translatable(value >= TypestonesConfig.RESOLUTION_WARNING
						? "config.typestones.resolution.warning" : "config.typestones.resolution.tooltip")))
				.create(x, y, BUTTON_WIDTH, 20, Component.translatable("config.typestones.resolution"), (button, value) -> resolution = value));
		addRenderableWidget(CycleButton.builder((Integer value) -> Component.translatable("config.typestones.stones", value), maxCols)
				.withValues(stones)
				.withTooltip(value -> Tooltip.create(Component.translatable("config.typestones.size.tooltip")))
				.create(x, y + ROW, BUTTON_WIDTH, 20, Component.translatable("config.typestones.max_cols"), (button, value) -> maxCols = value));
		addRenderableWidget(CycleButton.builder((Integer value) -> Component.translatable("config.typestones.stones", value), maxRows)
				.withValues(stones)
				.withTooltip(value -> Tooltip.create(Component.translatable("config.typestones.size.tooltip")))
				.create(x, y + 2 * ROW, BUTTON_WIDTH, 20, Component.translatable("config.typestones.max_rows"), (button, value) -> maxRows = value));
		addRenderableWidget(CycleButton.builder((Integer value) -> Component.translatable("config.typestones.chunks", value),
						VIEW_CHUNKS.contains(viewChunks) ? viewChunks : TypestonesConfig.DEFAULT.viewChunks())
				.withValues(VIEW_CHUNKS)
				.withTooltip(value -> Tooltip.create(Component.translatable("config.typestones.view.tooltip")))
				.create(x, y + 3 * ROW, BUTTON_WIDTH, 20, Component.translatable("config.typestones.view"), (button, value) -> viewChunks = value));

		int bottom = height - 28;
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> {
			TypestonesConfig.set(new TypestonesConfig(resolution, maxCols, maxRows, viewChunks));
			onClose();
		}).bounds(width / 2 - 154, bottom, 150, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
				.bounds(width / 2 + 4, bottom, 150, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(font, title, width / 2, 15, 0xFFFFFFFF);
		int y = height / 6 + 12 + 4 * ROW + 6;
		if (resolution >= TypestonesConfig.RESOLUTION_WARNING) {
			graphics.centeredText(font, Component.translatable("config.typestones.resolution.warning_line"), width / 2, y, 0xFFFF6060);
		}
		graphics.centeredText(font, Component.translatable("config.typestones.server_note"), width / 2, y + 14, 0xFFA0A0A0);
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
