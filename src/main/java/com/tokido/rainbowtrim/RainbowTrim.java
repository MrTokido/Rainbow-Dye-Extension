package com.tokido.rainbowtrim;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.function.Function;

/**
 * Rainbow Trim - armour trim add-on for Rainbow Dye.
 *
 * The rainbow trims are pure resources: pre-rendered animated sprites bound to the vanilla
 * trim sprite names through assets/minecraft/atlases/armor_trims.json. The only Java here
 * registers the ingredient item.
 *
 * ## About minecraft:provides_trim_material
 *
 * That component is typed Holder&lt;TrimMaterial&gt;, and trim_material is a datapack
 * registry - so no Holder for it exists yet at item-registration time. 1.0.0 stored a
 * ResourceKey instead, which registered fine and then threw ClassCastException inside
 * SmithingTrimRecipe every time a smithing table evaluated it, disconnecting the player.
 *
 * The component is therefore no longer a default on the item. It is applied where a real
 * registry lookup exists:
 *
 *   - crafted upgrades get it from the recipe's result components, so the game builds the
 *     Holder itself with the correct codec
 *   - the creative tab entry gets it from the tab's own HolderLookup.Provider
 *
 * Both produce a genuine Holder. If the creative lookup ever fails, the fallback is a
 * plain item that just isn't a valid trim ingredient - never a crash.
 */
public class RainbowTrim implements ModInitializer {

    public static final String MOD_ID = "rainbowtrim";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    /** Matches data/rainbowtrim/trim_material/rainbow.json */
    public static final String TRIM_MATERIAL_PATH = "rainbow";

    public static final Item RAINBOW_TRIM_UPGRADE = registerItem(
            "rainbow_trim_upgrade",
            Item::new,
            // EPIC renders the item name in light purple.
            new Item.Properties().rarity(Rarity.EPIC));

    @Override
    public void onInitialize() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS)
                .register(output -> output.accept(makeUpgradeStack(output)));

        LOGGER.info("[Rainbow Trim] loaded");
    }

    /**
     * Build a creative-tab stack carrying a real Holder, resolved through the tab's
     * registry lookup. Reflective so a rename anywhere in the chain degrades to
     * "plain item" instead of breaking the build or the game.
     */
    private static ItemStack makeUpgradeStack(Object output) {
        ItemStack stack = new ItemStack(RAINBOW_TRIM_UPGRADE);
        try {
            Object registryKey = Registries.class.getField("TRIM_MATERIAL").get(null);
            Object materialKey = ResourceKey.class
                    .getMethod("create", ResourceKey.class, Identifier.class)
                    .invoke(null, registryKey, id(TRIM_MATERIAL_PATH));

            Object context = callNoArg(output, "getContext");      // ItemDisplayParameters
            Object provider = callNoArg(context, "holders");       // HolderLookup.Provider
            Object lookup = callOneArg(provider, "lookupOrThrow", registryKey);
            Object holder = callOneArg(lookup, "getOrThrow", materialKey);

            @SuppressWarnings("unchecked")
            DataComponentType<Object> type = (DataComponentType<Object>)
                    DataComponents.class.getField("PROVIDES_TRIM_MATERIAL").get(null);
            stack.set(type, holder);

            LOGGER.info("[Rainbow Trim] creative stack bound to trim material {}", id(TRIM_MATERIAL_PATH));
        } catch (Throwable e) {
            LOGGER.warn("[Rainbow Trim] Could not resolve the trim material for the creative tab entry; "
                    + "craft the upgrade instead - crafted ones always carry it.", e);
        }
        return stack;
    }

    private static Object callNoArg(Object target, String name) throws Exception {
        for (Method m : target.getClass().getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == 0) {
                m.setAccessible(true);
                return m.invoke(target);
            }
        }
        throw new NoSuchMethodException(name + "() on " + target.getClass().getName());
    }

    private static Object callOneArg(Object target, String name, Object arg) throws Exception {
        for (Method m : target.getClass().getMethods()) {
            if (m.getName().equals(name)
                    && m.getParameterCount() == 1
                    && m.getParameterTypes()[0].isInstance(arg)) {
                m.setAccessible(true);
                return m.invoke(target, arg);
            }
        }
        throw new NoSuchMethodException(name + "(" + arg.getClass().getSimpleName()
                + ") on " + target.getClass().getName());
    }

    private static Item registerItem(String name,
                                     Function<Item.Properties, Item> factory,
                                     Item.Properties properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(name));
        Item item = factory.apply(properties.setId(key));
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }
}
