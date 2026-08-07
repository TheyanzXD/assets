package com.thelostecho.game.graphics;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * Ambient darkness overlay + point lights. A half-resolution full-screen Bitmap
 * is filled with the ambient dark colour, then each light punches a soft hole
 * using DST_OUT (a shared white RadialGradient, positioned via a reused
 * Matrix). A second pass adds a subtle coloured glow (SRC_OVER) for lamps,
 * drone spotlights, the player's sonar glow and suspicion cones. Rebuilt every
 * frame; everything is preallocated.
 */
public final class LightingRenderer {

    public static final int LIGHT_WARM = 0;
    public static final int LIGHT_CYAN = 1;
    public static final int LIGHT_YELLOW = 2;
    public static final int LIGHT_RED = 3;
    public static final int LIGHT_WHITE = 4;
    public static final int LIGHT_TYPES = 5;

    private static final int MAX_LIGHTS = 64;
    private static final float BASE_RADIUS = 2000f;
    private static final int HALF_RES_FACTOR = 2;

    private static final class Light {
        float sx;
        float sy;
        float radius;
        int type;
    }

    private final Light[] lights = new Light[MAX_LIGHTS];
    private int lightCount = 0;

    private Bitmap overlay;
    private Canvas overlayCanvas;
    private float viewW = 1f;
    private float viewH = 1f;

    private final Paint holePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint overlayPaint = new Paint();
    private final Matrix lightMatrix = new Matrix();
    private final RadialGradient holeGradient;
    private final RadialGradient[] tintGradients = new RadialGradient[LIGHT_TYPES];
    private final RectF fullScreen = new RectF();

    private int ambientColor = 0xAF080A1E;

    public LightingRenderer() {
        holePaint.setStyle(Paint.Style.FILL);
        holePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        overlayPaint.setFilterBitmap(true);
        overlayPaint.setDither(true);

        holeGradient = new RadialGradient(0f, 0f, BASE_RADIUS,
                new int[]{0xFFFFFFFF, 0xFFE8F8FF, 0x00000000},
                new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP);

        tintGradients[LIGHT_WARM] = new RadialGradient(0f, 0f, BASE_RADIUS,
                new int[]{0x99FFF0C0, 0x33FFE090, 0x00000000},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        tintGradients[LIGHT_CYAN] = new RadialGradient(0f, 0f, BASE_RADIUS,
                new int[]{0x8850E8FF, 0x2210C0F0, 0x00000000},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        tintGradients[LIGHT_YELLOW] = new RadialGradient(0f, 0f, BASE_RADIUS,
                new int[]{0x77FFF070, 0x22FFE040, 0x00000000},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        tintGradients[LIGHT_RED] = new RadialGradient(0f, 0f, BASE_RADIUS,
                new int[]{0x77FF5050, 0x22FF2020, 0x00000000},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        tintGradients[LIGHT_WHITE] = new RadialGradient(0f, 0f, BASE_RADIUS,
                new int[]{0x88FFFFFF, 0x22FFFFFF, 0x00000000},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);

        for (int i = 0; i < MAX_LIGHTS; i++) {
            lights[i] = new Light();
        }
    }

    public void setViewSize(float w, float h) {
        if (viewW == w && viewH == h && overlay != null) {
            return;
        }
        viewW = w;
        viewH = h;
        if (overlay != null) {
            overlay.recycle();
        }
        int bw = Math.max(2, (int) w / HALF_RES_FACTOR);
        int bh = Math.max(2, (int) h / HALF_RES_FACTOR);
        overlay = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        overlayCanvas = new Canvas(overlay);
        fullScreen.set(0f, 0f, w, h);
    }

    public void setAmbientColor(int color) {
        ambientColor = color;
    }

    public void clearLights() {
        lightCount = 0;
    }

    public void addLight(float screenX, float screenY, float radiusPx, int type) {
        if (lightCount >= MAX_LIGHTS) {
            return;
        }
        Light l = lights[lightCount];
        l.sx = screenX;
        l.sy = screenY;
        l.radius = radiusPx;
        l.type = type;
        lightCount++;
    }

    /** Re-composites the darkness buffer. Call once per frame. */
    public void update() {
        if (overlay == null || overlayCanvas == null) {
            return;
        }
        float k = 1f / HALF_RES_FACTOR;
        overlayCanvas.drawColor(ambientColor);

        // Pass 1: punch soft holes (DST_OUT).
        for (int i = 0; i < lightCount; i++) {
            Light l = lights[i];
            float r = Math.max(1f, l.radius * k);
            lightMatrix.setScale(r / BASE_RADIUS, r / BASE_RADIUS);
            lightMatrix.postTranslate(l.sx * k, l.sy * k);
            holeGradient.setLocalMatrix(lightMatrix);
            holePaint.setShader(holeGradient);
            overlayCanvas.drawCircle(l.sx * k, l.sy * k, r, holePaint);
        }
        // Pass 2: coloured glow (SRC_OVER).
        for (int i = 0; i < lightCount; i++) {
            Light l = lights[i];
            float r = Math.max(1f, l.radius * k * 0.85f);
            RadialGradient g = tintGradients[l.type];
            lightMatrix.setScale(r / BASE_RADIUS, r / BASE_RADIUS);
            lightMatrix.postTranslate(l.sx * k, l.sy * k);
            g.setLocalMatrix(lightMatrix);
            tintPaint.setShader(g);
            tintPaint.setXfermode(null);
            overlayCanvas.drawCircle(l.sx * k, l.sy * k, r, tintPaint);
        }
    }

    public void draw(Canvas canvas) {
        if (overlay != null) {
            canvas.drawBitmap(overlay, null, fullScreen, overlayPaint);
        }
    }

    public void dispose() {
        if (overlay != null) {
            overlay.recycle();
            overlay = null;
        }
        lightCount = 0;
    }
}
