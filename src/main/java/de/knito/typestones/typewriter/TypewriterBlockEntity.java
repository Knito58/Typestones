package de.knito.typestones.typewriter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;

import de.knito.typestones.ModBlocks;
import de.knito.typestones.ModComponents;
import de.knito.typestones.ModPencil;
import de.knito.typestones.ModPictures;
import de.knito.typestones.ModTypewriter;
import de.knito.typestones.block.Dyes;
import de.knito.typestones.block.TypestoneItem;
import de.knito.typestones.letter.LetterLibrary;
import de.knito.typestones.letter.Letters;
import de.knito.typestones.picture.Picture;
import de.knito.typestones.picture.PictureTile;
import de.knito.typestones.script.TypewriterScript;

/**
 * Makes one Typestone per character of a line of text, every {@link #INTERVAL} ticks. The letters come
 * from the built-in classic face or, for any other font, from the world's letter library, where the
 * player's game put them when Start was pressed (see {@link FontSettings}). Takes smooth
 * stone and dyes from inventories on any side but the back, and puts the blocks into the inventory at
 * the back. It holds no items itself; the two dye slots in its screen are only settings.
 */
public class TypewriterBlockEntity extends BlockEntity implements ExtendedMenuProvider<TypewriterMenu.OpenData> {
	public static final int INTERVAL = 5;
	public static final int MAX_TEXT_LENGTH = 128;
	public static final int MAX_REPEAT = 64;
	/** Menu slot numbers of the two dye settings. */
	public static final int SLOT_LETTER = 0;
	public static final int SLOT_BACKGROUND = 1;

	/** Dye samples: never taken from or given to a player. */
	private final SimpleContainer dyeSamples = new SimpleContainer(2) {
		@Override
		public void setChanged() {
			super.setChanged();
			TypewriterBlockEntity.this.setChanged();
		}

		@Override
		public boolean stillValid(Player player) {
			return Container.stillValidBlockEntity(TypewriterBlockEntity.this, player);
		}
	};

	private static final Codec<List<String>> LETTERS_CODEC = Codec.STRING.listOf();

	/** A picture being typed: one stone per tile, row by row from the top left. */
	public record PictureJob(String hash, int cols, int rows, PictureTile.Plane plane) {
		public static final Codec<PictureJob> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.fieldOf("hash").forGetter(PictureJob::hash),
				Codec.intRange(1, Picture.MAX_STONES).fieldOf("cols").forGetter(PictureJob::cols),
				Codec.intRange(1, Picture.MAX_STONES).fieldOf("rows").forGetter(PictureJob::rows),
				PictureTile.Plane.CODEC.optionalFieldOf("plane", PictureTile.Plane.WALL).forGetter(PictureJob::plane)
		).apply(instance, PictureJob::new));

		public static final StreamCodec<ByteBuf, PictureJob> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.stringUtf8(64), PictureJob::hash,
				ByteBufCodecs.VAR_INT, PictureJob::cols,
				ByteBufCodecs.VAR_INT, PictureJob::rows,
				ByteBufCodecs.idMapper(i -> PictureTile.Plane.values()[Math.clamp(i, 0, PictureTile.Plane.values().length - 1)],
						PictureTile.Plane::ordinal), PictureJob::plane,
				PictureJob::new);

		/** The picture a stone belongs to. */
		public static PictureJob of(PictureTile tile) {
			return new PictureJob(tile.hash(), tile.cols(), tile.rows(), tile.plane());
		}

		public PictureJob {
			cols = Math.clamp(cols, 1, Picture.MAX_STONES);
			rows = Math.clamp(rows, 1, Picture.MAX_STONES);
		}

		public int stones() {
			return cols * rows;
		}

		public PictureTile tile(int index) {
			return tileAt(index % cols, index / cols);
		}

		public PictureTile tileAt(int col, int row) {
			return new PictureTile(hash, cols, rows, col, row, plane);
		}
	}

	/** What a job takes and what the inputs hold right now. */
	public record Cost(int stones, int rgbDye, long haveStones, long haveRgbDye) {
		public boolean enough() {
			return haveStones >= stones && haveRgbDye >= rgbDye;
		}

		/** The status the job would wait in first, or null when everything is there. */
		public @Nullable TypewriterStatus missing() {
			if (haveStones < stones) {
				return TypewriterStatus.NEEDS_STONE;
			}
			return haveRgbDye < rgbDye ? TypewriterStatus.NEEDS_RGB_DYE : null;
		}
	}

	private String text = "";
	private int repeat = 1;
	private FontSettings font = FontSettings.DEFAULT;
	/** The job: one letter hash per character and the repeat as they were at Start, and how many blocks are made. */
	private List<String> jobLetters = List.of();
	/** The text of jobLetters, so a program can start the same line again without the font being baked anew. */
	private String jobText = "";
	/** Set while the job is a picture instead of jobLetters. */
	private @Nullable PictureJob pictureJob;
	private int jobRepeat = 1;
	private int made;
	private boolean running;
	private int ticks;
	private TypewriterStatus status = TypewriterStatus.IDLE;
	/** The program that claimed the Typewriter; not saved, a claim ends with the program. */
	private TypewriterScript.@Nullable Holder holder;

	/** Synced to the open screen: 0 = status, 1 = blocks made, 2 = blocks in the job, 3 = repeat, 4 = 1 while a program holds it. */
	private final ContainerData data = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case 0 -> status.ordinal();
				case 1 -> made;
				case 2 -> total();
				case 3 -> repeat;
				case 4 -> isClaimed() ? 1 : 0;
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
		}

		@Override
		public int getCount() {
			return 5;
		}
	};

	public TypewriterBlockEntity(BlockPos pos, BlockState state) {
		super(ModTypewriter.ENTITY, pos, state);
	}

	/**
	 * Keeps only characters a Typestone can show: for the classic face the ones it has, for other fonts
	 * anything but control characters. At most {@link #MAX_TEXT_LENGTH} chars; pairs are not split.
	 */
	public static String cleanText(String text, boolean classic) {
		StringBuilder clean = new StringBuilder();
		text.codePoints().forEach(c -> {
			boolean shown = classic ? Character.isBmpCodePoint(c) && Letters.hasClassic((char) c) : !Character.isISOControl(c);
			if (shown && clean.length() + Character.charCount(c) <= MAX_TEXT_LENGTH) {
				clean.appendCodePoint(c);
			}
		});
		return clean.toString();
	}

	/** The classic letter of each character; {@link Letters#NONE} for the space. */
	public static List<String> classicLetters(String text) {
		List<String> letters = new ArrayList<>();
		text.codePoints().forEach(c -> letters.add(Character.isBmpCodePoint(c) ? Letters.classic((char) c) : Letters.NONE));
		return List.copyOf(letters);
	}

	public static Item dyeItem(DyeColor color) {
		return BuiltInRegistries.ITEM.getOptional(Identifier.withDefaultNamespace(color.getSerializedName() + "_dye")).orElse(Items.AIR);
	}

	public static Optional<DyeColor> dyeOf(ItemStack stack) {
		for (DyeColor color : DyeColor.values()) {
			if (!stack.isEmpty() && stack.is(dyeItem(color))) {
				return Optional.of(color);
			}
		}
		return Optional.empty();
	}

	public Container dyeSamples() {
		return dyeSamples;
	}

	public void applySettings(String text, int repeat, FontSettings font) {
		this.font = font;
		this.text = cleanText(text, font.isClassic());
		this.repeat = Math.clamp(repeat, 1, MAX_REPEAT);
		setChanged();
	}

	public FontSettings font() {
		return font;
	}

	public TypewriterStatus status() {
		return status;
	}

	public String text() {
		return text;
	}

	/** Sets the line as a program does: cleaned for the current font, the rest of the settings kept. */
	public void setText(String text) {
		this.text = cleanText(text, font.isClassic());
		setChanged();
	}

	public int repeat() {
		return repeat;
	}

	public void setRepeat(int repeat) {
		this.repeat = Math.clamp(repeat, 1, MAX_REPEAT);
		setChanged();
	}

	public TypewriterScript.@Nullable Holder holder() {
		return holder;
	}

	public void setHolder(TypewriterScript.@Nullable Holder holder) {
		this.holder = holder;
	}

	/** True while a program holds the Typewriter; its screen then can't change it. A claim ends with its program. */
	public boolean isClaimed() {
		if (holder != null && !holder.isActive()) {
			holder = null;
		}
		return holder != null;
	}

	public boolean isRunning() {
		return running;
	}

	/** True while a picture job is not finished, also when it waits for items. */
	public boolean isPainting() {
		return running && pictureJob != null;
	}

	public @Nullable PictureJob pictureJob() {
		return pictureJob;
	}

	/** Stones made in the running or last job. */
	public int made() {
		return made;
	}

	/** Stones in the running or last job. */
	public int total() {
		return pictureJob != null ? pictureJob.stones() : jobLetters.size() * jobRepeat;
	}

	/**
	 * Starts the line for a program. A program can't bake letters, so with a font other than classic
	 * this only works for the line last started in the screen, whose letters are in the library.
	 */
	public void startFromProgram(LetterLibrary library) {
		if (font.isClassic()) {
			start(List.of(), library);
		} else if (text.equals(jobText) && !jobLetters.isEmpty()) {
			start(jobLetters, library);
		} else {
			status = text.isEmpty() ? TypewriterStatus.NO_TEXT : TypewriterStatus.MISSING_LETTERS;
		}
	}

	/**
	 * Starts typing a picture that is already in the library: one smooth stone and one RGB dye per
	 * stone. It starts also when items are missing; the job then waits for them.
	 */
	public void startPicture(Picture picture, PictureTile.Plane plane) {
		pictureJob = new PictureJob(picture.hash(), picture.cols(), picture.rows(), plane);
		made = 0;
		running = true;
		ticks = 0;
		status = TypewriterStatus.RUNNING;
		setChanged();
	}

	/** What a picture of that many stones takes, and what the inputs hold. */
	public Cost pictureCost(int stones) {
		if (level == null) {
			return new Cost(stones, stones, 0, 0);
		}
		List<Storage<ItemVariant>> inputs = inputs(level, worldPosition, getBlockState().getValue(TypewriterBlock.FACING).getOpposite());
		return new Cost(stones, stones, count(inputs, Items.SMOOTH_STONE), count(inputs, ModPictures.RGB_DYE));
	}

	private static long count(List<Storage<ItemVariant>> inputs, Item item) {
		long count = 0;
		for (Storage<ItemVariant> input : inputs) {
			for (StorageView<ItemVariant> view : input) {
				if (!view.isResourceBlank() && view.getResource().isOf(item)) {
					count += view.getAmount();
				}
			}
		}
		return count;
	}

	/** The letter hash of each character of the running or last job. */
	public List<String> jobLetters() {
		return jobLetters;
	}

	/**
	 * Starts the job. With a font other than classic, letters holds the hash of each character's
	 * letter; every one must be in the library, else nothing starts.
	 */
	public void start(List<String> letters, LetterLibrary library) {
		if (text.isEmpty()) {
			status = TypewriterStatus.NO_TEXT;
			return;
		}
		List<String> job = font.isClassic() ? classicLetters(text) : List.copyOf(letters);
		if (job.size() != text.codePointCount(0, text.length())
				|| job.stream().anyMatch(hash -> !hash.equals(Letters.NONE) && library.find(hash) == null)) {
			status = TypewriterStatus.MISSING_LETTERS;
			return;
		}
		boolean blank = job.stream().allMatch(Letters.NONE::equals);
		if (dyeOf(dyeSamples.getItem(SLOT_LETTER)).isEmpty() && !blank) {
			status = TypewriterStatus.NO_LETTER_DYE;
			return;
		}
		jobLetters = job;
		jobText = text;
		pictureJob = null;
		jobRepeat = repeat;
		made = 0;
		running = true;
		ticks = 0;
		status = TypewriterStatus.RUNNING;
		setChanged();
	}

	public void stop() {
		running = false;
		status = TypewriterStatus.IDLE;
		setChanged();
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, TypewriterBlockEntity typewriter) {
		if (!typewriter.running || ++typewriter.ticks < INTERVAL) {
			return;
		}
		typewriter.ticks = 0;
		typewriter.status = typewriter.makeOne(level, pos, state);
		if (typewriter.made >= typewriter.total()) {
			typewriter.running = false;
			typewriter.status = TypewriterStatus.DONE;
		}
		typewriter.setChanged();
	}

	/** Makes the next block of the job, all or nothing: materials are only used when the block fits. */
	private TypewriterStatus makeOne(Level level, BlockPos pos, BlockState state) {
		Direction back = state.getValue(TypewriterBlock.FACING).getOpposite();
		Storage<ItemVariant> output = ItemStorage.SIDED.find(level, pos.relative(back), back.getOpposite());
		if (output == null) {
			return TypewriterStatus.NO_OUTPUT;
		}
		List<Storage<ItemVariant>> inputs = inputs(level, pos, back);
		if (pictureJob != null) {
			return makePictureStone(output, inputs);
		}

		String glyph = jobLetters.get(made % jobLetters.size());
		boolean inked = !glyph.equals(Letters.NONE);
		Optional<DyeColor> letter = dyeOf(dyeSamples.getItem(SLOT_LETTER));
		Optional<DyeColor> background = dyeOf(dyeSamples.getItem(SLOT_BACKGROUND));
		if (inked && letter.isEmpty()) {
			return TypewriterStatus.NO_LETTER_DYE;
		}
		Dyes dyes = new Dyes(letter.orElse(DyeColor.BLACK), background);
		TypestoneItem item = ModBlocks.TYPESTONE_ITEM;

		try (Transaction transaction = Transaction.openOuter()) {
			if (missing(inputs, Items.SMOOTH_STONE, transaction)) {
				return TypewriterStatus.NEEDS_STONE;
			}
			if (inked && missing(inputs, dyeItem(letter.get()), transaction)) {
				return TypewriterStatus.NEEDS_LETTER_DYE;
			}
			if (background.isPresent() && missing(inputs, dyeItem(background.get()), transaction)) {
				return TypewriterStatus.NEEDS_BACKGROUND_DYE;
			}
			if (output.insert(ItemVariant.of(item.stack(glyph, dyes, 1)), 1, transaction) != 1) {
				return TypewriterStatus.OUTPUT_FULL;
			}
			transaction.commit();
		}
		made++;
		return TypewriterStatus.RUNNING;
	}

	/** The next stone of the picture job: one smooth stone and one RGB dye, all or nothing. */
	private TypewriterStatus makePictureStone(Storage<ItemVariant> output, List<Storage<ItemVariant>> inputs) {
		ItemStack stone = ModBlocks.TYPESTONE_ITEM.pictureStack(pictureJob.tile(made));
		try (Transaction transaction = Transaction.openOuter()) {
			if (missing(inputs, Items.SMOOTH_STONE, transaction)) {
				return TypewriterStatus.NEEDS_STONE;
			}
			if (missing(inputs, ModPictures.RGB_DYE, transaction)) {
				return TypewriterStatus.NEEDS_RGB_DYE;
			}
			if (output.insert(ItemVariant.of(stone), 1, transaction) != 1) {
				return TypewriterStatus.OUTPUT_FULL;
			}
			transaction.commit();
		}
		made++;
		return TypewriterStatus.RUNNING;
	}

	/** The inventory at the back, where the stones go; null when there is none. */
	public @Nullable Storage<ItemVariant> output() {
		if (level == null) {
			return null;
		}
		Direction back = getBlockState().getValue(TypewriterBlock.FACING).getOpposite();
		return ItemStorage.SIDED.find(level, worldPosition.relative(back), back.getOpposite());
	}

	/** The inventories on every side but the back. */
	private static List<Storage<ItemVariant>> inputs(Level level, BlockPos pos, Direction back) {
		List<Storage<ItemVariant>> inputs = new ArrayList<>();
		for (Direction side : Direction.values()) {
			if (side != back) {
				Storage<ItemVariant> input = ItemStorage.SIDED.find(level, pos.relative(side), side.getOpposite());
				if (input != null) {
					inputs.add(input);
				}
			}
		}
		return inputs;
	}

	/** Most pictures the screen offers a pencil for. */
	public static final int MAX_PENCIL_PICTURES = 8;

	/** The pictures with stones in the output, in the order they are found there, one tile of each. */
	public List<PictureTile> outputPictures() {
		Storage<ItemVariant> output = output();
		if (output == null) {
			return List.of();
		}
		Map<String, PictureTile> found = new LinkedHashMap<>();
		for (StorageView<ItemVariant> view : output.nonEmptyViews()) {
			PictureTile tile = view.getResource().toStack().get(ModComponents.PICTURE);
			if (tile != null) {
				found.putIfAbsent(tile.hash(), tile);
				if (found.size() >= MAX_PENCIL_PICTURES) {
					break;
				}
			}
		}
		return List.copyOf(found.values());
	}

	/**
	 * What the Pencil button offers: the last job first (a picture, or a line with its text), then the
	 * other pictures with stones in the output. A line is the choice with an empty hash.
	 */
	public List<TypewriterActionPayload.PencilChoice> pencilChoices() {
		List<TypewriterActionPayload.PencilChoice> choices = new ArrayList<>();
		if (pictureJob != null) {
			choices.add(new TypewriterActionPayload.PencilChoice(pictureJob.hash(), "", pictureJob.cols(), pictureJob.rows()));
		} else if (!jobLetters.isEmpty()) {
			choices.add(new TypewriterActionPayload.PencilChoice("", jobText, jobLetters.size(), 1));
		}
		for (PictureTile tile : outputPictures()) {
			if (pictureJob == null || !tile.hash().equals(pictureJob.hash())) {
				choices.add(new TypewriterActionPayload.PencilChoice(tile.hash(), "", tile.cols(), tile.rows()));
			}
		}
		return choices;
	}

	/** A pencil for the last job. */
	public ItemStack makePencil() {
		return makePencil("");
	}

	/**
	 * Makes a pencil from a stick and a smooth stone of the inputs, or refills an empty pencil found there instead: for the picture with this hash whose
	 * stones are in the output, or with an empty hash for the last job, a line in the current dyes or a
	 * picture. A picture pencil lays its stones row by row. Empty when that fails; the status then says why.
	 */
	public ItemStack makePencil(String pictureHash) {
		PictureJob picture = pictureJob;
		if (!pictureHash.isEmpty()) {
			picture = outputPictures().stream().filter(tile -> tile.hash().equals(pictureHash)).findFirst().map(PictureJob::of).orElse(null);
			if (picture == null) {
				status = TypewriterStatus.PICTURE_GONE;
				return ItemStack.EMPTY;
			}
		}
		if (level == null || jobLetters.isEmpty() && picture == null) {
			status = TypewriterStatus.NO_LINE;
			return ItemStack.EMPTY;
		}
		Direction back = getBlockState().getValue(TypewriterBlock.FACING).getOpposite();
		List<Storage<ItemVariant>> inputs = inputs(level, worldPosition, back);
		ItemStack refilled;
		try (Transaction transaction = Transaction.openOuter()) {
			refilled = takeEmptyPencil(inputs, transaction);
			if (refilled.isEmpty()) {
				if (missing(inputs, Items.STICK, transaction)) {
					status = TypewriterStatus.NEEDS_STICK;
					return ItemStack.EMPTY;
				}
				if (missing(inputs, Items.SMOOTH_STONE, transaction)) {
					status = TypewriterStatus.NEEDS_STONE;
					return ItemStack.EMPTY;
				}
			}
			transaction.commit();
		}
		ItemStack pencil;
		if (picture != null) {
			pencil = ModPencil.picturePencil(GlobalPos.of(level.dimension(), worldPosition), picture);
		} else {
			Dyes dyes = new Dyes(dyeOf(dyeSamples.getItem(SLOT_LETTER)).orElse(DyeColor.BLACK), dyeOf(dyeSamples.getItem(SLOT_BACKGROUND)));
			pencil = ModPencil.pencil(GlobalPos.of(level.dimension(), worldPosition), jobLetters, dyes);
		}
		// A refilled pencil keeps the name it got on an anvil.
		Component name = refilled.get(DataComponents.CUSTOM_NAME);
		if (name != null) {
			pencil.set(DataComponents.CUSTOM_NAME, name);
		}
		return pencil;
	}

	/** Takes one empty pencil (its line written) from the inputs; empty when there is none. */
	private static ItemStack takeEmptyPencil(List<Storage<ItemVariant>> inputs, Transaction transaction) {
		for (Storage<ItemVariant> input : inputs) {
			ItemVariant empty = null;
			for (StorageView<ItemVariant> view : input.nonEmptyViews()) {
				ItemStack stack = view.getResource().toStack();
				if (stack.is(ModPencil.ITEM) && !stack.has(ModComponents.PENCIL_LINE)) {
					empty = view.getResource();
					break;
				}
			}
			if (empty != null && input.extract(empty, 1, transaction) == 1) {
				return empty.toStack();
			}
		}
		return ItemStack.EMPTY;
	}

	/** Takes one item from the first input that has it; true when none has it. */
	private static boolean missing(List<Storage<ItemVariant>> inputs, Item item, Transaction transaction) {
		ItemVariant variant = ItemVariant.of(item);
		for (Storage<ItemVariant> input : inputs) {
			if (input.extract(variant, 1, transaction) == 1) {
				return false;
			}
		}
		return true;
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		font = new FontSettings(input.getStringOr("font", FontSettings.CLASSIC), input.getIntOr("letter_size", FontSettings.DEFAULT.size()),
				input.getIntOr("scale", 100), input.getIntOr("baseline", 0), input.getIntOr("font_style", FontSettings.REGULAR));
		text = cleanText(input.getStringOr("text", ""), font.isClassic());
		repeat = Math.clamp(input.getIntOr("repeat", 1), 1, MAX_REPEAT);
		// Typewriters from before fonts saved the job as classic text.
		jobLetters = input.read("job_letters", LETTERS_CODEC)
				.map(saved -> List.copyOf(saved.subList(0, Math.min(saved.size(), MAX_TEXT_LENGTH))))
				.orElseGet(() -> classicLetters(cleanText(input.getStringOr("job_text", ""), true)));
		jobText = input.getStringOr("job_text_line", "");
		pictureJob = input.read("job_picture", PictureJob.CODEC).orElse(null);
		jobRepeat = Math.clamp(input.getIntOr("job_repeat", 1), 1, MAX_REPEAT);
		made = Math.max(0, input.getIntOr("made", 0));
		running = input.getBooleanOr("running", false) && (!jobLetters.isEmpty() || pictureJob != null);
		status = running ? TypewriterStatus.RUNNING : TypewriterStatus.IDLE;
		dyeSamples.setItem(SLOT_LETTER, input.read("letter_dye", DyeColor.CODEC).map(c -> new ItemStack(dyeItem(c))).orElse(ItemStack.EMPTY));
		dyeSamples.setItem(SLOT_BACKGROUND, input.read("background_dye", DyeColor.CODEC).map(c -> new ItemStack(dyeItem(c))).orElse(ItemStack.EMPTY));
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putString("text", text);
		output.putInt("repeat", repeat);
		output.putString("font", font.font());
		output.putInt("letter_size", font.size());
		output.putInt("scale", font.scale());
		output.putInt("baseline", font.baseline());
		output.putInt("font_style", font.style());
		output.store("job_letters", LETTERS_CODEC, jobLetters);
		output.putString("job_text_line", jobText);
		if (pictureJob != null) {
			output.store("job_picture", PictureJob.CODEC, pictureJob);
		}
		output.putInt("job_repeat", jobRepeat);
		output.putInt("made", made);
		output.putBoolean("running", running);
		dyeOf(dyeSamples.getItem(SLOT_LETTER)).ifPresent(c -> output.store("letter_dye", DyeColor.CODEC, c));
		dyeOf(dyeSamples.getItem(SLOT_BACKGROUND)).ifPresent(c -> output.store("background_dye", DyeColor.CODEC, c));
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.typestones.typewriter");
	}

	@Override
	public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
		return new TypewriterMenu(containerId, inventory, dyeSamples, data, this);
	}

	@Override
	public TypewriterMenu.OpenData getScreenOpeningData(ServerPlayer player) {
		return new TypewriterMenu.OpenData(worldPosition, text, font);
	}
}
