package io.github.illagercpr.uncraftinglimit.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the GUI size of container screens; the uncrafting screen mixin places its
 * cooldown line at the same label row Twilight Forest uses for its own warnings.
 */
@Mixin(value = AbstractContainerScreen.class, remap = false)
public interface AbstractContainerScreenAccessor {

	@Accessor("imageWidth")
	int uncraftinglimit$imageWidth();

	@Accessor("imageHeight")
	int uncraftinglimit$imageHeight();
}
