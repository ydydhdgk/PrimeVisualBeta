package com.example.visuals;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.util.SharedConstants;
import net.minecraft.client.audio.SimpleSound;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputMappings;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.StringTextComponent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MenuScreen extends Screen {
    private static final int W = 400, H = 256, SIDE = 100, CW = 276;
    private static final int TAB_STEP = 16, TAB_H = 15;
    private static final String[] TABS = {"Инфо", "Мир", "Интерфейс", "Прицел", "Эффекты", "Хитбоксы",
            "Анимации", "Утилиты", "Бинды", "Цвета", "Панели", "Настройки"};
    private static final int[][] TAB_MODULES = {
            {Config.FPS, Config.FPSGRAPH, Config.COORDS, Config.DIR, Config.SPEED, Config.PING, Config.TIME},
            {Config.LIGHT, Config.BIOME, Config.GAMETIME, Config.COMPASS, Config.WARN},
            {Config.EFFECTS, Config.DURABILITY, Config.KEYS, Config.TARGET, Config.WATERMARK, Config.ITEMS, Config.HITMARKER},
            {}, {}, {}, {}, {}, {}, {}, {}, {}
    };
    private static final String[] STYLES = {"Крест", "Точка", "Круг", "Т-образный"};
    private static final String[] PARTICLES = {"Звёзды", "Огонь", "Сердца", "Искры", "Ноты", "Крит"};

    // идентификаторы полей ввода: 0..BIND_COUNT-1 - команды биндов, 10/11 - GPS X/Z, 12 - авто-команда
    private static final int F_GPSX = 10, F_GPSZ = 11, F_AUTO = 12;

    private static class Hit {
        final int x, y, w, h, id;
        Hit(int x, int y, int w, int h, int id) { this.x = x; this.y = y; this.w = w; this.h = h; this.id = id; }
    }

    private final List<Hit> hits = new ArrayList<>();
    private final float[] toggle = new float[Config.ON.length];
    private final float[] hover = new float[Config.ON.length];
    private float tabInd = 0f;
    private int tab = 0;
    private long tabAt = 0L;
    private int drag = -1;
    private int listening = -1;
    private int editing = -1;
    private String search = "";
    private boolean searchFocus = false;
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

    private void switchTab(int t) {
        if (t != tab) {
            tab = t;
            tabAt = System.currentTimeMillis();
            listening = -1;
            editing = -1;
        }
    }

    // ---- поля ввода ----
    private String getField(int f) {
        if (f >= 0 && f < Config.BIND_COUNT) return Config.bindCmd[f];
        if (f == F_GPSX) return Config.gpsX;
        if (f == F_GPSZ) return Config.gpsZ;
        return Config.autoCmd;
    }

    private void setField(int f, String v) {
        if (f >= 0 && f < Config.BIND_COUNT) Config.bindCmd[f] = v;
        else if (f == F_GPSX) Config.gpsX = v;
        else if (f == F_GPSZ) Config.gpsZ = v;
        else Config.autoCmd = v;
    }

    private boolean allowedChar(int f, char c) {
        if (f == F_GPSX || f == F_GPSZ) return (c >= '0' && c <= '9') || c == '-';
        return SharedConstants.isAllowedChatCharacter(c);
    }

    private int maxLen(int f) {
        return (f == F_GPSX || f == F_GPSZ) ? 8 : 100;
    }

    // ---- «карточки»: обычные или стеклянные ----
    private void card(MatrixStack ms, int x, int y, int w, int h, int r, int base, int hoverColor, float hov) {
        if (Config.glass) {
            Hud.glassRectA(ms, x, y, w, h, r, false, Math.round(0x30 + 0x28 * hov));
        } else {
            Hud.rr(ms, x, y, w, h, r, Hud.lerp(base, hoverColor, hov));
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

        int top = Hud.lerp(0x00060608, 0xA0060608, ease), bot = Hud.lerp(0x00090914, 0xD0090914, ease);
        for (int y = 0; y < height; y += 4) {
            AbstractGui.fill(ms, 0, y, width, Math.min(height, y + 4), Hud.lerp(top, bot, y / (float) height));
        }

        RenderSystem.pushMatrix();
        RenderSystem.translatef(width / 2f, height / 2f, 0f);
        RenderSystem.scalef(s, s, 1f);
        RenderSystem.translatef(-width / 2f, -height / 2f, 0f);
        try {
            drawWindow(ms, lmx, lmy);
        } finally {
            RenderSystem.popMatrix();
        }
    }

    private void label(MatrixStack ms, String s, int x, int y) {
        font.draw(ms, s, x, y, 0xFF8C90A8);
    }

    private void drawWindow(MatrixStack ms, int mx, int my) {
        hits.clear();
        int c1 = Config.c1(), c2 = Config.c2();

        int glow = (Hud.lerp(c1, c2, 0.5f) & 0x00FFFFFF) | 0x0C000000;
        for (int k = 4; k >= 1; k--) Hud.rr(ms, px - k * 2, py - k * 2, W + k * 4, H + k * 4, 10 + k * 2, glow);
        if (Config.glass) {
            Hud.glassRectA(ms, px, py, W, H, 10, false, 200);
        } else {
            Hud.rr(ms, px, py, W, H, 10, 0xF2101018);
        }

        // ---- боковая панель ----
        if (Config.glass) {
            Hud.rr(ms, px + 1, py + 1, SIDE - 1, H - 2, 9, 0x59000000);
            AbstractGui.fill(ms, px + SIDE - 10, py + 1, px + SIDE, py + H - 1, 0x59000000);
        } else {
            Hud.rr(ms, px, py, SIDE, H, 10, 0xFF0B0B11);
            AbstractGui.fill(ms, px + SIDE - 10, py, px + SIDE, py + H, 0xFF0B0B11);
        }
        ms.pushPose();
        ms.scale(1.2f, 1.2f, 1f);
        Hud.gradText(ms, font, "PrimeVisual", Math.round((px + 12) / 1.2f), Math.round((py + 11) / 1.2f));
        ms.popPose();
        font.draw(ms, "Right Shift - меню", px + 14, py + 26, 0xFF5D6076);

        int ty0 = py + 42;
        tabInd += (tab * (float) TAB_STEP - tabInd) * 0.25f;
        Hud.rr(ms, px + 8, ty0 + Math.round(tabInd), SIDE - 16, TAB_H, 6,
                (Hud.lerp(c1, c2, 0.5f) & 0x00FFFFFF) | 0x40000000);
        Hud.rr(ms, px + 8, ty0 + Math.round(tabInd) + 3, 2, TAB_H - 6, 1, Hud.lerp(c1, c2, 0.5f));
        for (int k = 0; k < TABS.length; k++) {
            boolean sel = k == tab;
            font.drawShadow(ms, TABS[k], px + 20, ty0 + k * TAB_STEP + 4, sel ? Hud.WHITE : 0xFF8C90A8);
            hits.add(new Hit(px + 8, ty0 + k * TAB_STEP, SIDE - 16, TAB_H, 200 + k));
        }
        font.draw(ms, "by whiteshapka", px + 14, py + H - 14, 0xFF4D5066);

        // ---- закрыть ----
        boolean hovClose = in(mx, my, px + W - 26, py + 8, 16, 16);
        Hud.rr(ms, px + W - 26, py + 8, 16, 16, 8, hovClose ? 0x66FF5566 : 0x30FFFFFF);
        font.drawShadow(ms, "×", px + W - 26 + 8 - font.width("×") / 2f, py + 12, Hud.WHITE);
        hits.add(new Hit(px + W - 26, py + 8, 16, 16, 502));

        // ---- заголовок и поиск ----
        int cx = px + SIDE + 12;
        ms.pushPose();
        ms.scale(1.3f, 1.3f, 1f);
        font.drawShadow(ms, TABS[tab], cx / 1.3f, (py + 13) / 1.3f, Hud.WHITE);
        ms.popPose();

        int sx = cx + 100, sw = 156;
        card(ms, sx, py + 10, sw, 16, 6, 0xFF171822, 0xFF21222F, in(mx, my, sx, py + 10, sw, 16) ? 1f : 0f);
        if (searchFocus) Hud.rrOutline(ms, sx, py + 10, sw, 16, 6, Config.c2());
        boolean blink = (System.currentTimeMillis() / 450L) % 2L == 0L;
        if (search.isEmpty() && !searchFocus) {
            font.draw(ms, "Поиск функций...", sx + 8, py + 14, 0xFF5D6076);
        } else {
            String shown = fitTail(search + (searchFocus && blink ? "|" : ""), sw - 16);
            font.drawShadow(ms, shown, sx + 8, py + 14, Hud.WHITE);
        }
        hits.add(new Hit(sx, py + 10, sw, 16, 700));

        for (int x = 0; x < CW; x += 2) {
            AbstractGui.fill(ms, cx + x, py + 34, cx + x + 2, py + 35, Hud.grad(x / (float) CW));
        }

        // ---- содержимое ----
        float sp = Math.min(1f, (System.currentTimeMillis() - tabAt) / 180f);
        float slide = (1f - (1f - (1f - sp) * (1f - sp))) * 14f;
        RenderSystem.pushMatrix();
        RenderSystem.translatef(slide, 0f, 0f);
        try {
            int y = py + 44;
            String q = search.trim().toLowerCase(Locale.ROOT);
            if (!q.isEmpty()) {
                drawSearchResults(ms, cx, y, mx, my, q);
            } else {
                switch (tab) {
                    case 0:
                    case 1:
                    case 2:
                        for (int idx : TAB_MODULES[tab]) y = moduleCard(ms, idx, cx, y, mx, my);
                        break;
                    case 3: drawCrosshairTab(ms, cx, y, mx, my); break;
                    case 4: drawEffectsTab(ms, cx, y, mx, my); break;
                    case 5: drawHitboxTab(ms, cx, y, mx, my); break;
                    case 6: drawAnimTab(ms, cx, y, mx, my); break;
                    case 7: drawUtilsTab(ms, cx, y, mx, my); break;
                    case 8: drawBindsTab(ms, cx, y, mx, my); break;
                    case 9: drawColorsTab(ms, cx, y, mx, my); break;
                    case 10: drawPanelsTab(ms, cx, y, mx, my); break;
                    default: drawSettingsTab(ms, cx, y, mx, my); break;
                }
            }
        } finally {
            RenderSystem.popMatrix();
        }
    }

    // ---- поиск ----
    private void drawSearchResults(MatrixStack ms, int cx, int y, int mx, int my, String q) {
        int found = 0;
        for (int i = 0; i < Config.ON.length && found < 7; i++) {
            String n = Config.NAMES[i].toLowerCase(Locale.ROOT);
            String d = Config.DESCS[i].toLowerCase(Locale.ROOT);
            if (n.contains(q) || d.contains(q)) {
                y = moduleCard(ms, i, cx, y, mx, my);
                found++;
            }
        }
        if (found == 0) label(ms, "Ничего не найдено", cx, y + 4);
    }

    // ---- вкладка «Прицел» ----
    private void drawCrosshairTab(MatrixStack ms, int cx, int y, int mx, int my) {
        y = moduleCard(ms, Config.CROSSHAIR, cx, y, mx, my);
        label(ms, "Стиль", cx, y + 1);
        y += 12;
        int cwid = (CW - 9) / 4;
        for (int k = 0; k < STYLES.length; k++) {
            int x = cx + k * (cwid + 3);
            boolean sel = k == Config.crosshairStyle;
            if (sel) Hud.rr(ms, x, y, cwid, 20, 5, Hud.lerp(Config.c1(), Config.c2(), 0.5f));
            else card(ms, x, y, cwid, 20, 5, 0xFF171822, 0xFF21222F, in(mx, my, x, y, cwid, 20) ? 1f : 0f);
            font.drawShadow(ms, STYLES[k], x + (cwid - font.width(STYLES[k])) / 2f, y + 6, Hud.WHITE);
            hits.add(new Hit(x, y, cwid, 20, 400 + k));
        }
        y += 24;
        label(ms, "Цвет", cx, y + 1);
        y += 12;
        int chw = (CW - 4 * 4) / 5;
        for (int k = 0; k < 5; k++) {
            int x = cx + k * (chw + 4);
            if (k == Config.crosshairColor) Hud.rr(ms, x - 2, y - 2, chw + 4, 22, 6, 0xFFFFFFFF);
            Hud.rr(ms, x, y, chw, 18, 5, Hud.crosshairColor(k));
            if (k == 1) Hud.rr(ms, x + chw / 2, y + 3, chw / 2 - 3, 12, 4, Config.c2());
            hits.add(new Hit(x, y, chw, 18, 420 + k));
        }
        y += 26;
        y = sliderBlock(ms, cx, y, "Размер", String.valueOf(Config.crosshairSize), 104,
                (Config.crosshairSize - 3) / 6f);
        card(ms, cx, y, CW, 40, 6, 0xFF12131C, 0xFF12131C, 0f);
        font.draw(ms, "Предпросмотр", cx + 8, y + 5, 0xFF5D6076);
        Hud.crosshairShape(ms, cx + CW / 2, y + 22, 3, Config.crosshairSize, Config.crosshairStyle,
                Hud.crosshairColor(Config.crosshairColor));
    }

    // ---- вкладка «Эффекты» (частицы) ----
    private void drawEffectsTab(MatrixStack ms, int cx, int y, int mx, int my) {
        y = moduleCard(ms, Config.TRAILS, cx, y, mx, my);
        y = moduleCard(ms, Config.JUMPCIRCLE, cx, y, mx, my);
        label(ms, "Тип частиц", cx, y + 1);
        y += 12;
        int cw = (CW - 5 * 4) / 6;
        for (int k = 0; k < PARTICLES.length; k++) {
            int x = cx + k * (cw + 4);
            boolean sel = k == Config.particleType;
            if (sel) Hud.rr(ms, x, y, cw, 20, 5, Hud.lerp(Config.c1(), Config.c2(), 0.5f));
            else card(ms, x, y, cw, 20, 5, 0xFF171822, 0xFF21222F, in(mx, my, x, y, cw, 20) ? 1f : 0f);
            font.drawShadow(ms, PARTICLES[k], x + (cw - font.width(PARTICLES[k])) / 2f, y + 6, Hud.WHITE);
            hits.add(new Hit(x, y, cw, 20, 430 + k));
        }
        y += 26;
        y = sliderBlock(ms, cx, y, "Размер круга прыжка", String.format(Locale.ROOT, "%.1f бл", Config.circleSize),
                106, (Config.circleSize - 0.5f) / 2f);
        label(ms, "Частицы видны только вам (клиентская сторона)", cx, y);
    }

    // ---- вкладка «Хитбоксы» ----
    private void drawHitboxTab(MatrixStack ms, int cx, int y, int mx, int my) {
        y = moduleCard(ms, Config.HITBOX, cx, y, mx, my);
        label(ms, "Показывать для", cx, y + 1);
        y += 12;
        String[] names = {"Игроки", "Мобы", "Предметы"};
        boolean[] on = {Config.hbPlayers, Config.hbMobs, Config.hbItems};
        int cw = (CW - 8) / 3;
        for (int k = 0; k < 3; k++) {
            int x = cx + k * (cw + 4);
            if (on[k]) Hud.rr(ms, x, y, cw, 20, 5, Hud.lerp(Config.c1(), Config.c2(), 0.5f));
            else card(ms, x, y, cw, 20, 5, 0xFF171822, 0xFF21222F, in(mx, my, x, y, cw, 20) ? 1f : 0f);
            font.drawShadow(ms, names[k], x + (cw - font.width(names[k])) / 2f, y + 6, Hud.WHITE);
            hits.add(new Hit(x, y, cw, 20, 440 + k));
        }
        y += 26;
        label(ms, "Цвет рамок", cx, y + 1);
        y += 12;
        int sw = (CW - 7 * 4) / 8;
        for (int k = 0; k < 8; k++) {
            int x = cx + k * (sw + 4);
            if (k == Config.hbColor) Hud.rr(ms, x - 2, y - 2, sw + 4, 22, 6, 0xFFFFFFFF);
            Hud.rr(ms, x, y, sw, 18, 5, Hud.hitboxColor(k));
            hits.add(new Hit(x, y, sw, 18, 450 + k));
        }
        y += 26;
        y = sliderBlock(ms, cx, y, "Дальность", Config.hbRange + " бл", 109, (Config.hbRange - 8) / 56f);
        label(ms, "Рамки скрыты за блоками, как в F3+B", cx, y);
    }

    // ---- вкладка «Анимации» ----
    private void drawAnimTab(MatrixStack ms, int cx, int y, int mx, int my) {
        y = moduleCard(ms, Config.ANIM_CHAT, cx, y, mx, my);
        y = moduleCard(ms, Config.ANIM_TAB, cx, y, mx, my);
        y = moduleCard(ms, Config.ANIM_GUI, cx, y, mx, my);
        y = moduleCard(ms, Config.ANIM_HOTBAR, cx, y, mx, my);
        y = sliderBlock(ms, cx, y, "Скорость анимаций", String.format(Locale.ROOT, "×%.1f", Config.animSpeed),
                108, (Config.animSpeed - 0.5f) / 1.5f);
        label(ms, "Чат, Tab, окна и хотбар двигаются плавно", cx, y);
    }

    // ---- вкладка «Утилиты» (GPS и авто-команда) ----
    private void drawUtilsTab(MatrixStack ms, int cx, int y, int mx, int my) {
        y = moduleCard(ms, Config.GPS, cx, y, mx, my);
        int fw = 90;
        field(ms, cx, y, fw, 24, F_GPSX, "X", 710, mx, my);
        field(ms, cx + fw + 4, y, fw, 24, F_GPSZ, "Z", 711, mx, my);
        int bx = cx + 2 * (fw + 4);
        button(ms, bx, y, CW - 2 * (fw + 4), 24, "Я здесь", 505, mx, my);
        y += 27;
        y = moduleCard(ms, Config.AUTOCMD, cx, y, mx, my);
        field(ms, cx, y, CW, 24, F_AUTO, "/команда для отправки по таймеру", 712, mx, my);
        y += 27;
        sliderBlock(ms, cx, y, "Интервал авто-команды", Config.autoInterval + " сек", 107,
                (Config.autoInterval - 10) / 290f);
    }

    // ---- вкладка «Бинды» ----
    private String keyName(int code) {
        if (code < 0) return "— нет —";
        try {
            return InputMappings.Type.KEYSYM.getOrCreate(code).getDisplayName().getString();
        } catch (Exception e) {
            return "Key " + code;
        }
    }

    private String fitTail(String s, int maxW) {
        while (s.length() > 0 && font.width(s) > maxW) s = s.substring(1);
        return s;
    }

    private String fitHead(String s, int maxW) {
        while (s.length() > 0 && font.width(s) > maxW) s = s.substring(0, s.length() - 1);
        return s;
    }

    private void field(MatrixStack ms, int x, int y, int w, int h, int f, String placeholder,
                       int hitId, int mx, int my) {
        boolean ed = editing == f;
        card(ms, x, y, w, h, 5, 0xFF171822, 0xFF21222F, in(mx, my, x, y, w, h) ? 1f : 0f);
        if (ed) Hud.rrOutline(ms, x, y, w, h, 5, Config.c1());
        String cur = getField(f);
        boolean blink = (System.currentTimeMillis() / 450L) % 2L == 0L;
        float ty = y + (h - 8) / 2f;
        if (cur.isEmpty() && !ed) {
            font.draw(ms, fitHead(placeholder, w - 12), x + 7, ty, 0xFF5D6076);
        } else {
            font.drawShadow(ms, fitTail(cur + (ed && blink ? "|" : ""), w - 14), x + 7, ty, Hud.WHITE);
        }
        hits.add(new Hit(x, y, w, h, hitId));
    }

    private void drawBindsTab(MatrixStack ms, int cx, int y, int mx, int my) {
        y = moduleCard(ms, Config.NOTIFY, cx, y, mx, my);
        int kw = 72, rowH = 24;
        for (int i = 0; i < Config.BIND_COUNT; i++) {
            boolean lk = listening == i;
            card(ms, cx, y, kw, rowH, 5, 0xFF171822, 0xFF21222F, in(mx, my, cx, y, kw, rowH) ? 1f : 0f);
            if (lk) Hud.rrOutline(ms, cx, y, kw, rowH, 5, Config.c2());
            String kn = fitHead(lk ? "Нажмите..." : keyName(Config.bindKey[i]), kw - 8);
            int kc = lk ? Config.c2() : (Config.bindKey[i] < 0 ? 0xFF6C6F86 : Hud.WHITE);
            font.drawShadow(ms, kn, cx + (kw - font.width(kn)) / 2f, y + 8, kc);
            hits.add(new Hit(cx, y, kw, rowH, 600 + i));

            int bx = cx + kw + 4, bw = CW - kw - 4 - 28;
            field(ms, bx, y, bw, rowH, i, "/команда", 620 + i, mx, my);

            int dx = cx + CW - 24;
            boolean hc = in(mx, my, dx, y, 24, rowH);
            card(ms, dx, y, 24, rowH, 5, 0xFF171822, 0xFF3A2030, hc ? 1f : 0f);
            font.drawShadow(ms, "×", dx + 12 - font.width("×") / 2f, y + 8, hc ? Hud.RED : 0xFF8C90A8);
            hits.add(new Hit(dx, y, 24, rowH, 640 + i));
            y += 27;
        }
        font.draw(ms, "Клик по клавише - выбор кнопки (Delete - убрать)", cx, y + 1, 0xFF5D6076);
        font.draw(ms, "Клик по полю - ввод команды (Ctrl+V, Enter)", cx, y + 11, 0xFF5D6076);
    }

    // ---- вкладка «Цвета» ----
    private void drawColorsTab(MatrixStack ms, int cx, int y, int mx, int my) {
        label(ms, "Цвет акцента", cx, y + 1);
        y += 12;
        int sw = (CW - 7 * 5) / 8;
        for (int k = 0; k < Config.PRESET_COUNT; k++) {
            int x = cx + k * (sw + 5);
            if (k == Config.preset) Hud.rr(ms, x - 2, y - 2, sw + 4, 24, 7, 0xFFFFFFFF);
            Hud.rr(ms, x, y, sw, 20, 5, Config.swatch1(k));
            Hud.rr(ms, x + sw / 2, y + 3, sw / 2 - 3, 14, 4, Config.swatch2(k));
            hits.add(new Hit(x, y, sw, 20, 300 + k));
        }
        y += 30;
        if (Config.preset == 6) {
            y = hueBlock(ms, cx, y);
        } else if (Config.preset == 7) {
            label(ms, "Радуга: цвета плавно меняются сами", cx, y);
            y += 16;
        } else {
            label(ms, "Выберите последний квадрат для своего оттенка", cx, y);
            y += 16;
        }
        y = moduleCard(ms, Config.ANIMATE, cx, y, mx, my);
        y += 2;
        card(ms, cx, y, CW, 44, 6, 0xFF12131C, 0xFF12131C, 0f);
        String name = "PrimeVisual";
        float sc = 1.6f;
        float tw = font.width(name) * sc;
        ms.pushPose();
        ms.scale(sc, sc, 1f);
        Hud.gradText(ms, font, name, Math.round((cx + (CW - tw) / 2f) / sc), Math.round((y + 15) / sc));
        ms.popPose();
    }

    private int hueBlock(MatrixStack ms, int x, int y) {
        label(ms, "Оттенок", x, y);
        String v = Math.round(Config.hue * 360f) + "°";
        font.drawShadow(ms, v, x + CW - font.width(v), y, Hud.WHITE);
        int sy = y + 14;
        for (int i = 0; i < CW; i += 2) {
            AbstractGui.fill(ms, x + i, sy + 3, Math.min(x + i + 2, x + CW), sy + 7,
                    Config.hsb(i / (float) CW, 0.75f, 1f));
        }
        Hud.rr(ms, x + (int) (CW * Config.hue) - 4, sy, 8, 10, 4, 0xFFFFFFFF);
        hits.add(new Hit(x - 4, sy - 3, CW + 8, 16, 105));
        return y + 34;
    }

    // ---- вкладка «Панели» ----
    private void drawPanelsTab(MatrixStack ms, int cx, int y, int mx, int my) {
        y = sliderBlock(ms, cx, y, "Размер HUD", Math.round(Config.scale * 100) + "%", 100,
                (Config.scale - 0.7f) / 0.8f);
        y = sliderBlock(ms, cx, y, "Плотность панелей", Math.round(Config.opacity * 100) + "%", 101,
                (Config.opacity - 0.3f) / 0.7f);
        y = sliderBlock(ms, cx, y, "Скругление углов", Config.radius + " px", 103, Config.radius / 8f);
        y += 2;
        button(ms, cx, y, CW, 22, "Стиль панелей: " + (Config.glass ? "Liquid Glass" : "Обычный"), 503, mx, my);
        y += 26;
        button(ms, cx, y, CW, 22, "Инфо-панель: " + (Config.infoRight ? "справа" : "слева"), 501, mx, my);
    }

    // ---- вкладка «Настройки» ----
    private void drawSettingsTab(MatrixStack ms, int cx, int y, int mx, int my) {
        String zv = String.format(Locale.ROOT, "×%.1f", 1.0 / Config.zoom);
        y = sliderBlock(ms, cx, y, "Сила зума (клавиша C)", zv, 102, (float) ((0.6 - Config.zoom) / 0.55));
        y += 2;
        button(ms, cx, y, CW, 22, "Редактор HUD (перетаскивание элементов)", 504, mx, my);
        y += 26;
        button(ms, cx, y, CW, 22, "Сбросить настройки", 500, mx, my);

        int cy = py + H - 12 - 52;
        card(ms, cx, cy, CW, 52, 6, 0xFF12131C, 0xFF12131C, 0f);
        String title = "Сделано whiteshapka";
        float sc = 1.4f;
        float tw = font.width(title) * sc;
        ms.pushPose();
        ms.scale(sc, sc, 1f);
        Hud.gradText(ms, font, title, Math.round((cx + (CW - tw) / 2f) / sc), Math.round((cy + 11) / sc));
        ms.popPose();
        String sub = "PrimeVisual Beta • Forge 1.16.5";
        font.draw(ms, sub, cx + (CW - font.width(sub)) / 2f, cy + 34, 0xFF5D6076);
    }

    private void button(MatrixStack ms, int x, int y, int w, int h, String text, int id, int mx, int my) {
        boolean hov = in(mx, my, x, y, w, h);
        card(ms, x, y, w, h, 6, 0xFF171822, 0xFF2A2B3D, hov ? 1f : 0f);
        font.drawShadow(ms, text, x + (w - font.width(text)) / 2f, y + (h - 8) / 2f, Hud.WHITE);
        hits.add(new Hit(x, y, w, h, id));
    }

    private int moduleCard(MatrixStack ms, int i, int x, int y, int mx, int my) {
        boolean hov = in(mx, my, x, y, CW, 24);
        hover[i] += ((hov ? 1f : 0f) - hover[i]) * 0.25f;
        toggle[i] += ((Config.ON[i] ? 1f : 0f) - toggle[i]) * 0.3f;
        card(ms, x, y, CW, 24, 5, 0xFF171822, 0xFF21222F, hover[i]);
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
        label(ms, label, x, y);
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
        else if (id == 103) Config.radius = Math.round(frac * 8f);
        else if (id == 104) Config.crosshairSize = 3 + Math.round(frac * 6f);
        else if (id == 105) Config.hue = frac;
        else if (id == 106) Config.circleSize = Math.round((0.5f + frac * 2f) * 10f) / 10f;
        else if (id == 107) Config.autoInterval = 10 + Math.round(frac * 58f) * 5;
        else if (id == 108) Config.animSpeed = Math.round((0.5f + frac * 1.5f) * 10f) / 10f;
        else if (id == 109) Config.hbRange = 8 + Math.round(frac * 56f);
    }

    @Override
    public boolean mouseClicked(double dx, double dy, int button) {
        if (button != 0) return super.mouseClicked(dx, dy, button);
        double lx = local(dx, width), ly = local(dy, height);
        listening = -1;
        editing = -1;
        searchFocus = false;
        for (Hit h : hits) {
            if (!in(lx, ly, h.x, h.y, h.w, h.h)) continue;
            int id = h.id;
            if (id < Config.ON.length) {
                Config.ON[id] = !Config.ON[id];
                click(Config.ON[id] ? 1.3f : 0.8f);
            } else if (id >= 100 && id <= 109) {
                drag = id;
                setSlider(id, lx);
            } else if (id >= 200 && id < 200 + TABS.length) {
                switchTab(id - 200);
                click(1.0f);
            } else if (id >= 300 && id < 300 + Config.PRESET_COUNT) {
                Config.preset = id - 300;
                click(1.1f);
            } else if (id >= 400 && id < 404) {
                Config.crosshairStyle = id - 400;
                click(1.1f);
            } else if (id >= 420 && id < 425) {
                Config.crosshairColor = id - 420;
                click(1.1f);
            } else if (id >= 430 && id < 436) {
                Config.particleType = id - 430;
                click(1.1f);
            } else if (id >= 440 && id < 443) {
                if (id == 440) Config.hbPlayers = !Config.hbPlayers;
                else if (id == 441) Config.hbMobs = !Config.hbMobs;
                else Config.hbItems = !Config.hbItems;
                click(1.1f);
            } else if (id >= 450 && id < 458) {
                Config.hbColor = id - 450;
                click(1.1f);
            } else if (id == 500) {
                Config.reset();
                click(0.7f);
            } else if (id == 501) {
                Config.infoRight = !Config.infoRight;
                click(1.0f);
            } else if (id == 502) {
                click(0.9f);
                onClose();
            } else if (id == 503) {
                Config.glass = !Config.glass;
                click(1.2f);
            } else if (id == 504) {
                click(1.0f);
                if (minecraft != null) minecraft.setScreen(new HudEditorScreen(this));
            } else if (id == 505) {
                if (minecraft != null && minecraft.player != null) {
                    Config.gpsX = String.valueOf((int) Math.floor(minecraft.player.getX()));
                    Config.gpsZ = String.valueOf((int) Math.floor(minecraft.player.getZ()));
                    click(1.2f);
                }
            } else if (id >= 600 && id < 600 + Config.BIND_COUNT) {
                listening = id - 600;
                click(1.0f);
            } else if (id >= 620 && id < 620 + Config.BIND_COUNT) {
                editing = id - 620;
                click(1.0f);
            } else if (id >= 640 && id < 640 + Config.BIND_COUNT) {
                Config.bindKey[id - 640] = -1;
                Config.bindCmd[id - 640] = "";
                click(0.7f);
            } else if (id == 700) {
                searchFocus = true;
            } else if (id == 710) {
                editing = F_GPSX;
            } else if (id == 711) {
                editing = F_GPSZ;
            } else if (id == 712) {
                editing = F_AUTO;
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
        if (listening != -1) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                listening = -1;
            } else if (key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) {
                Config.bindKey[listening] = -1;
                listening = -1;
            } else if (!ClientEvents.MENU.matches(key, scan) && !ClientEvents.ZOOM.matches(key, scan)) {
                Config.bindKey[listening] = key;
                listening = -1;
                click(1.2f);
            }
            return true;
        }
        if (editing != -1) {
            String cur = getField(editing);
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                editing = -1;
            } else if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!cur.isEmpty()) setField(editing, cur.substring(0, cur.length() - 1));
            } else if (Screen.isPaste(key) && minecraft != null) {
                String clip = minecraft.keyboardHandler.getClipboard();
                StringBuilder sb = new StringBuilder(cur);
                for (char c : clip.toCharArray()) {
                    if (allowedChar(editing, c) && sb.length() < maxLen(editing)) sb.append(c);
                }
                setField(editing, sb.toString());
            }
            return true;
        }
        if (searchFocus) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                if (!search.isEmpty()) search = "";
                else searchFocus = false;
            } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                searchFocus = false;
            } else if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!search.isEmpty()) search = search.substring(0, search.length() - 1);
            }
            return true;
        }
        if (ClientEvents.MENU.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (editing != -1) {
            String cur = getField(editing);
            if (allowedChar(editing, c) && cur.length() < maxLen(editing)) setField(editing, cur + c);
            return true;
        }
        if (searchFocus) {
            if (SharedConstants.isAllowedChatCharacter(c) && search.length() < 24) search += c;
            return true;
        }
        return super.charTyped(c, mods);
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
