package com.paintcraft.item;

import net.minecraft.world.item.Item;

/**
 * Tableau peint — résultat d'une session de peinture.
 * Stocke les données pixel (int[256]) en NBT sous la clé "pixels".
 * Logique complète à l'Étape 3.
 */
public class PaintingItem extends Item {

    public PaintingItem(Properties properties) {
        super(properties);
    }
}
