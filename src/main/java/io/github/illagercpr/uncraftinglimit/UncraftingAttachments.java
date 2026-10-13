package io.github.illagercpr.uncraftinglimit;

import com.mojang.serialization.Codec;
import io.github.illagercpr.uncraftinglimit.policy.CooldownPolicy;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * Per-player server data used by the limit modes.
 */
public final class UncraftingAttachments {

	public static final DeferredRegister<AttachmentType<?>> REGISTRY =
			DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, UncraftingLimit.MOD_ID);

	/**
	 * Game time ({@code level.getGameTime()}) of the player's most recent successful
	 * uncrafting, {@link io.github.illagercpr.uncraftinglimit.policy.CooldownPolicy#NEVER}
	 * until the first one. Serialized with the player data and copied on death, so dying
	 * cannot be used to escape an active cooldown.
	 *
	 * <p>The value is also synced to the owning client ({@code setData} pushes synced
	 * attachments through the attachment sync system), which lets the uncrafting screen
	 * render a live countdown.</p>
	 */
	public static final Supplier<AttachmentType<Long>> LAST_UNCRAFT_TIME =
			REGISTRY.register("last_uncraft_time", () -> AttachmentType.builder(() -> CooldownPolicy.NEVER)
					.serialize(Codec.LONG)
					.copyOnDeath()
					.sync((holder, player) -> holder == player, ByteBufCodecs.VAR_LONG)
					.build());

	private UncraftingAttachments() {
	}
}
