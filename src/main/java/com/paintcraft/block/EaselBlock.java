package com.paintcraft.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Bloc chevalet — bloc interactif avec BlockEntity.
 * - Clic droit + toile  → pose la toile sur le chevalet
 * - Clic droit + palette → ouvre la GUI de peinture
 * Logique complète à l'Étape 3.
 */
public class EaselBlock extends Block {

    public EaselBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }
}
