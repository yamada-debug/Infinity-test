package dev.gojoinfinity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.Identifier;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public class GojoInfinityMod implements ModInitializer {
	public static final String MOD_ID = "gojo_infinity";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		InfinityConfig.load();
		InfinityData.init();
		InfinityItems.init();

		// Client -> server: the toggle keybind. The server decides everything.
		PayloadTypeRegistry.serverboundPlay().register(ToggleInfinityPayload.TYPE, ToggleInfinityPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ToggleInfinityPayload.TYPE, (payload, context) ->
				context.server().execute(() -> InfinityManager.toggle(context.player())));

		// Damage fallback + per-tick interception.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> InfinityManager.allowDamage(entity, source));
		ServerTickEvents.END_SERVER_TICK.register(InfinityManager::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(InfinityManager::releaseAll);

		LOGGER.info("Gojo's Infinity loaded.");
	}
}
