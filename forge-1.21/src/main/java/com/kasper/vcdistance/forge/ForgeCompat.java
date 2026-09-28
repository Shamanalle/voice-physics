package com.kasper.vcdistance.forge;

import com.kasper.vcdistance.DistanceConfig;
import com.kasper.vcdistance.client.KeyMappings;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.lang.reflect.Method;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * What differs between Forge versions, for Minecraft 1.21 - 1.21.1. The hooks that use it are shared by
 * every Forge build since 1.20.6 (shared/forge, shared/forge-bus6).
 *
 * <p>The jar is built against Forge 52 (1.21.1) and also runs on Forge 51 (1.21), which has neither
 * the HUD layer event nor the mod context's own registerExtensionPoint. Both are therefore looked up
 * at run time; on Forge 51 the settings screen goes through ModLoadingContext and the HUD is off.
 */
final class ForgeCompat {

    private static final String LAYERS_EVENT = "net.minecraftforge.client.event.AddGuiOverlayLayersEvent";
    private static final String MOD_LOADING_CONTEXT = "net.minecraftforge.fml.ModLoadingContext";

    private ForgeCompat() {
    }

    static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    /** The key, filed under the addon's own category; null where this Minecraft cannot make one. */
    static KeyMapping key(String name) {
        return KeyMappings.create(name, "key.categories.vc-audio-distance");
    }

    /** The settings screen behind the Mods list's Config button. */
    static void configScreen(FMLJavaModLoadingContext context, ConfigScreenHandler.ConfigScreenFactory factory) {
        Supplier<ConfigScreenHandler.ConfigScreenFactory> supplier = () -> factory;
        // Forge 52: the mod context itself; Forge 51: the older ModLoadingContext.get()
        if (register(context, supplier)) {
            return;
        }
        try {
            if (register(Class.forName(MOD_LOADING_CONTEXT).getMethod("get").invoke(null), supplier)) {
                return;
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            // Neither exists: no Config button
        }
        DistanceConfig.LOGGER.warn("This Forge has no Config button for mods; open the settings with /voicephysics");
    }

    private static boolean register(Object target, Supplier<ConfigScreenHandler.ConfigScreenFactory> supplier) {
        try {
            Method register = target.getClass().getMethod("registerExtensionPoint", Class.class, Supplier.class);
            register.invoke(target, ConfigScreenHandler.ConfigScreenFactory.class, supplier);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** The voice HUD, drawn over the game; off where this Forge cannot add a layer (Forge 51). */
    @SuppressWarnings("unchecked")
    static void hud(IEventBus modBus, ResourceLocation id, LayeredDraw.Layer layer) {
        Class<? extends Event> type;
        Method layers;
        try {
            type = (Class<? extends Event>) Class.forName(LAYERS_EVENT);
            layers = type.getMethod("getLayeredDraw");
        } catch (ReflectiveOperationException | LinkageError e) {
            DistanceConfig.LOGGER.info("This Forge cannot add a HUD layer; the voice HUD is off");
            return;
        }
        Consumer<Event> listener = event -> {
            try {
                ((LayeredDraw) layers.invoke(event)).add(id, layer);
            } catch (ReflectiveOperationException | RuntimeException e) {
                DistanceConfig.LOGGER.warn("Could not add the voice HUD: {}", e.toString());
            }
        };
        modBus.addListener(EventPriority.NORMAL, false, (Class<Event>) type, listener);
    }
}
