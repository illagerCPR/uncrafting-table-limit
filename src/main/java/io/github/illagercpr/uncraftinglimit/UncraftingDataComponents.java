package io.github.illagercpr.uncraftinglimit;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * Data components used to carry durability between the block entity and the item form
 * of the Twilight Forest uncrafting table.
 */
public final class UncraftingDataComponents {

	public static final DeferredRegister.DataComponents REGISTRY =
			DeferredRegister.createDataComponents(UncraftingLimit.MOD_ID);

	/** Remaining uses of an uncrafting table, stored on the dropped/picked item. */
	public static final Supplier<DataComponentType<Integer>> DURABILITY =
			REGISTRY.registerComponentType("durability",
					builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

	private UncraftingDataComponents() {
	}
}
