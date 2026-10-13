package io.github.illagercpr.uncraftinglimit.mixin;

import io.github.illagercpr.uncraftinglimit.Config;
import io.github.illagercpr.uncraftinglimit.LimitMode;
import io.github.illagercpr.uncraftinglimit.UncraftingAttachments;
import io.github.illagercpr.uncraftinglimit.UncraftingLimit;
import io.github.illagercpr.uncraftinglimit.policy.CooldownPolicy;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import twilightforest.client.UncraftingScreen;

/**
 * Renders a live cooldown countdown inside Twilight Forest's uncrafting screen.
 *
 * <p>The action bar alone is easy to miss while the table GUI is open, so the remaining
 * time is drawn on the same label row Twilight Forest itself uses for its
 * {@code uncrafting_disabled} warning (bottom left, dark red, overprinting the vanilla
 * inventory label - our text is always longer, so nothing ghosts through).</p>
 *
 * <p>The cooldown value arrives through the synced {@code last_uncraft_time} player
 * attachment; the countdown itself is computed from the client's own game time, so no
 * per-tick packet traffic is needed.</p>
 */
@Mixin(value = UncraftingScreen.class, remap = false)
public abstract class UncraftingScreenMixin {

	@Inject(method = "renderLabels", at = @At("TAIL"), require = 1)
	private void uncraftinglimit$renderCooldownLine(GuiGraphics guiGraphics, int mouseX, int mouseY, CallbackInfo ci) {
		try {
			Minecraft minecraft = Minecraft.getInstance();
			Player player = minecraft.player;
			if (player == null || minecraft.level == null || !UncraftingLimit.isConfigReady()) {
				return;
			}
			if (Config.INSTANCE.mode.get() != LimitMode.COOLDOWN) {
				return;
			}
			long cooldownTicks = Config.INSTANCE.cooldownTicks.get();
			if (cooldownTicks <= 0) {
				return;
			}
			long remaining = CooldownPolicy.remainingTicks(
					minecraft.level.getGameTime(),
					player.getData(UncraftingAttachments.LAST_UNCRAFT_TIME),
					cooldownTicks);
			if (remaining <= 0) {
				return;
			}
			AbstractContainerScreenAccessor screen = (AbstractContainerScreenAccessor) (Object) this;
			Component text = Component.translatable("overlay.uncraftinglimit.cooldown_waiting",
					CooldownPolicy.formatDuration(remaining)).withStyle(ChatFormatting.DARK_RED);
			guiGraphics.drawString(minecraft.font, text,
					6, screen.uncraftinglimit$imageHeight() - 96 + 2, 0x404040, false);
		} catch (Throwable failure) {
			// A cosmetic overlay must never break the screen.
			UncraftingLimit.LOGGER.debug("Cooldown overlay rendering failed", failure);
		}
	}
}
