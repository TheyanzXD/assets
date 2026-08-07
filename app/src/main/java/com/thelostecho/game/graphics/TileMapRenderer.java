package com.thelostecho.game.graphics;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import java.util.Random;

/**
 * Grid-based 2D tile map. Data is kept as primitive arrays (no per-tile
 * objects); rendering culls to the visible viewport and multiplies by the
 * device density so the world is resolution-independent. Supports collision
 * queries, per-tile surface type (for footsteps), shadow tiles (stealth
 * hiding), camera follow with a dead zone, culling and a parallax skyline.
 */
public final class TileMapRenderer {

    public static final int TILE = 32;
    public static final int TILE_FLOOR = 0;
    public static final int TILE_WALL = 1;
    public static final int TILE_DECOR = 2;

    public static final int SURFACE_CONCRETE = 0;
    public static final int SURFACE_METAL = 1;
    public static final int SURFACE_GRASS = 2;

    private final Paint floorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint floorAltPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wallEdgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint decorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint();
    private final Paint skyPaint = new Paint();
    private final Paint buildingPaint = new Paint();
    private final RectF tileRect = new RectF();

    private int[][] tiles;
    private boolean[][] collision;
    private boolean[][] shadow;
    private int[][] surface;
    private int mapW;
    private int mapH;
    private final float scale;

    private float camX = 0f;
    private float camY = 0f;
    private float viewW = 1f;
    private float viewH = 1f;
    private float deadZoneW = 120f;
    private float deadZoneH = 120f;

    private boolean parallaxEnabled = true;
    private int areaSurface = SURFACE_CONCRETE;

    public TileMapRenderer(float density) {
        scale = Math.max(0.5f, density);
        floorPaint.setARGB(255, 46, 44, 52);
        floorAltPaint.setARGB(255, 40, 39, 46);
        wallPaint.setARGB(255, 76, 84, 104);
        wallEdgePaint.setARGB(255, 96, 106, 130);
        decorPaint.setARGB(255, 60, 70, 96);
        shadowPaint.setARGB(120, 0, 0, 0);
        skyPaint.setARGB(255, 12, 14, 26);
        buildingPaint.setARGB(255, 24, 26, 40);
    }

    public void setViewSize(float w, float h) {
        viewW = w;
        viewH = h;
    }

    public void setParallaxEnabled(boolean on) {
        parallaxEnabled = on;
    }

    public void setAreaSurface(int surfaceType) {
        areaSurface = surfaceType;
    }

    // ------------------------------------------------------------------
    // Map generation (procedural: rooms + corridors, always connected)
    // ------------------------------------------------------------------

    public void generateMap(int widthTiles, int heightTiles, long seed, int surfaceType) {
        mapW = widthTiles;
        mapH = heightTiles;
        areaSurface = surfaceType;
        tiles = new int[mapW][mapH];
        for (int x = 0; x < mapW; x++) {
            for (int y = 0; y < mapH; y++) {
                tiles[x][y] = TILE_FLOOR;
            }
        }
        Random rng = new Random(seed);

        // Border walls.
        for (int x = 0; x < mapW; x++) {
            tiles[x][0] = TILE_WALL;
            tiles[x][mapH - 1] = TILE_WALL;
        }
        for (int y = 0; y < mapH; y++) {
            tiles[0][y] = TILE_WALL;
            tiles[mapW - 1][y] = TILE_WALL;
        }

        // Rooms.
        int roomCount = 5 + rng.nextInt(4);
        int[] roomX = new int[roomCount];
        int[] roomY = new int[roomCount];
        int[] roomW = new int[roomCount];
        int[] roomH = new int[roomCount];
        for (int i = 0; i < roomCount; i++) {
            int rw = 6 + rng.nextInt(7);
            int rh = 5 + rng.nextInt(6);
            int rx = 2 + rng.nextInt(Math.max(1, mapW - rw - 4));
            int ry = 2 + rng.nextInt(Math.max(1, mapH - rh - 4));
            roomX[i] = rx;
            roomY[i] = ry;
            roomW[i] = rw;
            roomH[i] = rh;
            clearRoom(rx, ry, rw, rh);
        }

        // Connect rooms with wide L-shaped corridors (2 tiles wide).
        for (int i = 1; i < roomCount; i++) {
            int sx = roomX[i] + roomW[i] / 2;
            int sy = roomY[i] + roomH[i] / 2;
            int tx = roomX[i - 1] + roomW[i - 1] / 2;
            int ty = roomY[i - 1] + roomH[i - 1] / 2;
            carveCorridor(sx, sy, tx, sy);
            carveCorridor(tx, sy, tx, ty);
        }

        // A few interior wall segments to break up sight lines.
        int wallSegments = 3 + rng.nextInt(3);
        for (int i = 0; i < wallSegments; i++) {
            int sx = 3 + rng.nextInt(mapW - 8);
            int sy = 3 + rng.nextInt(mapH - 8);
            int len = 4 + rng.nextInt(6);
            boolean horizontal = rng.nextBoolean();
            for (int j = 0; j < len; j++) {
                int wx = horizontal ? sx + j : sx;
                int wy = horizontal ? sy : sy + j;
                if (wx > 1 && wy > 1 && wx < mapW - 2 && wy < mapH - 2) {
                    tiles[wx][wy] = TILE_WALL;
                }
            }
        }

        // Decor tiles: scattered floor markings / crates visuals.
        int decorCount = mapW * mapH / 40;
        for (int i = 0; i < decorCount; i++) {
            int dx = 2 + rng.nextInt(mapW - 4);
            int dy = 2 + rng.nextInt(mapH - 4);
            if (tiles[dx][dy] == TILE_FLOOR) {
                tiles[dx][dy] = TILE_DECOR;
            }
        }

        buildDerived();
    }

    private void clearRoom(int rx, int ry, int rw, int rh) {
        for (int x = rx; x < rx + rw; x++) {
            for (int y = ry; y < ry + rh; y++) {
                if (x > 0 && y > 0 && x < mapW - 1 && y < mapH - 1) {
                    tiles[x][y] = TILE_FLOOR;
                }
            }
        }
    }

    private void carveCorridor(int x0, int y0, int x1, int y1) {
        int stepX = x1 > x0 ? 1 : -1;
        int x = x0;
        while (x != x1) {
            tiles[x][y0] = TILE_FLOOR;
            if (y0 + 1 < mapH) {
                tiles[x][y0 + 1] = TILE_FLOOR;
            }
            x += stepX;
        }
        int stepY = y1 > y0 ? 1 : -1;
        int y = y0;
        while (y != y1) {
            tiles[x1][y] = TILE_FLOOR;
            if (x1 + 1 < mapW) {
                tiles[x1 + 1][y] = TILE_FLOOR;
            }
            y += stepY;
        }
    }

    private void buildDerived() {
        collision = new boolean[mapW][mapH];
        shadow = new boolean[mapW][mapH];
        surface = new int[mapW][mapH];
        for (int x = 0; x < mapW; x++) {
            for (int y = 0; y < mapH; y++) {
                collision[x][y] = tiles[x][y] == TILE_WALL;
                surface[x][y] = areaSurface;
                if (tiles[x][y] == TILE_FLOOR || tiles[x][y] == TILE_DECOR) {
                    shadow[x][y] = hasAdjacentWall(x, y);
                }
            }
        }
        camX = TILE * 1.5f;
        camY = TILE * 1.5f;
    }

    private boolean hasAdjacentWall(int x, int y) {
        if (x > 0 && tiles[x - 1][y] == TILE_WALL) {
            return true;
        }
        if (x < mapW - 1 && tiles[x + 1][y] == TILE_WALL) {
            return true;
        }
        if (y > 0 && tiles[x][y - 1] == TILE_WALL) {
            return true;
        }
        return y < mapH - 1 && tiles[x][y + 1] == TILE_WALL;
    }

    // ------------------------------------------------------------------
    // Queries (used by AI, entities, renderers)
    // ------------------------------------------------------------------

    public boolean isBlocked(int tx, int ty) {
        if (tx < 0 || ty < 0 || tx >= mapW || ty >= mapH) {
            return true;
        }
        return collision[tx][ty];
    }

    public boolean isWalkablePixel(float x, float y) {
        int tx = (int) Math.floor(x / TILE);
        int ty = (int) Math.floor(y / TILE);
        return !isBlocked(tx, ty);
    }

    /** AABB walkability test on the four corners. */
    public boolean isWalkableRect(float left, float top, float right, float bottom) {
        return isWalkablePixel(left, top) && isWalkablePixel(right, top)
                && isWalkablePixel(left, bottom) && isWalkablePixel(right, bottom);
    }

    public boolean isShadowPixel(float x, float y) {
        int tx = (int) Math.floor(x / TILE);
        int ty = (int) Math.floor(y / TILE);
        if (tx < 0 || ty < 0 || tx >= mapW || ty >= mapH) {
            return false;
        }
        return shadow[tx][ty];
    }

    public int getSurfaceAt(float x, float y) {
        int tx = (int) Math.floor(x / TILE);
        int ty = (int) Math.floor(y / TILE);
        if (tx < 0 || ty < 0 || tx >= mapW || ty >= mapH) {
            return areaSurface;
        }
        return surface[tx][ty];
    }

    public int getTileAt(int tx, int ty) {
        if (tx < 0 || ty < 0 || tx >= mapW || ty >= mapH) {
            return TILE_WALL;
        }
        return tiles[tx][ty];
    }

    public boolean isFloorTile(int tx, int ty) {
        return !isBlocked(tx, ty);
    }

    /** Dynamically blocks/unblocks a tile (used by pushable crates). */
    public void setBlocked(int tx, int ty, boolean blocked) {
        if (tx < 0 || ty < 0 || tx >= mapW || ty >= mapH) {
            return;
        }
        collision[tx][ty] = blocked;
        if (!blocked) {
            shadow[tx][ty] = false;
        }
    }

    /** Clears a door tile so the player can walk through after unlocking. */
    public void openDoorTile(int tx, int ty) {
        if (tx < 0 || ty < 0 || tx >= mapW || ty >= mapH) {
            return;
        }
        tiles[tx][ty] = TILE_FLOOR;
        collision[tx][ty] = false;
        shadow[tx][ty] = false;
        surface[tx][ty] = areaSurface;
    }

    public int getMapWidthTiles() {
        return mapW;
    }

    public int getMapHeightTiles() {
        return mapH;
    }

    public int getMapWidthPx() {
        return mapW * TILE;
    }

    public int getMapHeightPx() {
        return mapH * TILE;
    }

    // ------------------------------------------------------------------
    // Camera
    // ------------------------------------------------------------------

    public void updateCamera(float targetX, float targetY) {
        float cx = camX;
        float cy = camY;
        float left = cx + deadZoneW;
        float right = cx + viewW / scale - deadZoneW;
        float top = cy + deadZoneH;
        float bottom = cy + viewH / scale - deadZoneH;

        if (targetX < left) {
            cx += targetX - left;
        } else if (targetX > right) {
            cx += targetX - right;
        }
        if (targetY < top) {
            cy += targetY - top;
        } else if (targetY > bottom) {
            cy += targetY - bottom;
        }

        float maxX = Math.max(0f, mapW * TILE - viewW / scale);
        float maxY = Math.max(0f, mapH * TILE - viewH / scale);
        if (cx < 0f) {
            cx = 0f;
        }
        if (cy < 0f) {
            cy = 0f;
        }
        if (cx > maxX) {
            cx = maxX;
        }
        if (cy > maxY) {
            cy = maxY;
        }
        camX = cx;
        camY = cy;
    }

    public float getCamX() {
        return camX;
    }

    public float getCamY() {
        return camY;
    }

    public float getScale() {
        return scale;
    }

    public float worldToScreenX(float worldX) {
        return (worldX - camX) * scale;
    }

    public float worldToScreenY(float worldY) {
        return (worldY - camY) * scale;
    }

    public float screenToWorldX(float screenX) {
        return screenX / scale + camX;
    }

    public float screenToWorldY(float screenY) {
        return screenY / scale + camY;
    }

    // ------------------------------------------------------------------
    // Rendering
    //
    // NOTE: the scene applies a single canvas transform
    // (translate(-camX,-camY) * scale) before calling draw(), so all
    // drawing here is in world units.
    // ------------------------------------------------------------------

    /** Screen-space sky / parallax backdrop. */
    public void drawBackground(Canvas canvas) {
        if (!parallaxEnabled) {
            canvas.drawRect(0f, 0f, viewW, viewH, skyPaint);
            return;
        }
        canvas.drawRect(0f, 0f, viewW, viewH, skyPaint);
        float s = scale;
        float skylineWidth = mapW * TILE * s;
        float offset = -camX * s * 0.35f;
        int buildingCount = 14;
        float bw = skylineWidth / buildingCount;
        for (int i = 0; i < buildingCount; i++) {
            float bx = offset + i * bw;
            if (bx > viewW || bx + bw < 0f) {
                continue;
            }
            float bh = (70f + ((i * 37) % 80)) * s;
            float by = viewH * 0.4f - bh;
            canvas.drawRect(bx, by, bx + bw - 6f * s, viewH * 0.4f, buildingPaint);
        }
    }

    /** World-space tile rendering (call under the scene's world transform). */
    public void draw(Canvas canvas) {
        if (tiles == null) {
            return;
        }
        int startX = (int) Math.floor(camX / TILE);
        int startY = (int) Math.floor(camY / TILE);
        int endX = (int) Math.ceil((camX + viewW / scale) / TILE);
        int endY = (int) Math.ceil((camY + viewH / scale) / TILE);
        if (startX < 0) {
            startX = 0;
        }
        if (startY < 0) {
            startY = 0;
        }
        if (endX > mapW) {
            endX = mapW;
        }
        if (endY > mapH) {
            endY = mapH;
        }

        for (int y = startY; y < endY; y++) {
            for (int x = startX; x < endX; x++) {
                int t = tiles[x][y];
                if (t == TILE_WALL) {
                    continue;
                }
                tileRect.set(x * TILE, y * TILE, (x + 1) * TILE, (y + 1) * TILE);
                canvas.drawRect(tileRect, (x + y) % 2 == 0 ? floorPaint : floorAltPaint);
                if (t == TILE_DECOR) {
                    canvas.drawCircle(tileRect.centerX(), tileRect.centerY(),
                            TILE * 0.2f, decorPaint);
                }
                if (shadow[x][y]) {
                    canvas.drawRect(tileRect, shadowPaint);
                }
            }
        }
        // Walls on top.
        for (int y = startY; y < endY; y++) {
            for (int x = startX; x < endX; x++) {
                if (tiles[x][y] != TILE_WALL) {
                    continue;
                }
                tileRect.set(x * TILE, y * TILE, (x + 1) * TILE, (y + 1) * TILE);
                canvas.drawRect(tileRect, wallPaint);
                canvas.drawLine(tileRect.left, tileRect.top, tileRect.right, tileRect.top,
                        wallEdgePaint);
            }
        }
    }

    public void dispose() {
        // Primitive arrays; nothing to release.
    }
}
