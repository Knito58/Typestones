package de.knito.typestones.typewriter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
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
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;

import de.knito.typestones.ModBlocks;
import de.knito.typestones.ModTypewriter;
import de.knito.typestones.block.Dyes;
import de.knito.typestones.block.TypestoneItem;
import de.knito.typestones.letter.LetterLibrary;
import de.knito.typestones.letter.Letters;

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

	private String text = "";
	private int repeat = 1;
	private FontSettings font = FontSettings.DEFAULT;
	/** The job: one letter hash per character and the repeat as they were at Start, and how many blocks are made. */
	private List<String> jobLetters = List.of();
	private int jobRepeat = 1;
	private int made;
	private boolean running;
	private int ticks;
	private TypewriterStatus status = TypewriterStatus.IDLE;

	/** Synced to the open screen: 0 = status, 1 = blocks made, 2 = blocks in the job, 3 = repeat. */
	private final ContainerData data = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case 0 -> status.ordinal();
				case 1 -> made;
				case 2 -> jobLetters.size() * jobRepeat;
				case 3 -> repeat;
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
		}

		@Override
		public int getCount() {
			return 4;
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
		if (typewriter.made >= typewriter.jobLetters.size() * typewriter.jobRepeat) {
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
		List<Storage<ItemVariant>> inputs = new ArrayList<>();
		for (Direction side : Direction.values()) {
			if (side != back) {
				Storage<ItemVariant> input = ItemStorage.SIDED.find(level, pos.relative(side), side.getOpposite());
				if (input != null) {
					inputs.add(input);
				}
			}
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
		font = new FontSettings(input.getStringOr("font", FontSettings.CLASSIC), input.getIntOr("letter_size", 32),
				input.getIntOr("scale", 100), input.getIntOr("baseline", 0));
		text = cleanText(input.getStringOr("text", ""), font.isClassic());
		repeat = Math.clamp(input.getIntOr("repeat", 1), 1, MAX_REPEAT);
		// Typewriters from before fonts saved the job as classic text.
		jobLetters = input.read("job_letters", LETTERS_CODEC)
				.map(saved -> List.copyOf(saved.subList(0, Math.min(saved.size(), MAX_TEXT_LENGTH))))
				.orElseGet(() -> classicLetters(cleanText(input.getStringOr("job_text", ""), true)));
		jobRepeat = Math.clamp(input.getIntOr("job_repeat", 1), 1, MAX_REPEAT);
		made = Math.max(0, input.getIntOr("made", 0));
		running = input.getBooleanOr("running", false) && !jobLetters.isEmpty();
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
		output.store("job_letters", LETTERS_CODEC, jobLetters);
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
