package com.thelostecho.game.graphics;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import com.thelostecho.game.entities.DroneEnemy;
import com.thelostecho.game.entities.GuardEnemy;
import com.thelostecho.game.entities.TurretEnemy;

import java.util.List;

/**
 * Debug/visualization layer for enemy vision cones and turret fire lines.
 * Toggleable from developer settings; disabled by default.
 */
public final class VisionConeRenderer {

    private final Paint conePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint beamPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF coneRect = new RectF();

    public VisionConeRenderer() {
        conePaint.setStyle(Paint.Style.FILL);
        beamPaint.setStyle(Paint.Style.STROKE);
        beamPaint.setStrokeWidth(3f);
    }

    public void draw(Canvas canvas, List<DroneEnemy> drones,
                     List<GuardEnemy> guards, List<TurretEnemy> turrets) {
        if (drones != null) {
            for (int i = 0; i < drones.size(); i++) {
                DroneEnemy d = drones.get(i);
                if (!d.isActive()) {
                    continue;
                }
                int color;
                if (d.isAlert()) {
                    color = 0x33FF4040;
                } else if (d.isChasing()) {
                    color = 0x33FFE040;
                } else {
                    color = 0x2240A0FF;
                }
                drawCone(canvas, d.x, d.y, d.getFacingRad(),
                        DroneEnemy.CONE_ANGLE, d.getSightRange(), color);
            }
        }
        if (guards != null) {
            for (int i = 0; i < guards.size(); i++) {
                GuardEnemy g = guards.get(i);
                if (!g.isActive()) {
                    continue;
                }
                int color = g.isAlert() ? 0x33FF4040 : 0x2240A0FF;
                drawCone(canvas, g.x, g.y, g.getFacingRad(),
                        GuardEnemy.CONE_ANGLE, g.getSightRange(), color);
            }
        }
        if (turrets != null) {
            beamPaint.setColor(0x66FF3030);
            for (int i = 0; i < turrets.size(); i++) {
                TurretEnemy t = turrets.get(i);
                if (!t.isActive() || t.isDisabled()) {
                    continue;
                }
                float ang = t.getBarrelAngle();
                float ex = t.x + (float) Math.cos(ang) * t.getSightRange();
                float ey = t.y + (float) Math.sin(ang) * t.getSightRange();
                canvas.drawLine(t.x, t.y, ex, ey, beamPaint);
            }
        }
    }

    private void drawCone(Canvas canvas, float x, float y, float facing,
                          float coneAngle, float range, int color) {
        conePaint.setColor(color);
        float half = coneAngle * 0.5f;
        float start = facing - half;
        float sweep = coneAngle;
        coneRect.set(x - range, y - range, x + range, y + range);
        canvas.drawArc(coneRect, start * 57.29578f, sweep * 57.29578f, true, conePaint);
    }

    public void dispose() {
    }
}
