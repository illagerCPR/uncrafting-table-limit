package io.github.illagercpr.uncraftinglimit.gametest;

import io.github.illagercpr.uncraftinglimit.Config;
import io.github.illagercpr.uncraftinglimit.LimitMode;
import io.github.illagercpr.uncraftinglimit.UncraftingLimit;
import io.github.illagercpr.uncraftinglimit.policy.ItemCostPolicy;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import twilightforest.init.TFItems;
import twilightforest.inventory.UncraftingMenu;

import java.util.List;

import static io.github.illagercpr.uncraftinglimit.gametest.GameTestSupport.ASSEMBLY_SLOT_FIRST;
import static io.github.illagercpr.uncraftinglimit.gametest.GameTestSupport.createPlayer;
import static io.github.illagercpr.uncraftinglimit.gametest.GameTestSupport.gridItemCount;
import static io.github.illagercpr.uncraftinglimit.gametest.GameTestSupport.openMenu;
import static io.github.illagercpr.uncraftinglimit.gametest.GameTestSupport.placeTable;
import static io.github.illagercpr.uncraftinglimit.gametest.GameTestSupport.putChestInTinkerInput;

/**
 * M4 GameTests for the ITEM_COST limit mode: affordable take passes and deducts,
 * missing or insufficient cost refuses, creative players are exempt.
 */
@GameTestHolder(UncraftingLimit.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ItemCostGameTests {

	private static final List<String> REDSTONE_COST = List.of("minecraft:redstone:2");
	private static final List<String> DEFAULT_TORCHBERRIES = List.of("twilightforest:torchberries:1");

	private ItemCostGameTests() {
	}

	@GameTest(template = "smoke")
	public static void defaultCostEntryResolvesToTorchberries(final GameTestHelper helper) {
		List<ItemCostPolicy.CostEntry> entries = ItemCostPolicy.parseEntries(DEFAULT_TORCHBERRIES);
		helper.assertTrue(entries.size() == 1, "the default entry must parse");
		helper.assertTrue(entries.getFirst().item() == TFItems.TORCHBERRIES.get(),
				"the default cost item must be twilightforest:torchberries");
		helper.assertTrue(entries.getFirst().count() == 1, "the default cost count must be 1");
		helper.succeed();
	}

	@GameTest(template = "smoke")
	public static void affordableTakePassesAndDeducts(final GameTestHelper helper) {
		Config.INSTANCE.mode.set(LimitMode.ITEM_COST);
		Config.INSTANCE.itemCostEntries.set(REDSTONE_COST);
		try {
			placeTable(helper);
			ServerPlayer player = createPlayer(helper);
			player.getInventory().add(new ItemStack(Items.REDSTONE, 10));
			UncraftingMenu menu = openMenu(helper, player);
			putChestInTinkerInput(menu, 2);

			menu.clicked(ASSEMBLY_SLOT_FIRST, 0, ClickType.PICKUP, player);
			helper.assertTrue(!menu.getCarried().isEmpty(), "an affordable take must succeed");
			helper.assertTrue(countRedstone(player) == 8, "the cost must be deducted from the inventory");
		} finally {
			Config.INSTANCE.itemCostEntries.set(DEFAULT_TORCHBERRIES);
			Config.INSTANCE.mode.set(LimitMode.COOLDOWN);
			helper.succeed();
		}
	}

	@GameTest(template = "smoke")
	public static void missingCostRefuses(final GameTestHelper helper) {
		Config.INSTANCE.mode.set(LimitMode.ITEM_COST);
		Config.INSTANCE.itemCostEntries.set(REDSTONE_COST);
		try {
			placeTable(helper);
			ServerPlayer player = createPlayer(helper);
			UncraftingMenu menu = openMenu(helper, player);
			putChestInTinkerInput(menu, 2);
			int gridBefore = gridItemCount(menu);

			menu.clicked(ASSEMBLY_SLOT_FIRST, 0, ClickType.PICKUP, player);
			helper.assertTrue(gridItemCount(menu) == gridBefore, "a missing cost must refuse the take");
			helper.assertTrue(menu.getCarried().isEmpty(), "a refused take must not move items onto the cursor");
			helper.assertTrue(countRedstone(player) == 0, "a refused take must not consume anything");
		} finally {
			Config.INSTANCE.itemCostEntries.set(DEFAULT_TORCHBERRIES);
			Config.INSTANCE.mode.set(LimitMode.COOLDOWN);
			helper.succeed();
		}
	}

	@GameTest(template = "smoke")
	public static void insufficientCountRefuses(final GameTestHelper helper) {
		Config.INSTANCE.mode.set(LimitMode.ITEM_COST);
		Config.INSTANCE.itemCostEntries.set(REDSTONE_COST);
		try {
			placeTable(helper);
			ServerPlayer player = createPlayer(helper);
			player.getInventory().add(new ItemStack(Items.REDSTONE, 1));
			UncraftingMenu menu = openMenu(helper, player);
			putChestInTinkerInput(menu, 2);
			int gridBefore = gridItemCount(menu);

			menu.clicked(ASSEMBLY_SLOT_FIRST, 0, ClickType.PICKUP, player);
			helper.assertTrue(gridItemCount(menu) == gridBefore, "an insufficient count must refuse the take");
			helper.assertTrue(countRedstone(player) == 1, "the held item must not be touched");
		} finally {
			Config.INSTANCE.itemCostEntries.set(DEFAULT_TORCHBERRIES);
			Config.INSTANCE.mode.set(LimitMode.COOLDOWN);
			helper.succeed();
		}
	}

	@GameTest(template = "smoke")
	public static void creativeExemptSkipsCost(final GameTestHelper helper) {
		Config.INSTANCE.mode.set(LimitMode.ITEM_COST);
		Config.INSTANCE.itemCostEntries.set(REDSTONE_COST);
		Config.INSTANCE.exemptCreative.set(true);
		try {
			placeTable(helper);
			ServerPlayer player = createPlayer(helper);
			player.getAbilities().instabuild = true;
			UncraftingMenu menu = openMenu(helper, player);
			putChestInTinkerInput(menu, 2);

			menu.clicked(ASSEMBLY_SLOT_FIRST, 0, ClickType.PICKUP, player);
			helper.assertTrue(!menu.getCarried().isEmpty(),
					"creative players must bypass the item cost while exempt_creative is enabled");
		} finally {
			Config.INSTANCE.exemptCreative.set(false);
			Config.INSTANCE.itemCostEntries.set(DEFAULT_TORCHBERRIES);
			Config.INSTANCE.mode.set(LimitMode.COOLDOWN);
			helper.succeed();
		}
	}

	private static int countRedstone(ServerPlayer player) {
		int count = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(Items.REDSTONE)) {
				count += stack.getCount();
			}
		}
		return count;
	}
}
