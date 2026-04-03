package com.paintcraft.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

/**
 * Tableau peint — résultat d'une session de peinture.
 *
 * MC 1.21 : les données custom sur un ItemStack passent par DataComponents.
 * On utilise DataComponents.CUSTOM_DATA (wrappé dans CustomData) à la place
 * des anciens getTag() / setTag() qui n'existent plus.
 *
 * NBT stocké sous CUSTOM_DATA :
 *   "pixels" → int[256]  (couleurs ARGB du canvas 16×16)
 */
public class PaintingItem extends Item {

    public static final String NBT_PIXELS = "pixels";
    public static final int CANVAS_SIZE   = 16;
    public static final int PIXEL_COUNT   = CANVAS_SIZE * CANVAS_SIZE; // 256

    public PaintingItem(Properties properties) {
        super(properties);
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    /**
     * Crée un ItemStack "tableau peint" avec les pixels embarqués.
     * Utilise CustomData.set() — API MC 1.21 pour les données NBT custom.
     */
    public static ItemStack createPainting(int[] pixels) {
        ItemStack stack = new ItemStack(ModItems.PAINTING_ITEM.get());
        CompoundTag tag = new CompoundTag();
        tag.putIntArray(NBT_PIXELS, pixels);
        // MC 1.21 : CustomData.set(type, stack, tag) remplace stack.setTag()
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
        return stack;
    }

    // ── Lecture NBT ───────────────────────────────────────────────────────────

    /**
     * Extrait les pixels depuis l'ItemStack.
     * MC 1.21 : stack.get(DataComponents.CUSTOM_DATA) remplace stack.getTag().
     */
    public static int[] getPixels(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            CompoundTag tag = data.copyTag();
            int[] pixels = tag.getIntArray(NBT_PIXELS);
            if (pixels.length == PIXEL_COUNT) return pixels;
        }
        return new int[PIXEL_COUNT];
    }

    /** Retourne true si le tableau a au moins un pixel peint. */
    public static boolean hasPaintingData(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return false;
        int[] pixels = data.copyTag().getIntArray(NBT_PIXELS);
        if (pixels.length != PIXEL_COUNT) return false;
        for (int p : pixels) if (p != 0) return true;
        return false;
    }

    // ── Comportement item ─────────────────────────────────────────────────────

    /** Effet de brillance si le tableau a été peint (feedback visuel). */
    @Override
    public boolean isFoil(ItemStack stack) {
        return hasPaintingData(stack);
    }

    /**
     * MC 1.21 : appendHoverText prend Item.TooltipContext, plus @Nullable Level.
     */
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        if (hasPaintingData(stack)) {
            tooltip.add(Component.translatable("item.paintcraft.painting_item.tooltip.painted")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("item.paintcraft.painting_item.tooltip.blank")
                    .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        }
    }
}
