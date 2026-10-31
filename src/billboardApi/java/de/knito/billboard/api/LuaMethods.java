package de.knito.billboard.api;

/**
 * Collects the functions of a {@link LuaClient}.
 *
 * @since 0.1.0
 */
public interface LuaMethods {
	/**
	 * Adds one function. A Lua program calls it as {@code client.name(...)}; {@code client:name(...)} works as well.
	 *
	 * @param name a Lua name: letters, digits and underscores, not starting with a digit
	 */
	LuaMethods add(String name, LuaMethod method);
}
