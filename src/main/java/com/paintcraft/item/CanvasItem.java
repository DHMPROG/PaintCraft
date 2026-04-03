package com.paintcraft.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Toile vierge — se place sur le chevalet.
 * L'interaction principale est gérée dans EaselBlock.useItemOn().
 */
public class CanvasItem extends Item {

    public CanvasItem(Properties properties) {
        super(properties);
    }

    /** MC 1.21 : signature avec Item.TooltipContext. */
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.paintcraft.canvas.tooltip")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
