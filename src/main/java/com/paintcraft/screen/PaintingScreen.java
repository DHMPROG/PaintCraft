package com.paintcraft.screen;

import com.paintcraft.menu.PaintingMenu;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Écran de peinture (côté client uniquement).
 * Affiche la grille pixel 16x16 et la palette de couleurs.
 * Logique complète à l'Étape 4.
 */
public class PaintingScreen extends AbstractContainerScreen<PaintingMenu> {

    public PaintingScreen(PaintingMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void renderBg(net.minecraft.client.gui.GuiGraphics guiGraphics,
                            float partialTick, int mouseX, int mouseY) {
        // Le rendu complet sera implémenté à l'Étape 4
    }
}
