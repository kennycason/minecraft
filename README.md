# 🐜 Minecraft Mods Collection

A collection of custom Minecraft Fabric mods for unique gameplay experiences.

## Mods Included

### 🐜 AntFarm Battle Royale Generator

Procedurally generates giant AntFarm for battle royale with other players.

The Antfarm includes: 

- **Maze-like tunnel networks** - Winding passages, hidden alcoves, dead ends
- **Depth-based difficulty** - Easier at top, harder (and better loot!) at bottom
- **Queen's Chamber** - A hellish boss arena at the bottom with spikes, platforms, and treasure
- **Loot chests** - Scattered throughout with depth-scaled rewards
- **Monsters** - Cave spiders, regular spiders, and skeletons lurking in the tunnels
- **Light shafts** - Narrow tunnels to the glass walls for natural lighting
- **Unbreakable walls** - Bedrock frame with barrier-protected glass viewing panels

#### Commands
```
/antfarm demo              # Creates a 30x20x5 demo ant farm
/antfarm create <W> <H> <D> # Custom size (e.g., /antfarm create 100 60 8)
```

## Screenshots

<img src="screenshot/antfarm12.png" width="800" alt="Ant Farm - Full View">

<img src="screenshot/antfarm11.png" width="800" alt="Ant Farm - Full View">

<img src="screenshot/antfarm08.png" width="800" alt="Ant Farm - Tunnel Detail">

<img src="screenshot/antfarm06.png" width="800" alt="Ant Farm - Early Version">

<img src="screenshot/antfarm04.png" width="800" alt="Ant Farm - Early Version">

<img src="screenshot/antfarm02.png" width="800" alt="Ant Farm - First Prototype">


## Requirements

- Minecraft 1.21.x
- Fabric Loader 0.16+
- Fabric API
- Fabric Language Kotlin

## Installation

1. Install Fabric Loader for Minecraft 1.21.x
2. Copy `mods/antfarm-mod-1.0.0.jar` and `fabric-language-kotlin.jar` to your mods folder
3. Launch Minecraft with the Fabric profile

## Building from Source

```bash
cd mods/antfarm
./gradlew build
```

The compiled jar will be in `build/libs/`.

## Server Setup

```bash
# Set Java 21+ via jenv
jenv local 23

# Start the server
bash start.sh
```

## License

MIT
