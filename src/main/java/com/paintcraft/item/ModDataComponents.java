package com.paintcraft.item;

import com.paintcraft.PaintCraft;
import com.paintcraft.data.PaintingData;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.UnaryOperator;

/**
 * Registre des DataComponents personnalises du mod PaintCraft.
 *
 * MC 1.21 introduit DataComponents pour remplacer le NBT brut sur les ItemStacks.
 * Chaque DataComponent est typle, sérialisable (Codec + StreamCodec) et registre.
 *
 * Composants enregistres :
 *  - PAINTING_DATA : pixels d'un tableau peint (PaintingData record)
 */
public class ModDataComponents {

    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, PaintCraft.MODID);

    /**
     * Composant porteur des pixels d'un tableau peint.
     * Attache aux ItemStacks de PAINTING_ITEM.
     */
    public static final RegistryObject<DataComponentType<PaintingData>> PAINTING_DATA =
            COMPONENTS.register("painting_data", () -> register(builder -> builder
                    .persistent(PaintingData.CODEC)
                    .networkSynchronized(PaintingData.STREAM_CODEC)
            ));

    private static <T> DataComponentType<T> register(UnaryOperator<DataComponentType.Builder<T>> builderOp) {
        return builderOp.apply(DataComponentType.builder()).build();
    }

    public static void register(IEventBus eventBus) {
        COMPONENTS.register(eventBus);
    }
}
