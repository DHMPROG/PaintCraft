package com.paintcraft.data;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Arrays;

/**
 * Donnees d'un tableau peint — pixels ARGB d'un canvas 16x16.
 *
 * Utilise comme valeur d'un DataComponent (MC 1.21+) sur les ItemStacks
 * "painting_item". Remplace l'ancien stockage CustomData/NBT.
 *
 * - Codec : serialisation disque/JSON (CompoundTag via NBT-ops)
 * - StreamCodec : serialisation reseau (packets)
 *
 * Le record est immuable : toute modification doit creer une nouvelle instance.
 */
public record PaintingData(int[] pixels) {

    public static final int CANVAS_SIZE = 16;
    public static final int PIXEL_COUNT = CANVAS_SIZE * CANVAS_SIZE; // 256

    /** Instance vide (toile vierge — tous pixels a 0). */
    public static final PaintingData EMPTY = new PaintingData(new int[PIXEL_COUNT]);

    /**
     * Codec pour la serialisation persistante (NBT/JSON).
     * Encode les pixels comme un IntArray sous la cle "pixels".
     */
    public static final Codec<PaintingData> CODEC = Codec.INT_STREAM
            .xmap(s -> new PaintingData(s.toArray()),
                  pd -> Arrays.stream(pd.normalized()))
            .fieldOf("pixels")
            .codec();

    /**
     * StreamCodec pour la serialisation reseau.
     * Utilise INT_ARRAY pour transferer les 256 pixels en une seule operation.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, PaintingData> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pd) -> buf.writeVarIntArray(pd.normalized()),
                    buf -> new PaintingData(buf.readVarIntArray(PIXEL_COUNT))
            );

    /**
     * Constructeur compact : valide la longueur du tableau.
     * Si la taille est incorrecte, on copie/tronque/pad pour avoir exactement PIXEL_COUNT.
     */
    public PaintingData {
        if (pixels.length != PIXEL_COUNT) {
            int[] fixed = new int[PIXEL_COUNT];
            System.arraycopy(pixels, 0, fixed, 0, Math.min(pixels.length, PIXEL_COUNT));
            pixels = fixed;
        }
    }

    /** Retourne une copie defensive des pixels. */
    public int[] copyPixels() {
        return pixels.clone();
    }

    /** Retourne true si au moins un pixel est non-transparent. */
    public boolean isPainted() {
        for (int p : pixels) if (p != 0) return true;
        return false;
    }

    /** Retourne le tableau interne (taille garantie = PIXEL_COUNT). */
    private int[] normalized() {
        return pixels;
    }

    /**
     * Egalite par contenu — necessaire pour DataComponents.
     * Deux PaintingData sont egaux ssi leurs tableaux ont les memes pixels.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PaintingData other)) return false;
        return Arrays.equals(this.pixels, other.pixels);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(pixels);
    }
}
