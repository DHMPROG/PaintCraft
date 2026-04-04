package com.paintcraft.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.paintcraft.client.PaintingTextureManager;
import com.paintcraft.entity.WallPaintingEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

/**
 * Renderer custom pour WallPaintingEntity.
 *
 * Stratégie de rendu :
 *  1. Récupère (ou crée) une DynamicTexture 16×16 via PaintingTextureManager
 *  2. Rend un quad 1×1 bloc (face avant) avec RenderType.entityTranslucentCull
 *  3. Rend un quad arrière uni (bois sombre) pour la visibilité depuis derrière
 *
 * API MC 1.21 — VertexConsumer.setNormal() :
 *  MC 1.20 : .setNormal(Matrix3f, float, float, float)
 *  MC 1.21 : .setNormal(PoseStack.Pose, float, float, float)   ← correction principale
 *
 * Rotation :
 *  - direction.toYRot() aligne le quad (face locale +Z) sur la direction du tableau
 *    SOUTH(0°) → pas de rotation, NORTH(180°) → demi-tour, etc.
 *
 * @OnlyIn(Dist.CLIENT) — jamais chargé côté serveur.
 */
@OnlyIn(Dist.CLIENT)
public class WallPaintingRenderer extends EntityRenderer<WallPaintingEntity> {

    /** Offset Z pour éviter le z-fighting avec le mur (1/32 bloc). */
    private static final float Z_SURFACE_OFFSET = 0.03125f;

    // Couleur du dos (bois sombre)
    private static final int BACK_R = 74, BACK_G = 47, BACK_B = 18, BACK_A = 255;

    public WallPaintingRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0f; // pas d'ombre sous le tableau
    }

    @Override
    public ResourceLocation getTextureLocation(WallPaintingEntity entity) {
        // Retourne la DynamicTexture gérée par PaintingTextureManager
        return PaintingTextureManager.getOrCreate(entity.getId(), entity.getPixels());
    }

    @Override
    public void render(WallPaintingEntity entity,
                       float entityYaw,
                       float partialTick,
                       PoseStack poseStack,
                       MultiBufferSource bufferSource,
                       int packedLight) {

        Direction facing = entity.getDirection();
        if (facing == null || facing.getAxis() == Direction.Axis.Y) return;

        poseStack.pushPose();

        // ── 1. Rotation pour aligner le quad sur la direction du tableau ──────
        // Notre quad est dans le plan XY local, face +Z (SOUTH).
        // direction.toYRot() donne le yaw pour "faire face" à cette direction :
        //   SOUTH → 0°, WEST → 90°, NORTH → 180°, EAST → 270°
        poseStack.mulPose(Axis.YP.rotationDegrees(facing.toYRot()));

        // ── 2. Centrer + pousser contre le mur ───────────────────────────────
        // (-0.5, -0.5) centre le quad (va de (0,0) à (1,1) en local)
        // Z_SURFACE_OFFSET place la surface 1/32 devant l'entité (anti z-fighting)
        poseStack.translate(-0.5f, -0.5f, Z_SURFACE_OFFSET);

        // ── 3. Face avant (peinture) ──────────────────────────────────────────
        ResourceLocation texLoc = PaintingTextureManager.getOrCreate(entity.getId(), entity.getPixels());
        VertexConsumer frontConsumer = bufferSource.getBuffer(RenderType.entityTranslucentCull(texLoc));
        renderFront(frontConsumer, poseStack, packedLight);

        // ── 4. Face arrière (dos bois sombre) ─────────────────────────────────
        poseStack.translate(0.0f, 0.0f, -(Z_SURFACE_OFFSET + 0.001f));
        VertexConsumer backConsumer = bufferSource.getBuffer(RenderType.entitySolid(texLoc));
        renderBack(backConsumer, poseStack, packedLight);

        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    // =========================================================================
    // Helpers de rendu
    // =========================================================================

    /**
     * Face avant — quad texturé 1×1 bloc, face +Z locale.
     *
     * MC 1.21 : setNormal prend PoseStack.Pose (pas Matrix3f).
     * On passe poseStack.last() directement — MC applique lui-même la matrice normale.
     *
     * UV mapping (texture 16×16 row-major, origine haut-gauche) :
     *  BL(0,0,0) : UV(0,1) — bas-gauche de la texture
     *  BR(1,0,0) : UV(1,1) — bas-droit
     *  TR(1,1,0) : UV(1,0) — haut-droit
     *  TL(0,1,0) : UV(0,0) — haut-gauche
     */
    private void renderFront(VertexConsumer consumer, PoseStack poseStack, int packedLight) {
        PoseStack.Pose pose = poseStack.last();
        Matrix4f mat = pose.pose();

        // Bas-gauche
        consumer.addVertex(mat, 0, 0, 0)
                .setColor(255, 255, 255, 255)
                .setUv(0.0f, 1.0f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, 0, 0, 1);    // MC 1.21 : PoseStack.Pose, pas Matrix3f

        // Bas-droit
        consumer.addVertex(mat, 1, 0, 0)
                .setColor(255, 255, 255, 255)
                .setUv(1.0f, 1.0f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, 0, 0, 1);

        // Haut-droit
        consumer.addVertex(mat, 1, 1, 0)
                .setColor(255, 255, 255, 255)
                .setUv(1.0f, 0.0f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, 0, 0, 1);

        // Haut-gauche
        consumer.addVertex(mat, 0, 1, 0)
                .setColor(255, 255, 255, 255)
                .setUv(0.0f, 0.0f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, 0, 0, 1);
    }

    /**
     * Face arrière — quad couleur bois sombre, normale -Z locale.
     * Visible uniquement si le joueur passe "derrière" le mur.
     */
    private void renderBack(VertexConsumer consumer, PoseStack poseStack, int packedLight) {
        PoseStack.Pose pose = poseStack.last();
        Matrix4f mat = pose.pose();

        // Ordre inversé (CW depuis +Z = face visible depuis -Z)
        consumer.addVertex(mat, 0, 0, 0)
                .setColor(BACK_R, BACK_G, BACK_B, BACK_A)
                .setUv(0.0f, 1.0f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, 0, 0, -1);

        consumer.addVertex(mat, 0, 1, 0)
                .setColor(BACK_R, BACK_G, BACK_B, BACK_A)
                .setUv(0.0f, 0.0f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, 0, 0, -1);

        consumer.addVertex(mat, 1, 1, 0)
                .setColor(BACK_R, BACK_G, BACK_B, BACK_A)
                .setUv(1.0f, 0.0f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, 0, 0, -1);

        consumer.addVertex(mat, 1, 0, 0)
                .setColor(BACK_R, BACK_G, BACK_B, BACK_A)
                .setUv(1.0f, 1.0f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, 0, 0, -1);
    }
}
