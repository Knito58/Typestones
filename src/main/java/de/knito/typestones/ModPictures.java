package de.knito.typestones;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.imageio.ImageIO;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

import de.knito.typestones.network.PicturePayloads;
import de.knito.typestones.picture.Picture;
import de.knito.typestones.picture.PictureInfo;
import de.knito.typestones.picture.PictureLibrary;
import de.knito.typestones.picture.PictureTile;

/**
 * Pictures on Typestones: the RGB dye they cost, the server side of the picture library (answers
 * players' requests), and the operator commands to try pictures out until the Lua box can type them.
 */
public class ModPictures {
	private static final ResourceKey<Item> RGB_DYE_KEY = ResourceKey.create(Registries.ITEM, Typestones.id("rgb_dye"));

	/** One per picture stone; eight are crafted from black, white, red, green and blue dye. */
	public static final Item RGB_DYE = Registry.register(BuiltInRegistries.ITEM, RGB_DYE_KEY,
			new Item(new Item.Properties().setId(RGB_DYE_KEY)));

	public static final int REQUESTS_PER_MINUTE = 256;

	private static final int MINUTE = 20 * 60;

	/** Pictures asked for by a player in the current minute: tick the minute started, and count. */
	private static final Map<UUID, int[]> USAGE = new HashMap<>();

	/** Mod ids of the mods whose programs can type pictures; without one, pictures and their dye stay hidden. */
	public static final List<String> SCRIPTING_MODS = List.of("billboard_api", "computercraft");

	public static boolean scriptingAvailable() {
		return SCRIPTING_MODS.stream().anyMatch(FabricLoader.getInstance()::isModLoaded);
	}

	public static void initialize() {
		if (scriptingAvailable()) {
			CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(tab -> tab.accept(RGB_DYE));
		}

		PayloadTypeRegistry.serverboundPlay().register(PicturePayloads.Request.TYPE, PicturePayloads.Request.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PicturePayloads.Part.TYPE, PicturePayloads.Part.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(PicturePayloads.Request.TYPE,
				(payload, context) -> answer(context.player(), payload.hashes()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> USAGE.remove(handler.getPlayer().getUUID()));

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommands(dispatcher));
	}

	/** Sends the pictures the player asked for, each in parts. */
	static void answer(ServerPlayer player, List<String> hashes) {
		MinecraftServer server = player.level().getServer();
		int tick = server.getTickCount();
		int[] usage = USAGE.computeIfAbsent(player.getUUID(), id -> new int[] {tick, 0});
		if (tick - usage[0] >= MINUTE) {
			usage[0] = tick;
			usage[1] = 0;
		}
		PictureLibrary library = PictureLibrary.get(server);
		Path folder = PictureLibrary.folder(server);
		for (String hash : new LinkedHashSet<>(hashes)) {
			if (usage[1] >= REQUESTS_PER_MINUTE) {
				Typestones.LOGGER.warn("{} asked for more than {} pictures in a minute", player.getPlainTextName(), REQUESTS_PER_MINUTE);
				return;
			}
			usage[1]++;
			byte[] png = library.png(hash, folder);
			if (png == null) {
				continue;
			}
			int count = Math.max(1, (png.length + PicturePayloads.PART_SIZE - 1) / PicturePayloads.PART_SIZE);
			for (int i = 0; i < count; i++) {
				byte[] part = Arrays.copyOfRange(png, i * PicturePayloads.PART_SIZE, Math.min(png.length, (i + 1) * PicturePayloads.PART_SIZE));
				ServerPlayNetworking.send(player, new PicturePayloads.Part(hash, i, count, part));
			}
		}
	}

	/** The Typestones that carry the picture, one per tile, row by row from the top left. */
	public static List<ItemStack> stones(Picture picture) {
		ItemStack[] stones = new ItemStack[picture.cols() * picture.rows()];
		for (int row = 0; row < picture.rows(); row++) {
			for (int col = 0; col < picture.cols(); col++) {
				stones[row * picture.cols() + col] = ModBlocks.TYPESTONE_ITEM.pictureStack(PictureTile.of(picture, col, row));
			}
		}
		return List.of(stones);
	}

	/**
	 * A picture to check stones with: colors running across, a ring over all stones, and a big F that
	 * shows at once when a side is upside down or mirrored.
	 */
	public static Picture testPicture(int cols, int rows) {
		int width = cols * Picture.PIXELS;
		int height = rows * Picture.PIXELS;
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				image.setRGB(x, y, Color.HSBtoRGB((float) x / width, 0.6f, 1f - 0.5f * y / height));
			}
		}
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int size = Math.min(width, height);
		g.setColor(Color.WHITE);
		g.setStroke(new BasicStroke(Math.max(2, size / 24f)));
		g.drawOval(size / 12, size / 12, width - size / 6, height - size / 6);
		int bar = Math.max(2, size / 10);
		int left = width / 2 - size / 5;
		int top = height / 2 - size / 3;
		g.setColor(Color.BLACK);
		g.fillRect(left, top, bar, 2 * size / 3);
		g.fillRect(left, top, size / 2, bar);
		g.fillRect(left, top + size / 3 - bar / 2, size / 3, bar);
		g.dispose();
		return Picture.fit(image, cols, rows);
	}

	private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("typestones")
				.then(Commands.literal("pictures")
						.executes(ModPictures::count))
				.then(Commands.literal("picture")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("test")
								.then(Commands.argument("cols", IntegerArgumentType.integer(1, Picture.MAX_STONES))
										.then(Commands.argument("rows", IntegerArgumentType.integer(1, Picture.MAX_STONES))
												.executes(context -> give(context, testPicture(
														IntegerArgumentType.getInteger(context, "cols"),
														IntegerArgumentType.getInteger(context, "rows")), "test")))))
						.then(Commands.literal("load")
								.then(Commands.argument("file", StringArgumentType.string())
										.then(Commands.argument("cols", IntegerArgumentType.integer(1, Picture.MAX_STONES))
												.then(Commands.argument("rows", IntegerArgumentType.integer(1, Picture.MAX_STONES))
														.executes(ModPictures::load)))))
						.then(Commands.literal("remove")
								.then(Commands.argument("hash", StringArgumentType.word())
										.executes(ModPictures::remove)))));
	}

	private static int count(CommandContext<CommandSourceStack> context) {
		int size = PictureLibrary.get(context.getSource().getServer()).size();
		context.getSource().sendSuccess(() -> Component.translatable("commands.typestones.pictures", size, PictureLibrary.MAX_PICTURES), false);
		return size;
	}

	/** Reads a PNG or JPEG from the world's data/typestones/import folder. */
	private static int load(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		String name = StringArgumentType.getString(context, "file");
		Path folder = PictureLibrary.importFolder(context.getSource().getServer()).toAbsolutePath().normalize();
		Path file = folder.resolve(name).normalize();
		BufferedImage image = null;
		try {
			if (file.startsWith(folder) && Files.isRegularFile(file)) {
				image = ImageIO.read(file.toFile());
			}
		} catch (IOException e) {
			Typestones.LOGGER.warn("Can't read {}: {}", file, e.toString());
		}
		if (image == null) {
			context.getSource().sendFailure(Component.translatable("commands.typestones.picture.no_file", name, folder.toString()));
			return 0;
		}
		Picture picture = Picture.fit(image, IntegerArgumentType.getInteger(context, "cols"), IntegerArgumentType.getInteger(context, "rows"));
		return give(context, picture, name);
	}

	/** Adds the picture to the library and gives its stones to the player; what doesn't fit drops. */
	private static int give(CommandContext<CommandSourceStack> context, Picture picture, String name) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		MinecraftServer server = context.getSource().getServer();
		PictureLibrary library = PictureLibrary.get(server);
		try {
			PictureInfo info = PictureInfo.of(picture, name, player.getPlainTextName(), LocalDate.now().toString());
			if (library.add(picture, info, PictureLibrary.folder(server)) == PictureLibrary.Added.FULL) {
				context.getSource().sendFailure(Component.translatable("message.typestones.picture_library_full", PictureLibrary.MAX_PICTURES));
				return 0;
			}
		} catch (IOException e) {
			Typestones.LOGGER.error("Can't save picture {}", picture.hash(), e);
			context.getSource().sendFailure(Component.translatable("commands.typestones.picture.not_saved", e.getMessage()));
			return 0;
		}
		for (ItemStack stone : stones(picture)) {
			if (!player.getInventory().add(stone)) {
				Block.popResource(player.level(), player.blockPosition(), stone);
			}
		}
		context.getSource().sendSuccess(() -> Component.translatable("commands.typestones.picture.given",
				picture.cols(), picture.rows(), picture.hash()), false);
		return picture.cols() * picture.rows();
	}

	private static int remove(CommandContext<CommandSourceStack> context) {
		String hash = StringArgumentType.getString(context, "hash");
		MinecraftServer server = context.getSource().getServer();
		if (!PictureLibrary.get(server).remove(hash, PictureLibrary.folder(server))) {
			context.getSource().sendFailure(Component.translatable("commands.typestones.picture.unknown", hash));
			return 0;
		}
		context.getSource().sendSuccess(() -> Component.translatable("commands.typestones.picture.removed", hash), true);
		return 1;
	}
}
