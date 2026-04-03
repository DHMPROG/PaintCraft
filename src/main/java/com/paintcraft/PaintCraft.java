package com.paintcraft;

import com.mojang.logging.LogUtils;
import com.paintcraft.block.ModBlocks;
import com.paintcraft.blockentity.ModBlockEntities;
import com.paintcraft.item.ModItems;
import com.paintcraft.menu.ModMenuTypes;
import com.paintcraft.screen.PaintingScreen;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Point d'entrée principal du mod PaintCraft.
 *
 * L'annotation @Mod demande à Forge de charger cette classe
 * et d'appeler son constructeur au démarrage du jeu.
 */
@Mod(PaintCraft.MODID)
public class PaintCraft {

    public static final String MODID = "paintcraft";
    private static final Logger LOGGER = LogUtils.getLogger();

    public PaintCraft() {
        // Récupère le bus d'événements du mod (lifecycle events)
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // ── Enregistrement de tous nos registres via DeferredRegister ──
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModMenuTypes.register(modEventBus);
        ModCreativeTabs.register(modEventBus);

        // ── Lifecycle events ──
        modEventBus.addListener(this::commonSetup);

        // Enregistre l'instance sur le bus principal Forge
        // (pour les événements monde/serveur hors lifecycle)
        MinecraftForge.EVENT_BUS.register(this);

        LOGGER.info("PaintCraft mod loaded!");
    }

    /**
     * Exécuté sur les deux côtés (client + serveur) pendant l'initialisation.
     * Utilisé pour enregistrer les packets réseau.
     */
    private void commonSetup(final FMLCommonSetupEvent event) {
        // Le réseau sera initialisé ici à l'Étape 4
        LOGGER.info("PaintCraft common setup complete.");
    }

    /**
     * Classe interne annotée @EventBusSubscriber pour gérer les événements
     * qui ne concernent que le client (rendu, GUI, etc.).
     */
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {

        /**
         * Associe notre PaintingScreen (GUI côté client) au PaintingMenu
         * (container côté serveur).
         * enqueueWork() garantit l'exécution sur le thread principal.
         */
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() ->
                MenuScreens.register(ModMenuTypes.PAINTING_MENU.get(), PaintingScreen::new)
            );
            LOGGER.info("PaintCraft client setup complete.");
        }
    }
}
