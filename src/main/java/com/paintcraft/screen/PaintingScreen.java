package com.paintcraft.screen;

import com.paintcraft.menu.PaintingMenu;
import com.paintcraft.network.ModPackets;
import com.paintcraft.network.SavePaintingPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.network.PacketDistributor;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Écran de peinture — affiché côté client uniquement.
 *
 * Layout (imageWidth=290, imageHeight=215) :
 *
 *   [8, 8]──────────────────────[175,8]────────────[235,8]
 *   │  Grille 16×16             │ Palette 4×4       │
 *   │  (10px par cellule)       │ (14px par swatch) │
 *   │  160×160px                │ 56×56px           │
 *   │                           │                   │
 *   │                           │ [Couleur sélect.] │
 *   │                           │                   │
 *   │                           │ Outils:           │
 *   │                           │ [B] [F] [E]       │
 *   └───────────────────────────┴───────────────────┘
 *   [Clear]                    [Cancel]       [Done]
 *
 * Outils disponibles :
 *  - BRUSH      (B) : peint un pixel au clic / glisser
 *  - FILL       (F) : remplissage par diffusion (BFS)
 *  - EYEDROPPER (E) : copie la couleur d'un pixel
 *
 * Clic gauche : peindre / sélectionner
 * Clic droit  : effacer un pixel (transparent)
 */
public class PaintingScreen extends AbstractContainerScreen<PaintingMenu> {

    // ── Constantes de layout ──────────────────────────────────────────────────

    private static final int CANVAS_SIZE     = 16;   // pixels par côté
    private static final int CELL_SIZE       = 10;   // pixels écran par cellule
    private static final int GRID_X          = 8;    // offset grille dans le background
    private static final int GRID_Y          = 8;

    private static final int PALETTE_X       = 178;  // offset palette
    private static final int PALETTE_Y       = 8;
    private static final int SWATCH_SIZE     = 14;   // taille d'un swatch de couleur

    // ── Palette de 16 couleurs (Minecraft dye colors, ARGB) ──────────────────

    private static final int[] PALETTE_COLORS = {
            0xFFFFFFFF, // White
            0xFFF9801D, // Orange
            0xFFC74EBD, // Magenta
            0xFF3AB3DA, // Light Blue
            0xFFFED83D, // Yellow
            0xFF80C71F, // Lime
            0xFFF38BAA, // Pink
            0xFF474F52, // Gray
            0xFF9D9D97, // Light Gray
            0xFF169C9C, // Cyan
            0xFF8932B8, // Purple
            0xFF3C44AA, // Blue
            0xFF835432, // Brown
            0xFF5E7C16, // Green
            0xFFB02E26, // Red
            0xFF1D1D21  // Black
    };

    // ── Outils ───────────────────────────────────────────────────────────────

    private enum Tool { BRUSH, FILL, EYEDROPPER }

    // ── État interne ──────────────────────────────────────────────────────────

    private final int[] pixels;          // données de peinture locales (copie cliente)
    private int         selectedColorIdx = 0;
    private int         selectedColor    = PALETTE_COLORS[0];
    private Tool        currentTool      = Tool.BRUSH;

    // ── Constructeur ──────────────────────────────────────────────────────────

    public PaintingScreen(PaintingMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth  = 290;
        this.imageHeight = 215;
        // Charge les pixels existants depuis le menu (pré-peint ou vide)
        this.pixels = menu.getInitialPixels().clone();
    }

    // ── Initialisation ────────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();

        // ── Bouton "Done" : envoie les pixels au serveur ──────────────────────
        addRenderableWidget(Button.builder(
                Component.translatable("button.paintcraft.done"),
                btn -> onDone()
        ).bounds(leftPos + 228, topPos + 191, 54, 18).build());

        // ── Bouton "Cancel" : ferme sans sauvegarder ──────────────────────────
        addRenderableWidget(Button.builder(
                Component.translatable("button.paintcraft.cancel"),
                btn -> onClose()
        ).bounds(leftPos + 170, topPos + 191, 54, 18).build());

        // ── Bouton "Clear" : efface toute la toile ────────────────────────────
        addRenderableWidget(Button.builder(
                Component.translatable("button.paintcraft.clear"),
                btn -> Arrays.fill(pixels, 0x00000000)
        ).bounds(leftPos + GRID_X, topPos + 191, 54, 18).build());
    }

    // ── Rendu ─────────────────────────────────────────────────────────────────

    // Champs hover mis à jour dans render() pour usage dans renderGrid()
    private int lastMouseX, lastMouseY;

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        this.lastMouseX = mx;
        this.lastMouseY = my;
        renderBackground(g, mx, my, partialTick);
        super.render(g, mx, my, partialTick);
        renderTooltip(g, mx, my);
    }

    /**
     * Dessine le fond de la GUI, la grille de pixels, et la palette.
     * Appelé par AbstractContainerScreen.render() avant les widgets.
     */
    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        // ── Fond principal ────────────────────────────────────────────────────
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF2B2B2B);

        // ── Séparateur vertical ───────────────────────────────────────────────
        g.fill(leftPos + PALETTE_X - 4, topPos + 4,
               leftPos + PALETTE_X - 3, topPos + imageHeight - 4, 0xFF555555);

        // ── Titre ─────────────────────────────────────────────────────────────
        g.drawString(font, title, leftPos + PALETTE_X, topPos - 10, 0xFFFFFF, true);

        renderGrid(g);
        renderPalette(g);
        renderToolButtons(g);
    }

    /** Supprime les labels vanilla (inventaire joueur / titre container). */
    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // Intentionnellement vide — on gère le titre nous-mêmes dans renderBg
    }

    // ── Rendu de la grille de pixels ──────────────────────────────────────────

    private void renderGrid(GuiGraphics g) {
        int gx = leftPos + GRID_X;
        int gy = topPos + GRID_Y;
        int size = CANVAS_SIZE * CELL_SIZE;

        // Bordure externe de la grille
        g.fill(gx - 1, gy - 1, gx + size + 1, gy + size + 1, 0xFF888888);

        // Pixels
        for (int py = 0; py < CANVAS_SIZE; py++) {
            for (int px = 0; px < CANVAS_SIZE; px++) {
                int x = gx + px * CELL_SIZE;
                int y = gy + py * CELL_SIZE;
                int color = pixels[py * CANVAS_SIZE + px];

                // Damier pour les pixels transparents
                if ((color >>> 24) == 0) {
                    boolean even = (px + py) % 2 == 0;
                    color = even ? 0xFFCCCCCC : 0xFF888888;
                }
                g.fill(x, y, x + CELL_SIZE, y + CELL_SIZE, color);
            }
        }

        // Lignes de grille (semi-transparentes)
        for (int i = 0; i <= CANVAS_SIZE; i++) {
            int x = gx + i * CELL_SIZE;
            int y = gy + i * CELL_SIZE;
            g.fill(x, gy, x + 1, gy + size, 0x44000000);
            g.fill(gx, y, gx + size, y + 1, 0x44000000);
        }

        // Surlignage de la cellule sous le curseur
        int hoverX = (lastMouseX - gx) / CELL_SIZE;
        int hoverY = (lastMouseY - gy) / CELL_SIZE;
        if (hoverX >= 0 && hoverX < CANVAS_SIZE && hoverY >= 0 && hoverY < CANVAS_SIZE) {
            int hx = gx + hoverX * CELL_SIZE;
            int hy = gy + hoverY * CELL_SIZE;
            g.fill(hx, hy, hx + CELL_SIZE, hy + CELL_SIZE, 0x55FFFFFF);
        }
    }

    // ── Rendu de la palette de couleurs ──────────────────────────────────────

    private void renderPalette(GuiGraphics g) {
        int px = leftPos + PALETTE_X;
        int py = topPos + PALETTE_Y;

        // Titre section
        g.drawString(font, Component.translatable("screen.paintcraft.colors"),
                px, py - 9, 0xAAAAAA, false);

        // 16 swatches en grille 4×4
        for (int i = 0; i < PALETTE_COLORS.length; i++) {
            int col = i % 4;
            int row = i / 4;
            int sx = px + col * SWATCH_SIZE;
            int sy = py + row * SWATCH_SIZE;

            // Sélection : contour blanc
            if (i == selectedColorIdx) {
                g.fill(sx - 1, sy - 1, sx + SWATCH_SIZE + 1, sy + SWATCH_SIZE + 1, 0xFFFFFFFF);
            } else {
                g.fill(sx - 1, sy - 1, sx + SWATCH_SIZE + 1, sy + SWATCH_SIZE + 1, 0xFF333333);
            }
            g.fill(sx, sy, sx + SWATCH_SIZE, sy + SWATCH_SIZE, PALETTE_COLORS[i]);
        }

        // Indicateur couleur sélectionnée (bande en bas de la palette)
        int indY = py + 4 * SWATCH_SIZE + 6;
        g.fill(px - 1, indY - 1, px + 4 * SWATCH_SIZE + 1, indY + 13, 0xFF555555);
        g.fill(px, indY, px + 4 * SWATCH_SIZE, indY + 12, selectedColor);
        g.drawString(font, "#" + String.format("%06X", selectedColor & 0xFFFFFF),
                px + 4 * SWATCH_SIZE + 4, indY + 2, 0xAAAAAA, false);
    }

    // ── Rendu des boutons d'outil ─────────────────────────────────────────────

    private void renderToolButtons(GuiGraphics g) {
        int tx = leftPos + PALETTE_X;
        int ty = topPos + PALETTE_Y + 4 * SWATCH_SIZE + 28;

        g.drawString(font, Component.translatable("screen.paintcraft.tools"),
                tx, ty - 9, 0xAAAAAA, false);

        String[] labels = {
                Component.translatable("tool.paintcraft.brush").getString(),
                Component.translatable("tool.paintcraft.fill").getString(),
                Component.translatable("tool.paintcraft.eyedropper").getString()
        };
        Tool[] tools = Tool.values();

        for (int i = 0; i < 3; i++) {
            int bx = tx + i * 36;
            boolean active = (currentTool == tools[i]);
            // Fond du bouton
            g.fill(bx, ty, bx + 34, ty + 16,
                    active ? 0xFF888800 : 0xFF444444);
            g.fill(bx + 1, ty + 1, bx + 33, ty + 15,
                    active ? 0xFFDDDD00 : 0xFF666666);
            // Label centré
            int labelW = font.width(labels[i]);
            g.drawString(font, labels[i], bx + (34 - labelW) / 2, ty + 4,
                    active ? 0xFF000000 : 0xFFCCCCCC, false);
        }
    }

    // ── Interactions souris ───────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (handleGridClick(mx, my, button)) return true;
        if (handlePaletteClick(mx, my)) return true;
        if (handleToolClick(mx, my)) return true;
        return super.mouseClicked(mx, my, button);
    }

    /** Glisser la souris permet de peindre en continu avec le pinceau. */
    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button == 0 && currentTool == Tool.BRUSH) {
            handleGridClick(mx, my, 0);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    // ── Gestion des clics sur la grille ──────────────────────────────────────

    private boolean handleGridClick(double mx, double my, int button) {
        int gx = leftPos + GRID_X;
        int gy = topPos + GRID_Y;
        int size = CANVAS_SIZE * CELL_SIZE;

        if (mx < gx || mx >= gx + size || my < gy || my >= gy + size) return false;

        int px = (int) ((mx - gx) / CELL_SIZE);
        int py = (int) ((my - gy) / CELL_SIZE);
        int idx = py * CANVAS_SIZE + px;

        if (button == 0) { // Clic gauche
            switch (currentTool) {
                case BRUSH      -> pixels[idx] = selectedColor;
                case FILL       -> floodFill(px, py, pixels[idx], selectedColor);
                case EYEDROPPER -> pickColor(idx);
            }
        } else if (button == 1) { // Clic droit = effacer
            pixels[idx] = 0x00000000;
        }
        return true;
    }

    /** Pipette : récupère la couleur d'un pixel et repasse en mode pinceau. */
    private void pickColor(int idx) {
        selectedColor = pixels[idx];
        for (int i = 0; i < PALETTE_COLORS.length; i++) {
            if (PALETTE_COLORS[i] == selectedColor) {
                selectedColorIdx = i;
                break;
            }
        }
        currentTool = Tool.BRUSH;
    }

    // ── Gestion des clics sur la palette ─────────────────────────────────────

    private boolean handlePaletteClick(double mx, double my) {
        int px = leftPos + PALETTE_X;
        int py = topPos + PALETTE_Y;

        for (int i = 0; i < PALETTE_COLORS.length; i++) {
            int col = i % 4;
            int row = i / 4;
            int sx = px + col * SWATCH_SIZE;
            int sy = py + row * SWATCH_SIZE;
            if (mx >= sx && mx < sx + SWATCH_SIZE && my >= sy && my < sy + SWATCH_SIZE) {
                selectedColorIdx = i;
                selectedColor    = PALETTE_COLORS[i];
                return true;
            }
        }
        return false;
    }

    // ── Gestion des clics sur les boutons d'outil ─────────────────────────────

    private boolean handleToolClick(double mx, double my) {
        int tx = leftPos + PALETTE_X;
        int ty = topPos + PALETTE_Y + 4 * SWATCH_SIZE + 28;

        Tool[] tools = Tool.values();
        for (int i = 0; i < 3; i++) {
            int bx = tx + i * 36;
            if (mx >= bx && mx < bx + 34 && my >= ty && my < ty + 16) {
                currentTool = tools[i];
                return true;
            }
        }
        return false;
    }

    // ── Algorithmes ───────────────────────────────────────────────────────────

    /**
     * Remplissage par diffusion (BFS).
     * Remplace tous les pixels connectés de targetColor par fillColor.
     */
    private void floodFill(int startX, int startY, int targetColor, int fillColor) {
        if (targetColor == fillColor) return;

        Queue<int[]> queue = new LinkedList<>();
        queue.add(new int[]{startX, startY});

        while (!queue.isEmpty()) {
            int[] pos = queue.poll();
            int x = pos[0], y = pos[1];
            if (x < 0 || x >= CANVAS_SIZE || y < 0 || y >= CANVAS_SIZE) continue;
            int idx = y * CANVAS_SIZE + x;
            if (pixels[idx] != targetColor) continue;

            pixels[idx] = fillColor;
            queue.add(new int[]{x + 1, y});
            queue.add(new int[]{x - 1, y});
            queue.add(new int[]{x, y + 1});
            queue.add(new int[]{x, y - 1});
        }
    }

    // ── Actions des boutons ───────────────────────────────────────────────────

    /**
     * Envoie les pixels au serveur via SavePaintingPacket,
     * puis ferme la GUI.
     */
    private void onDone() {
        ModPackets.CHANNEL.send(
                new SavePaintingPacket(menu.getEaselPos(), pixels.clone()),
                PacketDistributor.SERVER.noArg()
        );
        onClose();
    }
}
