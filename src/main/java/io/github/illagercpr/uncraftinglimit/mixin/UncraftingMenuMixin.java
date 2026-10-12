package io.github.illagercpr.uncraftinglimit.mixin;

import io.github.illagercpr.uncraftinglimit.Config;
import io.github.illagercpr.uncraftinglimit.UncraftingLimit;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import twilightforest.inventory.UncraftingMenu;

/**
 * Single chokepoint for every uncrafting action.
 *
 * <p>{@code UncraftingMenu#clicked} is the only way a player can move items out of the internal
 * uncrafting matrix, so all limit modes hook here. No NeoForge event covers this code path,
 * because {@code UncraftingResultSlot} is a Twilight Forest internal class.</p>
 *
 * <p>M0 stage: this injection only proves that the mixin is applied and that the hook is reached.
 * The actual limiting logic is added per mode in later batches.</p>
 */
@Mixin(value = UncraftingMenu.class, remap = false)
public abstract class UncraftingMenuMixin {

	@Shadow
	private Player player;

	@Shadow
	private Level level;

	@Inject(method = "clicked", at = @At("HEAD"), cancellable = true, require = 1)
	private void uncraftinglimit$guardUncrafting(int slotNum, int mouseButton, ClickType clickType, Player clicker, CallbackInfo ci) {
		if (this.level == null || this.level.isClientSide()) {
			return;
		}

		// M0 verification probe. Replaced by the real per-mode policy check in M1+.
		UncraftingLimit.LOGGER.info(
				"[probe] UncraftingMenu.clicked reached: slot={}, mouse={}, type={}, player={}, mode={}",
				slotNum, mouseButton, clickType, clicker.getName().getString(),
				UncraftingLimit.isConfigReady() ? Config.INSTANCE.mode.get() : "config-not-ready");
	}
}
