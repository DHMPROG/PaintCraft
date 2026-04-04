package com.paintcraft.entity;

import com.paintcraft.PaintCraft;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registre des EntityType custom de PaintCraft.
 *
 * Utilise DeferredRegister (pattern Forge standard) pour éviter les problèmes
 * d'ordre d'initialisation des classes.
 *
 * L'enregistrement est déclenché depuis PaintCraft.java via :
 *   ModEntityTypes.ENTITY_TYPES.register(modEventBus);
 */
public class ModEntityTypes {

    /** DeferredRegister pour tous nos EntityType */
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, PaintCraft.MODID);

    /**
     * WallPaintingEntity — tableau peint accroché sur un mur.
     *
     * Paramètres du Builder :
     *  - MobCategory.MISC    → pas de limite de spawn naturel, pas hostile
     *  - sized(0.5f, 0.5f)   → hitbox initiale (recalculée par HangingEntity.setDirection)
     *  - clientTrackingRange → distance max de synchronisation avec les clients (10 chunks)
     *  - updateInterval      → fréquence de mise à jour réseau (MAX_VALUE = jamais, le tableau est statique)
     *  - noSummon()          → ne peut pas être invoqué via /summon
     */
    public static final RegistryObject<EntityType<WallPaintingEntity>> WALL_PAINTING =
            ENTITY_TYPES.register("wall_painting", () ->
                    EntityType.Builder.<WallPaintingEntity>of(WallPaintingEntity::new, MobCategory.MISC)
                            .sized(0.5f, 0.5f)
                            .clientTrackingRange(10)
                            .updateInterval(Integer.MAX_VALUE)
                            .noSummon()
                            .build("wall_painting")
            );

    private ModEntityTypes() { /* Classe utilitaire — pas d'instanciation */ }
}
