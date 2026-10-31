package de.knito.typestones.script.billboard;

import java.nio.file.Path;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import de.knito.billboard.api.BillboardApi;
import de.knito.billboard.api.LuaArgs;
import de.knito.billboard.api.LuaCaller;
import de.knito.billboard.api.LuaClient;
import de.knito.billboard.api.LuaException;
import de.knito.billboard.api.LuaMethod;
import de.knito.billboard.api.LuaMethods;

import de.knito.typestones.ModTypewriter;
import de.knito.typestones.script.TypewriterScript;
import de.knito.typestones.typewriter.TypewriterBlockEntity;

/**
 * The Typewriter as a machine of Lua Boxes (Billboard API). Only loaded when the Billboard API is
 * installed. A program finds it with {@code network.find("typewriter")}.
 */
public final class BillboardSupport {
	public static final String TYPE = "typestones:typewriter";

	private BillboardSupport() {
	}

	public static void register() {
		BillboardApi.CLIENTS.registerForBlockEntity((typewriter, side) -> new Client(typewriter), ModTypewriter.ENTITY);
	}

	/** A claim by one run of one Lua Box. */
	record BoxHolder(LuaCaller caller) implements TypewriterScript.Holder {
		@Override
		public boolean isActive() {
			return caller.isActive();
		}

		@Override
		public String describe() {
			return caller.describe();
		}
	}

	private record Client(TypewriterBlockEntity typewriter) implements LuaClient {
		@Override
		public String type() {
			return TYPE;
		}

		@Override
		public void methods(LuaMethods m) {
			TypewriterScript script = new TypewriterScript(typewriter);
			m.add("setText", changing(script, args -> script.setText(args.string(1))))
					.add("getText", args -> script.getText())
					.add("go", changing(script, args -> script.go()))
					.add("stop", changing(script, args -> script.stop()))
					.add("isRunning", args -> script.isRunning())
					.add("isPainting", args -> script.isPainting())
					.add("getStatus", args -> script.getStatus())
					.add("getStatusText", args -> script.getStatusText())
					.add("getProgress", args -> script.getProgress())
					.add("setRepeat", changing(script, args -> script.setRepeat(args.integer(1))))
					.add("getRepeat", args -> script.getRepeat())
					.add("getCost", lua(args -> script.getCost(options(args))))
					.add("drawImage", changing(script, args -> drawImage(script, args)))
					.add("claim", args -> script.claim(holder(args)))
					.add("release", args -> script.release(holder(args)))
					.add("isClaimed", args -> script.isClaimed());
		}

		/** drawImage(name, options): the image file of that name in the calling box. */
		private static Object drawImage(TypewriterScript script, LuaArgs args) throws LuaException {
			String name = args.string(1);
			Map<String, ?> options = args.isNil(2) ? Map.of() : args.map(2);
			// The box hands out only its own files, so a name can't reach outside its folder.
			@Nullable Path file = holder(args).caller().file(name);
			if (file == null) {
				return new Object[] {false, "not_found"};
			}
			return script.drawImage(name, TypewriterScript.ImageSource.file(file), options, holder(args).describe());
		}

		/** getCost(options) or getCost(name, options), as drawImage is called. */
		private static Map<String, ?> options(LuaArgs args) throws LuaException {
			int index = args.get(1) instanceof Map<?, ?> || args.get(1) instanceof java.util.List<?> ? 1 : 2;
			return args.isNil(index) ? Map.of() : args.map(index);
		}

		private static BoxHolder holder(LuaArgs args) throws LuaException {
			LuaCaller caller = args.caller();
			if (caller == null) {
				throw new LuaException("no program calls");
			}
			return new BoxHolder(caller);
		}

		/** A function that changes the Typewriter: refused while another program holds it. */
		private static LuaMethod changing(TypewriterScript script, LuaMethod method) {
			return lua(args -> {
				script.checkMayChange(holder(args));
				return method.call(args);
			});
		}

		/** Wrong arguments become Lua errors at the caller's line. */
		private static LuaMethod lua(LuaMethod method) {
			return args -> {
				try {
					return method.call(args);
				} catch (IllegalArgumentException e) {
					throw new LuaException(e.getMessage());
				}
			};
		}
	}
}
