package de.knito.billboard.api;

import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;

/**
 * The entry point for mods that offer their machines to Lua Boxes. Register a block entity type once, in your
 * mod initializer:
 *
 * <pre>{@code
 * BillboardApi.CLIENTS.registerForBlockEntity((typewriter, side) -> new TypewriterClient(typewriter), TYPEWRITER);
 * }</pre>
 *
 * Only this package is a promise; everything under {@code impl} may change in any release.
 *
 * @since 0.1.0
 */
public final class BillboardApi {
	public static final String MOD_ID = "billboard_api";

	/**
	 * Finds the Lua client of a block. The context is the side of that block that faces the Lua Box or pipe asking,
	 * or null when the library only asks whether the block is a client at all. Only block entities can be clients,
	 * because the library stores each client's name on its block entity.
	 */
	public static final BlockApiLookup<LuaClient, Direction> CLIENTS =
			BlockApiLookup.get(Identifier.fromNamespaceAndPath(MOD_ID, "lua_client"), LuaClient.class, Direction.class);

	private BillboardApi() {
	}
}
