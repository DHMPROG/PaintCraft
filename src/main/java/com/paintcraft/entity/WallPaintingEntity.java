package com.paintcraft.entity;

import com.paintcraft.item.ModItems;
import com.paintcraft.item.PaintingItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.Arrays;

/**
 * WallPaintingEntity — tableau peint accroché sur un mur.
 *
 * Extends HangingEntity pour bénéficier :
 *  - de la gestion d'attachement au mur (pos d'ancrage + direction)
 *  - du calcul automatique de la AABB via calculateBoundingBox() [MC 1.21 : abstract]
 *  - de la logique de "survie" (disparaît si le bloc support est cassé)
 *
 * Changements d'API MC 1.21.1 vs 1.20.x :
 *  - getWidth() / getHeight() → SUPPRIMÉS. Remplacés par calculateBoundingBox()
 *  - dropItem(ServerLevelAccessor, Entity) → SUPPRIMÉ. On override hurt() et remove()
 *  - getAddEntityPacket() → getAddEntityPacket(ServerEntity) (nouveau paramètre)
 *  - playPlacementSound() est PUBLIC dans HangingEntity (pas protected)
 *
 * Données persistées en NBT :
 *  "pixels" → int[256] : couleurs ARGB du canvas 16×16
 */
public class WallPaintingEntity extends HangingEntity {

    // ── Constantes ────────────────────────────────────────────────────────────

    public static final int PIXEL_WIDTH  = 16;
    public static final int PIXEL_HEIGHT = 16;
    public static final int PIXEL_COUNT  = PIXEL_WIDTH * PIXEL_HEIGHT; // 256

    private static final String NBT_PIXELS = "pixels";

    // ── Données pixel ─────────────────────────────────────────────────────────
    private int[] pixels = new int[PIXEL_COUNT];

    // =========================================================================
    // Constructeurs
    // =========================================================================

    /** Constructeur principal — utilisé par EntityType factory (les deux côtés). */
    public WallPaintingEntity(EntityType<? extends WallPaintingEntity> type, Level level) {
        super(type, level);
    }

    /**
     * Constructeur de convenance pour la pose côté serveur.
     *
     * @param pos BlockPos du bloc mur (l'ancre solide)
     * @param dir Direction de la face du tableau (normale du mur)
     *            setDirection() recalcule automatiquement la AABB et la position world.
     */
    public WallPaintingEntity(EntityType<? extends WallPaintingEntity> type,
                               Level level, BlockPos pos, Direction dir) {
        super(type, level, pos);
        this.setDirection(dir);
    }

    // =========================================================================
    // HangingEntity — AABB (MC 1.21 : méthode abstraite requise)
    // =========================================================================

    /**
     * Calcule la bounding box du tableau en fonction de son bloc d'ancrage et de sa direction.
     *
     * MC 1.21 : getWidth() / getHeight() ont été supprimés. calculateBoundingBox() est
     * maintenant la méthode abstraite à implémenter dans les sous-classes de HangingEntity.
     *
     * Géométrie pour un tableau 1×1 bloc :
     *  - Épaisseur : 1/16 bloc (0.0625), soit ±0.03125 autour du centre
     *  - Largeur/Hauteur : 1 bloc, soit ±0.5 autour du centre
     *  - Centre : centre du bloc d'ancrage + 15/32 vers la direction (face du mur)
     *
     * @param pos Bloc d'ancrage (le mur solide)
     * @param dir Direction vers laquelle la face du tableau pointe
     */
    @Override
    public AABB calculateBoundingBox(BlockPos pos, Direction dir) {
        // 15/32 = 0.46875 : décalage du centre du bloc d'ancrage vers la face du mur
        // Place la surface du tableau exactement sur la face du bloc.
        final double faceOffset = 0.46875;
        // Demi-épaisseur du tableau (1/32 bloc de chaque côté)
        final double halfThick  = 0.03125;
        // Demi-taille dans les axes perpendiculaires (1 bloc = ±0.5)
        final double halfSide   = 0.5;

        // Centre du tableau dans l'espace monde
        double cx = pos.getX() + 0.5 + dir.getStepX() * faceOffset;
        double cy = pos.getY() + 0.5 + dir.getStepY() * faceOffset;
        double cz = pos.getZ() + 0.5 + dir.getStepZ() * faceOffset;

        // Dans la direction de la normale : le tableau est fin (halfThick)
        // Dans les deux autres axes : le tableau fait 1 bloc (halfSide)
        Direction.Axis axis = dir.getAxis();
        double extentX = (axis == Direction.Axis.X) ? halfThick : halfSide;
        double extentY = halfSide; // toujours 1 bloc de haut
        double extentZ = (axis == Direction.Axis.Z) ? halfThick : halfSide;

        return new AABB(
                cx - extentX, cy - extentY, cz - extentZ,
                cx + extentX, cy + extentY, cz + extentZ
        );
    }

    // =========================================================================
    // Dégâts & drops — MC 1.21 : dropItem() supprimé
    // =========================================================================

    /**
     * Gère les dégâts directs (joueur qui casse le tableau, explosion, etc.).
     * Remplace l'ancien dropItem() pour les destructions actives.
     *
     * On appelle kill() qui déclenche remove(KILLED) où les items sont droppés.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) return false;
        if (!this.isRemoved()) {
            this.kill();
        }
        return true;
    }

    /**
     * Point de drop centralisé — appelé pour toute suppression "destructive".
     *
     * RemovalReason.KILLED couvre :
     *  - Le joueur casse le tableau (hurt → kill)
     *  - Le bloc support du tableau est cassé (HangingEntity.checkValidPosition → kill)
     *  - Une explosion détruit le tableau
     *
     * On ne droppe PAS pour UNLOADED_TO_CHUNK / UNLOADED_WITH_PLAYER
     * (le joueur s'éloigne simplement, le tableau est mis en veille).
     */
    @Override
    public void remove(RemovalReason reason) {
        if (reason == RemovalReason.KILLED && !this.level().isClientSide) {
            ItemStack drop = hasPaintingData()
                    ? PaintingItem.createPainting(this.pixels)
                    : new ItemStack(ModItems.PAINTING_ITEM.get());
            this.spawnAtLocation(drop);
            this.playSound(SoundEvents.PAINTING_BREAK, 1.0F, 1.0F);
        }
        super.remove(reason);
    }

    /**
     * Compat shim : certaines mappings attendent que HangingEntity/BlockAttachedEntity
     * expose dropItem(Entity). Implémentation simple qui droppe l'ItemStack du tableau.
     */
    @Override
    public void dropItem(Entity entity) {
        if (!this.level().isClientSide) {
            ItemStack drop = hasPaintingData()
                    ? PaintingItem.createPainting(this.pixels)
                    : new ItemStack(ModItems.PAINTING_ITEM.get());
            this.spawnAtLocation(drop);
        }
    }

    // =========================================================================
    // Son de pose
    // =========================================================================

    /**
     * MC 1.21 : playPlacementSound() est PUBLIC dans HangingEntity.
     * On doit déclarer notre override public (et non protected).
     */
    @Override
    public void playPlacementSound() {
        this.playSound(SoundEvents.PAINTING_PLACE, 1.0F, 1.0F);
    }

    // =========================================================================
    // Synched entity data (mappings 1.21 exigent defineSynchedData(Builder))
    // =========================================================================

    /**
     * Implémentation vide : nous n'avons pas de SynchedEntityData custom pour l'instant.
     * Signature adaptée aux mappings Mojang/Forge 1.21 : SynchedEntityData.Builder
     */
    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        // aucun champ synchrone personnalisé pour le moment
    }

    // =========================================================================
    // Persistance NBT
    // =========================================================================

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putIntArray(NBT_PIXELS, this.pixels);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(NBT_PIXELS, Tag.TAG_INT_ARRAY)) {
            int[] saved = tag.getIntArray(NBT_PIXELS);
            // Arrays.copyOf gère les mismatches de taille (compatibilité future)
            this.pixels = Arrays.copyOf(saved, PIXEL_COUNT);
        }
    }

    // =========================================================================
    // Accesseurs pixel
    // =========================================================================

    /** Retourne une copie du tableau de pixels (ARGB). */
    public int[] getPixels() {
        return this.pixels.clone();
    }

    /**
     * Remplace les données pixel.
     * @param newPixels Tableau ARGB de taille PIXEL_COUNT (256). Ignoré si null/taille incorrecte.
     */
    public void setPixels(int[] newPixels) {
        if (newPixels != null && newPixels.length == PIXEL_COUNT) {
            this.pixels = newPixels.clone();
        }
    }

    /** Retourne true si au moins un pixel est non-transparent. */
    public boolean hasPaintingData() {
        for (int pixel : this.pixels) {
            if (pixel != 0) return true;
        }
        return false;
    }

    /** Retourne la couleur ARGB d'un pixel (index row-major : y*16 + x). */
    public int getPixel(int index) {
        if (index < 0 || index >= PIXEL_COUNT) return 0;
        return this.pixels[index];
    }
}
