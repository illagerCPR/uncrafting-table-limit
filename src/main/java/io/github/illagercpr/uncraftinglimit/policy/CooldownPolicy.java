package io.github.illagercpr.uncraftinglimit.policy;

/**
 * Pure cooldown arithmetic, kept free of any Minecraft class so the conversion rules
 * are trivial to reason about and test.
 */
public final class CooldownPolicy {

	/** Marker for "this player has never uncrafted anything". */
	public static final long NEVER = -1L;

	private CooldownPolicy() {
	}

	/**
	 * @return true while the player is still waiting, i.e. fewer than {@code cooldownTicks}
	 *         game time units have passed since the last successful uncrafting.
	 */
	public static boolean isOnCooldown(long currentGameTime, long lastUncraftTime, long cooldownTicks) {
		if (cooldownTicks <= 0 || lastUncraftTime == NEVER) {
			return false;
		}
		return currentGameTime - lastUncraftTime < cooldownTicks;
	}

	/** @return how many ticks the player still has to wait, clamped to 0. */
	public static long remainingTicks(long currentGameTime, long lastUncraftTime, long cooldownTicks) {
		if (cooldownTicks <= 0 || lastUncraftTime == NEVER) {
			return 0;
		}
		return Math.max(0, cooldownTicks - (currentGameTime - lastUncraftTime));
	}

	/** @return whole seconds the player still has to wait, rounded up so it never shows 0 while blocked. */
	public static long remainingSeconds(long remainingTicks) {
		return (remainingTicks + 19) / 20;
	}

	/** @return a compact clock display such as {@code 19:59} or, above an hour, {@code 1:05:00}. */
	public static String formatDuration(long remainingTicks) {
		long seconds = remainingSeconds(remainingTicks);
		long hours = seconds / 3600;
		long minutes = (seconds % 3600) / 60;
		long secs = seconds % 60;
		if (hours > 0) {
			return String.format("%d:%02d:%02d", hours, minutes, secs);
		}
		return String.format("%d:%02d", minutes, secs);
	}
}
