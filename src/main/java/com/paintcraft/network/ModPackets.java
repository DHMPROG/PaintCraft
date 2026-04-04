package com.paintcraft.network;

import com.paintcraft.PaintCraft;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

/**
 * Registre centralisé du canal réseau PaintCraft.
 *
 * Forge 51/52 (MC 1.21) : ChannelBuilder + SimpleChannel remplace l'ancien
 * NetworkRegistry.newSimpleChannel() de Forge 1.20.x.
 *
 * Packets enregistrés :
 *  1. SavePaintingPacket   CLIENT → SERVEUR (pixels terminés depuis la GUI)
 *  2. SyncPaintingEntityPacket SERVEUR → CLIENT (pixels du WallPaintingEntity)
 *
 * Ajout du packet S2C pour l'étape 5 :
 *  Le ClientboundAddEntityPacket ne peut transporter qu'un seul int (la direction).
 *  Les 256 pixels (1 Ko) sont envoyés séparément via SyncPaintingEntityPacket,
 *  déclenché par PlayerEvent.StartTracking dans PaintCraft.java.
 */
public class ModPackets {

    /** Version du protocole réseau — client et serveur doivent correspondre exactement. */
    private static final int PROTOCOL_VERSION = 2; // Incrémenté pour l'ajout du packet S2C

    /**
     * Canal principal du mod.
     * ResourceLocation.fromNamespaceAndPath() — API MC 1.21 (remplace new ResourceLocation()).
     */
    public static final SimpleChannel CHANNEL = ChannelBuilder
            .named(ResourceLocation.fromNamespaceAndPath(PaintCraft.MODID, "main"))
            .networkProtocolVersion(PROTOCOL_VERSION)
            .clientAcceptedVersions(Channel.VersionTest.exact(PROTOCOL_VERSION))
            .serverAcceptedVersions(Channel.VersionTest.exact(PROTOCOL_VERSION))
            .simpleChannel();

    /**
     * Enregistre tous les packets sur le canal.
     * Appelé depuis PaintCraft.commonSetup() via event.enqueueWork().
     *
     * L'ordre d'enregistrement détermine l'ID du packet (discriminator).
     * Ne pas changer l'ordre entre client et serveur !
     */
    public static void register() {

        // ── Packet 1 : CLIENT → SERVEUR ───────────────────────────────────────
        // Sauvegarde les pixels finaux de la peinture dans l'EaselBlockEntity.
        CHANNEL.messageBuilder(SavePaintingPacket.class)
                .direction(PacketFlow.SERVERBOUND)
                .encoder(SavePaintingPacket::encode)
                .decoder(SavePaintingPacket::decode)
                // consumerMainThread : s'exécute sur le thread principal serveur
                // (obligatoire pour manipuler des BlockEntity en sécurité)
                .consumerMainThread(SavePaintingPacket::handle)
                .add();

        // ── Packet 2 : SERVEUR → CLIENT ──────────────────────────────────────
        // Synchronise les pixels d'un WallPaintingEntity vers les clients.
        // Envoyé via sendToPlayer() depuis PlayerEvent.StartTracking.
        CHANNEL.messageBuilder(SyncPaintingEntityPacket.class)
                .direction(PacketFlow.CLIENTBOUND)
                .encoder(SyncPaintingEntityPacket::encode)
                .decoder(SyncPaintingEntityPacket::decode)
                // consumerMainThread : s'exécute sur le thread principal client
                // (obligatoire pour accéder à Minecraft.getInstance().level)
                .consumerMainThread(SyncPaintingEntityPacket::handle)
                .add();
    }

    // ── Helpers d'envoi ───────────────────────────────────────────────────────

    /**
     * Envoie un packet S2C à un joueur spécifique.
     * Utilisé pour SyncPaintingEntityPacket dans PlayerEvent.StartTracking.
     *
     * @param packet Le packet à envoyer
     * @param player Le joueur destinataire
     */
    public static void sendToPlayer(Object packet, ServerPlayer player) {
        CHANNEL.send(packet, PacketDistributor.PLAYER.with(player));
    }
}
