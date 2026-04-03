package com.paintcraft.network;

import com.paintcraft.PaintCraft;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.SimpleChannel;

/**
 * Registre centralisé du canal réseau PaintCraft.
 *
 * Forge 51 (MC 1.21) : on utilise ChannelBuilder + SimpleChannel
 * à la place de l'ancien NetworkRegistry.newSimpleChannel().
 *
 * Packet enregistré :
 *  - SavePaintingPacket : CLIENT → SERVEUR, envoie les pixels terminés.
 */
public class ModPackets {

    /** Version du protocole réseau — doit correspondre côté client ET serveur. */
    private static final int PROTOCOL_VERSION = 1;

    /**
     * Canal principal du mod.
     * ResourceLocation.fromNamespaceAndPath() remplace new ResourceLocation(ns, path) en MC 1.21.
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
     */
    public static void register() {
        CHANNEL.messageBuilder(SavePaintingPacket.class)
                // Ce packet ne va QUE du client vers le serveur
                .direction(PacketFlow.SERVERBOUND)
                .encoder(SavePaintingPacket::encode)
                .decoder(SavePaintingPacket::decode)
                // consumerMainThread : le handler s'exécute sur le thread principal du serveur
                // (sécurité : manipulation du niveau/BlockEntity requiert le thread principal)
                .consumerMainThread(SavePaintingPacket::handle)
                .add();
    }
}
