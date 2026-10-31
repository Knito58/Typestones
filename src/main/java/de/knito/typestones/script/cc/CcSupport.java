package de.knito.typestones.script.cc;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.Collections;

import org.jspecify.annotations.Nullable;

import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IDynamicPeripheral;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.PeripheralLookup;

import de.knito.typestones.ModTypewriter;
import de.knito.typestones.TypestonesConfig;
import de.knito.typestones.picture.Picture;
import de.knito.typestones.script.TypewriterScript;
import de.knito.typestones.typewriter.TypewriterBlockEntity;

/**
 * The Typewriter as a CC: Tweaked peripheral, with the same functions as for the Lua Box. Only loaded
 * when CC: Tweaked is installed. A program finds it with {@code peripheral.find("typewriter")};
 * {@code drawImage} takes the image's bytes, read from a file in binary mode or from {@code http.get}.
 */
public final class CcSupport {
	public static final String TYPE = "typewriter";

	private static final List<String> METHODS = List.of("setText", "getText", "go", "stop", "isRunning", "isPainting", "getStatus",
			"getProgress", "setRepeat", "getRepeat", "getCost", "drawImage", "claim", "release", "isClaimed");
	/** Functions that change the Typewriter and are refused while another computer holds it. */
	private static final Set<String> CHANGING = Set.of("setText", "go", "stop", "setRepeat", "drawImage");

	/** Computers attached to some Typewriter right now; a claim ends when its computer goes. */
	private static final Set<IComputerAccess> ATTACHED = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

	private CcSupport() {
	}

	public static void register() {
		PeripheralLookup.get().registerForBlockEntity((typewriter, side) -> new Peripheral(typewriter), ModTypewriter.ENTITY);
	}

	record ComputerHolder(IComputerAccess computer) implements TypewriterScript.Holder {
		@Override
		public boolean isActive() {
			return ATTACHED.contains(computer);
		}

		@Override
		public String describe() {
			return "computer " + computer.getID();
		}
	}

	private record Peripheral(TypewriterBlockEntity typewriter) implements IDynamicPeripheral {
		@Override
		public String getType() {
			return TYPE;
		}

		@Override
		public String[] getMethodNames() {
			return METHODS.toArray(String[]::new);
		}

		@Override
		public void attach(IComputerAccess computer) {
			ATTACHED.add(computer);
		}

		@Override
		public void detach(IComputerAccess computer) {
			ATTACHED.remove(computer);
		}

		@Override
		public boolean equals(@Nullable IPeripheral other) {
			return other instanceof Peripheral peripheral && peripheral.typewriter == typewriter;
		}

		@Override
		public @Nullable Object getTarget() {
			return typewriter;
		}

		/** Called on the computer's thread: everything that touches the world goes to the server thread. */
		@Override
		public MethodResult callMethod(IComputerAccess computer, ILuaContext context, int method, IArguments arguments) throws LuaException {
			String name = METHODS.get(method);
			ComputerHolder holder = new ComputerHolder(computer);
			TypewriterScript script = new TypewriterScript(typewriter);
			if (name.equals("drawImage")) {
				return drawImage(script, holder, context, arguments);
			}
			// The arguments are only valid during this call; take what is needed now.
			Object[] values = arguments.getAll();
			return context.executeMainThreadTask(() -> lua(() -> {
				if (CHANGING.contains(name)) {
					script.checkMayChange(holder);
				}
				return call(script, holder, name, values);
			}));
		}

		private static Object[] call(TypewriterScript script, ComputerHolder holder, String name, Object[] args) {
			return switch (name) {
				case "setText" -> script.setText(string(args, 0));
				case "getText" -> new Object[] {script.getText()};
				case "go" -> script.go();
				case "stop" -> script.stop();
				case "isRunning" -> new Object[] {script.isRunning()};
				case "isPainting" -> new Object[] {script.isPainting()};
				case "getStatus" -> new Object[] {script.getStatus()};
				case "getProgress" -> script.getProgress();
				case "setRepeat" -> script.setRepeat(whole(args, 0));
				case "getRepeat" -> new Object[] {script.getRepeat()};
				case "getCost" -> new Object[] {script.getCost(table(args, args.length > 0 && args[0] instanceof Map<?, ?> ? 0 : 1))};
				case "claim" -> script.claim(holder);
				case "release" -> script.release(holder);
				case "isClaimed" -> script.isClaimed();
				default -> throw new IllegalArgumentException("no function " + name);
			};
		}

		/** The image is read right here on the computer's thread; saving and starting run on the server thread. */
		private MethodResult drawImage(TypewriterScript script, ComputerHolder holder, ILuaContext context, IArguments arguments)
				throws LuaException {
			ByteBuffer buffer = arguments.getBytes(0);
			byte[] bytes = new byte[buffer.remaining()];
			buffer.get(bytes);
			Map<String, ?> options = arguments.count() > 1 && arguments.get(1) != null ? strings(arguments.getTable(1)) : Map.of();
			TypewriterScript.Options parsed = lua(() -> TypewriterScript.Options.of(options));
			Object prepared = TypewriterScript.prepare(() -> bytes, parsed, TypestonesConfig.get().pictureResolution());
			if (!(prepared instanceof Picture picture)) {
				return MethodResult.of(false, prepared);
			}
			return context.executeMainThreadTask(() -> lua(() -> {
				script.checkMayChange(holder);
				return script.startPrepared(picture, "computer image", parsed, holder.describe());
			}));
		}
	}

	private interface Body<T> {
		T run();
	}

	/** Wrong arguments become Lua errors. */
	private static <T> T lua(Body<T> body) throws LuaException {
		try {
			return body.run();
		} catch (IllegalArgumentException e) {
			throw new LuaException(e.getMessage());
		}
	}

	private static String string(Object[] args, int index) {
		if (index < args.length && args[index] instanceof String text) {
			return text;
		}
		throw new IllegalArgumentException("bad argument #" + (index + 1) + " (string expected)");
	}

	private static int whole(Object[] args, int index) {
		if (index < args.length && args[index] instanceof Number number && number.doubleValue() == Math.rint(number.doubleValue())) {
			return (int) Math.clamp(number.longValue(), Integer.MIN_VALUE, Integer.MAX_VALUE);
		}
		throw new IllegalArgumentException("bad argument #" + (index + 1) + " (whole number expected)");
	}

	private static Map<String, ?> table(Object[] args, int index) {
		if (index < args.length && args[index] instanceof Map<?, ?> map) {
			return strings(map);
		}
		if (index >= args.length || args[index] == null) {
			return Map.of();
		}
		throw new IllegalArgumentException("bad argument #" + (index + 1) + " (table expected)");
	}

	private static Map<String, ?> strings(Map<?, ?> map) {
		Map<String, Object> result = new java.util.HashMap<>();
		map.forEach((key, value) -> result.put(String.valueOf(key), value));
		return result;
	}
}
