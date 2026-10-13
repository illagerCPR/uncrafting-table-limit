package io.github.illagercpr.uncraftinglimit;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Sends refusal messages to the action bar, rate limited per player so that repeatedly
 * clicking into an active limit cannot spam the player (or the log).
 *
 * <p>The throttle is deliberately in-memory only: it is cosmetic and resets with the
 * server, which costs nothing and cannot corrupt any state.</p>
 */
public final class RefusalFeedback {

	/** Entries older than this are pruned opportunistically (one minute of game time). */
	private static final long PRUNE_AFTER_TICKS = 1200;
	private static final int MAX_TRACKED_PLAYERS = 1024;

	private static final Map<UUID, Long> LAST_SENT_GAME_TIME = new ConcurrentHashMap<>();

	private RefusalFeedback() {
	}

	/**
	 * Shows {@code message} on the player's action bar unless the per-player minimum
	 * interval has not elapsed yet.
	 */
	public static void notifyDenied(Player player, Level level, Supplier<Component> message) {
		if (!Config.SPEC.isLoaded() || !Config.INSTANCE.notifyPlayer.get()) {
			return;
		}
		long now = level.getGameTime();
		long minInterval = Config.INSTANCE.feedbackMinInterval.get();
		Long lastSent = LAST_SENT_GAME_TIME.get(player.getUUID());
		if (lastSent != null && now - lastSent < minInterval) {
			return;
		}
		LAST_SENT_GAME_TIME.put(player.getUUID(), now);
		player.displayClientMessage(message.get(), true);
		if (LAST_SENT_GAME_TIME.size() > MAX_TRACKED_PLAYERS) {
			LAST_SENT_GAME_TIME.entrySet().removeIf(entry -> now - entry.getValue() > PRUNE_AFTER_TICKS);
		}
	}
}
