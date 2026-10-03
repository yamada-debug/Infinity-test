package dev.gojoinfinity;

import com.mojang.serialization.Codec;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

/** Persistent per-player data. Saved with the player, survives death and server restarts. */
public final class InfinityData {
	/** True once the player has absorbed an Infinity Core. */
	public static final AttachmentType<Boolean> UNLOCKED = AttachmentRegistry.create(
			GojoInfinityMod.id("unlocked"),
			builder -> builder.persistent(Codec.BOOL).copyOnDeath()
	);

	private InfinityData() { }

	public static void init() {
		// Class-load trigger so the attachment is registered during mod initialisation.
	}
}
