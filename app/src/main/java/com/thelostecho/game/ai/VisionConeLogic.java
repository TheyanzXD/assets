package com.thelostecho.game.ai;

import com.thelostecho.game.graphics.TileMapRenderer;

/**
 * Vision logic: range test, angle test (dot product against the facing vector)
 * and a DDA raycast for line-of-sight obstruction by the tile collision map.
 * Fully static, allocation-free.
 */
public final class VisionConeLogic {

    private VisionConeLogic() {
    }

    /**
     * @param map         tile map used for occlusion
     * @param ex,ey       observer position (world units)
     * @param facingRad   observer facing angle in radians
     * @param coneAngle   total cone angle in radians (60 degrees = PI/3)
     * @param range       max sight range in world units
     * @param px,py       target position
     */
    public static boolean canSee(TileMapRenderer map, float ex, float ey,
                                 float facingRad, float coneAngle, float range,
                                 float px, float py) {
        float dx = px - ex;
        float dy = py - ey;
        float distSq = dx * dx + dy * dy;
        float rangeSq = range * range;
        if (distSq > rangeSq) {
            return false;
        }
        if (distSq < 0.0001f) {
            return true;
        }
        float dist = (float) Math.sqrt(distSq);
        float toTargetAngle = (float) Math.atan2(dy, dx);
        float diff = normalizeAngle(toTargetAngle - facingRad);
        if (Math.abs(diff) > coneAngle * 0.5f) {
            return false;
        }
        return rayClear(map, ex, ey, ex + dx / dist * (dist - 8f),
                ey + dy / dist * (dist - 8f));
    }

    public static float normalizeAngle(float a) {
        while (a > (float) Math.PI) {
            a -= 2f * (float) Math.PI;
        }
        while (a < -(float) Math.PI) {
            a += 2f * (float) Math.PI;
        }
        return a;
    }

    /**
     * DDA tile traversal between two world points. Returns false if any
     * collision tile is crossed before the target is reached.
     */
    public static boolean rayClear(TileMapRenderer map, float x0, float y0,
                                   float x1, float y1) {
        int tile = TileMapRenderer.TILE;
        int tileX0 = (int) Math.floor(x0 / tile);
        int tileY0 = (int) Math.floor(y0 / tile);
        int tileX1 = (int) Math.floor(x1 / tile);
        int tileY1 = (int) Math.floor(y1 / tile);

        int stepX = tileX1 > tileX0 ? 1 : (tileX1 < tileX0 ? -1 : 0);
        int stepY = tileY1 > tileY0 ? 1 : (tileY1 < tileY0 ? -1 : 0);

        float dx = x1 - x0;
        float dy = y1 - y0;
        float tMaxX;
        float tMaxY;
        float tDeltaX;
        float tDeltaY;

        if (stepX != 0) {
            float nextX = (stepX > 0 ? tileX0 + 1 : tileX0) * tile;
            tMaxX = (nextX - x0) / dx;
            tDeltaX = tile / Math.abs(dx);
        } else {
            tMaxX = Float.MAX_VALUE;
            tDeltaX = Float.MAX_VALUE;
        }
        if (stepY != 0) {
            float nextY = (stepY > 0 ? tileY0 + 1 : tileY0) * tile;
            tMaxY = (nextY - y0) / dy;
            tDeltaY = tile / Math.abs(dy);
        } else {
            tMaxY = Float.MAX_VALUE;
            tDeltaY = Float.MAX_VALUE;
        }

        int cx = tileX0;
        int cy = tileY0;
        while (true) {
            if (map.isBlocked(cx, cy)) {
                return false;
            }
            if (cx == tileX1 && cy == tileY1) {
                break;
            }
            if (tMaxX < tMaxY) {
                tMaxX += tDeltaX;
                cx += stepX;
            } else {
                tMaxY += tDeltaY;
                cy += stepY;
            }
            if (cx < 0 || cy < 0 || cx >= map.getMapWidthTiles()
                    || cy >= map.getMapHeightTiles()) {
                break;
            }
        }
        return true;
    }
}
