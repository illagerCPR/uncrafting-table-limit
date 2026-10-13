package io.github.illagercpr.uncraftinglimit.gametest;

import io.github.illagercpr.uncraftinglimit.Config;
import io.github.illagercpr.uncraftinglimit.LimitMode;
import io.github.illagercpr.uncraftinglimit.UncraftingDataComponents;
import io.github.illagercpr.uncraftinglimit.UncraftingLimit;
import io.github.illagercpr.uncraftinglimit.block.UncraftingTableBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import twilightforest.init.TFBlocks;
import twilightforest.inventory.UncraftingMenu;

import static io.github.illagercpr.uncraftinglimit.gametest.GameTestSupport.ASSEMBLY_SLOT_FIRST;
import static io.github.illagercpr.uncraftinglimit.gametest.GameTestSupport.createPlayer;
import static io.github.illagercpr.uncraftinglimit.gametest.GameTestSupport.gridItemCount;
import static io.github.illagercpr.uncraftinglimit.gametest.GameTestSupport.openMenu;
import static io.github.illagercpr.uncraftinglimit.gametest.GameTestSupport.placeTable;
import static io.github.illagercpr.uncraftinglimit.gametest.GameTestSupport.putChestInTinkerInput;
import static io.github.illagercpr.uncraftinglimit.gametest.GameTestSupport.tablePos;

/**
 * M3 GameTests for the DURABILITY limit mode (docs/01 G6-G7).
 */
@GameTestHolder(UncraftingLimit.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DurabilityGameTests {

	private DurabilityGameTests() {
	}

	@GameTest(template = "smoke")
	public static void blockEntityAttachedToTable(final GameTestHelper helper) {
		placeTable(helper);
		BlockEntity be = helper.getLevel().getBlockEntity(tablePos(helper));
		helper.assertTrue(be instanceof UncraftingTableBlockEntity,
				"the uncrafting table must carry our block entity");
		helper.assertTrue(((UncraftingTableBlockEntity) be).remainingDurability() == Config.INSTANCE.maxDurability.get(),
				"a fresh table must start at the configured max durability");
		helper.succeed();
	}

	@GameTest(template = "smoke")
	public static void g6DurabilityConsumedPerUncrafting(final GameTestHelper helper) {
		Config.INSTANCE.mode.set(LimitMode.DURABILITY);
		try {
			placeTable(helper);
			ServerPlayer player = createPlayer(helper);
			UncraftingMenu menu = openMenu(helper, player);
			UncraftingTableBlockEntity table = tableAt(helper);
			table.setRemainingDurability(5);

			putChestInTinkerInput(menu, 4);
			menu.clicked(ASSEMBLY_SLOT_FIRST, 0, ClickType.PICKUP, player);
			helper.assertTrue(!menu.getCarried().isEmpty(), "the first uncrafting must succeed");
			helper.assertTrue(table.remainingDurability() == 4, "each uncrafting must consume exactly one use");

			// Re-prime and uncraft again.
			menu.setCarried(ItemStack.EMPTY);
			menu.assemblyMatrix.clearContent();
			putChestInTinkerInput(menu, 4);
			menu.clicked(ASSEMBLY_SLOT_FIRST, 0, ClickType.PICKUP, player);
			helper.assertTrue(table.remainingDurability() == 3, "the second uncrafting must consume another use");
		} finally {
			Config.INSTANCE.mode.set(LimitMode.COOLDOWN);
			helper.succeed();
		}
	}

	@GameTest(template = "smoke")
	public static void g7DestroyOnDeplete(final GameTestHelper helper) {
		helper.assertTrue(Config.INSTANCE.durabilityDestroyOnDeplete.get(),
				"this test expects the default destroy_on_deplete = true");
		Config.INSTANCE.mode.set(LimitMode.DURABILITY);
		try {
			placeTable(helper);
			ServerPlayer player = createPlayer(helper);
			UncraftingMenu menu = openMenu(helper, player);
			UncraftingTableBlockEntity table = tableAt(helper);
			table.setRemainingDurability(1);

			putChestInTinkerInput(menu, 2);
			menu.clicked(ASSEMBLY_SLOT_FIRST, 0, ClickType.PICKUP, player);
			helper.assertTrue(!menu.getCarried().isEmpty(), "the final uncrafting must still succeed");
			helper.assertTrue(helper.getLevel().getBlockState(tablePos(helper)).isAir(),
					"a depleted table must be destroyed (no drop)");
		} finally {
			Config.INSTANCE.mode.set(LimitMode.COOLDOWN);
			helper.succeed();
		}
	}

	@GameTest(template = "smoke")
	public static void depletedTableRefusesInDisableMode(final GameTestHelper helper) {
		Config.INSTANCE.mode.set(LimitMode.DURABILITY);
		Config.INSTANCE.durabilityDestroyOnDeplete.set(false);
		try {
			placeTable(helper);
			ServerPlayer player = createPlayer(helper);
			UncraftingMenu menu = openMenu(helper, player);
			UncraftingTableBlockEntity table = tableAt(helper);
			table.setRemainingDurability(1);

			putChestInTinkerInput(menu, 4);
			menu.clicked(ASSEMBLY_SLOT_FIRST, 0, ClickType.PICKUP, player);
			helper.assertTrue(!menu.getCarried().isEmpty(), "the final uncrafting must succeed");
			helper.assertTrue(!helper.getLevel().getBlockState(tablePos(helper)).isAir(),
					"with destroy_on_deplete = false the table must remain in place");
			helper.assertTrue(table.remainingDurability() == 0, "the durability must be exhausted");

			// Re-prime; the depleted table must refuse further uncrafting.
			menu.setCarried(ItemStack.EMPTY);
			menu.assemblyMatrix.clearContent();
			putChestInTinkerInput(menu, 4);
			int gridBefore = gridItemCount(menu);
			menu.clicked(ASSEMBLY_SLOT_FIRST, 0, ClickType.PICKUP, player);
			helper.assertTrue(gridItemCount(menu) == gridBefore,
					"a depleted table must refuse further uncrafting");
		} finally {
			Config.INSTANCE.durabilityDestroyOnDeplete.set(true);
			Config.INSTANCE.mode.set(LimitMode.COOLDOWN);
			helper.succeed();
		}
	}

	@GameTest(template = "smoke")
	public static void durabilityTravelsWithDrops(final GameTestHelper helper) {
		placeTable(helper);
		ServerPlayer player = createPlayer(helper);
		UncraftingTableBlockEntity table = tableAt(helper);
		table.setRemainingDurability(42);

		// Simulate survival mining: our playerDestroy override must transfer the durability.
		helper.getLevel().getBlockState(tablePos(helper)).getBlock().playerDestroy(
				helper.getLevel(), player, tablePos(helper),
				helper.getLevel().getBlockState(tablePos(helper)), table, new ItemStack(Items.DIAMOND_PICKAXE));

		ItemStack dropped = findDroppedTableItem(helper);
		helper.assertTrue(dropped != null, "mining the table must drop an item");
		Integer durability = dropped.get(UncraftingDataComponents.DURABILITY);
		helper.assertTrue(durability != null && durability == 42,
				"the dropped item must carry the remaining durability");

		// Round trip: placing such an item must restore the durability onto the new block entity.
		BlockPos secondPos = helper.absolutePos(new BlockPos(3, 1, 3));
		helper.setBlock(new BlockPos(3, 1, 3), TFBlocks.UNCRAFTING_TABLE.get());
		BlockEntity fresh = helper.getLevel().getBlockEntity(secondPos);
		helper.assertTrue(fresh instanceof UncraftingTableBlockEntity, "the new table must carry a block entity");
		fresh.applyComponentsFromItemStack(dropped);
		helper.assertTrue(((UncraftingTableBlockEntity) fresh).remainingDurability() == 42,
				"placing the item must restore the durability");
		helper.succeed();
	}

	private static UncraftingTableBlockEntity tableAt(GameTestHelper helper) {
		BlockEntity be = helper.getLevel().getBlockEntity(tablePos(helper));
		helper.assertTrue(be instanceof UncraftingTableBlockEntity, "the table must have our block entity");
		return (UncraftingTableBlockEntity) be;
	}

	private static ItemStack findDroppedTableItem(GameTestHelper helper) {
		ItemLike tableItem = TFBlocks.UNCRAFTING_TABLE.get().asItem();
		for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class,
				new AABB(tablePos(helper)).inflate(2.0))) {
			if (entity.getItem().is(tableItem.asItem())) {
				return entity.getItem();
			}
		}
		return null;
	}
}
