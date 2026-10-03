package dev.gojoinfinity;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: "the player pressed the Infinity key". Carries no data. */
public final class ToggleInfinityPayload implements CustomPacketPayload {
	public static final ToggleInfinityPayload INSTANCE = new ToggleInfinityPayload();
	public static final CustomPacketPayload.Type<ToggleInfinityPayload> TYPE =
			new CustomPacketPayload.Type<>(GojoInfinityMod.id("toggle"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ToggleInfinityPayload> CODEC = StreamCodec.unit(INSTANCE);

	private ToggleInfinityPayload() { }

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
