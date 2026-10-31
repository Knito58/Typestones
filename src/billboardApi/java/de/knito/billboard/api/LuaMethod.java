package de.knito.billboard.api;

import java.util.concurrent.CompletableFuture;

/**
 * One function of a {@link LuaClient}.
 *
 * @since 0.1.0
 */
@FunctionalInterface
public interface LuaMethod {
	/**
	 * Called on the server thread when a Lua program calls the function.
	 *
	 * <p>What it may return:
	 * <ul>
	 * <li>{@code null}, a {@link Boolean}, a {@link Number}, a {@link String}, a {@code byte[]} (a Lua string of
	 * raw bytes), a {@link java.util.List} or a {@link java.util.Map} with string or number keys, nested as deep as
	 * needed. Anything else is an error at the call.</li>
	 * <li>An {@code Object[]}: several return values.</li>
	 * <li>A {@link CompletableFuture}: the program waits, tick by tick, until it is done, and then gets its value,
	 * which may again be anything of this list but a future. A future may be completed on any thread; to do the last
	 * step on the server thread, use {@code future.thenApplyAsync(fn, server)}.</li>
	 * </ul>
	 *
	 * @throws LuaException to raise a Lua error with this message at the caller's line
	 */
	Object call(LuaArgs args) throws LuaException;
}
