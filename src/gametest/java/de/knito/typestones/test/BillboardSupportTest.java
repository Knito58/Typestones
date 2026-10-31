package de.knito.typestones.test;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import de.knito.billboard.api.BillboardApi;
import de.knito.billboard.api.LuaArgs;
import de.knito.billboard.api.LuaCaller;
import de.knito.billboard.api.LuaClient;
import de.knito.billboard.api.LuaException;
import de.knito.billboard.api.LuaMethod;

import de.knito.typestones.ModTypewriter;
import de.knito.typestones.script.billboard.BillboardSupport;
import de.knito.typestones.typewriter.TypewriterBlock;

/** The Typewriter's Lua functions as a Lua Box sees them, without the Billboard API mod itself. */
public class BillboardSupportTest {
	private static boolean registered;

	private record Box(String name, boolean[] active) implements LuaCaller {
		Box(String name) {
			this(name, new boolean[] {true});
		}

		@Override
		public String describe() {
			return "Lua Box " + name;
		}

		@Override
		public boolean isActive() {
			return active[0];
		}

		@Override
		public @Nullable Path file(String file) {
			return null;
		}
	}

	private static Object call(Map<String, LuaMethod> methods, String name, LuaCaller caller, Object... args) throws LuaException {
		LuaMethod method = methods.get(name);
		if (method == null) {
			throw new AssertionError("No Lua function " + name);
		}
		return method.call(new LuaArgs(List.of(args), caller));
	}

	@GameTest
	public void luaBoxDrivesTheTypewriter(GameTestHelper helper) throws LuaException {
		if (!registered) {
			BillboardSupport.register();
			registered = true;
		}
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, ModTypewriter.BLOCK.defaultBlockState().setValue(TypewriterBlock.FACING, Direction.NORTH));
		LuaClient client = BillboardApi.CLIENTS.find(helper.getLevel(), helper.absolutePos(pos), null);
		if (client == null) {
			throw helper.assertionException(Component.literal("The Typewriter is no Lua client"));
		}
		helper.assertTrue(client.type().equals("typestones:typewriter"), "Type: " + client.type());
		Map<String, LuaMethod> methods = new HashMap<>();
		client.methods((name, method) -> {
			methods.put(name, method);
			return null;
		});
		for (String name : List.of("setText", "getText", "go", "stop", "isRunning", "drawImage", "isPainting", "claim", "release",
				"isClaimed", "getStatus", "getProgress", "getCost", "setRepeat", "getRepeat")) {
			helper.assertTrue(methods.containsKey(name), "Missing Lua function " + name);
		}

		Box first = new Box("one");
		Box second = new Box("two");
		call(methods, "setText", first, "HELLO");
		helper.assertTrue("HELLO".equals(call(methods, "getText", first)), "getText");
		Object[] noFile = (Object[]) call(methods, "drawImage", first, "cat.png", Map.of("width", 2L, "height", 2L));
		helper.assertTrue(noFile[0].equals(false) && noFile[1].equals("not_found"), "drawImage of a file the box doesn't have");

		Object[] claimed = (Object[]) call(methods, "claim", first);
		helper.assertTrue(claimed[0].equals(true), "claim failed");
		Object[] taken = (Object[]) call(methods, "claim", second);
		helper.assertTrue(taken[0].equals(false) && taken[1].equals("Lua Box one"), "A second box took the claim");
		try {
			call(methods, "setText", second, "NO");
			throw helper.assertionException(Component.literal("A program changed a claimed Typewriter"));
		} catch (LuaException expected) {
			helper.assertTrue(expected.getMessage().contains("claimed by Lua Box one"), "Message: " + expected.getMessage());
		}
		helper.assertTrue("HELLO".equals(call(methods, "getText", second)), "Reading should work while claimed");
		first.active()[0] = false;
		helper.assertTrue(((Object[]) call(methods, "isClaimed", second))[0].equals(false), "The claim outlived its program");

		try {
			call(methods, "getCost", second, Map.of("width", 2L, "height", 1L, "fit", "crop"));
			throw helper.assertionException(Component.literal("A wrong fit passed"));
		} catch (LuaException expected) {
			helper.assertTrue(expected.getMessage().contains("fit"), "Message: " + expected.getMessage());
		}
		@SuppressWarnings("unchecked")
		Map<String, Object> cost = (Map<String, Object>) call(methods, "getCost", second, "cat.png", Map.of("width", 3L, "height", 2L));
		helper.assertTrue(cost.get("stones").equals(6) && cost.get("missing").equals("needs_stone"), "Cost: " + cost);
		helper.succeed();
	}
}
