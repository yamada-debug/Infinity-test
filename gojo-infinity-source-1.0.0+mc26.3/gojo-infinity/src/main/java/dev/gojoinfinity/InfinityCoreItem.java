package dev.gojoinfinity;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Right-click to permanently attune to Infinity. Consumed in Survival. */
public class InfinityCoreItem extends Item {
	public InfinityCoreItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		if (!(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}

		if (Boolean.TRUE.equals(serverPlayer.getAttached(InfinityData.UNLOCKED))) {
			serverPlayer.sendOverlayMessage(Component.translatable("message.gojo_infinity.already_unlocked"));
			return InteractionResult.FAIL;
		}

		serverPlayer.setAttached(InfinityData.UNLOCKED, Boolean.TRUE);
		if (!serverPlayer.hasInfiniteMaterials()) {
			serverPlayer.getItemInHand(hand).shrink(1);
		}
		level.playSound(null, serverPlayer.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 1.4F);
		serverPlayer.sendOverlayMessage(Component.translatable(
				"message.gojo_infinity.unlocked", Component.keybind("key.gojo_infinity.toggle")));
		return InteractionResult.SUCCESS;
	}
}
