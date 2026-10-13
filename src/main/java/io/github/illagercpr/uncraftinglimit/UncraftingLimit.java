package io.github.illagercpr.uncraftinglimit;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Uncrafting Table Limit — server side usage limits for the Twilight Forest uncrafting table.
 */
@Mod(UncraftingLimit.MOD_ID)
public final class UncraftingLimit {

	public static final String MOD_ID = "uncraftinglimit";
	public static final Logger LOGGER = LoggerFactory.getLogger("UncraftingTableLimit");

	/** Set once the common config has been read, so mixin code can safely consult it. */
	private static volatile boolean configReady = false;

	public UncraftingLimit(IEventBus modBus, ModContainer container) {
		container.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
		UncraftingAttachments.REGISTRY.register(modBus);
		modBus.addListener(this::onCommonSetup);
	}

	private void onCommonSetup(FMLCommonSetupEvent event) {
		event.enqueueWork(() -> {
			configReady = Config.SPEC.isLoaded();
			LOGGER.info("Uncrafting Table Limit ready. Active mode: {}, creative exempt: {}",
					Config.INSTANCE.mode.get(), Config.INSTANCE.exemptCreative.get());
		});
	}

	/**
	 * @return true when the config values can be trusted. Mixins fall back to "no limit" until then.
	 */
	public static boolean isConfigReady() {
		return configReady;
	}
}
