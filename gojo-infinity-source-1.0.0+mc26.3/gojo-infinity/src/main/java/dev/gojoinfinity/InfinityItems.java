package dev.gojoinfinity;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

public final class InfinityItems {
	public static final ResourceKey<Item> INFINITY_CORE_KEY =
			ResourceKey.create(Registries.ITEM, GojoInfinityMod.id("infinity_core"));

	public static final Item INFINITY_CORE = Registry.register(
			BuiltInRegistries.ITEM,
			INFINITY_CORE_KEY,
			new InfinityCoreItem(new Item.Properties()
					.setId(INFINITY_CORE_KEY)
					.stacksTo(1)
					.rarity(Rarity.EPIC)
					.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true))
	);

	private InfinityItems() { }

	public static void init() {
		// Creative players can take the core straight from the creative inventory.
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
				.register(entries -> entries.accept(INFINITY_CORE));
	}
}
