package com.paintcraft.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Container (Menu) côté serveur pour la GUI de peinture.
 * Transmet la BlockPos du chevalet via FriendlyByteBuf.
 * Logique complète à l'Étape 4.
 */
public class PaintingMenu extends AbstractContainerMenu {

    /**
     * Constructeur appelé par IForgeMenuType.create() sur le CLIENT.
     * Reçoit les données extra (BlockPos du chevalet) depuis le serveur.
     */
    public PaintingMenu(int windowId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(windowId, playerInventory);
    }

    /**
     * Constructeur côté SERVEUR, appelé via NetworkHooks.openScreen().
     */
    public PaintingMenu(int windowId, Inventory playerInventory) {
        super(ModMenuTypes.PAINTING_MENU.get(), windowId);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
