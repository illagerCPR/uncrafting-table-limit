package io.github.illagercpr.uncraftinglimit.block;

import io.github.illagercpr.uncraftinglimit.Config;
import io.github.illagercpr.uncraftinglimit.RefusalFeedback;
import io.github.illagercpr.uncraftinglimit.UncraftingBlockEntities;
import io.github.illagercpr.uncraftinglimit.UncraftingDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Durability tracker attached to the Twilight Forest uncrafting table.
 *
 * <p>The block entity is created through the {@code newBlockEntity} mixin on
 * {@code UncraftingTableBlock}. Durability travels with the item: it is written onto the
 * drops when the block is mined ({@code playerDestroy} mixin) and read back when the item
 * is placed again ({@code setPlacedBy} mixin), so picking a table up never resets it.</p>
 */
public class UncraftingTableBlockEntity extends BlockEntity {

	private static final String TAG_DURABILITY = "DurabilityRemaining";

	private int remainingDurability;

	public UncraftingTableBlockEntity(BlockPos pos, BlockState state) {
		this(UncraftingBlockEntities.UNCRAFTING_TABLE.get(), pos, state);
	}

	public UncraftingTableBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		this.remainingDurability = Config.SPEC.isLoaded() ? Config.INSTANCE.maxDurability.get() : 1000;
	}

	public int remainingDurability() {
		return this.remainingDurability;
	}

	public void setRemainingDurability(int value) {
		this.remainingDurability = Math.max(0, value);
		this.setChanged();
		this.syncToClient();
	}

	/** Consumes one use and handles the warning threshold plus the depletion behaviour. */
	public void consumeUse(Player actor) {
		int perUse = Config.INSTANCE.durabilityPerUse.get();
		this.remainingDurability = Math.max(0, this.remainingDurability - perUse);
		this.setChanged();
		this.syncToClient();

		Level level = this.level;
		BlockPos pos = this.worldPosition;
		if (level == null) {
			return;
		}
		if (this.remainingDurability <= 0) {
			if (Config.INSTANCE.durabilityDestroyOnDeplete.get()) {
				level.destroyBlock(pos, false, null, 512);
				RefusalFeedback.notifyDenied(actor, level,
						() -> Component.translatable("message.uncraftinglimit.durability_destroyed"));
			} else {
				RefusalFeedback.notifyDenied(actor, level,
						() -> Component.translatable("message.uncraftinglimit.durability_exhausted"));
			}
		} else if (this.remainingDurability <= Config.INSTANCE.durabilityWarnThreshold.get()) {
			RefusalFeedback.notifyDenied(actor, level, () -> Component.translatable(
					"message.uncraftinglimit.durability_warn", this.remainingDurability));
		}
	}

	/** Writes the remaining durability onto an item stack (drop side). */
	public void writeDurabilityTo(ItemStack stack) {
		stack.set(UncraftingDataComponents.DURABILITY.get(), this.remainingDurability);
	}

	/** Pushes the current durability to the clients tracking this block entity. */
	private void syncToClient() {
		Level level = this.level;
		if (level != null) {
			level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		// The save payload is tiny (one int) and lets the client GUI show the remaining
		// durability live; the vanilla default onDataPacket applies it on the client.
		return this.saveWithoutMetadata(registries);
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putInt(TAG_DURABILITY, this.remainingDurability);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		if (tag.contains(TAG_DURABILITY)) {
			this.remainingDurability = tag.getInt(TAG_DURABILITY);
		}
	}

	@Override
	protected void applyImplicitComponents(BlockEntity.DataComponentInput input) {
		super.applyImplicitComponents(input);
		Integer placed = input.get(UncraftingDataComponents.DURABILITY);
		if (placed != null) {
			this.remainingDurability = placed;
		}
	}

	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder builder) {
		super.collectImplicitComponents(builder);
		builder.set(UncraftingDataComponents.DURABILITY.get(), this.remainingDurability);
	}
}
