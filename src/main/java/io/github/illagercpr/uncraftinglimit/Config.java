package io.github.illagercpr.uncraftinglimit;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

/**
 * Common (server-authoritative) configuration.
 *
 * <p>All limiting happens server side, so the client never needs these values.</p>
 */
public final class Config {

	public static final ModConfigSpec SPEC;
	public static final Config INSTANCE;

	static {
		Pair<Config, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(Config::new);
		INSTANCE = pair.getLeft();
		SPEC = pair.getRight();
	}

	// ---- general ----
	public final ModConfigSpec.EnumValue<LimitMode> mode;
	public final ModConfigSpec.BooleanValue exemptCreative;
	public final ModConfigSpec.BooleanValue notifyPlayer;
	public final ModConfigSpec.BooleanValue feedbackSound;
	public final ModConfigSpec.IntValue feedbackMinInterval;

	// ---- cooldown ----
	public final ModConfigSpec.IntValue cooldownTicks;

	// ---- disabled ----
	public final ModConfigSpec.BooleanValue disabledBlockMenuOpen;

	// ---- durability ----
	public final ModConfigSpec.IntValue maxDurability;
	public final ModConfigSpec.IntValue durabilityPerUse;
	public final ModConfigSpec.BooleanValue durabilityDestroyOnDeplete;
	public final ModConfigSpec.IntValue durabilityWarnThreshold;

	// ---- item cost ----
	public final ModConfigSpec.ConfigValue<List<? extends String>> itemCostEntries;

	private Config(ModConfigSpec.Builder builder) {
		builder.comment("General settings for the uncrafting table limits.").push("general");
		this.mode = builder
				.comment("Which limiting method is active. Only one method can be active at a time.",
						"COOLDOWN    - after a successful uncrafting the player must wait 'cooldown.cooldown_ticks' ticks",
						"DISABLED    - items can no longer be taken out of the uncrafting grid",
						"DURABILITY  - each uncrafting table has its own durability, consumed per uncrafting",
						"ITEM_COST   - each uncrafting additionally consumes items listed in 'item_cost.item_cost_entries'")
				.translation("uncraftinglimit.config.mode")
				.defineEnum("mode", LimitMode.COOLDOWN);
		this.exemptCreative = builder
				.comment("If true, players in creative mode are not limited at all.")
				.translation("uncraftinglimit.config.exempt_creative")
				.define("exempt_creative", false);
		this.notifyPlayer = builder
				.comment("If true, the player receives an action bar message when an uncrafting is refused.")
				.translation("uncraftinglimit.config.notify_player")
				.define("notify_player", true);
		this.feedbackSound = builder
				.comment("If true, a sound cue plays when an uncrafting is refused. The action bar text alone",
						"can be hard to notice while the table's GUI is open.")
				.translation("uncraftinglimit.config.feedback_sound")
				.define("feedback_sound", true);
		this.feedbackMinInterval = builder
				.comment("Minimum amount of ticks between two refusal messages, to avoid spamming the action bar.")
				.translation("uncraftinglimit.config.feedback_min_interval")
				.defineInRange("feedback_min_interval", 20, 0, 1200);
		builder.pop();

		builder.comment("Settings for mode = COOLDOWN.").push("cooldown");
		this.cooldownTicks = builder
				.comment("Ticks the player has to wait after a successful uncrafting. 24000 ticks = one in-game day.",
						"Set to 0 to disable the waiting time (equivalent to having no limit).")
				.translation("uncraftinglimit.config.cooldown_ticks")
				.defineInRange("cooldown_ticks", 24000, 0, Integer.MAX_VALUE);
		builder.pop();

		builder.comment("Settings for mode = DISABLED.").push("disabled");
		this.disabledBlockMenuOpen = builder
				.comment("If true, the uncrafting table cannot even be opened while this mode is active.",
						"If false (default), the interface opens and recipes are previewed, but nothing can be taken out.")
				.translation("uncraftinglimit.config.disabled_block_menu_open")
				.define("block_menu_open", false);
		builder.pop();

		builder.comment("Settings for mode = DURABILITY.").push("durability");
		this.maxDurability = builder
				.comment("Durability of a freshly placed uncrafting table.")
				.translation("uncraftinglimit.config.max_durability")
				.defineInRange("max_durability", 1000, 1, Integer.MAX_VALUE);
		this.durabilityPerUse = builder
				.comment("Durability consumed by a single uncrafting (not per resulting item).")
				.translation("uncraftinglimit.config.durability_per_use")
				.defineInRange("durability_per_use", 1, 1, Integer.MAX_VALUE);
		this.durabilityDestroyOnDeplete = builder
				.comment("If true, the table is destroyed when its durability reaches zero.",
						"If false, the table stays but can no longer be used.")
				.translation("uncraftinglimit.config.destroy_on_deplete")
				.define("destroy_on_deplete", true);
		this.durabilityWarnThreshold = builder
				.comment("Start warning the player once the remaining durability drops to or below this value.")
				.translation("uncraftinglimit.config.warn_threshold")
				.defineInRange("warn_threshold", 50, 0, Integer.MAX_VALUE);
		builder.pop();

		builder.comment("Settings for mode = ITEM_COST.").push("item_cost");
		this.itemCostEntries = builder
				.comment("Items consumed by each uncrafting, in the form 'namespace:item:count' (count optional, default 1).",
						"Any single matching entry satisfies the cost. Empty list = this mode refuses every uncrafting.")
				.translation("uncraftinglimit.config.item_cost_entries")
				.defineListAllowEmpty("item_cost_entries", List.of("twilightforest:torchberries:1"),
						() -> "twilightforest:torchberries:1", Config::isValidItemCostEntry);
		builder.pop();
	}

	private static boolean isValidItemCostEntry(Object value) {
		return value instanceof String entry && !entry.isBlank();
	}
}
