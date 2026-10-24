package de.knito.typestones.station;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import de.knito.typestones.Typestones;

/** A button press in the Alpha Station screen. Always carries the current text and repeat count. */
public record StationActionPayload(Action action, String text, int repeat) implements CustomPacketPayload {
	public enum Action {
		SAVE, START, STOP
	}

	public static final Type<StationActionPayload> TYPE = new Type<>(Typestones.id("station_action"));

	public static final StreamCodec<ByteBuf, StationActionPayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.idMapper(i -> Action.values()[Math.clamp(i, 0, Action.values().length - 1)], Action::ordinal), StationActionPayload::action,
			ByteBufCodecs.stringUtf8(AlphaStationBlockEntity.MAX_TEXT_LENGTH * 4), StationActionPayload::text,
			ByteBufCodecs.VAR_INT, StationActionPayload::repeat,
			StationActionPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
