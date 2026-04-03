# 🎨 PaintCraft

> A Minecraft Forge mod that lets players paint custom pixel-art paintings and hang them on walls.

[![Build](https://github.com/YOUR_USERNAME/PaintCraft/actions/workflows/build.yml/badge.svg)](https://github.com/YOUR_USERNAME/PaintCraft/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21-brightgreen)](https://www.minecraft.net)
[![Forge](https://img.shields.io/badge/Forge-51.0.33-orange)](https://files.minecraftforge.net)

---

## 📖 Description

PaintCraft adds a fully interactive painting system to Minecraft. Craft an easel, place a blank canvas on it, open the painting GUI, create your pixel-art masterpiece, and hang your unique painting on any wall — it persists across server restarts!

### Features

- **Easel** — a placeable block that holds a canvas for painting
- **Blank Canvas** — craftable item that goes on the easel
- **Painter's Palette** — the tool used to open the painting GUI
- **Custom Paintings** — 16×16 pixel-art paintings stored in NBT, placeable on walls
- **Full multiplayer support** — painting data synced client ↔ server
- **Persistent** — paintings survive world saves and server restarts

---

## 🖼️ Screenshots

> *Screenshots coming soon — build the mod and start painting!*

| Easel in world | Painting GUI | Wall painting |
|:-:|:-:|:-:|
| ![easel](docs/screenshots/easel.png) | ![gui](docs/screenshots/gui.png) | ![wall](docs/screenshots/wall.png) |

---

## 🔧 Installation

### Requirements

- Minecraft **1.21**
- Forge **51.0.33** or newer (for 1.21)
- Java **21**

### Steps

1. Download the latest `.jar` from the [Releases](https://github.com/YOUR_USERNAME/PaintCraft/releases) page.
2. Drop the `.jar` into your `mods/` folder.
3. Launch Minecraft with the Forge profile.

---

## 🎮 Gameplay

```
1. Craft a Canvas:   4 Sticks + 1 White Wool + 1 Paper
2. Craft an Easel:   3 Sticks + 3 Planks (any)
3. Craft a Palette:  1 Plank + 1 Dye (any color)

4. Place the Easel in the world
5. Right-click the Easel with the Canvas  →  canvas is placed on the easel
6. Right-click the Easel with the Palette →  opens the painting GUI
7. Paint on the 16×16 grid, pick colors, use tools (Brush / Fill / Eyedropper)
8. Click "Done"  →  you receive a Painted Canvas item
9. Right-click on a wall to hang your painting!
```

---

## 🛠️ Build from Source

### Prerequisites

- JDK 21
- Git

### Steps

```bash
git clone https://github.com/YOUR_USERNAME/PaintCraft.git
cd PaintCraft

# Generate IDE run configurations
./gradlew genIntellijRuns   # IntelliJ IDEA
# or
./gradlew genEclipseRuns    # Eclipse

# Build the mod jar
./gradlew build
# Output: build/libs/paintcraft-<version>.jar

# Run the Minecraft client for testing
./gradlew runClient

# Run a dedicated server for testing
./gradlew runServer
```

---

## 📦 Project Structure

```
PaintCraft/
├── src/main/java/com/paintcraft/
│   ├── PaintCraft.java              # Main mod class
│   ├── block/
│   │   ├── ModBlocks.java           # Block registry
│   │   └── EaselBlock.java          # Easel block logic
│   ├── blockentity/
│   │   ├── ModBlockEntities.java    # BlockEntity registry
│   │   └── EaselBlockEntity.java    # Easel data storage
│   ├── item/
│   │   ├── ModItems.java            # Item registry
│   │   ├── CanvasItem.java          # Blank canvas item
│   │   ├── PaletteItem.java         # Painter's palette item
│   │   └── PaintingItem.java        # Finished painting item (NBT)
│   ├── menu/
│   │   ├── ModMenuTypes.java        # Menu registry
│   │   └── PaintingMenu.java        # Server-side container
│   ├── screen/
│   │   └── PaintingScreen.java      # Client-side painting GUI
│   └── network/
│       └── ModPackets.java          # Client↔Server packet handling
└── src/main/resources/
    ├── META-INF/mods.toml
    ├── assets/paintcraft/
    │   ├── blockstates/easel.json
    │   ├── lang/{en_us,fr_fr}.json
    │   ├── models/block/easel.json
    │   ├── models/item/{canvas,palette,painting}.json
    │   └── textures/...
    └── data/paintcraft/recipes/
        ├── easel.json
        ├── canvas.json
        └── palette.json
```

---

## 📝 License

This project is licensed under the **MIT License** — see [LICENSE](LICENSE) for details.

---

## 🤝 Contributing

Pull requests are welcome! For major changes, please open an issue first.

1. Fork the repository
2. Create your branch: `git checkout -b feature/my-feature`
3. Commit your changes: `git commit -m 'Add my feature'`
4. Push: `git push origin feature/my-feature`
5. Open a Pull Request

---

## 🙏 Credits

- Built with [Minecraft Forge](https://minecraftforge.net)
- Inspired by vanilla Minecraft paintings
