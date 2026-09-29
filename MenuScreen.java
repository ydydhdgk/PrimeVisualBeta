package com.example.visuals;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.audio.SimpleSound;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.StringTextComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MenuScreen extends Screen {
    private static final int W = 400, H = 256, SIDE = 100, CW = 276;
    private static final String[] TABS = {"Инфо", "Интерфейс", "Прицел", "Настройки"};
    private static final int[][] TAB_MODULES = {
            {Config.FPS, Config.COORDS, Config.DIR, Config.SPEED, Config.PING, Config.LIGHT, Config.TIME},
            {Config.EFFECTS, Config.DURABILITY, Config.KEYS, Config.TARGET, Config.WATERMARK},
            {Config.CROSSHAIR},
            {}
    };
    private static final String[] STYLES = {"Крест", "Точка", "Круг", "Т-образный"};

    private static class Hit {
        final int x, y, w, h, id;
        Hit(int x, int y, int w, int h, int id) { this.x = x; this.y = y; this.w = w; this.h = h; this.id = id; }
    }

    private final List<Hit> hits = new ArrayList<>();
    private final float[] toggle = new float[Config.ON.length];
    private final float[] hover = new float[Config.ON.length];
    private float tabInd = 0f;
    private int tab = 0;
    private int drag = -1;
    private final long openAt = System.currentTimeMillis();
    private int px, py;

    public MenuScreen() {
        super(new StringTextComponent("Useful Visuals"));
        for (int i = 0; i < toggle.length; i++) toggle[i] = Config.ON[i] ? 1f : 0f;
    }

    @Override
    protected void init() {
        px = (width - W) / 2;
        py = (height - H) / 2;
    }

    private float fit() {
        return Math.min(1f, Math.min((height - 8f) / H, (width - 8f) / W));
    }

    private double local(double m, int size) {
        return size / 2.0 + (m - size / 2.0) / fit();
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void click(float pitch) {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSound.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
        }
    }

    // ============================ Отрисовка ============================
    @Override
    public void render(MatrixStack ms, int mouseX, int mouseY, float pt) {
        long now = System.currentTimeMillis();
        float t = Math.min(1f, (now - openAt) / 200f);
        float ease = 1f - (1f - t) * (1f - t) * (1f - t);
        float s = (0.92f + 0.08f * ease) * fit();
        int lmx = (int) local(mouseX, width), lmy = (int) local(mouseY, height);
        int c1 = Config.c1(), c2 = Config.c2();

        // затемнённый градиентный фон (HUD остаётся виден как предпросмотр)
        int top = Hud.lerp(0x00060608, 0xA0060608, ease), bot = Hud.lerp(0x00090914, 0xD0090914, ease);
        for (int y = 0; y < height; y += 4) {
            AbstractGui.fill(ms, 0, y, width, Math.min(height, y + 4), Hud.lerp(top, bot, y / (float) height));
        }

        RenderSystem.pushMatrix();
        RenderSystem.translatef(width / 2f, height / 2f, 0f);
        RenderSystem.scalef(s, s, 1f);
        RenderSystem.translatef(-width / 2f, -height / 2f, 0f);
        try {
            drawWindow(ms, lmx, lmy, c1, c2);
        } finally {
            RenderSystem.popMatrix();
        }
    }

    private void drawWindow(MatrixStack ms, int mx, int my, int c1, int c2) {
        hits.clear();

        // свечение вокруг окна
        int glow = (Hud.lerp(c1, c2, 0.5f) & 0x00FFFFFF) | 0x0C000000;
        for (int k = 4; k >= 1; k--) Hud.rr(ms, px - k * 2, py - k * 2, W + k * 4, H + k * 4, 10 + k * 2, glow);
        Hud.rr(ms, px, py, W, H, 10, 0xF2101018);

        // боковая панель
        Hud.rr(ms, px, py, SIDE, H, 10, 0xFF0B0B11);
        AbstractGui.fill(ms, px + SIDE - 10, py, px + SIDE, py + H, 0xFF0B0B11);
        Hud.gradText(ms, font, "PrimeVisual", px + 14, py + 14);
        font.draw(ms, "Beta • 1.16.5", px + 14, py + 26, 0xFF5D6076);

        int ty0 = py + 52;
        tabInd += (tab * 28f - tabInd) * 0.25f; // плавный индикатор вкладки
        Hud.rr(ms, px + 8, ty0 + Math.round(tabInd), SIDE - 16, 24, 6,
                (Hud.lerp(c1, c2, 0.5f) & 0x00FFFFFF) | 0x40000000);
        Hud.rr(ms, px + 8, ty0 + Math.round(tabInd) + 5, 2, 14, 1, Hud.lerp(c1, c2, 0.5f));
        for (int k = 0; k < TABS.length; k++) {
            boolean sel = k == tab;
            font.drawShadow(ms, TABS[k], px + 20, ty0 + k * 28 + 8, sel ? Hud.WHITE : 0xFF8C90A8);
            hits.add(new Hit(px + 8, ty0 + k * 28, SIDE - 16, 24, 200 + k));
        }
        font.draw(ms, "Right Shift - меню", px + 10, py + H - 16, 0xFF4D5066);

        // заголовок и линия
        int cx = px + SIDE + 12;
        ms.pushPose();
        ms.scale(1.3f, 1.3f, 1f);
        font.drawShadow(ms, TABS[tab], cx / 1.3f, (py + 13) / 1.3f, Hud.WHITE);
        ms.popPose();
        for (int x = 0; x < CW; x += 2) {
            AbstractGui.fill(ms, cx + x, py + 34, cx + x + 2, py + 35, Hud.lerp(c1, c2, x / (float) CW));
        }

        int y = py + 44;
        if (tab == 0 || tab == 1) {
            for (int idx : TAB_MODULES[tab]) y = moduleCard(ms, idx, cx, y, mx, my);
        } else if (tab == 2) {
            y = moduleCard(ms, Config.CROSSHAIR, cx, y, mx, my);
            font.draw(ms, "Стиль", cx, y + 2, 0xFF8C90A8);
            y += 13;
            int cwid = (CW - 9) / 4;
            for (int k = 0; k < STYLES.length; k++) {
                int x = cx + k * (cwid + 3);
                boolean sel = k == Config.crosshairStyle;
                Hud.rr(ms, x, y, cwid, 20, 5, sel ? Hud.lerp(c1, c2, 0.5f) : 0xFF171822);
                font.drawShadow(ms, STYLES[k], x + (cwid - font.width(STYLES[k])) / 2f, y + 6, Hud.WHITE);
                hits.add(new Hit(x, y, cwid, 20, 400 + k));
            }
            y += 24;
            Hud.rr(ms, cx, y, CW, 60, 6, 0xFF12131C);
            font.draw(ms, "Предпросмотр", cx + 8, y + 6, 0xFF5D6076);
            Hud.crosshairShape(ms, cx + CW / 2, y + 32, 3, Config.crosshairStyle, Hud.WHITE);
            y += 68;
            String zv = String.format(Locale.ROOT, "×%.1f", 1.0 / Config.zoom);
            y = sliderBlock(ms, cx, y, "Сила зума (клавиша C)", zv, 102, (float) ((0.6 - Config.zoom) / 0.55));
        } else {
            font.draw(ms, "Цвет акцента", cx, y + 2, 0xFF8C90A8);
            y += 13;
            int sw = (CW - 5 * 6) / 6;
            for (int k = 0; k < Config.PRESETS.length; k++) {
                int x = cx + k * (sw + 6);
                if (k == Config.preset) Hud.rr(ms, x - 2, y - 2, sw + 4, 24, 7, 0xFFFFFFFF);
                Hud.rr(ms, x, y, sw, 20, 5, Config.PRESETS[k][0]);
                Hud.rr(ms, x + sw / 2, y + 3, sw / 2 - 3, 14, 4, Config.PRESETS[k][1]);
                hits.add(new Hit(x, y, sw, 20, 300 + k));
            }
            y += 30;
            y = sliderBlock(ms, cx, y, "Размер HUD", Math.round(Config.scale * 100) + "%", 100,
                    (Config.scale - 0.7f) / 0.8f);
            y = sliderBlock(ms, cx, y, "Прозрачность панелей", Math.round(Config.opacity * 100) + "%", 101,
                    (Config.opacity - 0.3f) / 0.7f);
            y += 4;
            boolean hov = in(mx, my, cx, y, CW, 22);
            Hud.rr(ms, cx, y, CW, 22, 6, hov ? 0xFF2A2B3D : 0xFF171822);
            String rs = "Сбросить настройки";
            font.drawShadow(ms, rs, cx + (CW - font.width(rs)) / 2f, y + 7, Hud.WHITE);
            hits.add(new Hit(cx, y, CW, 22, 500));
        }
    }

    private int moduleCard(MatrixStack ms, int i, int x, int y, int mx, int my) {
        boolean hov = in(mx, my, x, y, CW, 24);
        hover[i] += ((hov ? 1f : 0f) - hover[i]) * 0.25f;
        toggle[i] += ((Config.ON[i] ? 1f : 0f) - toggle[i]) * 0.3f;
        Hud.rr(ms, x, y, CW, 24, 5, Hud.lerp(0xFF171822, 0xFF21222F, hover[i]));
        font.drawShadow(ms, Config.NAMES[i], x + 10, y + 4, Hud.lerp(0xFFB4B7CC, 0xFFFFFFFF, toggle[i]));
        font.draw(ms, Config.DESCS[i], x + 10, y + 14, 0xFF6C6F86);
        int tx = x + CW - 36, ty = y + 7;
        Hud.rr(ms, tx, ty, 26, 10, 5, Hud.lerp(0xFF3A3B4D, Config.c1(), toggle[i]));
        Hud.rr(ms, tx + 1 + Math.round(toggle[i] * 16), ty + 1, 8, 8, 4, 0xFFFFFFFF);
        hits.add(new Hit(x, y, CW, 24, i));
        return y + 27;
    }

    private int sliderBlock(MatrixStack ms, int x, int y, String label, String value, int id, float frac) {
        frac = MathHelper.clamp(frac, 0f, 1f);
        font.draw(ms, label, x, y, 0xFF8C90A8);
        font.drawShadow(ms, value, x + CW - font.width(value), y, Hud.WHITE);
        int sy = y + 14;
        Hud.rr(ms, x, sy + 3, CW, 4, 2, 0xFF2A2B3B);
        int fw = (int) (CW * frac);
        if (fw > 0) Hud.rr(ms, x, sy + 3, Math.max(4, fw), 4, 2, Hud.lerp(Config.c1(), Config.c2(), frac));
        Hud.rr(ms, x + fw - 4, sy, 8, 10, 4, 0xFFFFFFFF);
        hits.add(new Hit(x - 4, sy - 3, CW + 8, 16, id));
        return y + 34;
    }

    // ============================ Ввод ============================
    private void setSlider(int id, double lmx) {
        float frac = MathHelper.clamp((float) ((lmx - (px + SIDE + 12)) / CW), 0f, 1f);
        if (id == 100) Config.scale = Math.round((0.7f + frac * 0.8f) * 20f) / 20f;
        else if (id == 101) Config.opacity = Math.round((0.3f + frac * 0.7f) * 20f) / 20f;
        else if (id == 102) Config.zoom = 0.6 - frac * 0.55;
    }

    @Override
    public boolean mouseClicked(double dx, double dy, int button) {
        if (button != 0) return super.mouseClicked(dx, dy, button);
        double lx = local(dx, width), ly = local(dy, height);
        for (Hit h : hits) {
            if (!in(lx, ly, h.x, h.y, h.w, h.h)) continue;
            int id = h.id;
            if (id < Config.ON.length) {
                Config.ON[id] = !Config.ON[id];
                click(Config.ON[id] ? 1.3f : 0.8f);
            } else if (id >= 200 && id < 204) {
                tab = id - 200;
                click(1.0f);
            } else if (id >= 300 && id < 306) {
                Config.preset = id - 300;
                click(1.1f);
            } else if (id >= 400 && id < 404) {
                Config.crosshairStyle = id - 400;
                click(1.1f);
            } else if (id == 500) {
                Config.reset();
                click(0.7f);
            } else if (id >= 100 && id <= 102) {
                drag = id;
                setSlider(id, lx);
            }
            return true;
        }
        return super.mouseClicked(dx, dy, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (drag != -1) {
            setSlider(drag, local(mx, width));
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        drag = -1;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (ClientEvents.MENU.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void removed() {
        Config.save();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
