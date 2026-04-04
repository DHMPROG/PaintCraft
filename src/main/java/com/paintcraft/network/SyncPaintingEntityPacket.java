package com.paintcraft.network;

import com.paintcraft.client.PaintingTextureManager;
import com.paintcraft.entity.WallPaintingEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.fml.DistExecutor;

/**
 * Packet SERVEUR → CLIENT : synchronise les données pixel d'un WallPaintingEntity.
 *
 * Problème : ClientboundAddEntityPacket ne peut transporter qu'un seul int "data"
 * (utilisé pour la direction). Nos 256 pixels (1 Ko) doivent voyager séparément.
 *
 * Solution : ce packet est envoyé côté serveur dans PlayerEvent.StartTracking
 * (voir PaintCraft.java), dès qu'un joueur entre dans la range de tracking d'un tableau.
 *
 * Données transportées :
 *  - entityId (int)    : ID réseau de l'entité (même que Entity.getId())
 *  - pixels   (int[256]) : couleurs ARGB du canvas 16×16
 *
 * Sécurité côté client :
 *  - On vérifie que l'entité existe dans le monde client
 *  - On vérifie que c'est bien un WallPaintingEntity
 *  - On ne modifie jamais l'état du serveur depuis ce handler
 */
public class SyncPaintingEntityPacket {

    private final int   entityId;
    private final int[] pixels;

    // ── Constructeur ──────────────────────────────────────────────────────────

    public SyncPaintingEntityPacket(int entityId, int[] pixels) {
        this.entityId = entityId;
        this.pixels   = pixels;
    }

    // ── Encode / Decode ───────────────────────────────────────────────────────

    /**
     * Sérialisation — appelé côté serveur avant l'envoi.
     *
     * Format binaire :
     *  [4 bytes] entityId
     *  [4 bytes × 256] pixels (ARGB)
     *
     * Taille totale : 4 + 1024 = 1028 bytes par packet.
     */
    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
        for (int pixel : pixels) {
            buf.writeInt(pixel);
        }
    }

    /**
     * Désérialisation — appelé côté client à la réception.
     */
    public static SyncPaintingEntityPacket decode(FriendlyByteBuf buf) {
        int   entityId = buf.readInt();
        int[] pixels   = new int[WallPaintingEntity.PIXEL_COUNT];
        for (int i = 0; i < WallPaintingEntity.PIXEL_COUNT; i++) {
            pixels[i] = buf.readInt();
        }
        return new SyncPaintingEntityPacket(entityId, pixels);
    }

    // ── Handler ───────────────────────────────────────────────────────────────

    /**
     * Traitement côté CLIENT — exécuté sur le thread principal Minecraft.
     *
     * On utilise DistExecutor.unsafeRunWhenOn(Dist.CLIENT) pour garantir que
     * le code client (Minecraft.getInstance(), PaintingTextureManager) n'est
     * jamais chargé sur un serveur dédié, même si la classe du packet l'est.
     *
     * Flux :
     *  1. Trouve l'entité par ID dans le monde client
     *  2. Met à jour ses pixels (WallPaintingEntity.setPixels)
     *  3. Invalide le cache texture (PaintingTextureManager.update)
     *     → Le prochain frame de rendu utilisera la nouvelle texture
     */
    public void handle(CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() ->
            // Isole le code client-only : ne sera pas exécuté sur un serveur dédié
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> this::handleClient)
        );
        ctx.setPacketHandled(true);
    }

    /**
     * Logique côté client (séparée pour éviter le chargement de classes client
     * sur un serveur dédié).
     *
     * @OnlyIn n'est pas utilisé ici car la méthode est appelée via DistExecutor,
     * ce qui garantit déjà l'exécution uniquement sur le client.
     */
    private void handleClient() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Entity entity = mc.level.getEntity(entityId);
        if (!(entity instanceof WallPaintingEntity painting)) return;

        // 1. Mise à jour des données pixel de l'entité
        painting.setPixels(pixels);

        // 2. Mise à jour de la DynamicTexture GPU (si elle existe déjà, on la re-upload;
        //    sinon, PaintingTextureManager la créera au prochain frame de rendu)
        PaintingTextureManager.update(entityId, pixels);
    }
}
