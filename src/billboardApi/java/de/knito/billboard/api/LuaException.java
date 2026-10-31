package de.knito.billboard.api;

import java.io.Serial;

/**
 * Thrown by a {@link LuaMethod} to raise a Lua error. The program sees the message with its own file and line in
 * front, as for any Lua error; it can catch it with {@code pcall}.
 *
 * @since 0.1.0
 */
public class LuaException extends Exception {
	@Serial
	private static final long serialVersionUID = 1L;

	public LuaException(String message) {
		super(message, null, false, false);
	}
}
