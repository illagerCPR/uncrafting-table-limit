package io.github.illagercpr.uncraftinglimit;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import twilightforest.init.TFBlocks;

/**
 * Remembers where the client player last interacted with an uncrafting table.
 *
 * <p>The client-side menu is built through {@code UncraftingMenu::fromNetwork}, which passes
 * {@code ContainerLevelAccess.NULL} - the screen therefore cannot learn the table position
 * from its menu. This tracker records the position of the right-clicked table instead, so
 * the uncrafting screen can read the (synced) durability of exactly that block entity.</p>
 *
 * <p>Only the client-side interaction is recorded; the class itself is deliberately
 * client-safe and inert on dedicated servers.</p>
 */
@EventBusSubscriber(modid = UncraftingLimit.MOD_ID)
public final class TableInteractionTracker {

	private static volatile BlockPos lastClientTablePos;

	private TableInteractionTracker() {
	}

	@SubscribeEvent
	private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
		if (!event.getLevel().isClientSide()) {
			return;
		}
		BlockState state = event.getLevel().getBlockState(event.getPos());
		if (state.is(TFBlocks.UNCRAFTING_TABLE.get())) {
			lastClientTablePos = event.getPos().immutable();
		}
	}

	/** @return the client-side position of the last right-clicked uncrafting table, or null. */
	public static BlockPos lastClientTablePos() {
		return lastClientTablePos;
	}
}
