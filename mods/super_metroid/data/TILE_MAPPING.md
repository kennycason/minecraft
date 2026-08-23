# Super Metroid Tile ID → Tileset Image Mapping

## Overview

Each room's level data contains 16-bit tile words. The lower 10 bits (bits 0-9) give a **metatile index** (0-1023). Each metatile is a 16×16 pixel block composed of four 8×8 sub-tiles.

The 1024 metatiles are split between two sources:

| Range     | Count | Source | Export File |
|-----------|-------|--------|-------------|
| 0 - 639   | 640   | **URE** (area-specific variable tiles) | `tileset_{id}_ure.png` |
| 640 - 1023 | 384   | **CRE** (Common Room Elements, shared by all rooms) | `cre_tiles.png` |

## Tile Word Format (16-bit)

```
Bits 0-9:   metatile index (0-1023)
Bit  10:    horizontal flip
Bit  11:    vertical flip
Bits 12-15: collision type (0=air, 8=solid, 9=door, etc.)
```

A tile is considered "solid" when `collision >= 8`.

## Exported Tileset Images

### URE (Area Tiles): `tileset_{id}_ure.png`

- Contains 8×8 sub-tiles (NOT the 16×16 metatiles directly)
- Layout: **16 columns** of 8×8 pixel tiles
- 640 sub-tiles → 16 columns × 40 rows → **128 × 320 pixels**
- These are the raw 4bpp character tiles used by metatiles 0-639

### CRE (Common Tiles): `cre_tiles.png`

- Same format: 16 columns of 8×8 pixel tiles
- 384 sub-tiles → 16 columns × 24 rows → **128 × 192 pixels**
- These are the raw 4bpp character tiles used by metatiles 640-1023

### Palette: `tileset_{id}_palette.png`

- 8 rows × 16 columns of color swatches
- Each row is a 16-color sub-palette used by the tileset

## How Metatiles Map to Sub-Tiles

Each metatile (16×16) is composed of **4 sub-tiles** (8×8), arranged:

```
[TL] [TR]
[BL] [BR]
```

Each sub-tile reference is a 16-bit SNES tilemap word:
```
Bits 0-9:   sub-tile number (0-1023) — index into the 8x8 graphics data
Bits 10-12: palette row (0-7)
Bit  13:    priority
Bit  14:    horizontal flip
Bit  15:    vertical flip
```

## Metatile Grid Image (Full Tileset)

The editor can also render a **metatile grid** (all 1024 metatiles at 16×16 each):
- Layout: **32 columns × 32 rows** → **512 × 512 pixels**
- Metatile `i` is at grid position: column `i % 32`, row `i / 32`
- Pixel offset: `x = (i % 32) * 16`, `y = (i / 32) * 16`

## Mapping a Room Tile ID to the Tileset Image

Given a metatile index from room level data:

1. **Determine source**: index < 640 → URE, index >= 640 → CRE
2. **The metatile grid** (512×512) is the easiest lookup — just use the index directly
3. **The sub-tile sheets** (URE/CRE PNGs) contain the raw 8×8 characters, not metatiles. To go from metatile to sub-tiles you need the metatile table (tile table data from ROM), which defines which four 8×8 tiles compose each metatile.

### Simplest Approach: Use the Metatile Grid

If you export the full tileset grid (512×512), each metatile index maps directly:

```
x = (metatileIndex % 32) * 16
y = (metatileIndex / 32) * 16
crop 16×16 pixels from (x, y)
```

This is the recommended approach for the Minecraft mod — it gives you the rendered 16×16 tile image directly.

## Room JSON Fields

```json
{
  "tileset": 0,        // which tileset to use (determines URE graphics)
  "area": 0,           // area number
  "levelDataBase64": "..." // base64-encoded decompressed level data
}
```

### Parsing levelDataBase64

```
Bytes 0-1:  layer1Size (little-endian 16-bit)
Bytes 2+:   Layer 1 tile data (tilesX × tilesY × 2 bytes)
            Each tile = 2 bytes (little-endian 16-bit word)
After L1:   BTS data (tilesX × tilesY × 1 byte)
After BTS:  Layer 2 tile data (same format as L1)

tilesX = width * 16    (width in screens, 16 tiles per screen)
tilesY = height * 16
```

## Tileset IDs

The `tileset` field in room JSON identifies which URE graphics to load. Common values:

| Tileset | Area |
|---------|------|
| 0 | Crateria (surface, Landing Site) |
| 1 | Crateria (underground) |
| 2 | Brinstar green |
| 3 | Brinstar red |
| 4 | Norfair |
| ... | (29 total tilesets in vanilla ROM) |

CRE tiles are the same across all tilesets (shared door caps, save stations, etc).
