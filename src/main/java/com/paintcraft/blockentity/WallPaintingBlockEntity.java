package com.paintcraft.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * BlockEntity du Tableau Mural — stocke les pixels du tableau peint.
 *
 * Identique à EaselBlockEntity dans sa structure NBT :
 *  - "pixels" (int[256]) : couleurs ARGB 16×16
 *
 * Clé du bon fonctionnement :
 *  - saveAdditional / loadAdditional → persistance sur disque
 *  - getUpdateTag / getUpdatePacket  → sync initiale vers clients (chargement chunk)
 */
public class WallPaintingBlockEntity extends BlockEntity {

    public static final String NBT_PIXELS = "pixels";
    public static final int CANVAS_SIZE   = 16;
    public static final int PIXEL_COUNT   = CANVAS_SIZE * CANVAS_SIZE; // 256

    private int[] pixels = new int[PIXEL_COUNT];

    // ── Constructeur ──────────────────────────────────────────────────────────

    public WallPaintingBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WALL_PAINTING_BE.get(), pos, state);
    }

    // ── Persistance NBT (MC 1.21 : avec HolderLookup.Provider) ───────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putIntArray(NBT_PIXELS, pixels);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        int[] saved = tag.getIntArray(NBT_PIXELS);
        pixels = (saved.length == PIXEL_COUNT) ? saved : new int[PIXEL_COUNT];
    }

    // ── Synchronisation client ────────────────────────────────────────────────

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    @Nullable
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public int[] getPixels() {
        return pixels;
    }

    public void setPixels(int[] pixels) {
        if (pixels.length == PIXEL_COUNT) {
            this.pixels = pixels;
        }
    }
}
