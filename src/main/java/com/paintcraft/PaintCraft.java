package com.paintcraft;

import com.mojang.logging.LogUtils;
import com.paintcraft.block.ModBlocks;
import com.paintcraft.blockentity.ModBlockEntities;
import com.paintcraft.client.PaintingTextureManager;
import com.paintcraft.client.renderer.WallPaintingRenderer;
import com.paintcraft.entity.ModEntityTypes;
import com.paintcraft.entity.WallPaintingEntity;
import com.paintcraft.item.ModItems;
import com.paintcraft.menu.ModMenuTypes;
import com.paintcraft.network.ModPackets;
import com.paintcraft.network.SyncPaintingEntityPacket;
import com.paintcraft.screen.PaintingScreen;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.event.level.LevelEvent;
import org.slf4j.Logger;

/**
 * Point d'entrée principal du mod PaintCraft.
 *
 * ─── Étape 5 : ajouts ────────────────────────────────────────────────────────
 *  - Enregistrement de ModEntityTypes (WallPaintingEntity)
 *  - Enregistrement du renderer WallPaintingRenderer (client uniquement)
 *  - PlayerEvent.StartTracking : envoi de SyncPaintingEntityPacket au joueur
 *    qui entre dans la range de tracking d'un WallPaintingEntity
 *  - EntityLeaveLevelEvent : libération des DynamicTexture GPU côté client
 *  - LevelEvent.Unload : nettoyage complet du cache texture au déchargement du monde
 */
@Mod(PaintCraft.MODID)
public class PaintCraft {

    public static final String MODID = "paintcraft";
    private static final Logger LOGGER = LogUtils.getLogger();

    public PaintCraft() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // ── Enregistrement de tous nos registres via DeferredRegister ──────────
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModMenuTypes.register(modEventBus);
        ModCreativeTabs.register(modEventBus);

        // ── Étape 5 : enregistrement des entity types ─────────────────────────
        ModEntityTypes.ENTITY_TYPES.register(modEventBus);

        // ── Lifecycle events ───────────────────────────────────────────────────
        modEventBus.addListener(this::commonSetup);

        // ── Bus principal Forge (événements monde / entités / joueurs) ─────────
        MinecraftForge.EVENT_BUS.register(this);

        LOGGER.info("PaintCraft mod loaded!");
    }

    /**
     * Setup commun (client + serveur).
     * Enregistre les packets réseau sur le thread principal Forge.
     */
    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(ModPackets::register);
        LOGGER.info("PaintCraft common setup complete.");
    }

    // =========================================================================
    // Événements serveur / communs (bus principal Forge)
    // =========================================================================

    /**
     * Déclenché quand un joueur commence à "tracker" (suivre) une entité.
     *
     * Pourquoi cet événement ?
     *  Le ClientboundAddEntityPacket ne peut transporter qu'un seul int (la direction).
     *  Les 256 pixels du WallPaintingEntity doivent être envoyés séparément.
     *  PlayerEvent.StartTracking est le moment idéal : le client vient de recevoir
     *  le packet de spawn et est prêt à recevoir des données supplémentaires.
     *
     * Exécuté CÔTÉ SERVEUR uniquement (le joueur est un ServerPlayer).
     */
    @SubscribeEvent
    public void onStartTracking(PlayerEvent.StartTracking event) {
        // Vérifie que l'entité trackée est bien un de nos tableaux muraux
        if (!(event.getTarget() instanceof WallPaintingEntity painting)) return;
        // Vérifie que c'est bien un ServerPlayer (côté serveur)
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;

        // Envoie les pixels au joueur qui commence à tracker le tableau
        ModPackets.sendToPlayer(
                new SyncPaintingEntityPacket(painting.getId(), painting.getPixels()),
                serverPlayer
        );
        LOGGER.debug("Sent SyncPaintingEntityPacket for entity {} to player {}",
                painting.getId(), serverPlayer.getName().getString());
    }

    // =========================================================================
    // Événements client uniquement
    // =========================================================================

    /**
     * Classe interne pour les événements côté MOD BUS (lifecycle client).
     * Séparé en classe interne @EventBusSubscriber pour que Forge sache
     * sur quel bus s'abonner (Bus.MOD vs Bus.FORGE).
     */
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {

        /**
         * Associe PaintingScreen (GUI) au PaintingMenu (container).
         * Exécuté pendant le setup client, avant le chargement du monde.
         */
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() ->
                MenuScreens.register(ModMenuTypes.PAINTING_MENU.get(), PaintingScreen::new)
            );
            LOGGER.info("PaintCraft client setup complete.");
        }

        /**
         * Étape 5 : enregistre le WallPaintingRenderer pour WallPaintingEntity.
         *
         * EntityRenderersEvent.RegisterRenderers est l'event Forge 1.21 correct
         * pour enregistrer des renderers d'entités (remplace EntityRendererRegistry
         * de Forge 1.19.x et le setup dans FMLClientSetupEvent de 1.20.x).
         */
        @SubscribeEvent
        public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(
                    ModEntityTypes.WALL_PAINTING.get(),
                    WallPaintingRenderer::new
            );
            LOGGER.info("WallPaintingRenderer registered.");
        }
    }

    /**
     * Classe interne pour les événements côté FORGE BUS (monde, entités) — CLIENT uniquement.
     *
     * Pourquoi une classe séparée de ClientModEvents ?
     *  ClientModEvents utilise Bus.MOD (lifecycle Forge).
     *  Cette classe utilise Bus.FORGE (événements runtime) mais uniquement côté client
     *  (pour accéder à PaintingTextureManager @OnlyIn CLIENT en sécurité).
     */
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class ClientForgeEvents {

        /**
         * Libère la DynamicTexture GPU d'un WallPaintingEntity quand il quitte le niveau.
         *
         * Déclencheurs :
         *  - Le joueur s'éloigne du tableau (sort de la range de tracking)
         *  - Le tableau est détruit (cassé par le joueur ou une explosion)
         *  - Déchargement du chunk
         *
         * Sans ce nettoyage, les textures orphelines s'accumulent en VRAM.
         */
        @SubscribeEvent
        public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
            if (event.getEntity() instanceof WallPaintingEntity painting) {
                PaintingTextureManager.release(painting.getId());
                LOGGER.debug("Released DynamicTexture for WallPaintingEntity {}", painting.getId());
            }
        }

        /**
         * Libère TOUTES les textures quand le monde est déchargé.
         *
         * Cas couverts :
         *  - Retour au menu principal
         *  - Changement de dimension (le niveau client change)
         *  - Crash de connexion
         *
         * Garantit qu'aucune texture orpheline ne persiste entre les sessions.
         */
        @SubscribeEvent
        public static void onLevelUnload(LevelEvent.Unload event) {
            // LevelEvent.Unload se déclenche pour chaque dimension sur le client
            // On nettoie tout pour être sûr
            PaintingTextureManager.releaseAll();
            LOGGER.debug("PaintingTextureManager cleared on level unload.");
        }
    }
}
