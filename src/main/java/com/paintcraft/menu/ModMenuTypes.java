package com.paintcraft.menu;

import com.paintcraft.PaintCraft;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registre centralisé des MenuType (containers/GUI) du mod.
 *
 * On utilise IForgeMenuType.create() au lieu du constructeur vanilla
 * car il fournit un FriendlyByteBuf au menu : cela permet d'envoyer
 * la position du chevalet du serveur vers le client lors de l'ouverture.
 */
public class ModMenuTypes {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, PaintCraft.MODID);

    /**
     * Type de menu pour la GUI de peinture.
     * IForgeMenuType.create() → factory qui reçoit (windowId, playerInv, extraData)
     * extraData est un FriendlyByteBuf dans lequel on lit la BlockPos du chevalet.
     */
    public static final RegistryObject<MenuType<PaintingMenu>> PAINTING_MENU =
            MENUS.register("painting_menu",
                    () -> IForgeMenuType.create(PaintingMenu::new)
            );

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}
