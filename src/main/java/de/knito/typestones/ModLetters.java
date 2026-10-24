package de.knito.typestones;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import org.jspecify.annotations.Nullable;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import de.knito.typestones.block.TypestoneItem;
import de.knito.typestones.letter.Letter;
import de.knito.typestones.letter.LetterInfo;
import de.knito.typestones.letter.LetterLibrary;
import de.knito.typestones.letter.Letters;
import de.knito.typestones.letter.LibraryLetter;
import de.knito.typestones.network.LetterPayloads;

/**
 * The server side of the letter library: answers players' requests for letters, takes newly baked
 * letters with limits per player, and the operator commands under /typestones.
 */
public class ModLetters {
	/** Letter sizes a player may upload. */
	public static final Set<Integer> UPLOAD_SIZES = Set.of(16, 32, 64);
	public static final int REQUESTS_PER_MINUTE = 8192;
	public static final int UPLOADS_PER_MINUTE = 256;

	private static final int MINUTE = 20 * 60;

	/** Letters asked for and uploaded by a player in the current minute. */
	private static final class Usage {
		int minuteStart;
		int requested;
		int uploaded;

		Usage(int tick) {
			minuteStart = tick;
		}

		void roll(int tick) {
			if (tick - minuteStart >= MINUTE) {
				minuteStart = tick;
				requested = 0;
				uploaded = 0;
			}
		}
	}

	private static final Map<UUID, Usage> USAGE = new HashMap<>();

	public static void initialize() {
		PayloadTypeRegistry.serverboundPlay().register(LetterPayloads.Request.TYPE, LetterPayloads.Request.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(LetterPayloads.Upload.TYPE, LetterPayloads.Upload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(LetterPayloads.Answer.TYPE, LetterPayloads.Answer.STREAM_CODEC);

		ServerPlayNetworking.registerGlobalReceiver(LetterPayloads.Request.TYPE,
				(payload, context) -> answer(context.player(), payload.hashes()));
		ServerPlayNetworking.registerGlobalReceiver(LetterPayloads.Upload.TYPE,
				(payload, context) -> upload(context.player(), payload.letters()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> USAGE.remove(handler.getPlayer().getUUID()));

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommands(dispatcher));
	}

	private static Usage usage(ServerPlayer player) {
		int tick = player.level().getServer().getTickCount();
		Usage usage = USAGE.computeIfAbsent(player.getUUID(), id -> new Usage(tick));
		usage.roll(tick);
		return usage;
	}

	/** Sends the letters of the library the player asked for; built-in ones the player has already. */
	static void answer(ServerPlayer player, List<String> hashes) {
		Usage usage = usage(player);
		LetterLibrary library = LetterLibrary.get(player.level().getServer());
		List<LibraryLetter> batch = new ArrayList<>();
		for (String hash : new LinkedHashSet<>(hashes)) {
			if (usage.requested >= REQUESTS_PER_MINUTE) {
				Typestones.LOGGER.warn("{} asked for more than {} letters in a minute", player.getPlainTextName(), REQUESTS_PER_MINUTE);
				break;
			}
			usage.requested++;
			LibraryLetter letter = Letters.builtIn(hash) == null ? library.find(hash) : null;
			if (letter != null) {
				batch.add(letter);
				if (batch.size() == LetterPayloads.MAX_ANSWER) {
					ServerPlayNetworking.send(player, new LetterPayloads.Answer(List.copyOf(batch)));
					batch.clear();
				}
			}
		}
		if (!batch.isEmpty()) {
			ServerPlayNetworking.send(player, new LetterPayloads.Answer(batch));
		}
		Typestones.LOGGER.info("{} asked for {} letters", player.getPlainTextName(), hashes.size());
	}

	/** Adds a player's new baked letters to the library, with the player as baker and today as date. */
	static void upload(ServerPlayer player, List<LibraryLetter> letters) {
		Usage usage = usage(player);
		LetterLibrary library = LetterLibrary.get(player.level().getServer());
		for (LibraryLetter sent : letters) {
			// A Typewriter sends all letters of its text on each Start; known ones cost nothing.
			if (library.find(sent.hash()) != null) {
				continue;
			}
			if (usage.uploaded >= UPLOADS_PER_MINUTE) {
				Typestones.LOGGER.warn("{} uploaded more than {} letters in a minute", player.getPlainTextName(), UPLOADS_PER_MINUTE);
				return;
			}
			usage.uploaded++;
			LibraryLetter letter = checked(sent, player.getPlainTextName(), LocalDate.now().toString());
			if (letter == null) {
				Typestones.LOGGER.warn("{} uploaded a malformed letter", player.getPlainTextName());
				continue;
			}
			if (library.add(letter) == LetterLibrary.Added.FULL) {
				player.sendSystemMessage(Component.translatable("message.typestones.library_full", LetterLibrary.MAX_LETTERS));
				return;
			}
		}
	}

	/** The letter as the library stores it, or null when a modified client sent nonsense. */
	public static @Nullable LibraryLetter checked(LibraryLetter sent, String baker, String date) {
		Letter letter = sent.letter();
		LetterInfo info = sent.info();
		if (!UPLOAD_SIZES.contains(letter.size())
				|| !LetterInfo.isValidText(info.text())
				|| info.font().length() > LetterInfo.MAX_FONT
				|| !info.locale().matches("[a-z0-9_]{0," + LetterInfo.MAX_LOCALE + "}")) {
			return null;
		}
		return new LibraryLetter(letter, new LetterInfo(info.text(), info.font(), info.locale(), baker, date));
	}

	private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("typestones")
				.then(Commands.literal("letters")
						.executes(ModLetters::count))
				.then(Commands.literal("letter")
						.executes(ModLetters::infoOfHeld)
						.then(Commands.argument("hash", StringArgumentType.word())
								.executes(context -> info(context, StringArgumentType.getString(context, "hash")))
								.then(Commands.literal("remove")
										.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
										.executes(ModLetters::remove)))));
	}

	private static int count(CommandContext<CommandSourceStack> context) {
		int size = LetterLibrary.get(context.getSource().getServer()).size();
		context.getSource().sendSuccess(() -> Component.translatable("commands.typestones.letters", size, LetterLibrary.MAX_LETTERS), false);
		return size;
	}

	/** The letter of the Typestone in the player's main hand. */
	private static int infoOfHeld(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ItemStack held = context.getSource().getPlayerOrException().getMainHandItem();
		String hash = TypestoneItem.letter(held);
		if (!(held.getItem() instanceof TypestoneItem) || hash.equals(Letters.NONE)) {
			context.getSource().sendFailure(Component.translatable("commands.typestones.letter.hold"));
			return 0;
		}
		return info(context, hash);
	}

	private static int info(CommandContext<CommandSourceStack> context, String hash) {
		MinecraftServer server = context.getSource().getServer();
		LibraryLetter letter = LetterLibrary.get(server).find(hash);
		if (letter == null) {
			context.getSource().sendFailure(Component.translatable("commands.typestones.letter.unknown", hash));
			return 0;
		}
		LetterInfo info = letter.info();
		if (Letters.builtIn(hash) != null) {
			context.getSource().sendSuccess(() -> Component.translatable("commands.typestones.letter.info_built_in",
					info.text(), info.unicodeName(), info.script(), info.font(), letter.letter().size(), hash), false);
			return 1;
		}
		context.getSource().sendSuccess(() -> Component.translatable("commands.typestones.letter.info",
				info.text(), info.unicodeName(), info.script(), info.font(), letter.letter().size(),
				info.locale(), info.baker(), info.baked(), hash), false);
		return 1;
	}

	private static int remove(CommandContext<CommandSourceStack> context) {
		String hash = StringArgumentType.getString(context, "hash");
		if (!LetterLibrary.get(context.getSource().getServer()).remove(hash)) {
			context.getSource().sendFailure(Component.translatable("commands.typestones.letter.not_removable", hash));
			return 0;
		}
		context.getSource().sendSuccess(() -> Component.translatable("commands.typestones.letter.removed", hash), true);
		return 1;
	}
}
