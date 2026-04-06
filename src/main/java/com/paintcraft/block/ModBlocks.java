package com.paintcraft.block;

import com.paintcraft.PaintCraft;
import com.paintcraft.item.ModItems;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

/**
 * Registre centralisé de tous les blocs du mod PaintCraft.
 *
 * On utilise le DeferredRegister de Forge : les objets ne sont créés
 * qu'au moment où Forge déclenche l'événement d'enregistrement,
 * évitant tout problème d'ordre d'initialisation.
 */
public class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, PaintCraft.MODID);

    // ── Blocs ──────────────────────────────────────────────────────────────

    /**
     * Le chevalet : bloc interactif sur lequel on pose une toile pour peindre.
     * - noOcclusion() → ne bloque pas la lumière (forme non cubique)
     * - noCollission() est intentionnellement absent (on peut marcher dessus)
     */
    public static final RegistryObject<Block> EASEL = BLOCKS.register("easel",
            () -> new EaselBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.WOOD)
                            .strength(1.5f, 3.0f)       // résistance aux explosions modérée
                            .sound(SoundType.WOOD)
                            .noOcclusion()               // modèle non cubique → pas d'occlusion
            )
    );

    /**
     * Le tableau mural : bloc fin posé sur un mur, rendu par WallPaintingRenderer.
     * Pas de BlockItem — c'est PaintingItem qui le place directement.
     */
    public static final RegistryObject<Block> WALL_PAINTING = BLOCKS.register("wall_painting",
            () -> new WallPaintingBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.WOOD)
                            .strength(0.5f, 1.0f)
                            .sound(SoundType.WOOD)
                            .noOcclusion()
            )
    );

    // ── BlockItems ────────────────────────────────────────────────────────
    // Chaque bloc a besoin d'un BlockItem pour exister dans l'inventaire.

    public static final RegistryObject<Item> EASEL_ITEM = ModItems.ITEMS.register("easel",
            () -> new BlockItem(EASEL.get(), new Item.Properties())
    );

    // ── Méthode d'enregistrement ───────────────────────────────────────────

    /**
     * À appeler dans le constructeur du mod principal.
     * Lie ce DeferredRegister au bus d'événements du mod.
     */
    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
