package io.github.illagercpr.uncraftinglimit;

/**
 * The mutually exclusive ways of limiting the Twilight Forest uncrafting table.
 * Exactly one of these is active at a time, selected by the {@code mode} config option.
 */
public enum LimitMode {
	/** After a successful uncrafting, the player must wait a configured amount of time. */
	COOLDOWN,
	/** Taking items out of the uncrafting grid is refused outright. */
	DISABLED,
	/** Every uncrafting table has its own durability, consumed per uncrafting. */
	DURABILITY,
	/** Every uncrafting additionally consumes configured items from the player's inventory. */
	ITEM_COST
}
