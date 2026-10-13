package io.github.illagercpr.uncraftinglimit.mixin;

import io.github.illagercpr.uncraftinglimit.UncraftingDataComponents;
import io.github.illagercpr.uncraftinglimit.block.UncraftingTableBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import twilightforest.block.UncraftingTableBlock;

/**
 * Attaches a block entity to Twilight Forest's uncrafting table.
 *
 * <p>{@code UncraftingTableBlock} is a plain {@code Block}; this mixin adds the
 * {@link EntityBlock} interface plus the three methods the target needs:</p>
 * <ul>
 *   <li>{@code newBlockEntity} - creates the durability tracker (any later query through
 *       {@code Level#getBlockEntity} also creates it on demand, so pre-existing tables get
 *       one without migration);</li>
 *   <li>{@code setPlacedBy} - reads the durability component from the placed item, so
 *       picking a table up and placing it again does not reset it;</li>
 *   <li>{@code playerDestroy} - vanilla behaviour (stat + exhaustion + drops) with the
 *       remaining durability written onto the dropped item.</li>
 * </ul>
 *
 * <p>Public methods in a mixin are copied verbatim into the target class; together with
 * the {@code implements} clause this turns the plain block into an entity block without
 * touching Twilight Forest's bytecode beyond the merge.</p>
 */
@Mixin(value = UncraftingTableBlock.class, remap = false)
public abstract class UncraftingTableBlockMixin implements EntityBlock {

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new UncraftingTableBlockEntity(pos, state);
	}

	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		Integer placed = stack.get(UncraftingDataComponents.DURABILITY);
		if (placed != null && level.getBlockEntity(pos) instanceof UncraftingTableBlockEntity table) {
			table.setRemainingDurability(placed);
		}
	}

	public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
			@Nullable BlockEntity blockEntity, ItemStack tool) {
		player.awardStat(Stats.BLOCK_MINED.get(state.getBlock()));
		player.causeFoodExhaustion(0.005F);
		if (level instanceof ServerLevel serverLevel && blockEntity instanceof UncraftingTableBlockEntity table) {
			for (ItemStack drop : Block.getDrops(state, serverLevel, pos, blockEntity, player, tool)) {
				table.writeDurabilityTo(drop);
				Block.popResource(level, pos, drop);
			}
		} else {
			Block.dropResources(state, level, pos, blockEntity, player, tool);
		}
	}
}
