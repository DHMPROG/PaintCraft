package com.paintcraft.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Container (Menu) pour la GUI de peinture.
 *
 * Deux constructeurs distincts selon le côté :
 *
 *  ① Côté SERVEUR — appelé depuis SimpleMenuProvider dans EaselBlock :
 *     PaintingMenu(int windowId, Inventory inv, BlockPos easelPos)
 *
 *  ② Côté CLIENT — appelé par IForgeMenuType.create() :
 *     PaintingMenu(int windowId, Inventory inv, FriendlyByteBuf extraData)
 *     → lit la BlockPos depuis le buffer envoyé par le serveur
 *
 * Logique complète (slots, painting data) à l'Étape 4.
 */
public class PaintingMenu extends AbstractContainerMenu {

    /** Position du chevalet — partagée entre les deux constructeurs. */
    private final BlockPos easelPos;

    // ── Constructeur CLIENT (IForgeMenuType factory) ──────────────────────────

    public PaintingMenu(int windowId, Inventory playerInventory, FriendlyByteBuf extraData) {
        // Lit la BlockPos depuis le FriendlyByteBuf écrit par le serveur
        this(windowId, playerInventory, extraData.readBlockPos());
    }

    // ── Constructeur SERVEUR (SimpleMenuProvider) ─────────────────────────────

    public PaintingMenu(int windowId, Inventory playerInventory, BlockPos easelPos) {
        super(ModMenuTypes.PAINTING_MENU.get(), windowId);
        this.easelPos = easelPos;
    }

    // ── Méthodes requises par AbstractContainerMenu ───────────────────────────

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    // ── Getter ────────────────────────────────────────────────────────────────

    public BlockPos getEaselPos() {
        return easelPos;
    }
}
