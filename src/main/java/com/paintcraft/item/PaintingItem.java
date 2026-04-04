package com.paintcraft.item;

import com.paintcraft.entity.ModEntityTypes;
import com.paintcraft.entity.WallPaintingEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

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
 *
 * ─── Étape 5 : ajout de useOn() ─────────────────────────────────────────────
 * Le joueur peut poser le tableau peint sur n'importe quel mur (face verticale)
 * en faisant clic droit. Cela crée un WallPaintingEntity avec les pixels du tableau.
 *
 * Conditions de pose :
 *  1. La face cliquée doit être verticale (NORTH/SOUTH/EAST/WEST, pas UP/DOWN)
 *  2. Le tableau doit avoir au moins un pixel peint (pas de toile vierge)
 *  3. Le bloc derrière (le mur) doit être solide (vérifié par entity.survives())
 *  4. L'espace en avant du mur doit être libre (ou remplaçable)
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

    // ── Pose sur mur (Étape 5) ────────────────────────────────────────────────

    /**
     * Appelé quand le joueur fait clic droit sur une face de bloc avec ce tableau.
     *
     * Flux serveur (isClientSide = false) :
     *  1. Vérifications (face verticale, tableau peint, espace libre)
     *  2. Création du WallPaintingEntity avec les pixels de l'ItemStack
     *  3. Validation via entity.survives() (mur solide)
     *  4. Ajout au monde + son de pose
     *  5. Consommation de l'item (sauf en mode créatif)
     *
     * Côté client : on retourne immédiatement sidedSuccess pour l'animation du bras.
     *
     * @param context Contexte d'utilisation (bloc cliqué, face, joueur, niveau, item)
     * @return InteractionResult.sidedSuccess si posé, PASS ou FAIL sinon
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Direction clickedFace = context.getClickedFace();

        // ── 1. Vérification : on ne pose pas sur le dessus/dessous d'un bloc ──
        if (clickedFace.getAxis() == Direction.Axis.Y) {
            return InteractionResult.PASS;
        }

        // ── 2. Vérification : le tableau doit être peint ──────────────────────
        ItemStack stack = context.getItemInHand();
        if (!hasPaintingData(stack)) {
            // Toile vierge → on ne peut pas la poser (renvoie PASS pour ne pas
            // bloquer d'autres interactions potentielles)
            return InteractionResult.PASS;
        }

        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos(); // Le bloc mur sur lequel on clique

        // ── 3. Côté client : on se contente d'affirmer le succès visuel ───────
        //    La vraie logique est côté serveur.
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        // ── 4. (Serveur) Vérification : l'espace en avant du mur est libre ────
        BlockPos inFrontPos = clickedPos.relative(clickedFace);
        BlockState inFrontState = level.getBlockState(inFrontPos);
        if (!inFrontState.canBeReplaced()) {
            // Espace occupé par un bloc non-remplaçable (ex : pierre, bois...)
            return InteractionResult.FAIL;
        }

        // ── 5. (Serveur) Création et validation de l'entité ───────────────────
        //
        // WallPaintingEntity(type, level, wallBlockPos, facingDirection) :
        //  - wallBlockPos : position du bloc mur (l'ancrage)
        //  - facingDirection : direction de la face cliquée = sens vers lequel
        //    la peinture fait face (ex: SOUTH si on clique sur la face sud d'un mur nord)
        //
        // setDirection() (appelé dans le constructeur de convenance) recalcule
        // automatiquement la position world et la bounding box de l'entité.
        WallPaintingEntity painting = new WallPaintingEntity(
                ModEntityTypes.WALL_PAINTING.get(),
                level,
                clickedPos,
                clickedFace
        );

        // Transfère les pixels de l'ItemStack vers l'entité
        painting.setPixels(getPixels(stack));

        // survives() vérifie que :
        //  - le bloc d'ancrage (clickedPos) est solide (a une face solide)
        //  - la bounding box de l'entité ne chevauche pas d'autres entités solides
        if (!painting.survives()) {
            return InteractionResult.FAIL;
        }

        // ── 6. (Serveur) Ajout au monde + son + consommation de l'item ────────
        level.addFreshEntity(painting);
        painting.playPlacementSound();

        // En mode créatif, on ne consomme pas l'item
        if (context.getPlayer() != null && !context.getPlayer().isCreative()) {
            stack.shrink(1);
        }

        return InteractionResult.CONSUME;
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
            tooltip.add(Component.translatable("item.paintcraft.painting_item.tooltip.place_hint")
                    .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("item.paintcraft.painting_item.tooltip.blank")
                    .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        }
    }
}
