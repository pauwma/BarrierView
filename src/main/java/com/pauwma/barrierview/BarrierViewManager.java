package com.pauwma.barrierview;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.matrix.Matrix4d;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.DebugShape;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.player.ClearDebugShapes;
import com.hypixel.hytale.protocol.packets.player.DisplayDebug;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class BarrierViewManager {

    public enum DisplayMode {
        INDIVIDUAL,
        GROUPED
    }

    private static final ScheduledExecutorService SCHEDULER = Executors.newScheduledThreadPool(1);
    private static ScheduledFuture<?> updateTask;
    private static boolean running = false;

    private static final Set<UUID> enabledPlayers = ConcurrentHashMap.newKeySet();
    private static final Map<String, World> activeWorlds = new ConcurrentHashMap<>();
    private static final Map<UUID, DisplayMode> playerDisplayModes = new ConcurrentHashMap<>();
    private static final Map<UUID, Vector3f> playerColors = new ConcurrentHashMap<>();

    // Default wireframe color (red for barriers)
    private static final Vector3f DEFAULT_COLOR = new Vector3f(1.0f, 0.0f, 0.0f);

    // Color presets
    public enum ColorPreset {
        RED(1.0f, 0.0f, 0.0f),
        GREEN(0.0f, 1.0f, 0.0f),
        BLUE(0.0f, 0.0f, 1.0f),
        YELLOW(1.0f, 1.0f, 0.0f),
        CYAN(0.0f, 1.0f, 1.0f),
        MAGENTA(1.0f, 0.0f, 1.0f),
        ORANGE(1.0f, 0.5f, 0.0f),
        PINK(1.0f, 0.4f, 0.7f),
        PURPLE(0.6f, 0.0f, 1.0f),
        LIME(0.5f, 1.0f, 0.0f),
        AQUA(0.0f, 0.8f, 0.8f),
        WHITE(1.0f, 1.0f, 1.0f),
        GRAY(0.5f, 0.5f, 0.5f),
        BLACK(0.0f, 0.0f, 0.0f),
        GOLD(1.0f, 0.84f, 0.0f);

        public final float r, g, b;

        ColorPreset(float r, float g, float b) {
            this.r = r;
            this.g = g;
            this.b = b;
        }

        public Vector3f toVector() {
            return new Vector3f(r, g, b);
        }
    }

    // Edge thickness for wireframe
    private static final double EDGE_THICKNESS = 0.04;

    // Chunk size (Hytale chunks are typically 32x32)
    private static final int CHUNK_SIZE = 32;

    // Barrier block ID
    private static final String BARRIER_BLOCK_ID = "Barrier";

    public static DisplayMode getDisplayMode(UUID playerUuid) {
        return playerDisplayModes.getOrDefault(playerUuid, DisplayMode.GROUPED);
    }

    public static Vector3f getColor(UUID playerUuid) {
        return playerColors.getOrDefault(playerUuid, DEFAULT_COLOR);
    }

    public static void setColor(UUID playerUuid, Vector3f color) {
        playerColors.put(playerUuid, color);
    }

    public static void setColor(UUID playerUuid, ColorPreset preset) {
        playerColors.put(playerUuid, preset.toVector());
    }

    public static Vector3f parseHexColor(String hex) {
        // Remove # if present
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        // Support 3-char hex (e.g., "F00" -> "FF0000")
        if (hex.length() == 3) {
            hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2);
        }
        if (hex.length() != 6) {
            return null;
        }
        try {
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);
            return new Vector3f(r / 255.0f, g / 255.0f, b / 255.0f);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String colorToHex(Vector3f color) {
        int r = Math.round(color.x * 255);
        int g = Math.round(color.y * 255);
        int b = Math.round(color.z * 255);
        return String.format("#%02X%02X%02X", r, g, b);
    }

    public static DisplayMode cycleDisplayMode(UUID playerUuid) {
        DisplayMode current = getDisplayMode(playerUuid);
        DisplayMode next = current == DisplayMode.INDIVIDUAL ? DisplayMode.GROUPED : DisplayMode.INDIVIDUAL;
        playerDisplayModes.put(playerUuid, next);
        return next;
    }

    public static boolean toggle(UUID playerUuid) {
        if (enabledPlayers.contains(playerUuid)) {
            enabledPlayers.remove(playerUuid);
            return false;
        } else {
            enabledPlayers.add(playerUuid);
            return true;
        }
    }

    public static boolean isEnabled(UUID playerUuid) {
        return enabledPlayers.contains(playerUuid);
    }

    public static void registerWorld(World world) {
        if (world != null) {
            activeWorlds.put(world.getName(), world);
        }
    }

    public static void removePlayer(UUID playerUuid) {
        enabledPlayers.remove(playerUuid);
        playerDisplayModes.remove(playerUuid);
        playerColors.remove(playerUuid);
    }

    public static void start() {
        if (running) return;
        running = true;

        updateTask = SCHEDULER.scheduleAtFixedRate(() -> {
            try {
                updateBarrierIndicators();
            } catch (Exception e) {
                System.err.println("[BarrierView] Error updating indicators: " + e.getMessage());
            }
        }, 0L, 1500L, TimeUnit.MILLISECONDS);
    }

    public static void stop() {
        running = false;
        if (updateTask != null) {
            updateTask.cancel(false);
            updateTask = null;
        }
        for (World world : activeWorlds.values()) {
            clearDebugShapes(world);
        }
        activeWorlds.clear();
        enabledPlayers.clear();
        playerDisplayModes.clear();
        playerColors.clear();
    }

    private static void updateBarrierIndicators() {
        if (activeWorlds.isEmpty()) return;

        for (World world : activeWorlds.values()) {
            Collection<PlayerRef> playerRefs = world.getPlayerRefs();
            if (playerRefs.isEmpty()) continue;

            for (PlayerRef playerRef : playerRefs) {
                try {
                    if (playerRef == null) continue;

                    UUID playerUuid = playerRef.getUuid();
                    if (!enabledPlayers.contains(playerUuid)) continue;

                    Transform transform = playerRef.getTransform();
                    if (transform == null) continue;

                    Vector3d position = transform.getPosition();
                    if (position == null) continue;

                    int playerX = (int) Math.floor(position.x);
                    int playerY = (int) Math.floor(position.y);
                    int playerZ = (int) Math.floor(position.z);

                    int playerChunkX = Math.floorDiv(playerX, CHUNK_SIZE);
                    int playerChunkZ = Math.floorDiv(playerZ, CHUNK_SIZE);

                    DisplayMode mode = getDisplayMode(playerUuid);

                    world.execute(() -> {
                        try {
                            scanAndRenderBarriers(playerRef, world, playerX, playerY, playerZ, playerChunkX, playerChunkZ, mode);
                        } catch (Exception e) {
                            // Silently ignore
                        }
                    });

                } catch (Exception e) {
                    // Player may have disconnected
                }
            }
        }
    }

    private static void scanAndRenderBarriers(PlayerRef playerRef, World world,
                                               int playerX, int playerY, int playerZ,
                                               int playerChunkX, int playerChunkZ,
                                               DisplayMode mode) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store == null) return;

        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null) return;

        TransformComponent transformComponent = store.getComponent(ref, EntityModule.get().getTransformComponentType());
        if (transformComponent == null) return;

        WorldChunk chunk = transformComponent.getChunk();
        if (chunk == null) return;

        // Collect all barrier positions
        Set<BlockPos> barrierPositions = new HashSet<>();

        int chunkMinX = playerChunkX * CHUNK_SIZE;
        int chunkMaxX = chunkMinX + CHUNK_SIZE - 1;
        int chunkMinZ = playerChunkZ * CHUNK_SIZE;
        int chunkMaxZ = chunkMinZ + CHUNK_SIZE - 1;

        int minY = Math.max(0, playerY - 32);
        int maxY = Math.min(255, playerY + 32);

        for (int x = chunkMinX; x <= chunkMaxX; x++) {
            for (int z = chunkMinZ; z <= chunkMaxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    try {
                        BlockType blockType = chunk.getBlockType(x, y, z);
                        if (blockType != null && BARRIER_BLOCK_ID.equals(blockType.getId())) {
                            barrierPositions.add(new BlockPos(x, y, z));
                        }
                    } catch (Exception e) {
                        // Block access failed
                    }
                }
            }
        }

        if (barrierPositions.isEmpty()) return;

        Vector3f playerColor = getColor(playerRef.getUuid());
        com.hypixel.hytale.protocol.Vector3f protoColor = new com.hypixel.hytale.protocol.Vector3f(
                playerColor.x, playerColor.y, playerColor.z
        );

        if (mode == DisplayMode.INDIVIDUAL) {
            // Render individual wireframes (all 12 edges per block)
            for (BlockPos pos : barrierPositions) {
                renderWireframeBox(playerRef, pos.x, pos.y, pos.z, pos.x + 1, pos.y + 1, pos.z + 1, protoColor);
            }
        } else {
            // Render outline only - edges where there's no adjacent barrier
            renderOutline(playerRef, barrierPositions, protoColor);
        }
    }

    private static void renderOutline(PlayerRef playerRef, Set<BlockPos> barrierPositions,
                                       com.hypixel.hytale.protocol.Vector3f protoColor) {
        // Collect all unique edges and check each one
        Set<EdgePos> drawnEdges = new HashSet<>();

        for (BlockPos pos : barrierPositions) {
            int x = pos.x;
            int y = pos.y;
            int z = pos.z;

            // Check all 12 edges of this block
            // Vertical edges (along Y axis) - check 4 blocks around each edge in XZ plane
            checkAndDrawVerticalEdge(playerRef, barrierPositions, drawnEdges, x, y, z, protoColor);     // -X,-Z corner
            checkAndDrawVerticalEdge(playerRef, barrierPositions, drawnEdges, x+1, y, z, protoColor);   // +X,-Z corner
            checkAndDrawVerticalEdge(playerRef, barrierPositions, drawnEdges, x, y, z+1, protoColor);   // -X,+Z corner
            checkAndDrawVerticalEdge(playerRef, barrierPositions, drawnEdges, x+1, y, z+1, protoColor); // +X,+Z corner

            // Horizontal edges along X axis - check 4 blocks around each edge in YZ plane
            checkAndDrawXEdge(playerRef, barrierPositions, drawnEdges, x, y, z, protoColor);     // -Y,-Z corner
            checkAndDrawXEdge(playerRef, barrierPositions, drawnEdges, x, y+1, z, protoColor);   // +Y,-Z corner
            checkAndDrawXEdge(playerRef, barrierPositions, drawnEdges, x, y, z+1, protoColor);   // -Y,+Z corner
            checkAndDrawXEdge(playerRef, barrierPositions, drawnEdges, x, y+1, z+1, protoColor); // +Y,+Z corner

            // Horizontal edges along Z axis - check 4 blocks around each edge in XY plane
            checkAndDrawZEdge(playerRef, barrierPositions, drawnEdges, x, y, z, protoColor);     // -X,-Y corner
            checkAndDrawZEdge(playerRef, barrierPositions, drawnEdges, x+1, y, z, protoColor);   // +X,-Y corner
            checkAndDrawZEdge(playerRef, barrierPositions, drawnEdges, x, y+1, z, protoColor);   // -X,+Y corner
            checkAndDrawZEdge(playerRef, barrierPositions, drawnEdges, x+1, y+1, z, protoColor); // +X,+Y corner
        }
    }

    // Check if vertical edge at (ex, ey, ez) should be drawn
    // Edge goes from (ex, ey, ez) to (ex, ey+1, ez)
    // Check the 4 blocks around it in XZ plane
    private static void checkAndDrawVerticalEdge(PlayerRef playerRef, Set<BlockPos> barriers,
                                                  Set<EdgePos> drawn, int ex, int ey, int ez,
                                                  com.hypixel.hytale.protocol.Vector3f color) {
        EdgePos edge = new EdgePos(ex, ey, ez, 'Y');
        if (drawn.contains(edge)) return;

        // 4 blocks around this vertical edge
        boolean a = barriers.contains(new BlockPos(ex - 1, ey, ez - 1));
        boolean b = barriers.contains(new BlockPos(ex, ey, ez - 1));
        boolean c = barriers.contains(new BlockPos(ex - 1, ey, ez));
        boolean d = barriers.contains(new BlockPos(ex, ey, ez));

        if (shouldDrawEdge(a, b, c, d)) {
            drawn.add(edge);
            renderEdge(playerRef, ex, ey + 0.5, ez, EDGE_THICKNESS, 1.0, EDGE_THICKNESS, color);
        }
    }

    // Check if X-axis edge at (ex, ey, ez) should be drawn
    // Edge goes from (ex, ey, ez) to (ex+1, ey, ez)
    // Check the 4 blocks around it in YZ plane
    private static void checkAndDrawXEdge(PlayerRef playerRef, Set<BlockPos> barriers,
                                           Set<EdgePos> drawn, int ex, int ey, int ez,
                                           com.hypixel.hytale.protocol.Vector3f color) {
        EdgePos edge = new EdgePos(ex, ey, ez, 'X');
        if (drawn.contains(edge)) return;

        // 4 blocks around this X edge
        boolean a = barriers.contains(new BlockPos(ex, ey - 1, ez - 1));
        boolean b = barriers.contains(new BlockPos(ex, ey, ez - 1));
        boolean c = barriers.contains(new BlockPos(ex, ey - 1, ez));
        boolean d = barriers.contains(new BlockPos(ex, ey, ez));

        if (shouldDrawEdge(a, b, c, d)) {
            drawn.add(edge);
            renderEdge(playerRef, ex + 0.5, ey, ez, 1.0, EDGE_THICKNESS, EDGE_THICKNESS, color);
        }
    }

    // Check if Z-axis edge at (ex, ey, ez) should be drawn
    // Edge goes from (ex, ey, ez) to (ex, ey, ez+1)
    // Check the 4 blocks around it in XY plane
    private static void checkAndDrawZEdge(PlayerRef playerRef, Set<BlockPos> barriers,
                                           Set<EdgePos> drawn, int ex, int ey, int ez,
                                           com.hypixel.hytale.protocol.Vector3f color) {
        EdgePos edge = new EdgePos(ex, ey, ez, 'Z');
        if (drawn.contains(edge)) return;

        // 4 blocks around this Z edge
        boolean a = barriers.contains(new BlockPos(ex - 1, ey - 1, ez));
        boolean b = barriers.contains(new BlockPos(ex, ey - 1, ez));
        boolean c = barriers.contains(new BlockPos(ex - 1, ey, ez));
        boolean d = barriers.contains(new BlockPos(ex, ey, ez));

        if (shouldDrawEdge(a, b, c, d)) {
            drawn.add(edge);
            renderEdge(playerRef, ex, ey, ez + 0.5, EDGE_THICKNESS, EDGE_THICKNESS, 1.0, color);
        }
    }

    // Determine if edge should be drawn based on the 4 blocks around it
    // a,b,c,d form a 2x2 grid: a-b / c-d (where a-d and b-c are diagonals)
    private static boolean shouldDrawEdge(boolean a, boolean b, boolean c, boolean d) {
        int count = (a ? 1 : 0) + (b ? 1 : 0) + (c ? 1 : 0) + (d ? 1 : 0);

        if (count == 0 || count == 4) {
            // No blocks or completely surrounded - don't draw
            return false;
        } else if (count == 1 || count == 3) {
            // Single block corner or 3-block concave corner - draw
            return true;
        } else {
            // count == 2: draw only if diagonal, not if adjacent
            return (a && d && !b && !c) || (b && c && !a && !d);
        }
    }

    // Helper class for edge positions
    private static class EdgePos {
        final int x, y, z;
        final char axis; // 'X', 'Y', or 'Z'

        EdgePos(int x, int y, int z, char axis) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.axis = axis;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof EdgePos)) return false;
            EdgePos edgePos = (EdgePos) o;
            return x == edgePos.x && y == edgePos.y && z == edgePos.z && axis == edgePos.axis;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y, z, axis);
        }
    }

    private static List<BoundingBox> findConnectedGroups(Set<BlockPos> positions) {
        List<BoundingBox> groups = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();

        for (BlockPos start : positions) {
            if (visited.contains(start)) continue;

            // BFS to find all connected blocks
            Set<BlockPos> group = new HashSet<>();
            Queue<BlockPos> queue = new LinkedList<>();
            queue.add(start);

            while (!queue.isEmpty()) {
                BlockPos current = queue.poll();
                if (visited.contains(current)) continue;
                if (!positions.contains(current)) continue;

                visited.add(current);
                group.add(current);

                // Check all 6 neighbors (and diagonals for better grouping)
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;
                            BlockPos neighbor = new BlockPos(current.x + dx, current.y + dy, current.z + dz);
                            if (positions.contains(neighbor) && !visited.contains(neighbor)) {
                                queue.add(neighbor);
                            }
                        }
                    }
                }
            }

            // Calculate bounding box for this group
            if (!group.isEmpty()) {
                int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
                int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

                for (BlockPos pos : group) {
                    minX = Math.min(minX, pos.x);
                    minY = Math.min(minY, pos.y);
                    minZ = Math.min(minZ, pos.z);
                    maxX = Math.max(maxX, pos.x + 1);
                    maxY = Math.max(maxY, pos.y + 1);
                    maxZ = Math.max(maxZ, pos.z + 1);
                }

                groups.add(new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ));
            }
        }

        return groups;
    }

    private static void renderWireframeBox(PlayerRef playerRef, double minX, double minY, double minZ,
                                            double maxX, double maxY, double maxZ,
                                            com.hypixel.hytale.protocol.Vector3f protoColor) {
        double lenX = maxX - minX;
        double lenY = maxY - minY;
        double lenZ = maxZ - minZ;

        // Bottom edges
        renderEdge(playerRef, (minX + maxX) / 2.0, minY, minZ, lenX, EDGE_THICKNESS, EDGE_THICKNESS, protoColor);
        renderEdge(playerRef, (minX + maxX) / 2.0, minY, maxZ, lenX, EDGE_THICKNESS, EDGE_THICKNESS, protoColor);
        renderEdge(playerRef, minX, minY, (minZ + maxZ) / 2.0, EDGE_THICKNESS, EDGE_THICKNESS, lenZ, protoColor);
        renderEdge(playerRef, maxX, minY, (minZ + maxZ) / 2.0, EDGE_THICKNESS, EDGE_THICKNESS, lenZ, protoColor);

        // Top edges
        renderEdge(playerRef, (minX + maxX) / 2.0, maxY, minZ, lenX, EDGE_THICKNESS, EDGE_THICKNESS, protoColor);
        renderEdge(playerRef, (minX + maxX) / 2.0, maxY, maxZ, lenX, EDGE_THICKNESS, EDGE_THICKNESS, protoColor);
        renderEdge(playerRef, minX, maxY, (minZ + maxZ) / 2.0, EDGE_THICKNESS, EDGE_THICKNESS, lenZ, protoColor);
        renderEdge(playerRef, maxX, maxY, (minZ + maxZ) / 2.0, EDGE_THICKNESS, EDGE_THICKNESS, lenZ, protoColor);

        // Vertical edges
        renderEdge(playerRef, minX, (minY + maxY) / 2.0, minZ, EDGE_THICKNESS, lenY, EDGE_THICKNESS, protoColor);
        renderEdge(playerRef, maxX, (minY + maxY) / 2.0, minZ, EDGE_THICKNESS, lenY, EDGE_THICKNESS, protoColor);
        renderEdge(playerRef, minX, (minY + maxY) / 2.0, maxZ, EDGE_THICKNESS, lenY, EDGE_THICKNESS, protoColor);
        renderEdge(playerRef, maxX, (minY + maxY) / 2.0, maxZ, EDGE_THICKNESS, lenY, EDGE_THICKNESS, protoColor);
    }

    private static void renderEdge(PlayerRef playerRef, double cx, double cy, double cz,
                                   double sx, double sy, double sz,
                                   com.hypixel.hytale.protocol.Vector3f color) {
        Matrix4d matrix = new Matrix4d();
        matrix.identity();
        matrix.translate(cx, cy, cz);
        matrix.scale(sx, sy, sz);

        DisplayDebug packet = new DisplayDebug(DebugShape.Cube, matrix.asFloatData(), color, 2.0f, true, null);
        playerRef.getPacketHandler().write((Packet) packet);
    }

    public static void clearDebugShapes(PlayerRef playerRef) {
        if (playerRef == null) return;
        ClearDebugShapes packet = new ClearDebugShapes();
        playerRef.getPacketHandler().write((Packet) packet);
    }

    public static void clearDebugShapes(World world) {
        if (world == null) return;
        ClearDebugShapes packet = new ClearDebugShapes();
        for (PlayerRef playerRef : world.getPlayerRefs()) {
            playerRef.getPacketHandler().write((Packet) packet);
        }
    }

    // Helper class for block positions
    private static class BlockPos {
        final int x, y, z;

        BlockPos(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BlockPos)) return false;
            BlockPos blockPos = (BlockPos) o;
            return x == blockPos.x && y == blockPos.y && z == blockPos.z;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y, z);
        }
    }

    // Helper class for bounding boxes
    private static class BoundingBox {
        final double minX, minY, minZ, maxX, maxY, maxZ;

        BoundingBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }
    }
}
