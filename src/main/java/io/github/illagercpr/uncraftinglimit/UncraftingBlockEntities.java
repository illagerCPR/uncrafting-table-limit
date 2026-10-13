package io.github.illagercpr.uncraftinglimit;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;
import io.github.illagercpr.uncraftinglimit.block.UncraftingTableBlockEntity;
import twilightforest.init.TFBlocks;

import java.util.function.Supplier;

/**
 * Own block entity types. The Twilight Forest uncrafting table is declared as a valid
 * block directly in the builder, so {@code BlockEntityTypeAddBlocksEvent} (which exists
 * for attaching blocks to pre-existing types) is not needed.
 */
public final class UncraftingBlockEntities {

	public static final DeferredRegister<BlockEntityType<?>> REGISTRY =
			DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, UncraftingLimit.MOD_ID);

	public static final Supplier<BlockEntityType<UncraftingTableBlockEntity>> UNCRAFTING_TABLE =
			REGISTRY.register("uncrafting_table", () -> BlockEntityType.Builder.of(
					UncraftingTableBlockEntity::new, TFBlocks.UNCRAFTING_TABLE.get()).build(null));

	private UncraftingBlockEntities() {
	}
}
