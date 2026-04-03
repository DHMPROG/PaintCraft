package com.paintcraft.blockentity;

import com.paintcraft.PaintCraft;
import com.paintcraft.block.ModBlocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registre centralisé des BlockEntityType du mod PaintCraft.
 *
 * Un BlockEntityType associe une classe BlockEntity à un ou plusieurs blocs.
 * Forge l'utilise pour savoir quel BlockEntity instancier quand il charge
 * un chunk contenant ce bloc.
 */
public class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, PaintCraft.MODID);

    /**
     * Type de BlockEntity associé au bloc EASEL.
     * - EaselBlockEntity::new  → constructeur utilisé pour créer l'instance
     * - ModBlocks.EASEL.get()  → le(s) bloc(s) qui peuvent avoir ce BE
     * - .build(null)           → null = pas de DataFixer (pas nécessaire ici)
     */
    public static final RegistryObject<BlockEntityType<EaselBlockEntity>> EASEL_BE =
            BLOCK_ENTITIES.register("easel",
                    () -> BlockEntityType.Builder
                            .of(EaselBlockEntity::new, ModBlocks.EASEL.get())
                            .build(null)
            );

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
