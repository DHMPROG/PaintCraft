package com.paintcraft.menu;

import com.paintcraft.blockentity.EaselBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Container (Menu) pour la GUI de peinture.
 *
 * Rôle :
 *  - Transporte la BlockPos du chevalet du serveur vers le client
 *  - Donne accès aux pixels existants (si la toile a déjà été peinte)
 *  - Valide que le joueur est encore proche du chevalet
 *
 * Ce menu n'a pas de slots d'inventaire — la GUI est purement graphique.
 * Les données de peinture transitent via SavePaintingPacket, pas via des slots.
 *
 * Deux constructeurs :
 *  ① Serveur : PaintingMenu(windowId, inventory, BlockPos)
 *  ② Client  : PaintingMenu(windowId, inventory, FriendlyByteBuf)
 */
public class PaintingMenu extends AbstractContainerMenu {

    private final BlockPos  easelPos;
    private final int[]     initialPixels; // Pixels existants, chargés à l'ouverture

    // ── Constructeur CLIENT ───────────────────────────────────────────────────

    public PaintingMenu(int windowId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(windowId, playerInventory, extraData.readBlockPos());
    }

    // ── Constructeur SERVEUR ──────────────────────────────────────────────────

    public PaintingMenu(int windowId, Inventory playerInventory, BlockPos easelPos) {
        super(ModMenuTypes.PAINTING_MENU.get(), windowId);
        this.easelPos = easelPos;

        // Tente de charger les pixels existants depuis le BlockEntity
        // (level est accessible via le player)
        int[] loaded = null;
        if (playerInventory.player.level() != null) {
            BlockEntity be = playerInventory.player.level().getBlockEntity(easelPos);
            if (be instanceof EaselBlockEntity easel) {
                loaded = easel.getPixels().clone();
            }
        }
        this.initialPixels = (loaded != null) ? loaded : new int[EaselBlockEntity.PIXEL_COUNT];
    }

    // ── Validation ────────────────────────────────────────────────────────────

    /**
     * Vérifie que le joueur est toujours à portée du chevalet.
     * Appelé à chaque tick pour fermer automatiquement si le joueur s'éloigne.
     */
    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(
                easelPos.getX() + 0.5,
                easelPos.getY() + 0.5,
                easelPos.getZ() + 0.5
        ) <= 64.0; // 8 blocs²
    }

    // ── Slots (aucun pour ce menu) ────────────────────────────────────────────

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public BlockPos getEaselPos() {
        return easelPos;
    }

    /**
     * Pixels à pré-charger dans PaintingScreen à l'ouverture.
     * Permet de continuer une peinture inachevée.
     */
    public int[] getInitialPixels() {
        return initialPixels;
    }
}
