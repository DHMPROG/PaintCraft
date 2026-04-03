package com.paintcraft;

import com.paintcraft.block.ModBlocks;
import com.paintcraft.item.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Définit l'onglet créatif "PaintCraft" qui regroupe tous
 * nos items dans le menu créatif de Minecraft.
 */
public class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, PaintCraft.MODID);

    public static final RegistryObject<CreativeModeTab> PAINTCRAFT_TAB =
            CREATIVE_TABS.register("paintcraft_tab", () ->
                    CreativeModeTab.builder()
                            // Place l'onglet après "Tools and Utilities"
                            .withTabsBefore(CreativeModeTabs.TOOLS_AND_UTILITIES)
                            // Icône de l'onglet : la palette
                            .icon(() -> ModItems.PALETTE.get().getDefaultInstance())
                            // Nom affiché (traduit via les fichiers lang)
                            .title(Component.translatable("itemGroup.paintcraft"))
                            // Contenu de l'onglet
                            .displayItems((params, output) -> {
                                output.accept(ModItems.CANVAS.get());
                                output.accept(ModItems.PALETTE.get());
                                output.accept(ModItems.PAINTING_ITEM.get());
                                output.accept(ModBlocks.EASEL_ITEM.get());
                            })
                            .build()
            );

    public static void register(IEventBus eventBus) {
        CREATIVE_TABS.register(eventBus);
    }
}
