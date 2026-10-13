package io.github.illagercpr.uncraftinglimit.policy;

import io.github.illagercpr.uncraftinglimit.UncraftingLimit;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Cost configuration for the ITEM_COST mode: a list of {@code namespace:item[:count]}
 * entries, any single affordable entry satisfies the cost.
 */
public final class ItemCostPolicy {

	/** One configured cost line: pay {@code count} of {@code item}. */
	public record CostEntry(Item item, int count) {
	}

	/** Warn about every malformed or unknown entry only once. */
	private static final Set<String> WARNED_ENTRIES = new HashSet<>();

	private ItemCostPolicy() {
	}

	/** Parses the configured entries; unknown or malformed lines are skipped with a one-time warning. */
	public static List<CostEntry> parseEntries(List<? extends String> rawEntries) {
		List<CostEntry> entries = new ArrayList<>();
		if (rawEntries.isEmpty()) {
			warnOnce("<empty>", "item_cost.item_cost_entries is empty; ITEM_COST mode refuses every uncrafting");
			return entries;
		}
		for (String raw : rawEntries) {
			CostEntry entry = parseEntry(raw);
			if (entry != null) {
				entries.add(entry);
			}
		}
		return entries;
	}

	private static CostEntry parseEntry(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String[] parts = raw.split(":");
		if (parts.length != 2 && parts.length != 3) {
			warnOnce(raw, "expected 'namespace:item[:count]', skipping");
			return null;
		}
		ResourceLocation id = ResourceLocation.tryParse(parts.length == 3 ? parts[0] + ":" + parts[1] : raw);
		if (id == null) {
			warnOnce(raw, "not a valid item id, skipping");
			return null;
		}
		Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
		if (item == null) {
			warnOnce(raw, "unknown item, skipping");
			return null;
		}
		int count = 1;
		if (parts.length == 3) {
			try {
				count = Integer.parseInt(parts[2]);
			} catch (NumberFormatException ignored) {
				warnOnce(raw, "count is not a number, using 1");
				count = 1;
			}
		}
		if (count <= 0) {
			warnOnce(raw, "count must be positive, skipping");
			return null;
		}
		return new CostEntry(item, count);
	}

	private static void warnOnce(String key, String detail) {
		if (WARNED_ENTRIES.add(key)) {
			UncraftingLimit.LOGGER.warn("[item_cost] Entry '{}': {}", key, detail);
		}
	}

	/** @return the first configured entry the player can actually pay. */
	public static Optional<CostEntry> findAffordable(Player player, List<CostEntry> entries) {
		return entries.stream().filter(entry -> countItem(player, entry.item()) >= entry.count()).findFirst();
	}

	/** Removes the cost from the player's inventory; call only after {@link #findAffordable}. */
	public static void consume(Player player, CostEntry entry) {
		int remaining = entry.count();
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.isEmpty() && stack.is(entry.item())) {
				int taken = Math.min(remaining, stack.getCount());
				stack.shrink(taken);
				remaining -= taken;
			}
		}
	}

	private static int countItem(Player player, Item item) {
		int count = 0;
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.isEmpty() && stack.is(item)) {
				count += stack.getCount();
			}
		}
		return count;
	}
}
