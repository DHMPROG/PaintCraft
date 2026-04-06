package com.paintcraft.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.paintcraft.block.EaselBlock;
import com.paintcraft.blockentity.EaselBlockEntity;
import com.paintcraft.client.PaintingTextureManager;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

/**
 * Renderer du chevalet — dessine la peinture dynamique sur la toile.
 *
 * Le BlockEntity est cote LOWER (y), mais la toile visible est cote UPPER
 * (y+1). On translate le rendu d'un bloc vers le haut.
 *
 * Position de la toile dans le modele easel_upper_canvas :
 *   from [3, 0, 2]  to [13, 12, 2.5]
 * En coords locales du bloc upper : x=[3/16..13/16], y=[0..12/16], z=[2/16..2.5/16]
 * Soit largeur=10/16, hauteur=12/16, et la face avant est a z=2/16.
 *
 * On dessine un quad textue 10/16 x 12/16 a z = 2/16 - epsilon (pour passer
 * devant la toile vierge du modele et eviter le z-fighting).
 *
 * Rotation : on applique la meme rotation Y que le blockstate (basee sur FACING).
 */
@OnlyIn(Dist.CLIENT)
public class EaselBlockEntityRenderer implements BlockEntityRenderer<EaselBlockEntity> {

    /** Decalage anti z-fighting (1/64 bloc devant la toile vierge). */
    private static final float Z_OFFSET = 0.5f / 16f;

    // Geometrie de la toile peinte (en blocs)
    private static final float CANVAS_X_MIN = 3f / 16f;
    private static final float CANVAS_X_MAX = 13f / 16f;
    private static final float CANVAS_Y_MIN = 0f;
    private static final float CANVAS_Y_MAX = 12f / 16f;
    private static final float CANVAS_Z     = 2f / 16f - Z_OFFSET;

    public EaselBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        // pas de ressources a charger
    }

    @Override
    public void render(EaselBlockEntity be,
                       float partialTick,
                       PoseStack poseStack,
                       MultiBufferSource bufferSource,
                       int packedLight,
                       int packedOverlay) {

        // Pas de toile -> rien a rendre
        if (!be.hasCanvas()) return;

        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof EaselBlock)) return;

        Direction facing = state.getValue(EaselBlock.FACING);

        poseStack.pushPose();

        // Le BE est sur la moitie LOWER ; la toile est sur la moitie UPPER (+1 bloc Y)
        poseStack.translate(0.5f, 1.0f, 0.5f);

        // Le blockstate easel.json applique des rotations Y :
        //   NORTH=180, SOUTH=0, EAST=90, WEST=270
        // On applique la meme rotation au rendu de la toile.
        float rot = switch (facing) {
            case SOUTH -> 0f;
            case EAST  -> 90f;
            case NORTH -> 180f;
            case WEST  -> 270f;
            default    -> 0f;
        };
        poseStack.mulPose(Axis.YP.rotationDegrees(rot));

        // Re-centre apres rotation (la rotation se fait autour du centre du bloc)
        poseStack.translate(-0.5f, 0.0f, -0.5f);

        // Texture dynamique pour ce chevalet (BlockPos comme cle stable)
        ResourceLocation texLoc = PaintingTextureManager.getOrCreateForBlock(
                be.getBlockPos(), be.getPixels());

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucentCull(texLoc));
        renderCanvasQuad(consumer, poseStack, packedLight, packedOverlay);

        poseStack.popPose();
    }

    /**
     * Quad texture aux dimensions de la toile dans le modele.
     * UV mapping (texture 16x16, origine haut-gauche).
     */
    private void renderCanvasQuad(VertexConsumer consumer, PoseStack poseStack,
                                  int packedLight, int packedOverlay) {
        PoseStack.Pose pose = poseStack.last();
        Matrix4f mat = pose.pose();

        // Bas-gauche
        consumer.addVertex(mat, CANVAS_X_MIN, CANVAS_Y_MIN, CANVAS_Z)
                .setColor(255, 255, 255, 255)
                .setUv(0.0f, 1.0f)
                .setOverlay(packedOverlay)
                .setLight(packedLight)
                .setNormal(pose, 0, 0, -1);

        // Bas-droit
        consumer.addVertex(mat, CANVAS_X_MAX, CANVAS_Y_MIN, CANVAS_Z)
                .setColor(255, 255, 255, 255)
                .setUv(1.0f, 1.0f)
                .setOverlay(packedOverlay)
                .setLight(packedLight)
                .setNormal(pose, 0, 0, -1);

        // Haut-droit
        consumer.addVertex(mat, CANVAS_X_MAX, CANVAS_Y_MAX, CANVAS_Z)
                .setColor(255, 255, 255, 255)
                .setUv(1.0f, 0.0f)
                .setOverlay(packedOverlay)
                .setLight(packedLight)
                .setNormal(pose, 0, 0, -1);

        // Haut-gauche
        consumer.addVertex(mat, CANVAS_X_MIN, CANVAS_Y_MAX, CANVAS_Z)
                .setColor(255, 255, 255, 255)
                .setUv(0.0f, 0.0f)
                .setOverlay(packedOverlay)
                .setLight(packedLight)
                .setNormal(pose, 0, 0, -1);
    }

    /** Toujours rendre le BE meme s'il est hors-frustum proche (la toile est petite). */
    @Override
    public boolean shouldRenderOffScreen(EaselBlockEntity be) {
        return false;
    }
}
