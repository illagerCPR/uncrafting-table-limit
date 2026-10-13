package io.github.illagercpr.uncraftinglimit;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import twilightforest.init.TFBlocks;

/**
 * Mode = DISABLED interactions with the table block itself.
 *
 * <p>By default the menu still opens (preview is allowed, taking is refused by the mixin);
 * with {@code disabled.block_menu_open = true} the interaction is denied outright via
 * {@code setUseBlock(DENY)} on the right-click event, which fires before
 * {@code useWithoutItem} would open the menu.</p>
 */
@EventBusSubscriber(modid = UncraftingLimit.MOD_ID)
public final class DisabledModeInteract {

	private DisabledModeInteract() {
	}

	@SubscribeEvent
	private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
		Level level = event.getLevel();
		if (level.isClientSide() || !UncraftingLimit.isConfigReady()) {
			return;
		}
		if (Config.INSTANCE.mode.get() != LimitMode.DISABLED) {
			return;
		}
		BlockState state = level.getBlockState(event.getPos());
		if (!state.is(TFBlocks.UNCRAFTING_TABLE.get())) {
			return;
		}
		if (Config.INSTANCE.exemptCreative.get() && event.getEntity().getAbilities().instabuild) {
			return;
		}
		if (Config.INSTANCE.disabledBlockMenuOpen.get()) {
			event.setUseBlock(TriState.FALSE);
			RefusalFeedback.notifyDenied(event.getEntity(), level,
					() -> Component.translatable("message.uncraftinglimit.menu_blocked"));
		} else {
			// The menu opens, but make sure the player knows why nothing can be taken out.
			RefusalFeedback.notifyDenied(event.getEntity(), level,
					() -> Component.translatable("message.uncraftinglimit.disabled"));
		}
	}
}
