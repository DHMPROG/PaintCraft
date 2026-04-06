package com.paintcraft.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.paintcraft.PaintCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

/**
 * Gestionnaire de textures dynamiques pour les WallPaintingEntity.
 *
 * Chaque tableau peint possède des pixels uniques. Au lieu de générer un fichier
 * PNG sur disque, on crée une DynamicTexture GPU à la volée (NativeImage → GPU).
 *
 * Cycle de vie :
 *  1. getOrCreate(entityId, pixels)  → créé lors du premier rendu
 *  2. update(entityId, pixels)       → mis à jour quand SyncPaintingEntityPacket arrive
 *  3. release(entityId)              → libéré quand l'entité quitte le niveau
 *  4. releaseAll()                   → libéré lors du déchargement du monde
 *
 * Format des couleurs :
 *  - Notre stockage interne : ARGB (0xAARRGGBB — standard Java)
 *  - NativeImage.setPixelRGBA() attend : ABGR (0xAABBGGRR — little-endian OpenGL)
 *  → argbToAbgr() fait la conversion avant l'upload GPU.
 *
 * @OnlyIn(Dist.CLIENT) — cette classe ne doit JAMAIS être chargée côté serveur.
 */
@OnlyIn(Dist.CLIENT)
public final class PaintingTextureManager {

    // ── Dimensions du canvas ──────────────────────────────────────────────────
    private static final int TEX_WIDTH  = 16;
    private static final int TEX_HEIGHT = 16;

    // ── Cache : entityId → ResourceLocation + DynamicTexture ─────────────────
    private static final Map<Integer, ResourceLocation> LOCATIONS = new HashMap<>();
    private static final Map<Integer, DynamicTexture>   TEXTURES  = new HashMap<>();

    // ── Cache : BlockPos (easel) → ResourceLocation + DynamicTexture ─────────
    // Hash + signature de pixels pour detecter les changements et re-uploader.
    private static final Map<BlockPos, ResourceLocation> BE_LOCATIONS  = new HashMap<>();
    private static final Map<BlockPos, DynamicTexture>   BE_TEXTURES   = new HashMap<>();
    private static final Map<BlockPos, Integer>          BE_PIXELS_HASH = new HashMap<>();

    private PaintingTextureManager() { /* Classe utilitaire — pas d'instanciation */ }

    // =========================================================================
    // API publique
    // =========================================================================

    /**
     * Retourne la ResourceLocation de la texture pour cet entityId.
     * La crée si elle n'existe pas encore (premier appel lors du rendu).
     *
     * Appelé à chaque frame par WallPaintingRenderer.
     *
     * @param entityId ID numérique de l'entité (unique par session Minecraft)
     * @param pixels   int[256] ARGB (peut contenir des 0 pour transparent)
     * @return         ResourceLocation enregistrée dans le TextureManager
     */
    public static ResourceLocation getOrCreate(int entityId, int[] pixels) {
        if (LOCATIONS.containsKey(entityId)) {
            return LOCATIONS.get(entityId);
        }
        return create(entityId, pixels);
    }

    /**
     * Met à jour la texture existante avec de nouveaux pixels (sans recréer).
     * Appelé par le handler de SyncPaintingEntityPacket.
     *
     * Si la texture n'existe pas encore (ex : packet reçu avant le premier rendu),
     * elle est créée directement.
     *
     * @param entityId  ID de l'entité
     * @param newPixels Nouveau tableau ARGB
     */
    public static void update(int entityId, int[] newPixels) {
        DynamicTexture tex = TEXTURES.get(entityId);
        if (tex != null) {
            NativeImage img = tex.getPixels();
            if (img != null) {
                // Mise à jour des pixels dans l'image native (pas de réallocation)
                writePixels(img, newPixels);
                tex.upload(); // Re-upload vers le GPU
            }
        } else {
            // La texture n'existe pas encore → on la crée directement
            create(entityId, newPixels);
        }
    }

    /**
     * Libère la texture GPU associée à cette entité.
     * Appelé quand l'entité quitte le niveau (EntityLeaveLevelEvent côté client).
     *
     * @param entityId ID de l'entité à libérer
     */
    public static void release(int entityId) {
        ResourceLocation loc = LOCATIONS.remove(entityId);
        DynamicTexture tex   = TEXTURES.remove(entityId);

        if (loc != null) {
            // Désenregistre du TextureManager → libère la VRAM
            Minecraft.getInstance().getTextureManager().release(loc);
        }
        if (tex != null) {
            tex.close(); // Libère la NativeImage côté CPU
        }
    }

    /**
     * Libère TOUTES les textures du cache.
     * Appelé lors du déchargement du monde (LevelEvent.Unload côté client).
     */
    public static void releaseAll() {
        // Itère sur une copie des IDs pour éviter ConcurrentModificationException
        for (int entityId : LOCATIONS.keySet().stream().toList()) {
            release(entityId);
        }
        // Les maps sont déjà vidées par release(), mais par sécurité :
        LOCATIONS.clear();
        TEXTURES.clear();

        // Libere aussi les textures de chevalets (block entities)
        for (BlockPos key : BE_LOCATIONS.keySet().stream().toList()) {
            releaseBlock(key);
        }
        BE_LOCATIONS.clear();
        BE_TEXTURES.clear();
        BE_PIXELS_HASH.clear();
    }

    // =========================================================================
    // Internals
    // =========================================================================

    /**
     * Crée une nouvelle DynamicTexture à partir des pixels ARGB.
     * L'enregistre dans le TextureManager Minecraft avec un nom unique.
     */
    private static ResourceLocation create(int entityId, int[] pixels) {
        // 1. Crée l'image native (CPU-side)
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, TEX_WIDTH, TEX_HEIGHT, false);
        writePixels(image, pixels);

        // 2. Crée la texture GPU à partir de l'image
        DynamicTexture texture = new DynamicTexture(image);

        // 3. Génère un ResourceLocation unique par entité
        //    Format: paintcraft:dynamic/painting_<entityId>
        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(
                PaintCraft.MODID, "dynamic/painting_" + entityId);

        // 4. Enregistre dans le TextureManager Minecraft
        Minecraft.getInstance().getTextureManager().register(loc, texture);

        // 5. Cache
        LOCATIONS.put(entityId, loc);
        TEXTURES.put(entityId, texture);

        return loc;
    }

    /**
     * Écrit les pixels ARGB dans une NativeImage au format ABGR attendu par OpenGL.
     *
     * NativeImage.setPixelRGBA(x, y, color) attend un int ABGR :
     *   bits 31-24 : Alpha
     *   bits 23-16 : Blue  (≠ ARGB !)
     *   bits 15-8  : Green
     *   bits  7-0  : Red
     *
     * Notre format ARGB : bits 31-24=A, 23-16=R, 15-8=G, 7-0=B
     * → Il faut échanger R et B avant l'appel.
     */
    private static void writePixels(NativeImage image, int[] pixels) {
        for (int i = 0; i < pixels.length && i < TEX_WIDTH * TEX_HEIGHT; i++) {
            int x = i % TEX_WIDTH;
            int y = i / TEX_WIDTH;
            image.setPixelRGBA(x, y, argbToAbgr(pixels[i]));
        }
    }

    // =========================================================================
    // API BlockEntity (chevalets) — keying par BlockPos
    // =========================================================================

    /**
     * Retourne (en creant si necessaire) la texture dynamique pour un chevalet
     * a une position donnee. Si les pixels ont change depuis le dernier appel,
     * la texture existante est mise a jour in-place (pas de reallocation GPU).
     *
     * Appele a chaque frame par EaselBlockEntityRenderer.
     *
     * @param pos    position du bloc inferieur du chevalet (cle stable)
     * @param pixels pixels ARGB courants (lus depuis le BlockEntity)
     * @return ResourceLocation utilisable avec VertexConsumerProvider
     */
    public static ResourceLocation getOrCreateForBlock(BlockPos pos, int[] pixels) {
        BlockPos key = pos.immutable();
        DynamicTexture tex = BE_TEXTURES.get(key);
        if (tex == null) {
            return createForBlock(key, pixels);
        }

        // Verifie si les pixels ont change (hash rapide)
        int newHash = java.util.Arrays.hashCode(pixels);
        Integer oldHash = BE_PIXELS_HASH.get(key);
        if (oldHash == null || oldHash != newHash) {
            NativeImage img = tex.getPixels();
            if (img != null) {
                writePixels(img, pixels);
                tex.upload();
                BE_PIXELS_HASH.put(key, newHash);
            }
        }
        return BE_LOCATIONS.get(key);
    }

    /** Libere la texture d'un chevalet (BE detruit, chunk decharge, etc.). */
    public static void releaseBlock(BlockPos pos) {
        BlockPos key = pos.immutable();
        ResourceLocation loc = BE_LOCATIONS.remove(key);
        DynamicTexture tex   = BE_TEXTURES.remove(key);
        BE_PIXELS_HASH.remove(key);

        if (loc != null) {
            Minecraft.getInstance().getTextureManager().release(loc);
        }
        if (tex != null) {
            tex.close();
        }
    }

    /** Cree une nouvelle texture pour un chevalet. */
    private static ResourceLocation createForBlock(BlockPos key, int[] pixels) {
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, TEX_WIDTH, TEX_HEIGHT, false);
        writePixels(image, pixels);

        DynamicTexture texture = new DynamicTexture(image);

        // Format: paintcraft:dynamic/easel_<x>_<y>_<z>
        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(
                PaintCraft.MODID,
                "dynamic/easel_" + key.getX() + "_" + key.getY() + "_" + key.getZ());

        Minecraft.getInstance().getTextureManager().register(loc, texture);

        BE_LOCATIONS.put(key, loc);
        BE_TEXTURES.put(key, texture);
        BE_PIXELS_HASH.put(key, java.util.Arrays.hashCode(pixels));

        return loc;
    }

    /**
     * Convertit un int ARGB (Java standard) en int ABGR (NativeImage / OpenGL).
     *
     * ARGB : 0xAARRGGBB
     * ABGR : 0xAABBGGRR
     *
     * Les bits A et G restent en place — seuls R et B sont échangés.
     */
    private static int argbToAbgr(int argb) {
        int a =  argb >>> 24;          // Alpha (bits 31-24)
        int r = (argb >> 16) & 0xFF;   // Red   (bits 23-16)
        int g = (argb >>  8) & 0xFF;   // Green (bits 15-8)
        int b =  argb        & 0xFF;   // Blue  (bits  7-0)
        // Réassemble en ABGR
        return (a << 24) | (b << 16) | (g << 8) | r;
    }
}
