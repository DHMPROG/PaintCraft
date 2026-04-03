package com.paintcraft.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Palette de peintre — ouvre la GUI de peinture quand on clique sur le chevalet.
 * L'interaction est gérée dans EaselBlock.useItemOn().
 */
public class PaletteItem extends Item {

    public PaletteItem(Properties properties) {
        super(properties);
    }

    /** MC 1.21 : signature avec Item.TooltipContext. */
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.paintcraft.palette.tooltip")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
