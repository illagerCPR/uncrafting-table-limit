package io.github.illagercpr.uncraftinglimit.mixin;

import io.github.illagercpr.uncraftinglimit.Config;
import io.github.illagercpr.uncraftinglimit.LimitMode;
import io.github.illagercpr.uncraftinglimit.TableInteractionTracker;
import io.github.illagercpr.uncraftinglimit.UncraftingAttachments;
import io.github.illagercpr.uncraftinglimit.UncraftingLimit;
import io.github.illagercpr.uncraftinglimit.block.UncraftingTableBlockEntity;
import io.github.illagercpr.uncraftinglimit.policy.CooldownPolicy;
import io.github.illagercpr.uncraftinglimit.policy.ItemCostPolicy;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import twilightforest.client.UncraftingScreen;

import java.util.List;
import java.util.Optional;

/**
 * Renders one mode-specific status line inside Twilight Forest's uncrafting screen:
 * the live cooldown countdown (COOLDOWN), the refusal notice (DISABLED), the remaining
 * durability of the table (DURABILITY) or the cost of the next uncrafting (ITEM_COST).
 *
 * <p>The action bar alone is easy to miss while the table GUI is open, so this line is
 * drawn where Twilight Forest itself draws its {@code uncrafting_disabled} warning
 * (bottom left of the GUI). The vanilla inventory label on that row is erased first so
 * the two texts never overlap.</p>
 *
 * <p>Data sources are all client-safe: the synced {@code last_uncraft_time} attachment,
 * the synced common config, the synced block entity (durability) and the client player's
 * own inventory. Nothing here changes any game state.</p>
 */
@Mixin(value = UncraftingScreen.class, remap = false)
public abstract class UncraftingScreenMixin {

	/** Standard container GUI background gray, used to erase the vanilla label line. */
	private static final int GUI_BACKGROUND = 0xFFC6C6C6;
	/** Twilight Forest's label gray, used for informational lines. */
	private static final int LABEL_GRAY = 0x404040;
	/** Twilight Forest's warning red (ChatFormatting.DARK_RED), used for refusal lines. */
	private static final int WARNING_RED = 0xFF5555;

	@Inject(method = "renderLabels", at = @At("TAIL"), require = 1)
	private void uncraftinglimit$renderStatusLine(GuiGraphics guiGraphics, int mouseX, int mouseY, CallbackInfo ci) {
		try {
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft.player == null || minecraft.level == null || !UncraftingLimit.isConfigReady()) {
				return;
			}
			StatusLine line = uncraftinglimit$statusFor(minecraft.player);
			if (line == null) {
				return;
			}
			AbstractContainerScreenAccessor screen = (AbstractContainerScreenAccessor) (Object) this;
			int y = screen.uncraftinglimit$imageHeight() - 96 + 2;
			// Erase the vanilla inventory label on this row first, so the status text and
			// the label never overlap (drawString does not clear the glyphs behind it).
			guiGraphics.fill(4, y - 1, screen.uncraftinglimit$imageWidth() - 4, y + 9, GUI_BACKGROUND);
			guiGraphics.drawString(minecraft.font, line.text(), 6, y, line.color(), false);
		} catch (Throwable failure) {
			// A cosmetic overlay must never break the screen.
			UncraftingLimit.LOGGER.debug("Status overlay rendering failed", failure);
		}
	}

	@Unique
	private StatusLine uncraftinglimit$statusFor(Player player) {
		Minecraft minecraft = Minecraft.getInstance();
		return switch (Config.INSTANCE.mode.get()) {
			case COOLDOWN -> uncraftinglimit$cooldownLine(minecraft, player);
			case DISABLED -> uncraftinglimit$disabledLine(player);
			case DURABILITY -> uncraftinglimit$durabilityLine(minecraft);
			case ITEM_COST -> uncraftinglimit$itemCostLine(player);
		};
	}

	@Unique
	private StatusLine uncraftinglimit$cooldownLine(Minecraft minecraft, Player player) {
		long cooldownTicks = Config.INSTANCE.cooldownTicks.get();
		if (cooldownTicks <= 0) {
			return null;
		}
		long remaining = CooldownPolicy.remainingTicks(
				minecraft.level.getGameTime(),
				player.getData(UncraftingAttachments.LAST_UNCRAFT_TIME),
				cooldownTicks);
		if (remaining <= 0) {
			return null;
		}
		return new StatusLine(Component.translatable("overlay.uncraftinglimit.cooldown_waiting",
				CooldownPolicy.formatDuration(remaining)), WARNING_RED);
	}

	@Unique
	private StatusLine uncraftinglimit$disabledLine(Player player) {
		if (Config.INSTANCE.exemptCreative.get() && player.getAbilities().instabuild) {
			return null;
		}
		return new StatusLine(Component.translatable("message.uncraftinglimit.disabled"), WARNING_RED);
	}

	@Unique
	private StatusLine uncraftinglimit$durabilityLine(Minecraft minecraft) {
		BlockPos pos = TableInteractionTracker.lastClientTablePos();
		if (pos == null || !(minecraft.level.getBlockEntity(pos) instanceof UncraftingTableBlockEntity table)) {
			return null;
		}
		int remaining = table.remainingDurability();
		if (remaining <= 0) {
			return new StatusLine(Component.translatable("message.uncraftinglimit.durability_exhausted"), WARNING_RED);
		}
		boolean low = remaining <= Config.INSTANCE.durabilityWarnThreshold.get();
		return new StatusLine(Component.translatable("overlay.uncraftinglimit.durability_remaining", remaining),
				low ? WARNING_RED : LABEL_GRAY);
	}

	@Unique
	private StatusLine uncraftinglimit$itemCostLine(Player player) {
		List<ItemCostPolicy.CostEntry> entries = ItemCostPolicy.parseEntries(Config.INSTANCE.itemCostEntries.get());
		if (entries.isEmpty()) {
			return new StatusLine(Component.translatable("message.uncraftinglimit.item_cost_unavailable"), WARNING_RED);
		}
		Optional<ItemCostPolicy.CostEntry> affordable = ItemCostPolicy.findAffordable(player, entries);
		ItemCostPolicy.CostEntry entry = affordable.orElse(entries.getFirst());
		Component text = affordable.isPresent()
				? Component.translatable("overlay.uncraftinglimit.item_cost", entry.count(),
						new ItemStack(entry.item()).getHoverName())
				: Component.translatable("message.uncraftinglimit.item_cost_missing", entry.count(),
						new ItemStack(entry.item()).getHoverName());
		return new StatusLine(text, affordable.isPresent() ? LABEL_GRAY : WARNING_RED);
	}

	@Unique
	private record StatusLine(Component text, int color) {
	}
}
