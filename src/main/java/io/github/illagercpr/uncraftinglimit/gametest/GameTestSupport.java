package io.github.illagercpr.uncraftinglimit.gametest;

import io.github.illagercpr.uncraftinglimit.UncraftingAttachments;
import io.github.illagercpr.uncraftinglimit.policy.CooldownPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import twilightforest.block.UncraftingTableBlock;
import twilightforest.init.TFBlocks;
import twilightforest.inventory.UncraftingMenu;

/**
 * Shared plumbing for the uncrafting-menu GameTests. All tests share the level's fake
 * player, so every test starts from a clean slate.
 */
public final class GameTestSupport {

	public static final int TABLE_X = 2;
	public static final int TABLE_Y = 1;
	public static final int TABLE_Z = 2;

	/** Slot 11 is the first assembly-grid slot; with an empty hand it redirects into the grid. */
	public static final int ASSEMBLY_SLOT_FIRST = 11;
	public static final int GRID_SLOT_FIRST = 2;
	public static final int GRID_SLOT_LAST = 10;

	private GameTestSupport() {
	}

	public static void placeTable(GameTestHelper helper) {
		helper.setBlock(TABLE_X, TABLE_Y, TABLE_Z, TFBlocks.UNCRAFTING_TABLE.get());
	}

	public static ServerPlayer createPlayer(GameTestHelper helper) {
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
		BlockPos tablePos = tablePos(helper);
		player.moveTo(tablePos.getX() + 0.5, tablePos.getY(), tablePos.getZ() + 0.5, 0.0F, 0.0F);
		return player;
	}

	/**
	 * NeoForge's {@code FakePlayer.openMenu} is a deliberate no-op, so construct the menu
	 * exactly the way {@code UncraftingTableBlock#getMenuProvider} does and attach it like
	 * {@code openMenu} would.
	 */
	public static UncraftingMenu openMenu(GameTestHelper helper, ServerPlayer player) {
		BlockPos tablePos = tablePos(helper);
		BlockState state = helper.getLevel().getBlockState(tablePos);
		helper.assertTrue(state.getBlock() instanceof UncraftingTableBlock, "an uncrafting table must be placed");
		UncraftingMenu menu = new UncraftingMenu(1, player.getInventory(), helper.getLevel(),
				ContainerLevelAccess.create(helper.getLevel(), tablePos));
		player.containerMenu = menu;
		helper.assertTrue(player.containerMenu == menu, "the menu must be attached to the player");
		return menu;
	}

	public static BlockPos tablePos(GameTestHelper helper) {
		return helper.absolutePos(new BlockPos(TABLE_X, TABLE_Y, TABLE_Z));
	}

	public static void putChestInTinkerInput(UncraftingMenu menu, int chestCount) {
		menu.tinkerInput.setItem(0, new ItemStack(Items.CHEST, chestCount));
		menu.slotsChanged(menu.tinkerInput);
	}

	/** Total item count over the uncrafting grid slots (2-10), read through the public slot list. */
	public static int gridItemCount(UncraftingMenu menu) {
		int count = 0;
		for (int slotNum = GRID_SLOT_FIRST; slotNum <= GRID_SLOT_LAST; slotNum++) {
			count += menu.getSlot(slotNum).getItem().getCount();
		}
		return count;
	}
}
