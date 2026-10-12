package io.github.illagercpr.uncraftinglimit.gametest;

import io.github.illagercpr.uncraftinglimit.UncraftingLimit;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * M0 GameTest smoke test: proves the headless harness works end to end.
 * Real mechanic tests (cooldown, disabled, durability, item cost) arrive with M1+.
 */
@GameTestHolder(UncraftingLimit.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SmokeGameTests {

	private SmokeGameTests() {
	}

	@GameTest(template = "smoke")
	public static void smokePlaceAndVerify(final GameTestHelper helper) {
		helper.setBlock(1, 1, 1, Blocks.STONE);
		helper.succeedWhenBlockPresent(Blocks.STONE, 1, 1, 1);
	}
}
