package com.paintcraft.blockentity;

import com.paintcraft.item.PaintingItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * BlockEntity du Chevalet — stocke l'état de la toile en NBT.
 *
 * Données persistées :
 *  - "hasCanvas"  (boolean) : toile posée ?
 *  - "pixels"     (int[256]) : couleurs ARGB du canvas 16×16
 */
public class EaselBlockEntity extends BlockEntity {

    public static final String NBT_HAS_CANVAS = "hasCanvas";
    public static final String NBT_PIXELS     = "pixels";

    public static final int CANVAS_SIZE = 16;
    public static final int PIXEL_COUNT = CANVAS_SIZE * CANVAS_SIZE; // 256

    private boolean hasCanvas = false;
    private int[]   pixels    = new int[PIXEL_COUNT];

    // ── Constructeur ──────────────────────────────────────────────────────────

    public EaselBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.EASEL_BE.get(), pos, state);
    }

    // ── Persistance NBT (MC 1.21 : les deux méthodes prennent HolderLookup.Provider) ──

    /**
     * Sauvegarde — appelé lors de l'écriture du chunk sur disque.
     * MC 1.21 : saveAdditional(CompoundTag, HolderLookup.Provider)
     */
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean(NBT_HAS_CANVAS, hasCanvas);
        tag.putIntArray(NBT_PIXELS, pixels);
    }

    /**
     * Chargement — appelé lors de la lecture du chunk depuis disque.
     * MC 1.21 : renommé en loadAdditional (n'était pas 'load' avant).
     */
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        hasCanvas = tag.getBoolean(NBT_HAS_CANVAS);

        int[] saved = tag.getIntArray(NBT_PIXELS);
        pixels = (saved.length == PIXEL_COUNT) ? saved : new int[PIXEL_COUNT];
    }

    // ── Synchronisation client ────────────────────────────────────────────────

    /**
     * MC 1.21 : getUpdateTag prend HolderLookup.Provider.
     * Retourne le tag complet pour la sync initiale (chargement de chunk).
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    /**
     * Packet S→C envoyé après level.sendBlockUpdated().
     * Forge/Vanilla lit getUpdateTag() pour remplir ce packet.
     */
    @Override
    @Nullable
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public boolean hasCanvas() { return hasCanvas; }
    public void setCanvas(boolean v) { this.hasCanvas = v; }

    public int[] getPixels() { return pixels; }
    public void setPixels(int[] px) {
        if (px.length == PIXEL_COUNT) this.pixels = px;
    }

    public void clearPixels() { this.pixels = new int[PIXEL_COUNT]; }

    public void clearCanvas() {
        this.hasCanvas = false;
        clearPixels();
    }

    /** Retourne true si au moins un pixel a été peint. */
    public boolean hasPainting() {
        for (int p : pixels) if (p != 0) return true;
        return false;
    }

    /** Crée l'ItemStack "tableau peint" avec les pixels en NBT. */
    public ItemStack getPaintingItemStack() {
        return PaintingItem.createPainting(pixels);
    }
}
