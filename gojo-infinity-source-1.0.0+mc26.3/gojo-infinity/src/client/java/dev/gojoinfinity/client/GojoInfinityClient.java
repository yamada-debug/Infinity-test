package dev.gojoinfinity.client;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.sdl.SDLScancode;

import net.minecraft.client.KeyMapping;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import dev.gojoinfinity.GojoInfinityMod;
import dev.gojoinfinity.ToggleInfinityPayload;

/** Client side: just the rebindable key. All the effects are produced by the server. */
public class GojoInfinityClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(GojoInfinityMod.id("main"));

		KeyMapping toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.gojo_infinity.toggle",
				InputConstants.Type.KEYBOARD,
				SDLScancode.SDL_SCANCODE_G, // default: G (rebindable in Options > Controls > Key Binds)
				category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			boolean pressed = false;
			while (toggleKey.consumeClick()) {
				pressed = true; // drain queued clicks so one tick never sends two toggles
			}
			if (pressed && client.getConnection() != null) {
				ClientPlayNetworking.send(ToggleInfinityPayload.INSTANCE);
			}
		});
	}
}
