package de.knito.billboard.api;

/**
 * A machine that Lua Box programs can drive. A box reaches it when the machine touches the box or a pipe of the
 * box's network.
 *
 * <p>Every method is called on the server thread, inside the box's time slice of a few milliseconds per tick. A
 * method should only take the order and leave the real work to the machine's own tick.
 *
 * <p>The library adds {@code getName}, {@code setName} and {@code getType} to every client and stores the name
 * itself. A client that defines one of these names gets an error in the log, and its own method is ignored.
 *
 * @since 0.1.0
 */
public interface LuaClient {
	/**
	 * The machine's type, a namespaced id such as {@code "typestones:typewriter"}. Lua programs search by it, and
	 * its path is the start of the default name: {@code typewriter_1}.
	 */
	String type();

	/** Lists the functions Lua may call. Called again whenever the library needs them, so keep it cheap. */
	void methods(LuaMethods methods);
}
