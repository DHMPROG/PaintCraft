package com.paintcraft.item;

import com.paintcraft.PaintCraft;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registre centralisé de tous les items du mod PaintCraft.
 *
 * Note : le BlockItem du chevalet est enregistré ici aussi
 * (depuis ModBlocks) pour partager le même DeferredRegister<Item>.
 */
public class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, PaintCraft.MODID);

    // ── Items ──────────────────────────────────────────────────────────────

    /**
     * Toile vierge — se place sur le chevalet.
     * stacksTo(16) : on peut en porter 16 par slot.
     */
    public static final RegistryObject<Item> CANVAS = ITEMS.register("canvas",
            () -> new CanvasItem(new Item.Properties().stacksTo(16))
    );

    /**
     * Palette de peintre — ouvre la GUI de peinture quand on clique
     * sur un chevalet avec une toile posée.
     * stacksTo(1) : outil unique, non stackable.
     */
    public static final RegistryObject<Item> PALETTE = ITEMS.register("palette",
            () -> new PaletteItem(new Item.Properties().stacksTo(1))
    );

    /**
     * Tableau peint — résultat de la session de peinture.
     * Contient les données pixel en NBT (clé "pixels", tableau int[256]).
     * stacksTo(1) : chaque tableau est unique.
     */
    public static final RegistryObject<Item> PAINTING_ITEM = ITEMS.register("painting_item",
            () -> new PaintingItem(new Item.Properties().stacksTo(1))
    );

    // ── Méthode d'enregistrement ───────────────────────────────────────────

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
