package com.paintcraft.network;

import com.paintcraft.blockentity.EaselBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.network.CustomPayloadEvent;

/**
 * Packet CLIENT → SERVEUR : sauvegarde les données de peinture dans l'EaselBlockEntity.
 *
 * Envoyé quand le joueur clique "Done" dans PaintingScreen.
 * Contient :
 *  - easelPos (BlockPos) : position du chevalet à mettre à jour
 *  - pixels   (int[256]) : couleurs ARGB du canvas 16×16
 *
 * Sécurité : on vérifie que le joueur est suffisamment proche du chevalet
 * pour éviter qu'un client malveillant ne modifie des blocs à distance.
 */
public class SavePaintingPacket {

    /** Distance maximale autorisée (en blocs²) pour interagir avec le chevalet. */
    private static final double MAX_DISTANCE_SQ = 8.0 * 8.0;

    private final BlockPos easelPos;
    private final int[]    pixels;

    // ── Constructeur ──────────────────────────────────────────────────────────

    public SavePaintingPacket(BlockPos easelPos, int[] pixels) {
        this.easelPos = easelPos;
        this.pixels   = pixels;
    }

    // ── Encode / Decode (sérialisation réseau) ────────────────────────────────

    /**
     * Sérialise le packet dans le buffer réseau.
     * Appelé côté émetteur (client).
     */
    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(easelPos);
        for (int pixel : pixels) {
            buf.writeInt(pixel);
        }
    }

    /**
     * Désérialise le packet depuis le buffer réseau.
     * Appelé côté récepteur (serveur).
     */
    public static SavePaintingPacket decode(FriendlyByteBuf buf) {
        BlockPos pos    = buf.readBlockPos();
        int[]    pixels = new int[EaselBlockEntity.PIXEL_COUNT];
        for (int i = 0; i < EaselBlockEntity.PIXEL_COUNT; i++) {
            pixels[i] = buf.readInt();
        }
        return new SavePaintingPacket(pos, pixels);
    }

    // ── Handler (exécuté sur le thread principal du serveur) ──────────────────

    /**
     * Traitement du packet côté serveur.
     * enqueueWork() garantit l'exécution sur le thread principal Minecraft.
     */
    public void handle(CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            ServerLevel level = player.serverLevel();

            // Sécurité : distance maximale
            double distSq = player.distanceToSqr(
                    easelPos.getX() + 0.5,
                    easelPos.getY() + 0.5,
                    easelPos.getZ() + 0.5
            );
            if (distSq > MAX_DISTANCE_SQ) return;

            // Sécurité : taille des pixels
            if (pixels.length != EaselBlockEntity.PIXEL_COUNT) return;

            BlockEntity be = level.getBlockEntity(easelPos);
            if (!(be instanceof EaselBlockEntity easel)) return;
            if (!easel.hasCanvas()) return;

            // Mise à jour des données de peinture
            easel.setPixels(pixels);
            easel.setChanged();

            // Notifie les clients proches du changement (rendu du chevalet)
            level.sendBlockUpdated(
                    easelPos,
                    level.getBlockState(easelPos),
                    level.getBlockState(easelPos),
                    3
            );
        });

        ctx.setPacketHandled(true);
    }
}
