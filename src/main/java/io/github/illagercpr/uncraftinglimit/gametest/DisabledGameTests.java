package io.github.illagercpr.uncraftinglimit.gametest;

import io.github.illagercpr.uncraftinglimit.Config;
import io.github.illagercpr.uncraftinglimit.LimitMode;
import io.github.illagercpr.uncraftinglimit.UncraftingAttachments;
import io.github.illagercpr.uncraftinglimit.UncraftingLimit;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import twilightforest.inventory.UncraftingMenu;

import static io.github.illagercpr.uncraftinglimit.gametest.GameTestSupport.ASSEMBLY_SLOT_FIRST;
import static io.github.illagercpr.uncraftinglimit.gametest.GameTestSupport.createPlayer;
import static io.github.illagercpr.uncraftinglimit.gametest.GameTestSupport.gridItemCount;
import static io.github.illagercpr.uncraftinglimit.gametest.GameTestSupport.openMenu;
import static io.github.illagercpr.uncraftinglimit.gametest.GameTestSupport.placeTable;
import static io.github.illagercpr.uncraftinglimit.gametest.GameTestSupport.putChestInTinkerInput;
import static io.github.illagercpr.uncraftinglimit.gametest.GameTestSupport.tablePos;

/**
 * M2 GameTests for the DISABLED limit mode (docs/01 G5) plus the mandatory regression
 * that the normal crafting / recrafting path stays free under every limit.
 */
@GameTestHolder(UncraftingLimit.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DisabledGameTests {

	private DisabledGameTests() {
	}

	@GameTest(template = "smoke")
	public static void g5DisabledRefusesGridTakeButOpensMenu(final GameTestHelper helper) {
		Config.INSTANCE.mode.set(LimitMode.DISABLED);
		try {
			placeTable(helper);
			ServerPlayer player = createPlayer(helper);
			UncraftingMenu menu = openMenu(helper, player);
			helper.assertTrue(player.containerMenu instanceof UncraftingMenu,
					"the menu must still open in DISABLED mode (preview is allowed)");

			putChestInTinkerInput(menu, 2);
			int inputBefore = menu.tinkerInput.getItem(0).getCount();
			int gridBefore = gridItemCount(menu);

			menu.clicked(ASSEMBLY_SLOT_FIRST, 0, ClickType.PICKUP, player);
			helper.assertTrue(gridItemCount(menu) == gridBefore, "DISABLED mode must refuse taking uncrafted items");
			helper.assertTrue(menu.getCarried().isEmpty(), "a refused click must not move items onto the cursor");
			helper.assertTrue(menu.tinkerInput.getItem(0).getCount() == inputBefore,
					"a refused click must not consume the tinker input");
			helper.assertTrue(player.experienceLevel == 100, "a refused click must not charge experience");
		} finally {
			Config.INSTANCE.mode.set(LimitMode.COOLDOWN);
			helper.succeed();
		}
	}

	/**
	 * The recrafting / normal crafting path must stay untouched by every limit (docs/02 M2):
	 * even with an active cooldown or DISABLED mode, placing planks into the assembly grid
	 * and taking the crafted result must work exactly like a vanilla crafting table.
	 */
	@GameTest(template = "smoke")
	public static void recraftingPathStaysFreeUnderLimits(final GameTestHelper helper) {
		placeTable(helper);
		ServerPlayer player = createPlayer(helper);

		// Scenario 1: COOLDOWN mode with an active cooldown.
		UncraftingMenu menu = openMenu(helper, player);
		player.setData(UncraftingAttachments.LAST_UNCRAFT_TIME, helper.getLevel().getGameTime());
		craftSticks(helper, menu, player);
		closeAndReopen(helper, player);

		// Scenario 2: DISABLED mode.
		Config.INSTANCE.mode.set(LimitMode.DISABLED);
		try {
			menu = openMenu(helper, player);
			craftSticks(helper, menu, player);
		} finally {
			Config.INSTANCE.mode.set(LimitMode.COOLDOWN);
		}
		helper.succeed();
	}

	@GameTest(template = "smoke")
	public static void blockMenuOpenDeniesTableInteraction(final GameTestHelper helper) {
		Config.INSTANCE.mode.set(LimitMode.DISABLED);
		Config.INSTANCE.disabledBlockMenuOpen.set(true);
		try {
			placeTable(helper);
			ServerPlayer player = createPlayer(helper);

			PlayerInteractEvent.RightClickBlock event = new PlayerInteractEvent.RightClickBlock(
					player, InteractionHand.MAIN_HAND, tablePos(helper),
					new BlockHitResult(Vec3.ZERO, net.minecraft.core.Direction.UP, tablePos(helper), false));
			net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);

			helper.assertTrue(event.getUseBlock() == TriState.FALSE,
					"block_menu_open must deny the table interaction");
			helper.assertTrue(!(player.containerMenu instanceof UncraftingMenu),
					"no menu may be attached when the interaction is denied");
		} finally {
			Config.INSTANCE.disabledBlockMenuOpen.set(false);
			Config.INSTANCE.mode.set(LimitMode.COOLDOWN);
			helper.succeed();
		}
	}

	/** Places two planks (middle column), takes the crafted sticks off the result slot. */
	private static void craftSticks(GameTestHelper helper, UncraftingMenu menu, ServerPlayer player) {
		placeInAssembly(menu, player, 12); // container slot 1
		placeInAssembly(menu, player, 15); // container slot 4

		helper.assertTrue(menu.getSlot(1).getItem().is(Items.STICK),
				"two vertical planks must yield sticks on the result slot");

		int inputExperience = player.experienceLevel;
		menu.clicked(1, 0, ClickType.PICKUP, player);
		helper.assertTrue(menu.getCarried().is(Items.STICK),
				"taking the crafted result must work under every limit mode");
		helper.assertTrue(player.experienceLevel == inputExperience,
				"normal crafting must never charge experience");
	}

	private static void placeInAssembly(UncraftingMenu menu, ServerPlayer player, int slotNum) {
		menu.setCarried(new ItemStack(Items.OAK_PLANKS, 1));
		menu.clicked(slotNum, 0, ClickType.PICKUP, player);
		// A carried stack is placed wholesale by a left click, emptying the cursor.
		menu.setCarried(ItemStack.EMPTY);
	}

	private static void closeAndReopen(GameTestHelper helper, ServerPlayer player) {
		if (player.containerMenu != null && player.containerMenu != player.inventoryMenu) {
			player.containerMenu.setCarried(ItemStack.EMPTY);
			player.containerMenu.removed(player);
			player.containerMenu = player.inventoryMenu;
		}
		player.getInventory().clearContent();
	}
}
