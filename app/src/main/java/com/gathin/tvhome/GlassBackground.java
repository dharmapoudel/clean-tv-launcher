package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;

/**
 * A top-pill background that is real glass, like the dock band's: an AGSL shader over a
 * small blurred copy of the wallpaper (the frost), so the pill wears the wallpaper itself
 * and there is no light/dark background flip — only the glyph ink changes. Focused, it
 * draws the same solid near-white disc the flat background used. Needs API 33+; without
 * the shader (or before the first frost arrives) the wrapped fallback drawable is drawn.
 */
public class GlassBackground extends Drawable implements Drawable.Callback {

    // The same shader as DockDecoration's band (kept in step by hand).
    private static final String GLASS_AGSL =
            "uniform shader frost;\n"
            + "uniform float2 frostScale;\n"
            + "uniform float2 offset;\n"
            + "uniform float2 center;\n"
            + "uniform float2 halfSize;\n"
            + "uniform float radius;\n"
            + "uniform float refraction;\n"
            + "uniform half4 tint;\n"
            + "uniform float lift;\n"
            + "float hash(float2 p) {\n"
            + "  p = fract(p * float2(123.34, 456.21));\n"
            + "  p += dot(p, p + 45.32);\n"
            + "  return fract(p.x * p.y);\n"
            + "}\n"
            + "half4 main(float2 xy) {\n"
            + "  float2 d = xy - center;\n"
            + "  float2 q = abs(d) - (halfSize - radius);\n"
            + "  float sd = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;\n"
            + "  float aa = clamp(0.5 - sd, 0.0, 1.0);\n"
            + "  if (aa <= 0.0) { return half4(0.0); }\n"
            + "  float2 dir = d / halfSize;\n"
            + "  float rim = 1.0 - clamp(-sd * 0.4545, 0.0, 1.0);\n"
            + "  float light = clamp(-dir.y * 0.75 - dir.x * 0.25 + 0.3, 0.0, 1.0);\n"
            + "  float sheen = (1.0 - clamp((d.y + halfSize.y) * 0.055, 0.0, 1.0)) * 0.06;\n"
            + "  float hl = rim * (0.08 + 0.5 * light) + sheen;\n"
            + "  float edge = 1.0 - clamp(-sd * 0.0714, 0.0, 1.0);\n"
            + "  float2 sxy = xy - dir * (edge * edge * refraction);\n"
            + "  half3 bg = frost.eval((sxy + offset) * frostScale).rgb * 1.12 + half3(0.02);\n"
            + "  half3 col = mix(bg, tint.rgb, tint.a);\n"
            + "  col += half3(lift);\n"
            + "  col *= 1.0 - 0.2 * (1.0 - clamp(-sd * 0.0625, 0.0, 1.0)) * clamp(dir.y, 0.0, 1.0);\n"
            + "  col += half3(hl);\n"
            + "  col += (hash(xy) - 0.5) * 0.0039;\n"
            + "  return half4(col * aa, aa);\n"
            + "}\n";

    private final Drawable fallback;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint solidPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final int[] loc = new int[2];
    /** The RuntimeShader, held as its base class so older APIs never resolve it. */
    private Shader glassShader;
    private BitmapShader frostShader;
    private Bitmap frostBitmap, frostShaderBitmap;
    private int frostScreenW, frostScreenH;
    private float tintAlpha = 0.12f;
    private boolean focused;

    GlassBackground(Drawable fallback) {
        this.fallback = fallback;
        solidPaint.setColor(0xF5FFFFFF);
        if (fallback != null) fallback.setCallback(this);
    }

    /** Hands the glass the wallpaper's frost and the screen size it maps to. */
    void setFrost(Bitmap frost, int screenW, int screenH) {
        if (frost == null || screenW <= 0 || screenH <= 0) return;
        frostBitmap = frost;
        frostScreenW = screenW;
        frostScreenH = screenH;
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                if (glassShader == null) {
                    glassShader = new android.graphics.RuntimeShader(GLASS_AGSL);
                }
                if (frost != frostShaderBitmap) {
                    frostShader = new BitmapShader(frost, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                    ((android.graphics.RuntimeShader) glassShader).setInputShader("frost", frostShader);
                    frostShaderBitmap = frost;
                }
            } catch (RuntimeException ignored) {
                glassShader = null;
            }
        }
        invalidateSelf();
    }

    void setTintAlpha(float alpha) {
        tintAlpha = alpha;
        invalidateSelf();
    }

    @Override
    public boolean isStateful() {
        return true;
    }

    @Override
    protected boolean onStateChange(int[] stateSet) {
        boolean now = false;
        for (int state : stateSet) {
            if (state == android.R.attr.state_focused) now = true;
        }
        if (fallback != null) fallback.setState(stateSet);
        if (now != focused) {
            focused = now;
            invalidateSelf();
            return true;
        }
        return false;
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty()) return;
        float radius = bounds.height() / 2f;
        if (focused) {
            rect.set(bounds);
            canvas.drawRoundRect(rect, radius, radius, solidPaint);
            return;
        }
        Callback callback = getCallback();
        if (glassShader != null && frostBitmap != null && callback instanceof View) {
            View host = (View) callback;
            host.getLocationOnScreen(loc);
            android.graphics.RuntimeShader shader = (android.graphics.RuntimeShader) glassShader;
            shader.setFloatUniform("frostScale",
                    frostBitmap.getWidth() / (float) frostScreenW,
                    frostBitmap.getHeight() / (float) frostScreenH);
            shader.setFloatUniform("offset", loc[0], loc[1]);
            shader.setFloatUniform("center", bounds.centerX(), bounds.centerY());
            shader.setFloatUniform("halfSize", bounds.width() / 2f, bounds.height() / 2f);
            shader.setFloatUniform("radius", radius);
            shader.setFloatUniform("refraction", 5f);
            shader.setFloatUniform("tint", 0x11 / 255f, 0x11 / 255f, 0x13 / 255f, tintAlpha);
            shader.setFloatUniform("lift", 0.02f);
            paint.setShader(shader);
            rect.set(bounds);
            canvas.drawRoundRect(rect, radius, radius, paint);
            return;
        }
        if (fallback != null) {
            fallback.setBounds(bounds);
            fallback.draw(canvas);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        solidPaint.setAlpha(alpha);
        if (fallback != null) fallback.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
        if (fallback != null) fallback.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public void invalidateDrawable(Drawable who) {
        invalidateSelf();
    }

    @Override
    public void scheduleDrawable(Drawable who, Runnable what, long when) {
        scheduleSelf(what, when);
    }

    @Override
    public void unscheduleDrawable(Drawable who, Runnable what) {
        unscheduleSelf(what);
    }
}
