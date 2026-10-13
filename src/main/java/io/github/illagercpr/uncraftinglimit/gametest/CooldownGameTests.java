package io.github.illagercpr.uncraftinglimit.gametest;

import io.github.illagercpr.uncraftinglimit.Config;
import io.github.illagercpr.uncraftinglimit.UncraftingAttachments;
import io.github.illagercpr.uncraftinglimit.UncraftingLimit;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import twilightforest.inventory.UncraftingMenu;

import static io.github.illagercpr.uncraftinglimit.gametest.GameTestSupport.ASSEMBLY_SLOT_FIRST;
import static io.github.illagercpr.uncraftinglimit.gametest.GameTestSupport.createPlayer;
import static io.github.illagercpr.uncraftinglimit.gametest.GameTestSupport.gridItemCount;
import static io.github.illagercpr.uncraftinglimit.gametest.GameTestSupport.openMenu;
import static io.github.illagercpr.uncraftinglimit.gametest.GameTestSupport.placeTable;
import static io.github.illagercpr.uncraftinglimit.gametest.GameTestSupport.putChestInTinkerInput;

/**
 * M1 GameTests for the COOLDOWN limit mode (docs/01 G1-G4 + G8).
 *
 * <p>Each test drives the real {@link UncraftingMenu#clicked} chokepoint with a fake
 * player, exactly like the network handler would. A denial therefore proves the mixin
 * is applied and intercepting - the behavioral equivalent of the M0 probe.</p>
 */
@GameTestHolder(UncraftingLimit.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CooldownGameTests {

	private CooldownGameTests() {
	}

	@GameTest(template = "smoke")
	public static void g1OpenUncraftingMenu(final GameTestHelper helper) {
		placeTable(helper);
		ServerPlayer player = createPlayer(helper);
		openMenu(helper, player);
		helper.assertTrue(player.containerMenu instanceof UncraftingMenu,
				"opening the table must produce an UncraftingMenu");
		helper.assertTrue(((UncraftingMenu) player.containerMenu).slots.size() == 56,
				"the uncrafting menu must expose its 56 slots");
		helper.succeed();
	}

	@GameTest(template = "smoke")
	public static void g2PrimeUncraftingPreview(final GameTestHelper helper) {
		placeTable(helper);
		ServerPlayer player = createPlayer(helper);
		UncraftingMenu menu = openMenu(helper, player);
		putChestInTinkerInput(menu, 2);

		helper.assertTrue(menu.storedGhostRecipe != null, "a chest must resolve to an uncrafting recipe");
		helper.assertTrue(menu.getUncraftingCost() > 0, "the uncrafting cost must be computed for the preview");
		helper.assertTrue(gridItemCount(menu) > 0, "the uncrafting grid must hold the recipe ingredients");
		helper.succeed();
	}

	@GameTest(template = "smoke")
	public static void g3CooldownDeniesSecondUncrafting(final GameTestHelper helper) {
		placeTable(helper);
		ServerPlayer player = createPlayer(helper);
		UncraftingMenu menu = openMenu(helper, player);
		putChestInTinkerInput(menu, 4);
		int cost = menu.getUncraftingCost();

		// First uncrafting: the redirected take succeeds and starts the cooldown.
		menu.clicked(ASSEMBLY_SLOT_FIRST, 0, ClickType.PICKUP, player);
		helper.assertTrue(!menu.getCarried().isEmpty(), "the first uncrafting must put an ingredient on the cursor");
		helper.assertTrue(player.experienceLevel == 100 - cost,
				"a successful uncrafting must charge the Twilight Forest experience cost exactly once");
		long afterFirst = player.getData(UncraftingAttachments.LAST_UNCRAFT_TIME);
		helper.assertTrue(afterFirst == helper.getLevel().getGameTime(),
				"a successful uncrafting must start the cooldown");

		// Restore the pre-take state: stow the cursor, empty the assembly grid, refill the preview.
		menu.setCarried(ItemStack.EMPTY);
		menu.assemblyMatrix.clearContent();
		putChestInTinkerInput(menu, 4);

		int gridBefore = gridItemCount(menu);
		helper.assertTrue(gridBefore > 0, "the uncrafting preview must be refilled for the second attempt");

		// Second uncrafting while cooling down: refused by the mixin.
		menu.clicked(ASSEMBLY_SLOT_FIRST, 0, ClickType.PICKUP, player);
		helper.assertTrue(gridItemCount(menu) == gridBefore, "the second uncrafting must be refused while cooling down");
		helper.assertTrue(menu.getCarried().isEmpty(), "a refused click must not move items onto the cursor");
		helper.assertTrue(player.experienceLevel == 100 - cost, "a refused click must not charge experience");
		helper.assertTrue(player.getData(UncraftingAttachments.LAST_UNCRAFT_TIME) == afterFirst,
				"a refused click must not refresh the cooldown");
		helper.succeed();
	}

	@GameTest(template = "smoke")
	public static void g4ExpiredCooldownAllowsUncrafting(final GameTestHelper helper) {
		placeTable(helper);
		ServerPlayer player = createPlayer(helper);
		UncraftingMenu menu = openMenu(helper, player);
		putChestInTinkerInput(menu, 2);

		long now = helper.getLevel().getGameTime();
		player.setData(UncraftingAttachments.LAST_UNCRAFT_TIME, now - Config.INSTANCE.cooldownTicks.get());

		menu.clicked(ASSEMBLY_SLOT_FIRST, 0, ClickType.PICKUP, player);
		helper.assertTrue(!menu.getCarried().isEmpty(), "an expired cooldown must allow uncrafting");
		helper.assertTrue(player.getData(UncraftingAttachments.LAST_UNCRAFT_TIME) == helper.getLevel().getGameTime(),
				"a successful uncrafting must restart the cooldown");
		helper.succeed();
	}

	@GameTest(template = "smoke")
	public static void g8CreativeExemptSkipsCooldown(final GameTestHelper helper) {
		Config.INSTANCE.exemptCreative.set(true);
		try {
			placeTable(helper);
			ServerPlayer player = createPlayer(helper);
			player.getAbilities().instabuild = true;
			UncraftingMenu menu = openMenu(helper, player);
			putChestInTinkerInput(menu, 2);

			player.setData(UncraftingAttachments.LAST_UNCRAFT_TIME, helper.getLevel().getGameTime());

			menu.clicked(ASSEMBLY_SLOT_FIRST, 0, ClickType.PICKUP, player);
			helper.assertTrue(!menu.getCarried().isEmpty(),
					"creative players must bypass the cooldown while exempt_creative is enabled");
		} finally {
			Config.INSTANCE.exemptCreative.set(false);
			helper.succeed();
		}
	}
}
