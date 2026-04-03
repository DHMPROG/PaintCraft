package com.paintcraft.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * BlockEntity du chevalet — stocke :
 * - hasCanvas  : boolean, indique si une toile est posée
 * - pixels     : int[256], les données de peinture (16x16 ARGB)
 * Logique complète à l'Étape 3.
 */
public class EaselBlockEntity extends BlockEntity {

    public EaselBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.EASEL_BE.get(), pos, state);
    }
}
