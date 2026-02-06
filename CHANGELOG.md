# Changelog

All notable changes to BarrierView will be documented in this file.

## [1.0.0] - 2025-02-06

### Complete Redesign
This version is a complete rewrite of the mod, replacing the texture-based approach with a command-driven wireframe system.

### Added
- **`/showbarrier`** command to toggle barrier visibility per-player (alias: `/barrierview`)
- **`/barriermode`** command to switch between Individual and Grouped display modes
- **`/barriercolor`** command to customize wireframe colors
  - 15 color presets: red, green, blue, yellow, cyan, magenta, orange, pink, purple, lime, aqua, white, gray, black, gold
  - Animated rainbow mode that cycles through 8 colors
  - Full hex color support (#RGB, #RRGGBB formats)
- **Grouped display mode** - Renders only the outer outline of connected barrier shapes, hiding internal edges
- **Individual display mode** - Renders complete wireframe boxes around each barrier block
- Per-player settings for visibility, display mode, and color
- Automatic cleanup when players disconnect

### Changed
- Barriers are now invisible by default (vanilla behavior)
- Visibility is now toggled per-player instead of globally
- Switched from texture replacement to debug wireframe rendering
- Grouped mode is now the default display mode

### Removed
- Barrier texture replacement assets (Barrier.blockymodel, BlockTextures)
- Asset pack functionality (IncludesAssetPack: false)

### Technical
- Uses Hytale's DisplayDebug packet system for efficient per-player rendering
- Scans current chunk (32x32 blocks) within ±32 blocks vertically
- Updates every 1.5 seconds for balanced performance
- Smart edge detection algorithm hides internal edges between adjacent barriers in Grouped mode

---

## [0.2.0] - Previous Version

### Features
- Made barrier blocks visible using custom textures
- Global visibility (all players see the same thing)
- Texture-based approach with asset pack
