package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/** Draws the tvOS style lighter band behind the first (dock) row and adds a gap below it. */
public class DockDecoration extends RecyclerView.ItemDecoration {

    private final int dockSize;
    private final float density;
    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    /** Extra band below the tiles for their names (always_show_names), in px. */
    private final float namesRoom;
    /** Light glass like the top bar's pills (config.xml dock_glass): a white tint that fades downwards. */
    private final boolean lightGlass;
    /** Padding all round the tiles equal to the gap between them (config.xml dock_even_padding). */
    private final boolean evenPadding;
    /** The band's top and bottom the glass gradient was made for; it is remade when they move. */
    private float shaderTop = Float.NaN, shaderBottom = Float.NaN;
    /** Draw the band and its edge (config.xml dock_band); false leaves the tiles floating on the wallpaper. */
    private final boolean bandVisible;
    /** single_row: the tiles after the dock share its row, so they take the same offsets to stay level with it. */
    private final boolean singleRow;
    /** dock_band_all: in a single row, the band runs behind every app instead of the dock apps only. */
    private final boolean bandAll;
    /** Whether the wallpaper under the band is bright. The band's paint is the same
     *  either way (see onDraw) — the flag only forces the gradient to be remade on a flip. */
    private boolean brightWallpaper;
    private final int edgeColorNormal;

    // Fork change: real glass for the band (the free-my-tv recipe) — an AGSL shader over a
    // small blurred copy of the wallpaper (the frost), with a smoky tint, refraction at the
    // rim and a directional rim light. Needs API 33+; without it (or without a frost) the
    // flat gradient below is drawn exactly as before.
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
    private final Paint glassPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** The RuntimeShader, held as its base class so older APIs never resolve it. */
    private android.graphics.Shader glassShader;
    private android.graphics.BitmapShader frostShader;
    private android.graphics.Bitmap frostBitmap, frostShaderBitmap;
    private int frostScreenW, frostScreenH;
    private final int[] screenLoc = new int[2];

    /** Hands the band the wallpaper's frost (a small blurred copy) and the screen size it
     *  maps to. Creates the glass shader on first use; any failure leaves it null and the
     *  band keeps its flat gradient. */
    void setFrost(android.graphics.Bitmap frost, int screenW, int screenH) {
        if (frost == null || screenW <= 0 || screenH <= 0) return;
        frostBitmap = frost;
        frostScreenW = screenW;
        frostScreenH = screenH;
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            try {
                if (glassShader == null) {
                    glassShader = new android.graphics.RuntimeShader(GLASS_AGSL);
                }
                if (frost != frostShaderBitmap) {
                    frostShader = new android.graphics.BitmapShader(frost,
                            Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                    ((android.graphics.RuntimeShader) glassShader).setInputShader("frost", frostShader);
                    frostShaderBitmap = frost;
                }
            } catch (RuntimeException ignored) {
                glassShader = null;
            }
        }
    }

    /**
     * @param translucent the cinematic shelf's band: the artwork and the glow show through it.
     * @param names       the dock tiles show their names, so the band reaches down over them.
     * @param opacity     the plain band's opacity in percent (dock_opacity), so a wallpaper can show through it.
     * @param lightGlass  a light frosted band like the pills, for a sky or wallpaper behind it.
     * @param evenPadding the band's padding is the gap between the tiles on all four sides (dock_even_padding).
     * @param bandVisible false skips the band and its edge, so the tiles float on the wallpaper (dock_band).
     * @param singleRow   every app sits in the dock's row (single_row); the band covers the dock apps only, unless bandAll.
     * @param bandAll     with singleRow, the band runs behind every app in the row (dock_band_all).
     */
    DockDecoration(int dockSize, float density, boolean translucent, boolean names, int opacity, boolean lightGlass,
                   boolean evenPadding, boolean bandVisible, boolean singleRow, boolean bandAll) {
        this.dockSize = dockSize;
        this.singleRow = singleRow;
        this.bandAll = singleRow && bandAll;
        this.density = density;
        this.namesRoom = names ? 22 * density : 0;
        this.lightGlass = lightGlass;
        this.evenPadding = evenPadding;
        this.bandVisible = bandVisible;
        band.setColor(translucent ? 0x8C121014 : (Math.round(opacity * 2.55f) << 24) | 0x141416);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(density);
        edgeColorNormal = lightGlass ? 0x38FFFFFF : translucent ? 0x26FFFFFF : 0x1FFFFFFF;
        edge.setColor(edgeColorNormal);
    }

    /** Note a bright (or dark) wallpaper under the band; remakes the gradient on a flip. */
    void setBrightWallpaper(boolean bright) {
        if (brightWallpaper == bright) return;
        brightWallpaper = bright;
        shaderTop = Float.NaN;   // force the gradient to be remade
    }

    /** Concentric corners: the band's radius is the tiles' radius plus the band's padding around them. */
    static float radiusPx(float tileRadiusPx, float paddingPx) {
        return tileRadiusPx + paddingPx;
    }

    /**
     * The band's padding on every side for dock_even_padding: the gap between two dock tiles (a cell minus its tile, as
     * AppAdapter.tileWidthPx lays them out), capped by the room the grid leaves at its edges.
     */
    static float evenPaddingPx(int innerWidthPx, int columns, int tileWidthPx, float roomPx) {
        float gap = columns > 0 ? innerWidthPx / (float) columns - tileWidthPx : 0f;
        return Math.max(0f, Math.min(gap, roomPx));
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        int pos = parent.getChildAdapterPosition(view);
        if (pos >= 0 && (singleRow || pos < dockSize)) {
            outRect.top = (int) (6 * density);
            outRect.bottom = (int) (12 * density);
        }
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        if (!bandVisible) return;
        boolean any = false;
        int cardWidth = 0;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int pos = parent.getChildAdapterPosition(child);
            if (pos < 0 || (!bandAll && pos >= dockSize)) continue;
            RecyclerView.ViewHolder vh = parent.getChildViewHolder(child);
            View card = vh instanceof AppAdapter.Holder ? ((AppAdapter.Holder) vh).card : child.findViewById(R.id.card);
            if (card == null) continue;
            float l = child.getLeft() + card.getLeft();
            float t = child.getTop() + card.getTop() + child.getTranslationY();
            float r = l + card.getWidth();
            float b = t + card.getHeight();
            cardWidth = card.getWidth();
            if (!any) {
                rect.set(l, t, r, b);
                any = true;
            } else {
                rect.union(l, t, r, b);
            }
        }
        if (!any) return;
        float pad = 14 * density;
        // The band can only draw inside the grid, and the first and last dock tiles sit 12dp in from its
        // edges: a full 14dp at the sides sliced the rounded ends flat (#454). Keep the sides, with the
        // 1dp edge line, inside the grid; the top and bottom have room.
        // 22dp at the sides (the grid's 10dp padding makes room): a focused first or last tile grows about 7dp
        // outwards and still keeps about the band's top margin from its rounded end.
        float sidePad = 22 * density;
        float side = Math.max(0f, Math.min(sidePad, Math.min(rect.left, parent.getWidth() - rect.right) - density));
        float radius = AppAdapter.cornerRadius(cardWidth, density);
        if (evenPadding) {
            // The same padding on all four sides, so the band's corners are concentric with the tiles' everywhere.
            int inner = parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight();
            // A single row's tiles can run off the screen, so only its top limits the padding; its ends are clamped below.
            float room = (singleRow ? rect.top : Math.min(rect.top, Math.min(rect.left, parent.getWidth() - rect.right))) - density;
            float even = evenPaddingPx(inner, dockSize, cardWidth, room);
            rect.inset(-even, -even);
            radius = radiusPx(radius, even);
        } else {
            // dock_even_padding off: the band as before, with its fixed 26 dp corners.
            rect.inset(singleRow ? -sidePad : -side, -pad);
            radius = 26 * density;
        }
        if (singleRow) {
            // Tiles past the screen's edge: the band stops at the grid's edge with its rounded end instead of being
            // sliced square there.
            rect.left = Math.max(rect.left, density);
            rect.right = Math.min(rect.right, parent.getWidth() - density);
        }
        rect.bottom += namesRoom;
        if (lightGlass && (rect.top != shaderTop || rect.bottom != shaderBottom)) {
            // The pills' glass (pill_bg: white, 18% fading to 8%), a little fainter over this much larger area.
            // Fork change: this faint white glass in both modes — on bright wallpapers it
            // was chosen over a stronger white glass and over a dark tint.
            band.setShader(new LinearGradient(0, rect.top, 0, rect.bottom,
                    0x24FFFFFF, 0x0DFFFFFF,
                    Shader.TileMode.CLAMP));
            shaderTop = rect.top;
            shaderBottom = rect.bottom;
        }
        if (glassShader != null && frostBitmap != null) {
            // The glass band: the frost is sampled in screen coordinates, so the grid's
            // place on the screen goes in as the offset. The shader draws its own rim
            // light, so the flat edge stroke is skipped.
            android.graphics.RuntimeShader shader = (android.graphics.RuntimeShader) glassShader;
            parent.getLocationOnScreen(screenLoc);
            shader.setFloatUniform("frostScale",
                    frostBitmap.getWidth() / (float) frostScreenW,
                    frostBitmap.getHeight() / (float) frostScreenH);
            shader.setFloatUniform("offset", screenLoc[0], screenLoc[1]);
            shader.setFloatUniform("center", rect.centerX(), rect.centerY());
            shader.setFloatUniform("halfSize", rect.width() / 2f, rect.height() / 2f);
            shader.setFloatUniform("radius", radius);
            shader.setFloatUniform("refraction", 10 * density);
            // Smoked glass: the frost mixed 45% toward near-black, lifted a touch.
            shader.setFloatUniform("tint", 0x11 / 255f, 0x11 / 255f, 0x13 / 255f, 0.45f);
            shader.setFloatUniform("lift", 0.02f);
            glassPaint.setShader(shader);
            c.drawRoundRect(rect, radius, radius, glassPaint);
            return;
        }
        // Fork change: no dark border in bright mode (a dark edge line read too heavy);
        // the band keeps its normal faint white edge in both modes.
        edge.setColor(edgeColorNormal);
        c.drawRoundRect(rect, radius, radius, band);
        c.drawRoundRect(rect, radius, radius, edge);
    }
}
