package io.github.illagercpr.uncraftinglimit.mixin;

import io.github.illagercpr.uncraftinglimit.Config;
import io.github.illagercpr.uncraftinglimit.LimitMode;
import io.github.illagercpr.uncraftinglimit.RefusalFeedback;
import io.github.illagercpr.uncraftinglimit.UncraftingAttachments;
import io.github.illagercpr.uncraftinglimit.UncraftingLimit;
import io.github.illagercpr.uncraftinglimit.policy.CooldownPolicy;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import twilightforest.inventory.UncraftingContainer;
import twilightforest.inventory.UncraftingMenu;

/**
 * Single chokepoint for every uncrafting action.
 *
 * <p>{@code UncraftingMenu#clicked} is the only way a player can move items out of the internal
 * uncrafting grid, so all limit modes hook here. No NeoForge event covers this code path,
 * because {@code UncraftingResultSlot} is a Twilight Forest internal class.</p>
 *
 * <p>Two facts about Twilight Forest 4.8.3345 shape this mixin (verified against the
 * released jar and the upstream sources):</p>
 * <ul>
 *   <li>At the top of {@code clicked}, a click on an <em>empty</em> assembly-grid slot with an
 *       <em>empty</em> hand while the assembly grid is empty is redirected into the internal
 *       uncrafting grid ({@code slotNum -= 9}). The uncrafting grid slots sit off-screen at
 *       x=300000, so this redirect is the only legitimate route to them - the gate therefore
 *       replicates it exactly, including direct (modified-client) clicks on the grid.</li>
 *   <li>{@code UncraftingSlot#onTake} copies all remaining ingredients into the assembly grid
 *       and consumes one input item, so exactly <em>one</em> take from the uncrafting grid
 *       equals one complete uncrafting. Detecting that take by the consumed tinker input
 *       cleanly identifies a finished uncrafting - Twilight Forest's own refusals (missing
 *       experience, marked ingredients) leave the input untouched. (The grid's item count is
 *       useless for this: a successful take immediately refills the grid through
 *       {@code UncraftingInputContainer#removeItem} → {@code slotsChanged}.)</li>
 * </ul>
 */
@Mixin(value = UncraftingMenu.class, remap = false)
public abstract class UncraftingMenuMixin {

	@Shadow
	private Player player;

	@Shadow
	private Level level;

	@Shadow
	@Final
	private UncraftingContainer uncraftingMatrix;

	@Shadow
	@Final
	public CraftingContainer assemblyMatrix;

	@Shadow
	@Final
	public net.minecraft.world.Container tinkerInput;

	/** Tinker input item count, armed at HEAD for a permitted take; -1 otherwise. */
	@Unique
	private int uncraftinglimit$takeSnapshot = -1;

	@Inject(method = "clicked", at = @At("HEAD"), cancellable = true, require = 1)
	private void uncraftinglimit$guardUncrafting(int slotNum, int mouseButton, ClickType clickType, Player clicker, CallbackInfo ci) {
		this.uncraftinglimit$takeSnapshot = -1;
		if (this.level == null || this.level.isClientSide() || !UncraftingLimit.isConfigReady()) {
			return;
		}
		try {
			if (!this.uncraftinglimit$isUncraftingGridTake(slotNum, mouseButton, clickType, clicker)) {
				return;
			}
			if (this.uncraftinglimit$refuseInCurrentMode(clicker)) {
				// Cancelled: Twilight Forest never runs, the client sees no slot change.
				ci.cancel();
				return;
			}
			// The take is allowed: snapshot the input so the RETURN callback can detect success.
			this.uncraftinglimit$takeSnapshot = this.uncraftinglimit$tinkerInputCount();
		} catch (Throwable failure) {
			// Fail-open: the click path is hot; limiting logic must never break gameplay.
			UncraftingLimit.LOGGER.debug("Uncrafting limit check failed; allowing the click", failure);
		}
	}

	@Inject(method = "clicked", at = @At("RETURN"), require = 1)
	private void uncraftinglimit$afterUncraftingClick(int slotNum, int mouseButton, ClickType clickType, Player clicker, CallbackInfo ci) {
		int snapshot = this.uncraftinglimit$takeSnapshot;
		this.uncraftinglimit$takeSnapshot = -1;
		if (snapshot < 0 || this.level == null || this.level.isClientSide() || !UncraftingLimit.isConfigReady()) {
			return;
		}
		try {
			if (this.uncraftinglimit$tinkerInputCount() < snapshot) {
				this.uncraftinglimit$onUncraftingSuccess(clicker);
			}
		} catch (Throwable failure) {
			UncraftingLimit.LOGGER.debug("Uncrafting success handling failed", failure);
		}
	}

	/**
	 * Replicates Twilight Forest's slot redirect and reports whether this click would take
	 * an ingredient out of the uncrafting grid. Must match {@code UncraftingMenu#clicked}
	 * line for line - a missed redirect would let uncrafting bypass the limit.
	 */
	@Unique
	private boolean uncraftinglimit$isUncraftingGridTake(int slotNum, int mouseButton, ClickType clickType, Player clicker) {
		if (slotNum <= 0) {
			return false;
		}
		AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
		if (slotNum >= menu.slots.size()) {
			return false;
		}
		Slot slot = menu.slots.get(slotNum);
		if (slot.container == this.assemblyMatrix
				&& clicker.containerMenu != null
				&& clicker.containerMenu.getCarried().isEmpty()
				&& !slot.hasItem()
				&& this.assemblyMatrix.isEmpty()
				&& (clickType != ClickType.SWAP || clicker.getInventory().getItem(mouseButton).isEmpty())) {
			slotNum -= 9;
		}
		if (slotNum < 0 || slotNum >= menu.slots.size()) {
			return false;
		}
		Slot effectiveSlot = menu.slots.get(slotNum);
		// Only a grid slot that actually holds an ingredient is a take attempt; clicking an
		// empty one is a no-op and must neither trigger nor be blocked by any limit.
		return effectiveSlot.container == this.uncraftingMatrix && effectiveSlot.hasItem();
	}

	/** Dispatches the mutually exclusive limit modes for a take attempt. */
	@Unique
	private boolean uncraftinglimit$refuseInCurrentMode(Player clicker) {
		LimitMode mode = Config.INSTANCE.mode.get();
		return switch (mode) {
			case COOLDOWN -> this.uncraftinglimit$refuseDuringCooldown(clicker);
			case DISABLED -> this.uncraftinglimit$refuseDisabled(clicker);
			// Remaining modes arrive with their own batches (M3+).
			case DURABILITY, ITEM_COST -> false;
		};
	}

	@Unique
	private boolean uncraftinglimit$isCreativeExempt(Player clicker) {
		return Config.INSTANCE.exemptCreative.get() && clicker.getAbilities().instabuild;
	}

	@Unique
	private boolean uncraftinglimit$refuseDuringCooldown(Player clicker) {
		long cooldownTicks = Config.INSTANCE.cooldownTicks.get();
		if (cooldownTicks <= 0 || this.uncraftinglimit$isCreativeExempt(clicker)) {
			return false;
		}
		long now = this.level.getGameTime();
		long lastUncraft = clicker.getData(UncraftingAttachments.LAST_UNCRAFT_TIME);
		if (!CooldownPolicy.isOnCooldown(now, lastUncraft, cooldownTicks)) {
			return false;
		}
		RefusalFeedback.notifyDenied(clicker, this.level, () -> Component.translatable(
				"message.uncraftinglimit.cooldown_waiting",
				CooldownPolicy.formatDuration(CooldownPolicy.remainingTicks(now, lastUncraft, cooldownTicks))));
		return true;
	}

	/** Mode DISABLED: preview stays available, taking ingredients out is refused outright. */
	@Unique
	private boolean uncraftinglimit$refuseDisabled(Player clicker) {
		if (this.uncraftinglimit$isCreativeExempt(clicker)) {
			return false;
		}
		RefusalFeedback.notifyDenied(clicker, this.level,
				() -> Component.translatable("message.uncraftinglimit.disabled"));
		return true;
	}

	/** A full uncrafting just happened; each limit mode records what it needs. */
	@Unique
	private void uncraftinglimit$onUncraftingSuccess(Player clicker) {
		switch (Config.INSTANCE.mode.get()) {
			case COOLDOWN -> clicker.setData(UncraftingAttachments.LAST_UNCRAFT_TIME, this.level.getGameTime());
			case DISABLED, DURABILITY, ITEM_COST -> {
				// handled by their own batches
			}
		}
	}

	/**
	 * Detects a finished uncrafting by the tinker input count: Twilight Forest's
	 * {@code UncraftingSlot#onTake} consumes {@code numberOfInputItems} from the input slot on
	 * every successful take, while its own refusals (missing experience, marked ingredients)
	 * leave the input untouched. Counting the uncrafting grid itself does NOT work - a
	 * successful take immediately refills the grid through
	 * {@code UncraftingInputContainer#removeItem} → {@code slotsChanged}.
	 */
	@Unique
	private int uncraftinglimit$tinkerInputCount() {
		return this.tinkerInput.isEmpty() ? 0 : this.tinkerInput.getItem(0).getCount();
	}
}
