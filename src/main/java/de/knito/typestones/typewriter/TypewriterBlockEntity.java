package de.knito.typestones.typewriter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
import de.knito.typestones.letter.Letters;

/**
 * Makes one Typestone per character of a line of text, every {@link #INTERVAL} ticks. Takes smooth
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

	private String text = "";
	private int repeat = 1;
	/** The job: text and repeat as they were when Start was pressed, and how many blocks are made. */
	private String jobText = "";
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
				case 2 -> jobText.length() * jobRepeat;
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

	/** Keeps only characters a Typestone can show. */
	public static String cleanText(String text) {
		StringBuilder clean = new StringBuilder();
		for (char c : text.toCharArray()) {
			if (Letters.hasClassic(c) && clean.length() < MAX_TEXT_LENGTH) {
				clean.append(c);
			}
		}
		return clean.toString();
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

	public void applySettings(String text, int repeat) {
		this.text = cleanText(text);
		this.repeat = Math.clamp(repeat, 1, MAX_REPEAT);
		setChanged();
	}

	public void start() {
		if (text.isEmpty()) {
			status = TypewriterStatus.NO_TEXT;
			return;
		}
		if (dyeOf(dyeSamples.getItem(SLOT_LETTER)).isEmpty() && !text.isBlank()) {
			status = TypewriterStatus.NO_LETTER_DYE;
			return;
		}
		jobText = text;
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
		if (typewriter.made >= typewriter.jobText.length() * typewriter.jobRepeat) {
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

		char c = jobText.charAt(made % jobText.length());
		String glyph = Letters.classic(c);
		Optional<DyeColor> letter = dyeOf(dyeSamples.getItem(SLOT_LETTER));
		Optional<DyeColor> background = dyeOf(dyeSamples.getItem(SLOT_BACKGROUND));
		if (c != ' ' && letter.isEmpty()) {
			return TypewriterStatus.NO_LETTER_DYE;
		}
		Dyes dyes = new Dyes(letter.orElse(DyeColor.BLACK), background);
		TypestoneItem item = ModBlocks.TYPESTONE_ITEM;

		try (Transaction transaction = Transaction.openOuter()) {
			if (missing(inputs, Items.SMOOTH_STONE, transaction)) {
				return TypewriterStatus.NEEDS_STONE;
			}
			if (c != ' ' && missing(inputs, dyeItem(letter.get()), transaction)) {
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
		text = cleanText(input.getStringOr("text", ""));
		repeat = Math.clamp(input.getIntOr("repeat", 1), 1, MAX_REPEAT);
		jobText = cleanText(input.getStringOr("job_text", ""));
		jobRepeat = Math.clamp(input.getIntOr("job_repeat", 1), 1, MAX_REPEAT);
		made = Math.max(0, input.getIntOr("made", 0));
		running = input.getBooleanOr("running", false) && !jobText.isEmpty();
		status = running ? TypewriterStatus.RUNNING : TypewriterStatus.IDLE;
		dyeSamples.setItem(SLOT_LETTER, input.read("letter_dye", DyeColor.CODEC).map(c -> new ItemStack(dyeItem(c))).orElse(ItemStack.EMPTY));
		dyeSamples.setItem(SLOT_BACKGROUND, input.read("background_dye", DyeColor.CODEC).map(c -> new ItemStack(dyeItem(c))).orElse(ItemStack.EMPTY));
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putString("text", text);
		output.putInt("repeat", repeat);
		output.putString("job_text", jobText);
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
		return new TypewriterMenu.OpenData(worldPosition, text);
	}
}
