package io.github.illagercpr.uncraftinglimit.gametest;

import io.github.illagercpr.uncraftinglimit.Config;
import io.github.illagercpr.uncraftinglimit.UncraftingAttachments;
import io.github.illagercpr.uncraftinglimit.UncraftingLimit;
import io.github.illagercpr.uncraftinglimit.policy.CooldownPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import twilightforest.block.UncraftingTableBlock;
import twilightforest.init.TFBlocks;
import twilightforest.inventory.UncraftingMenu;

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

	private static final int TABLE_X = 2;
	private static final int TABLE_Y = 1;
	private static final int TABLE_Z = 2;

	/** Slot 11 is the first assembly-grid slot; with an empty hand it redirects into the grid. */
	private static final int ASSEMBLY_SLOT_FIRST = 11;
	private static final int GRID_SLOT_FIRST = 2;
	private static final int GRID_SLOT_LAST = 10;

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

	// ---- helpers -----------------------------------------------------------------

	private static void placeTable(GameTestHelper helper) {
		helper.setBlock(TABLE_X, TABLE_Y, TABLE_Z, TFBlocks.UNCRAFTING_TABLE.get());
	}

	/**
	 * All tests share the level's fake player, so every test starts from a clean slate:
	 * no leftover menu, inventory, experience flags or cooldown stamp.
	 */
	private static ServerPlayer createPlayer(GameTestHelper helper) {
		ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
		if (player.containerMenu != null && player.containerMenu != player.inventoryMenu) {
			player.containerMenu.setCarried(ItemStack.EMPTY);
			player.containerMenu.removed(player);
		}
		player.containerMenu = player.inventoryMenu;
		player.getInventory().clearContent();
		player.getAbilities().instabuild = false;
		player.experienceLevel = 100;
		player.experienceProgress = 0.0F;
		player.totalExperience = 0;
		player.setData(UncraftingAttachments.LAST_UNCRAFT_TIME, CooldownPolicy.NEVER);
		BlockPos tablePos = helper.absolutePos(new BlockPos(TABLE_X, TABLE_Y, TABLE_Z));
		player.moveTo(tablePos.getX() + 0.5, tablePos.getY(), tablePos.getZ() + 0.5, 0.0F, 0.0F);
		return player;
	}

	private static UncraftingMenu openMenu(GameTestHelper helper, ServerPlayer player) {
		BlockPos tablePos = helper.absolutePos(new BlockPos(TABLE_X, TABLE_Y, TABLE_Z));
		BlockState state = helper.getLevel().getBlockState(tablePos);
		helper.assertTrue(state.getBlock() instanceof UncraftingTableBlock, "an uncrafting table must be placed");
		// NeoForge's FakePlayer.openMenu is a deliberate no-op, so construct the menu exactly
		// the way UncraftingTableBlock#getMenuProvider does and attach it like openMenu would.
		UncraftingMenu menu = new UncraftingMenu(1, player.getInventory(), helper.getLevel(),
				ContainerLevelAccess.create(helper.getLevel(), tablePos));
		player.containerMenu = menu;
		helper.assertTrue(player.containerMenu == menu, "the menu must be attached to the player");
		return menu;
	}

	private static void putChestInTinkerInput(UncraftingMenu menu, int chestCount) {
		menu.tinkerInput.setItem(0, new ItemStack(Items.CHEST, chestCount));
		menu.slotsChanged(menu.tinkerInput);
	}

	/** Total item count over the uncrafting grid slots (2-10), read through the public slot list. */
	private static int gridItemCount(UncraftingMenu menu) {
		int count = 0;
		for (int slotNum = GRID_SLOT_FIRST; slotNum <= GRID_SLOT_LAST; slotNum++) {
			count += menu.getSlot(slotNum).getItem().getCount();
		}
		return count;
	}
}
