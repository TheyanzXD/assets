package com.thelostecho.game.ai;

import android.graphics.Point;

import com.thelostecho.game.graphics.TileMapRenderer;

import java.util.ArrayList;
import java.util.List;

/**
 * A* pathfinding on the tile collision grid. Uses Manhattan distance, an
 * open/closed array indexed by tile (fast, no allocations), a bounded search
 * depth to avoid lag, and a reused result list so callers never allocate.
 */
public final class AIPathfinding {

    public static final int MAX_NODES = 200;
    private static final int MOVE_COST_STRAIGHT = 10;
    private static final int MOVE_COST_DIAG = 14;

    private final TileMapRenderer map;
    private final ArrayList<Point> path = new ArrayList<Point>();

    // Work buffers (reused).
    private byte[] closed;
    private int[] openX;
    private int[] openY;
    private int[] gCost;
    private int[] fCost;
    private int[] parentIndex;
    private int openSize;

    public AIPathfinding(TileMapRenderer map) {
        this.map = map;
        int tileCount = map.getMapWidthTiles() * map.getMapHeightTiles();
        closed = new byte[tileCount];
        openX = new int[MAX_NODES];
        openY = new int[MAX_NODES];
        gCost = new int[MAX_NODES];
        fCost = new int[MAX_NODES];
        parentIndex = new int[MAX_NODES];
    }

    /**
     * Finds a path from (sx,sy) to (tx,ty) in world units. Returns the internal
     * list of tile-centers; the caller must consume it before the next call.
     * Empty list means no path (caller should treat the target as unreachable).
     */
    public List<Point> findPath(float sx, float sy, float tx, float ty) {
        path.clear();
        int startX = (int) Math.floor(sx / TileMapRenderer.TILE);
        int startY = (int) Math.floor(sy / TileMapRenderer.TILE);
        int targetX = (int) Math.floor(tx / TileMapRenderer.TILE);
        int targetY = (int) Math.floor(ty / TileMapRenderer.TILE);

        int w = map.getMapWidthTiles();
        int h = map.getMapHeightTiles();

        if (startX < 0 || startY < 0 || startX >= w || startY >= h) {
            return path;
        }
        if (map.isBlocked(targetX, targetY)) {
            // Nudge the target to the nearest walkable neighbour.
            Point n = nearestWalkable(targetX, targetY, w, h);
            if (n == null) {
                return path;
            }
            targetX = n.x;
            targetY = n.y;
        }
        if (startX == targetX && startY == targetY) {
            path.add(new Point(startX, startY));
            return path;
        }

        // Reset buffers for this search.
        int tileCount = w * h;
        if (closed.length != tileCount) {
            closed = new byte[tileCount];
            openX = new int[MAX_NODES];
            openY = new int[MAX_NODES];
            gCost = new int[MAX_NODES];
            fCost = new int[MAX_NODES];
            parentIndex = new int[MAX_NODES];
        } else {
            java.util.Arrays.fill(closed, 0, tileCount, (byte) 0);
        }
        openSize = 0;

        openAdd(startX, startY, 0, manhattan(startX, startY, targetX, targetY), -1);

        while (openSize > 0) {
            int best = findBestOpen();
            int bx = openX[best];
            int by = openY[best];
            if (bx == targetX && by == targetY) {
                rebuildPath(best);
                return path;
            }
            // Move from open to closed.
            int bc = parentIndex[best];
            openSize--;
            openX[best] = openX[openSize];
            openY[best] = openY[openSize];
            gCost[best] = gCost[openSize];
            fCost[best] = fCost[openSize];
            parentIndex[best] = parentIndex[openSize];
            closed[by * w + bx] = 1;

            // 4-connected neighbours (keeps search tight and cheap).
            addNeighbour(bx, by, bx + 1, by, MOVE_COST_STRAIGHT, targetX, targetY, w, h, bc);
            addNeighbour(bx, by, bx - 1, by, MOVE_COST_STRAIGHT, targetX, targetY, w, h, bc);
            addNeighbour(bx, by, bx, by + 1, MOVE_COST_STRAIGHT, targetX, targetY, w, h, bc);
            addNeighbour(bx, by, bx, by - 1, MOVE_COST_STRAIGHT, targetX, targetY, w, h, bc);
        }
        return path;
    }

    private void addNeighbour(int px, int py, int nx, int ny, int moveCost,
                              int targetX, int targetY, int w, int h, int parent) {
        if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
            return;
        }
        if (map.isBlocked(nx, ny)) {
            return;
        }
        if (closed[ny * w + nx] == 1) {
            return;
        }
        // Manhattan distance to player never exceeds 200 in a bounded map, so a
        // simple linear probe into the open list is both correct and cheap.
        for (int i = 0; i < openSize; i++) {
            if (openX[i] == nx && openY[i] == ny) {
                int newG = gCost[parent] + moveCost;
                if (newG < gCost[i]) {
                    gCost[i] = newG;
                    fCost[i] = newG + manhattan(nx, ny, targetX, targetY);
                    parentIndex[i] = parent;
                }
                return;
            }
        }
        openAdd(nx, ny, gCost[parent] + moveCost,
                manhattan(nx, ny, targetX, targetY), parent);
    }

    private void openAdd(int x, int y, int g, int f, int parent) {
        if (openSize >= MAX_NODES) {
            return;
        }
        int w = map.getMapWidthTiles();
        if (closed[y * w + x] == 1) {
            return;
        }
        openX[openSize] = x;
        openY[openSize] = y;
        gCost[openSize] = g;
        fCost[openSize] = f;
        parentIndex[openSize] = parent;
        openSize++;
    }

    private int findBestOpen() {
        int best = 0;
        int bestF = fCost[0];
        for (int i = 1; i < openSize; i++) {
            if (fCost[i] < bestF) {
                bestF = fCost[i];
                best = i;
            }
        }
        return best;
    }

    private int manhattan(int x0, int y0, int x1, int y1) {
        return Math.abs(x1 - x0) + Math.abs(y1 - y0);
    }

    private void rebuildPath(int endIndex) {
        int[] stackX = new int[MAX_NODES];
        int[] stackY = new int[MAX_NODES];
        int depth = 0;
        int idx = endIndex;
        while (idx >= 0 && depth < MAX_NODES) {
            stackX[depth] = openX[idx];
            stackY[depth] = openY[idx];
            depth++;
            idx = parentIndex[idx];
        }
        // Append in reverse so the path starts nearest the caller.
        for (int i = depth - 1; i >= 0; i--) {
            path.add(new Point(stackX[i], stackY[i]));
        }
    }

    private Point nearestWalkable(int x, int y, int w, int h) {
        for (int r = 1; r <= 4; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    int nx = x + dx;
                    int ny = y + dy;
                    if (nx >= 0 && ny >= 0 && nx < w && ny < h && !map.isBlocked(nx, ny)) {
                        return new Point(nx, ny);
                    }
                }
            }
        }
        return null;
    }

    public boolean hasPath() {
        return !path.isEmpty();
    }

    public float getPathTileCenterX(int index) {
        Point p = path.get(index);
        return p.x * TileMapRenderer.TILE + TileMapRenderer.TILE * 0.5f;
    }

    public float getPathTileCenterY(int index) {
        Point p = path.get(index);
        return p.y * TileMapRenderer.TILE + TileMapRenderer.TILE * 0.5f;
    }

    public int getPathSize() {
        return path.size();
    }

    public void clearPath() {
        path.clear();
    }
}
