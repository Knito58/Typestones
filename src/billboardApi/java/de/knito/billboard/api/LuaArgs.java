package de.knito.billboard.api;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * The arguments of one Lua call, already turned into Java values: {@code null}, {@link Boolean}, {@link Long} for
 * whole numbers, {@link Double} for others, {@link String}, {@link List} for a Lua table with the keys 1 to n, and
 * {@link Map} with {@link String} keys for any other table. Numbered from 1, as in Lua.
 *
 * <p>The getters throw a {@link LuaException} with a message in CC: Tweaked style, such as
 * {@code bad argument #1 (string expected, got nil)}, so a client can pass them straight on.
 *
 * @since 0.1.0
 */
public final class LuaArgs {
	private final List<@Nullable Object> values;
	private final @Nullable LuaCaller caller;

	/**
	 * Mainly for the library and for tests. A top-level {@code byte[]} stands for a Lua string as it came, so
	 * {@link #bytes} can give back binary data unchanged; {@link #get} and {@link #string} show it as UTF-8 text.
	 */
	public LuaArgs(List<?> values, @Nullable LuaCaller caller) {
		this.values = Collections.unmodifiableList(new ArrayList<>(values));
		this.caller = caller;
	}

	public static LuaArgs of(@Nullable Object... values) {
		return new LuaArgs(Arrays.asList(values), null);
	}

	/** The program making this call; null only for arguments made by hand, such as in tests. */
	public @Nullable LuaCaller caller() {
		return caller;
	}

	/** Number of arguments, nils at the end included. */
	public int count() {
		return values.size();
	}

	/** The argument as described above, {@code null} if missing. */
	public @Nullable Object get(int index) {
		Object value = raw(index);
		return value instanceof byte[] bytes ? text(bytes) : value;
	}

	public boolean isNil(int index) {
		return raw(index) == null;
	}

	public String string(int index) throws LuaException {
		Object value = raw(index);
		if (value instanceof byte[] bytes) {
			return text(bytes);
		}
		if (value instanceof String text) {
			return text;
		}
		throw bad(index, "string");
	}

	public String optString(int index, String fallback) throws LuaException {
		return isNil(index) ? fallback : string(index);
	}

	/** A Lua string as raw bytes, for binary data such as pixels. */
	public byte[] bytes(int index) throws LuaException {
		Object value = raw(index);
		if (value instanceof byte[] bytes) {
			return bytes.clone();
		}
		if (value instanceof String text) {
			return text.getBytes(StandardCharsets.UTF_8);
		}
		throw bad(index, "string");
	}

	public double number(int index) throws LuaException {
		if (raw(index) instanceof Number number) {
			return number.doubleValue();
		}
		throw bad(index, "number");
	}

	public double optNumber(int index, double fallback) throws LuaException {
		return isNil(index) ? fallback : number(index);
	}

	/** A whole number that fits an int; 2.5 is an error, not 2. */
	public int integer(int index) throws LuaException {
		if (raw(index) instanceof Long number && number >= Integer.MIN_VALUE && number <= Integer.MAX_VALUE) {
			return number.intValue();
		}
		if (raw(index) instanceof Number) {
			throw new LuaException("bad argument #" + index + " (number has no integer representation)");
		}
		throw bad(index, "number");
	}

	public int optInteger(int index, int fallback) throws LuaException {
		return isNil(index) ? fallback : integer(index);
	}

	public boolean bool(int index) throws LuaException {
		if (raw(index) instanceof Boolean bool) {
			return bool;
		}
		throw bad(index, "boolean");
	}

	public boolean optBool(int index, boolean fallback) throws LuaException {
		return isNil(index) ? fallback : bool(index);
	}

	/** A Lua table with keys other than 1 to n. */
	public Map<String, ?> map(int index) throws LuaException {
		if (raw(index) instanceof Map<?, ?> map) {
			@SuppressWarnings("unchecked")
			Map<String, ?> result = (Map<String, ?>) map;
			return result;
		}
		if (raw(index) instanceof List<?> list && list.isEmpty()) {
			return Map.of();
		}
		throw bad(index, "table");
	}

	/** A Lua table with the keys 1 to n; an empty table counts as an empty list. */
	public List<?> list(int index) throws LuaException {
		if (raw(index) instanceof List<?> list) {
			return list;
		}
		throw bad(index, "table");
	}

	private @Nullable Object raw(int index) {
		return index >= 1 && index <= values.size() ? values.get(index - 1) : null;
	}

	private LuaException bad(int index, String expected) {
		return new LuaException("bad argument #" + index + " (" + expected + " expected, got " + luaType(raw(index)) + ")");
	}

	private static String luaType(@Nullable Object value) {
		return switch (value) {
			case null -> "nil";
			case Boolean b -> "boolean";
			case Number n -> "number";
			case String s -> "string";
			case byte[] b -> "string";
			default -> "table";
		};
	}

	private static String text(byte[] bytes) {
		return new String(bytes, StandardCharsets.UTF_8);
	}

	@Override
	public String toString() {
		return "LuaArgs" + values.stream().map(v -> v instanceof byte[] b ? text(b) : v).toList();
	}
}
