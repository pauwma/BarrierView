# BarrierView

A Hytale server plugin that renders wireframe outlines around invisible barrier blocks, making them visible to players for building and debugging purposes.

## Features

- **Per-Player Toggle** - Each player can independently enable or disable barrier visibility
- **Display Modes** - Choose between Individual block outlines or Grouped connected shapes
- **Customizable Colors** - 15 preset colors plus full hex color support
- **Real-Time Updates** - Automatically detects and displays barriers in your current chunk
- **Lightweight** - Minimal performance impact with efficient chunk-based scanning

## Commands

| Command | Alias | Description |
|---------|-------|-------------|
| `/showbarrier` | `/barrierview` | Toggle barrier wireframe visibility on/off |
| `/barriermode` | - | Switch between Individual and Grouped display modes |
| `/barriercolor [color]` | - | Change wireframe color (preset name or hex value) |

### Color Options

**Presets:** `red`, `green`, `blue`, `yellow`, `cyan`, `magenta`, `orange`, `pink`, `purple`, `lime`, `aqua`, `white`, `gray`, `black`, `gold`

**Hex Values:** Use standard hex format like `#FF0000`, `#F00`, `FF5500`, etc.

## Display Modes

### Individual Mode
Renders a complete wireframe box around each barrier block separately. Best for precise block placement and counting.

### Grouped Mode (Default)
Detects connected barrier blocks and renders only the outer outline of the combined shape. Creates cleaner visuals for walls, floors, and complex structures. Internal edges between adjacent blocks are hidden.

## Installation

1. Build the plugin using `./gradlew build`
2. Copy the generated JAR from `build/libs/` to your server's mods folder
3. Restart the server

## Requirements

- Hytale Server (Early Access)
- Java 25+

## Building from Source

```bash
# Clone the repository
git clone <repository-url>
cd BarrierView

# Build the plugin
./gradlew build

# The compiled JAR will be in build/libs/
```

## Usage Examples

```
# Enable barrier visibility
/showbarrier

# Switch to grouped outline mode
/barriermode

# Change color to cyan
/barriercolor cyan

# Use a custom hex color
/barriercolor #00FF88
```

## Technical Details

- Scans the player's current chunk (32x32 blocks) within ±32 blocks vertically
- Uses Hytale's debug shape rendering system for efficient per-player visuals
- Updates every 1.5 seconds to balance responsiveness and performance
- All settings are stored per-player and cleared on disconnect

## License

MIT License - Feel free to use, modify, and distribute.

## Contributing

Contributions are welcome! Please feel free to submit issues and pull requests.
