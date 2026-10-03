package com.example.visuals;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.audio.SimpleSound;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputMappings;
import net.minecraft.util.SharedConstants;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.StringTextComponent;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MenuScreen extends Screen {
    private static final int W = 400, H = 256, SIDE = 100, CW = 276;
    private static final int TAB_STEP = 24, TAB_H = 20;
    private static final String[] TABS = {"HUD", "Прицел", "Визуал", "Утилиты", "Стиль", "Настройки"};
    private static final String[] STYLES = {"Крест", "Точка", "Круг", "Т-образный"};
    private static final String[] PARTICLES = {"Звёзды", "Огонь", "Сердца", "Искры", "Ноты", "Крит", "Радуга"};
    private static final String[] HB_CATS = {"Игроки", "Мобы", "Предметы", "Другое"};
    private static final float[] SAVED_SCROLL = new float[TABS.length];
    private static String cfgName = "";

    // идентификаторы полей ввода: 0..BIND_COUNT-1 - команды биндов
    private static final int F_GPSX = 10, F_GPSZ = 11, F_AUTO = 12, F_CFG = 13, F_WM = 14;
    // идентификаторы выбора клавиши (кроме биндов)
    private static final int L_CALC = 90, L_HUD = 91;

    private static class Hit {
        final int x, y, w, h, id;
        Hit(int x, int y, int w, int h, int id) { this.x = x; this.y = y; this.w = w; this.h = h; this.id = id; }
    }

    private final List<Hit> hits = new ArrayList<>();
    private final Map<Integer, float[]> st = new HashMap<>();
    private float tabInd = 0f;
    private int tab;
    private long tabAt = 0L;
    private int drag = -1;
    private int listening = -1;
    private int editing = -1;
    private String search = "";
    private boolean searchFocus = false;
    private long openAt = System.currentTimeMillis();
    private long closingAt = 0L;
    private int px, py;
    private float curScale = 1f;
    // прокрутка
    private float scroll, scrollTarget;
    private int maxScroll = 0, viewTop, viewBottom, chromeHits = 0;
    // конфиги
    private List<String> profiles;
    private String status = "";
    private long statusAt = 0L;

    public MenuScreen() {
        super(new StringTextComponent("Useful Visuals"));
        tab = MathHelper.clamp(Config.lastTab, 0, TABS.length - 1);
        tabInd = tab * (float) TAB_STEP;
        scroll = scrollTarget = SAVED_SCROLL[tab];
        profiles = Config.listProfiles();
    }

    @Override
    protected void init() {
        px = (width - W) / 2;
        py = (height - H) / 2;
        openAt = System.currentTimeMillis();
        closingAt = 0L;
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
        if (Config.sounds && minecraft != null) {
            minecraft.getSoundManager().play(SimpleSound.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
        }
    }

    private int logoClicks = 0;
    private long logoAt = 0L;

    /** 7 быстрых кликов по логотипу открывают секретные эффекты. */
    private void logoClick() {
        long now = System.currentTimeMillis();
        if (now - logoAt > 1500L) logoClicks = 0;
        logoAt = now;
        logoClicks++;
        click(0.8f + logoClicks * 0.1f);
        if (logoClicks >= 7) {
            logoClicks = 0;
            if (!Config.secret) {
                Config.secret = true;
                say("Секрет открыт: монеты, алмазы и золото");
            } else {
                say("Секрет уже открыт");
            }
        }
    }

    private void say(String s) {
        status = s;
        statusAt = System.currentTimeMillis();
    }

    private void switchTab(int t) {
        if (t == tab) return;
        SAVED_SCROLL[tab] = scrollTarget;
        tab = t;
        Config.lastTab = t;
        scroll = scrollTarget = SAVED_SCROLL[t];
        tabAt = System.currentTimeMillis();
        listening = -1;
        editing = -1;
    }

    // ---- «карточки»: обычные или стеклянные ----
    private void card(MatrixStack ms, int x, int y, int w, int h, int r, int base, int hoverColor, float hov) {
        if (Config.glass) {
            Hud.glassRectA(ms, x, y, w, h, r, false, Math.round(0x30 + 0x28 * hov));
        } else {
            Hud.rr(ms, x, y, w, h, r, Hud.lerp(base, hoverColor, hov));
        }
    }

    // ---- поля ввода ----
    private String getField(int f) {
        if (f >= 0 && f < Config.BIND_COUNT) return Config.bindCmd[f];
        switch (f) {
            case F_GPSX: return Config.gpsX;
            case F_GPSZ: return Config.gpsZ;
            case F_AUTO: return Config.autoCmd;
            case F_CFG: return cfgName;
            default: return Config.wmText;
        }
    }

    private void setField(int f, String v) {
        if (f >= 0 && f < Config.BIND_COUNT) Config.bindCmd[f] = v;
        else if (f == F_GPSX) Config.gpsX = v;
        else if (f == F_GPSZ) Config.gpsZ = v;
        else if (f == F_AUTO) Config.autoCmd = v;
        else if (f == F_CFG) cfgName = v;
        else Config.wmText = v;
    }

    private boolean allowedChar(int f, char c) {
        if (f == F_GPSX || f == F_GPSZ) return (c >= '0' && c <= '9') || c == '-';
        if (f == F_CFG) return Character.isLetterOrDigit(c) || c == ' ' || c == '_' || c == '-';
        return SharedConstants.isAllowedChatCharacter(c);
    }

    private int maxLen(int f) {
        if (f == F_GPSX || f == F_GPSZ) return 8;
        if (f == F_CFG) return 24;
        if (f == F_WM) return 20;
        return 100;
    }

    // ---- логические опции (переключатели) ----
    private boolean optValue(int id) {
        switch (id) {
            case 800: return Config.keysMouse;
            case 801: return Config.keysSpace;
            case 802: return Config.wmFps;
            case 803: return Config.wmTime;
            case 804: return Config.crosshairOutline;
            case 805: return Config.hbEye;
            case 806: return Config.textShadow;
            case 807: return Config.sounds;
            default: return false;
        }
    }

    private void optToggle(int id) {
        switch (id) {
            case 800: Config.keysMouse = !Config.keysMouse; break;
            case 801: Config.keysSpace = !Config.keysSpace; break;
            case 802: Config.wmFps = !Config.wmFps; break;
            case 803: Config.wmTime = !Config.wmTime; break;
            case 804: Config.crosshairOutline = !Config.crosshairOutline; break;
            case 805: Config.hbEye = !Config.hbEye; break;
            case 806: Config.textShadow = !Config.textShadow; break;
            case 807: Config.sounds = !Config.sounds; break;
            default: break;
        }
    }

    // ============================ Отрисовка ============================
    @Override
    public void render(MatrixStack ms, int mouseX, int mouseY, float pt) {
        long now = System.currentTimeMillis();
        float t = closingAt == 0L ? Math.min(1f, (now - openAt) / 200f)
                : 1f - Math.min(1f, (now - closingAt) / 150f);
        float ease = 1f - (1f - t) * (1f - t) * (1f - t);
        float s = (0.92f + 0.08f * ease) * fit();
        curScale = s;
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
        Fx.render(ms, minecraft, width, height); // эффекты видны и поверх меню (кнопка «Проверить»)
        if (closingAt != 0L && now - closingAt >= 150L && minecraft != null) {
            minecraft.setScreen(null);
        }
    }

    private void label(MatrixStack ms, String s, int x, int y) {
        font.draw(ms, s, x, y, 0xFF8C90A8);
    }

    private int section(MatrixStack ms, String title, int x, int y) {
        font.drawShadow(ms, title, (float) x, (float) (y + 2), Config.c2());
        int tx = x + font.width(title) + 6;
        for (int i = tx; i < x + CW; i += 2) {
            AbstractGui.fill(ms, i, y + 6, Math.min(i + 2, x + CW), y + 7,
                    Hud.lerp(0x50FFFFFF, 0x00FFFFFF, (i - tx) / (float) Math.max(1, x + CW - tx)));
        }
        return y + 16;
    }

    private void beginClip() {
        if (minecraft == null) return;
        double gs = minecraft.getWindow().getGuiScale();
        float cxs = width / 2f, cys = height / 2f;
        float x1 = cxs + ((px + SIDE) - cxs) * curScale, x2 = cxs + ((px + W) - cxs) * curScale;
        float y1 = cys + (viewTop - cys) * curScale, y2 = cys + (viewBottom - cys) * curScale;
        int fbH = minecraft.getWindow().getHeight();
        int sx = Math.max(0, (int) Math.floor(x1 * gs));
        int sw = Math.max(0, (int) Math.ceil((x2 - x1) * gs));
        int sy = Math.max(0, (int) Math.floor(fbH - y2 * gs));
        int sh = Math.max(0, (int) Math.ceil((y2 - y1) * gs));
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(sx, sy, sw, sh);
    }

    private void endClip() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private void drawWindow(MatrixStack ms, int mx, int my) {
        hits.clear();
        int c1 = Config.c1(), c2 = Config.c2();

        // ---- окно ----
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
        minecraft.getTextureManager().bind(Hud.LOGO);
        RenderSystem.color4f(1f, 1f, 1f, 1f);
        AbstractGui.blit(ms, px + 8, py + 6, 0f, 0f, 26, 26, 26, 26);
        hits.add(new Hit(px + 8, py + 6, 26, 26, 830));
        ms.pushPose();
        ms.scale(0.9f, 0.9f, 1f);
        Hud.gradText(ms, font, "PrimeVisual", Math.round((px + 38) / 0.9f), Math.round((py + 14) / 0.9f));
        ms.popPose();
        font.draw(ms, "Right Shift - меню", px + 14, py + 35, 0xFF5D6076);

        int ty0 = py + 46;
        tabInd += (tab * (float) TAB_STEP - tabInd) * 0.25f;
        Hud.rr(ms, px + 8, ty0 + Math.round(tabInd), SIDE - 16, TAB_H, 6,
                (Hud.lerp(c1, c2, 0.5f) & 0x00FFFFFF) | 0x40000000);
        Hud.rr(ms, px + 8, ty0 + Math.round(tabInd) + 4, 2, TAB_H - 8, 1, Hud.lerp(c1, c2, 0.5f));
        for (int k = 0; k < TABS.length; k++) {
            boolean sel = k == tab;
            font.drawShadow(ms, TABS[k], px + 20, ty0 + k * TAB_STEP + 6, sel ? Hud.WHITE : 0xFF8C90A8);
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

        int sx = cx + 100, sw = 154;
        card(ms, sx, py + 10, sw, 16, 6, 0xFF171822, 0xFF21222F, in(mx, my, sx, py + 10, sw, 16) ? 1f : 0f);
        if (searchFocus) Hud.rrOutline(ms, sx, py + 10, sw, 16, 6, Config.c2());
        boolean blink = (System.currentTimeMillis() / 450L) % 2L == 0L;
        if (search.isEmpty() && !searchFocus) {
            font.draw(ms, "Поиск функций...", sx + 8, py + 14, 0xFF5D6076);
        } else {
            font.drawShadow(ms, fitTail(search + (searchFocus && blink ? "|" : ""), sw - 16), sx + 8, py + 14, Hud.WHITE);
        }
        hits.add(new Hit(sx, py + 10, sw, 16, 700));

        for (int x = 0; x < CW; x += 2) {
            AbstractGui.fill(ms, cx + x, py + 34, cx + x + 2, py + 35, Hud.grad(x / (float) CW));
        }
        chromeHits = hits.size();

        // ---- содержимое с прокруткой ----
        viewTop = py + 38;
        viewBottom = py + H - 6;
        scroll += (scrollTarget - scroll) * 0.3f;
        if (Math.abs(scrollTarget - scroll) < 0.4f) scroll = scrollTarget;
        int baseY = py + 44;
        int startY = baseY - Math.round(scroll);
        int end = startY;

        float sp = Math.min(1f, (System.currentTimeMillis() - tabAt) / 180f);
        float slide = (1f - (1f - (1f - sp) * (1f - sp))) * 14f;
        try {
            beginClip();
            RenderSystem.pushMatrix();
            RenderSystem.translatef(slide, 0f, 0f);
            try {
                String q = search.trim().toLowerCase(Locale.ROOT);
                if (!q.isEmpty()) {
                    end = drawSearchResults(ms, cx, startY, mx, my, q);
                } else {
                    switch (tab) {
                        case 0: end = drawHudTab(ms, cx, startY, mx, my); break;
                        case 1: end = drawCrosshairTab(ms, cx, startY, mx, my); break;
                        case 2: end = drawVisualTab(ms, cx, startY, mx, my); break;
                        case 3: end = drawUtilsTab(ms, cx, startY, mx, my); break;
                        case 4: end = drawStyleTab(ms, cx, startY, mx, my); break;
                        default: end = drawSettingsTab(ms, cx, startY, mx, my); break;
                    }
                }
            } finally {
                RenderSystem.popMatrix();
            }
        } finally {
            endClip();
        }

        int viewH = viewBottom - baseY;
        maxScroll = Math.max(0, (end - startY) - viewH + 8);
        scrollTarget = MathHelper.clamp(scrollTarget, 0f, (float) maxScroll);
        if (scroll > maxScroll) scroll = maxScroll;
        if (System.currentTimeMillis() - statusAt < 2500L && !status.isEmpty()) {
            int bw = font.width(status) + 20;
            int bx = cx + (CW - bw) / 2;
            Hud.glassRectA(ms, bx, py + H - 30, bw, 18, 6, false, 230);
            font.drawShadow(ms, status, bx + 10f, py + H - 25f, Hud.GREEN);
        }
        if (maxScroll > 0) { // полоса прокрутки
            int trackX = px + W - 9;
            Hud.rr(ms, trackX, baseY, 3, viewH, 1, 0x30FFFFFF);
            int thumbH = Math.max(18, viewH * viewH / (viewH + maxScroll));
            int thumbY = baseY + Math.round((viewH - thumbH) * (scroll / maxScroll));
            Hud.rr(ms, trackX, thumbY, 3, thumbH, 1, Hud.lerp(c1, c2, 0.5f));
        }
    }

    // ============================ Элементы ============================
    private float[] state(int key, boolean on) {
        float[] a = st.get(key);
        if (a == null) {
            a = new float[]{on ? 1f : 0f, 0f};
            st.put(key, a);
        }
        return a;
    }

    private int switchCard(MatrixStack ms, int x, int y, String name, String desc, int hitId, boolean on, int mx, int my) {
        boolean hov = in(mx, my, x, y, CW, 24);
        float[] a = state(hitId, on);
        a[0] += ((on ? 1f : 0f) - a[0]) * 0.3f;
        a[1] += ((hov ? 1f : 0f) - a[1]) * 0.25f;
        card(ms, x, y, CW, 24, 5, 0xFF171822, 0xFF21222F, a[1]);
        font.drawShadow(ms, name, x + 10, y + 4, Hud.lerp(0xFFB4B7CC, 0xFFFFFFFF, a[0]));
        font.draw(ms, desc, x + 10, y + 14, 0xFF6C6F86);
        int tx = x + CW - 36, ty = y + 7;
        Hud.rr(ms, tx, ty, 26, 10, 5, Hud.lerp(0xFF3A3B4D, Config.c1(), a[0]));
        Hud.rr(ms, tx + 1 + Math.round(a[0] * 16), ty + 1, 8, 8, 4, 0xFFFFFFFF);
        hits.add(new Hit(x, y, CW, 24, hitId));
        return y + 27;
    }

    private int moduleCard(MatrixStack ms, int i, int x, int y, int mx, int my) {
        return switchCard(ms, x, y, Config.NAMES[i], Config.DESCS[i], i, Config.ON[i], mx, my);
    }

    private int optionCard(MatrixStack ms, int x, int y, String name, String desc, int id, int mx, int my) {
        return switchCard(ms, x, y, name, desc, id, optValue(id), mx, my);
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

    private void button(MatrixStack ms, int x, int y, int w, int h, String text, int id, int mx, int my) {
        boolean hov = in(mx, my, x, y, w, h);
        card(ms, x, y, w, h, 6, 0xFF171822, 0xFF2A2B3D, hov ? 1f : 0f);
        font.drawShadow(ms, text, x + (w - font.width(text)) / 2f, y + (h - 8) / 2f, Hud.WHITE);
        hits.add(new Hit(x, y, w, h, id));
    }

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

    /** Строка «название + кнопка выбора клавиши». */
    private int keyRow(MatrixStack ms, int x, int y, String name, int keyVal, int listenId, int hitId, int mx, int my) {
        boolean lk = listening == listenId;
        card(ms, x, y, CW, 24, 5, 0xFF171822, 0xFF21222F, 0f);
        font.drawShadow(ms, name, x + 10, y + 8, Hud.WHITE);
        int bx = x + CW - 88, bw = 80;
        card(ms, bx, y + 2, bw, 20, 5, 0xFF1D1E2C, 0xFF2A2B3D, in(mx, my, bx, y + 2, bw, 20) ? 1f : 0f);
        if (lk) Hud.rrOutline(ms, bx, y + 2, bw, 20, 5, Config.c2());
        String kn = fitHead(lk ? "Нажмите..." : keyName(keyVal), bw - 8);
        font.drawShadow(ms, kn, bx + (bw - font.width(kn)) / 2f, y + 8, lk ? Config.c2() : (keyVal < 0 ? 0xFF6C6F86 : Hud.WHITE));
        hits.add(new Hit(bx, y + 2, bw, 20, hitId));
        return y + 27;
    }

    // ---- поиск ----
    private int drawSearchResults(MatrixStack ms, int cx, int y, int mx, int my, String q) {
        int found = 0;
        for (int i = 0; i < Config.ON.length; i++) {
            String n = Config.NAMES[i].toLowerCase(Locale.ROOT);
            String d = Config.DESCS[i].toLowerCase(Locale.ROOT);
            if (n.contains(q) || d.contains(q)) {
                y = moduleCard(ms, i, cx, y, mx, my);
                found++;
            }
        }
        if (found == 0) {
            label(ms, "Ничего не найдено", cx, y + 4);
            y += 16;
        }
        return y + 4;
    }

    // ============================ Вкладка «HUD» ============================
    private int drawHudTab(MatrixStack ms, int cx, int y, int mx, int my) {
        y = section(ms, "Информация", cx, y);
        int[] info = {Config.FPS, Config.FPSGRAPH, Config.COORDS, Config.DIR, Config.SPEED, Config.PING, Config.TIME};
        for (int i : info) y = moduleCard(ms, i, cx, y, mx, my);

        y = section(ms, "Мир и игрок", cx, y);
        int[] world = {Config.LIGHT, Config.BIOME, Config.GAMETIME, Config.WEATHER, Config.HUNGER, Config.COMPASS};
        for (int i : world) y = moduleCard(ms, i, cx, y, mx, my);

        y = section(ms, "Интерфейс", cx, y);
        int[] ui = {Config.EFFECTS, Config.DURABILITY, Config.ITEMS, Config.TARGET, Config.WARN, Config.NOTIFY};
        for (int i : ui) y = moduleCard(ms, i, cx, y, mx, my);

        y = section(ms, "Клавиши WASD", cx, y);
        y = moduleCard(ms, Config.KEYS, cx, y, mx, my);
        y = optionCard(ms, cx, y, "Кнопки мыши и CPS", "Строка ЛКМ и ПКМ под WASD", 800, mx, my);
        y = optionCard(ms, cx, y, "Пробел", "Строка пробела внизу", 801, mx, my);
        y = sliderBlock(ms, cx, y, "Размер клавиш", Config.keysSize + " px", 111, (Config.keysSize - 20) / 10f);

        y = section(ms, "Ватермарка", cx, y);
        y = moduleCard(ms, Config.WATERMARK, cx, y, mx, my);
        y = optionCard(ms, cx, y, "FPS в плашке", "Показывать кадры в секунду", 802, mx, my);
        y = optionCard(ms, cx, y, "Время в плашке", "Показывать реальное время", 803, mx, my);
        label(ms, "Свой текст плашки", cx, y + 1);
        y += 12;
        field(ms, cx, y, CW, 24, F_WM, "PrimeVisual", 714, mx, my);
        return y + 31;
    }

    // ============================ Вкладка «Прицел» ============================
    private int drawCrosshairTab(MatrixStack ms, int cx, int y, int mx, int my) {
        y = section(ms, "Прицел", cx, y);
        y = moduleCard(ms, Config.CROSSHAIR, cx, y, mx, my);
        y = moduleCard(ms, Config.HITMARKER, cx, y, mx, my);
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
        int chw = (CW - 5 * 4) / 6;
        for (int k = 0; k < 6; k++) {
            int x = cx + k * (chw + 4);
            if (k == Config.crosshairColor) Hud.rr(ms, x - 2, y - 2, chw + 4, 22, 6, 0xFFFFFFFF);
            Hud.rr(ms, x, y, chw, 18, 5, Hud.crosshairColor(k));
            if (k == 1) Hud.rr(ms, x + chw / 2, y + 3, chw / 2 - 3, 12, 4, Config.c2());
            hits.add(new Hit(x, y, chw, 18, 420 + k));
        }
        y += 26;
        y = sliderBlock(ms, cx, y, "Размер", String.valueOf(Config.crosshairSize), 104, (Config.crosshairSize - 3) / 6f);
        y = sliderBlock(ms, cx, y, "Толщина", Config.crosshairThick + " px", 112, (Config.crosshairThick - 1) / 2f);
        y = optionCard(ms, cx, y, "Обводка", "Тёмный контур вокруг линий", 804, mx, my);
        card(ms, cx, y, CW, 44, 6, 0xFF12131C, 0xFF12131C, 0f);
        font.draw(ms, "Предпросмотр", cx + 8, y + 5, 0xFF5D6076);
        Hud.crosshairShape(ms, cx + CW / 2, y + 24, 3, Config.crosshairSize, Config.crosshairStyle,
                Hud.crosshairColor(Config.crosshairColor));
        y += 52;

        y = section(ms, "Зум", cx, y);
        String zv = String.format(Locale.ROOT, "×%.1f", 1.0 / Config.zoom);
        y = sliderBlock(ms, cx, y, "Сила зума (клавиша C)", zv, 102, (float) ((0.6 - Config.zoom) / 0.55));
        y = moduleCard(ms, Config.CINEZOOM, cx, y, mx, my);
        return y + 4;
    }

    // ============================ Вкладка «Визуал» ============================
    private int drawVisualTab(MatrixStack ms, int cx, int y, int mx, int my) {
        y = section(ms, "Хитбоксы", cx, y);
        y = moduleCard(ms, Config.HITBOX, cx, y, mx, my);
        label(ms, "Показывать для", cx, y + 1);
        y += 12;
        boolean[] on = {Config.hbPlayers, Config.hbMobs, Config.hbItems, Config.hbOthers};
        int cw = (CW - 3 * 4) / 4;
        for (int k = 0; k < 4; k++) {
            int x = cx + k * (cw + 4);
            if (on[k]) Hud.rr(ms, x, y, cw, 20, 5, Hud.lerp(Config.c1(), Config.c2(), 0.5f));
            else card(ms, x, y, cw, 20, 5, 0xFF171822, 0xFF21222F, in(mx, my, x, y, cw, 20) ? 1f : 0f);
            font.drawShadow(ms, HB_CATS[k], x + (cw - font.width(HB_CATS[k])) / 2f, y + 6, Hud.WHITE);
            hits.add(new Hit(x, y, cw, 20, 440 + k));
        }
        y += 26;
        label(ms, "Цвет рамок", cx, y + 1);
        y += 12;
        int sw = (CW - 8 * 4) / 9;
        for (int k = 0; k < 9; k++) {
            int x = cx + k * (sw + 4);
            if (k == Config.hbColor) Hud.rr(ms, x - 2, y - 2, sw + 4, 22, 6, 0xFFFFFFFF);
            Hud.rr(ms, x, y, sw, 18, 5, Hud.hitboxColor(k));
            hits.add(new Hit(x, y, sw, 18, 450 + k));
        }
        y += 26;
        y = optionCard(ms, cx, y, "Линия глаз", "Красная линия на уровне глаз", 805, mx, my);
        y = sliderBlock(ms, cx, y, "Дальность", Config.hbRange + " бл", 109, (Config.hbRange - 8) / 120f);
        label(ms, "Рамки скрыты за блоками, как в F3+B", cx, y - 4);
        y += 10;

        y = section(ms, "Частицы", cx, y);
        y = moduleCard(ms, Config.TRAILS, cx, y, mx, my);
        y = moduleCard(ms, Config.JUMPCIRCLE, cx, y, mx, my);
        label(ms, "Тип частиц", cx, y + 1);
        y += 12;
        int pw = (CW - 3 * 4) / 4;
        for (int k = 0; k < PARTICLES.length; k++) {
            int x = cx + (k % 4) * (pw + 4);
            int yy = y + (k / 4) * 24;
            boolean sel = k == Config.particleType;
            if (k == 6) {
                if (sel) Hud.rr(ms, x - 2, yy - 2, pw + 4, 24, 6, 0xFFFFFFFF);
                Hud.rr(ms, x, yy, pw, 20, 5, Hud.rainbow(0f));
            } else if (sel) {
                Hud.rr(ms, x, yy, pw, 20, 5, Hud.lerp(Config.c1(), Config.c2(), 0.5f));
            } else {
                card(ms, x, yy, pw, 20, 5, 0xFF171822, 0xFF21222F, in(mx, my, x, yy, pw, 20) ? 1f : 0f);
            }
            font.drawShadow(ms, PARTICLES[k], x + (pw - font.width(PARTICLES[k])) / 2f, yy + 6, Hud.WHITE);
            hits.add(new Hit(x, yy, pw, 20, 430 + k));
        }
        y += 52;
        y = sliderBlock(ms, cx, y, "Плотность следов", String.valueOf(Config.trailDensity), 113,
                (Config.trailDensity - 1) / 5f);
        y = sliderBlock(ms, cx, y, "Размер круга прыжка", String.format(Locale.ROOT, "%.1f бл", Config.circleSize),
                106, (Config.circleSize - 0.5f) / 2f);

        y = section(ms, "Эффекты при ударе", cx, y);
        y = moduleCard(ms, Config.HITFX, cx, y, mx, my);
        label(ms, "Что вылетает", cx, y + 1);
        y += 12;
        String[] fxn = {"Доллары", "Амогусы", "Айфоны", "Микс", "Монеты", "Алмазы", "Золото"};
        int nfx = Config.secret ? 7 : 4;
        int fw = (CW - 3 * 4) / 4;
        for (int k = 0; k < nfx; k++) {
            int x = cx + (k % 4) * (fw + 4), yy = y + (k / 4) * 24;
            if (k == Config.fxType) Hud.rr(ms, x, yy, fw, 20, 5, Hud.lerp(Config.c1(), Config.c2(), 0.5f));
            else card(ms, x, yy, fw, 20, 5, 0xFF171822, 0xFF21222F, in(mx, my, x, yy, fw, 20) ? 1f : 0f);
            font.drawShadow(ms, fxn[k], x + (fw - font.width(fxn[k])) / 2f, yy + 6, k >= 4 ? 0xFFFFD84A : Hud.WHITE);
            hits.add(new Hit(x, yy, fw, 20, 470 + k));
        }
        y += nfx > 4 ? 52 : 28;
        y = sliderBlock(ms, cx, y, "Количество", String.valueOf(Config.fxCount), 117, (Config.fxCount - 3) / 17f);
        y = sliderBlock(ms, cx, y, "Размер", Math.round(Config.fxSize * 100) + "%", 118, (Config.fxSize - 0.5f) / 1.5f);
        y = moduleCard(ms, Config.KILLFX, cx, y, mx, my);
        y = moduleCard(ms, Config.HITSND, cx, y, mx, my);
        label(ms, "Звук", cx, y + 1);
        y += 12;
        String[] snn = {"Pay", "Cash", "Pop", "Boing", "Bell"};
        int sw5 = (CW - 4 * 4) / 5;
        for (int k = 0; k < 5; k++) {
            int x = cx + k * (sw5 + 4);
            if (k == Config.hitSound) Hud.rr(ms, x, y, sw5, 20, 5, Hud.lerp(Config.c1(), Config.c2(), 0.5f));
            else card(ms, x, y, sw5, 20, 5, 0xFF171822, 0xFF21222F, in(mx, my, x, y, sw5, 20) ? 1f : 0f);
            font.drawShadow(ms, snn[k], x + (sw5 - font.width(snn[k])) / 2f, y + 6, Hud.WHITE);
            hits.add(new Hit(x, y, sw5, 20, 480 + k));
        }
        y += 26;
        y = sliderBlock(ms, cx, y, "Громкость звука", Math.round(Config.hitVolume * 100) + "%", 119, (Config.hitVolume - 0.1f) / 0.9f);
        button(ms, cx, y, CW, 22, "Проверить эффект и звук", 822, mx, my);
        y += 30;

        y = section(ms, "Экранные эффекты", cx, y);
        y = moduleCard(ms, Config.VIGNETTE, cx, y, mx, my);
        label(ms, "Цвет виньетки", cx, y + 1);
        y = colorChips(ms, cx, y + 12, new String[]{"Акцент", "Тёмная", "Радуга"}, Config.vigColor, 490, mx, my);
        y = moduleCard(ms, Config.LOWHP, cx, y, mx, my);
        y = moduleCard(ms, Config.SPEEDLINES, cx, y, mx, my);

        y = section(ms, "Косметика (видите только вы)", cx, y);
        y = moduleCard(ms, Config.CAPE, cx, y, mx, my);
        label(ms, "Своя картинка: .minecraft/primevisual/cape.png", cx, y - 4);
        y += 10;
        button(ms, cx, y, CW, 22, "Обновить картинку плаща", 821, mx, my);
        y += 28;
        y = moduleCard(ms, Config.HAT, cx, y, mx, my);
        label(ms, "Цвет шляпы", cx, y + 1);
        y = colorChips(ms, cx, y + 12, new String[]{"Соломенная", "Акцент", "Радуга"}, Config.hatColor, 460, mx, my);
        y = sliderBlock(ms, cx, y, "Размер шляпы", Math.round(Config.hatScale * 100) + "%", 114, (Config.hatScale - 0.7f) / 0.8f);
        y = moduleCard(ms, Config.HALO, cx, y, mx, my);
        label(ms, "Цвет нимба", cx, y + 1);
        y = colorChips(ms, cx, y + 12, new String[]{"Золотой", "Акцент", "Радуга"}, Config.haloColor, 465, mx, my);
        y = sliderBlock(ms, cx, y, "Размер нимба", Math.round(Config.haloScale * 100) + "%", 115, (Config.haloScale - 0.6f) / 1.0f);
        y = sliderBlock(ms, cx, y, "Высота нимба", Math.round(Config.haloHeight * 100) + "%", 116, Config.haloHeight / 0.3f);

        y = section(ms, "Анимации", cx, y);
        y = moduleCard(ms, Config.ANIM_CHAT, cx, y, mx, my);
        y = moduleCard(ms, Config.ANIM_TAB, cx, y, mx, my);
        y = moduleCard(ms, Config.ANIM_GUI, cx, y, mx, my);
        y = moduleCard(ms, Config.ANIM_HOTBAR, cx, y, mx, my);
        y = sliderBlock(ms, cx, y, "Скорость анимаций", String.format(Locale.ROOT, "×%.1f", Config.animSpeed),
                108, (Config.animSpeed - 0.5f) / 1.5f);
        return y + 4;
    }

    /** Три чипа выбора цвета; последний (радуга) переливается. */
    private int colorChips(MatrixStack ms, int x, int y, String[] names, int sel, int baseId, int mx, int my) {
        int cw = (CW - 8) / 3;
        for (int k = 0; k < 3; k++) {
            int cx = x + k * (cw + 4);
            if (k == 2) {
                if (sel == 2) Hud.rr(ms, cx - 2, y - 2, cw + 4, 24, 6, 0xFFFFFFFF);
                Hud.rr(ms, cx, y, cw, 20, 5, Hud.rainbow(0f));
            } else if (k == sel) {
                Hud.rr(ms, cx, y, cw, 20, 5, Hud.lerp(Config.c1(), Config.c2(), 0.5f));
            } else {
                card(ms, cx, y, cw, 20, 5, 0xFF171822, 0xFF21222F, in(mx, my, cx, y, cw, 20) ? 1f : 0f);
            }
            font.drawShadow(ms, names[k], cx + (cw - font.width(names[k])) / 2f, y + 6, Hud.WHITE);
            hits.add(new Hit(cx, y, cw, 20, baseId + k));
        }
        return y + 26;
    }

    // ============================ Вкладка «Утилиты» ============================
    private int drawUtilsTab(MatrixStack ms, int cx, int y, int mx, int my) {
        y = section(ms, "Помощники", cx, y);
        y = moduleCard(ms, Config.SPRINT, cx, y, mx, my);
        y = moduleCard(ms, Config.BRIGHT, cx, y, mx, my);
        y = sliderBlock(ms, cx, y, "Уровень яркости", "×" + Math.round(Config.brightness), 110,
                (Config.brightness - 1f) / 14f);
        y = moduleCard(ms, Config.DEATHPOINT, cx, y, mx, my);
        y = moduleCard(ms, Config.CHATTIME, cx, y, mx, my);
        label(ms, "Проверьте правила сервера: часть функций может быть запрещена", cx, y - 4);
        y += 10;

        y = section(ms, "Калькулятор", cx, y);
        y = moduleCard(ms, Config.CALC, cx, y, mx, my);
        y = keyRow(ms, cx, y, "Клавиша отправки ответа", Config.calcKey, L_CALC, 650, mx, my);
        label(ms, "Ответ дописывается к примеру в чате, отправляете его вы", cx, y - 4);
        y += 10;

        y = section(ms, "GPS", cx, y);
        y = moduleCard(ms, Config.GPS, cx, y, mx, my);
        int fw = 90;
        field(ms, cx, y, fw, 24, F_GPSX, "X", 710, mx, my);
        field(ms, cx + fw + 4, y, fw, 24, F_GPSZ, "Z", 711, mx, my);
        button(ms, cx + 2 * (fw + 4), y, CW - 2 * (fw + 4), 24, "Я здесь", 505, mx, my);
        y += 31;

        y = section(ms, "Авто-команда", cx, y);
        y = moduleCard(ms, Config.AUTOCMD, cx, y, mx, my);
        field(ms, cx, y, CW, 24, F_AUTO, "/команда для отправки по таймеру", 712, mx, my);
        y += 27;
        y = sliderBlock(ms, cx, y, "Интервал", Config.autoInterval + " сек", 107, (Config.autoInterval - 10) / 290f);

        y = section(ms, "Бинды", cx, y);
        y = keyRow(ms, cx, y, "Скрыть / показать HUD мода", Config.hudKey, L_HUD, 651, mx, my);
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
        return y + 26;
    }

    // ============================ Вкладка «Стиль» ============================
    private int drawStyleTab(MatrixStack ms, int cx, int y, int mx, int my) {
        y = section(ms, "Цвет акцента", cx, y);
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
            label(ms, "Оттенок", cx, y);
            String v = Math.round(Config.hue * 360f) + "°";
            font.drawShadow(ms, v, cx + CW - font.width(v), y, Hud.WHITE);
            int sy = y + 14;
            for (int i = 0; i < CW; i += 2) {
                AbstractGui.fill(ms, cx + i, sy + 3, Math.min(cx + i + 2, cx + CW), sy + 7,
                        Config.hsb(i / (float) CW, 0.75f, 1f));
            }
            Hud.rr(ms, cx + (int) (CW * Config.hue) - 4, sy, 8, 10, 4, 0xFFFFFFFF);
            hits.add(new Hit(cx - 4, sy - 3, CW + 8, 16, 105));
            y += 34;
        } else if (Config.preset == 7) {
            label(ms, "Радуга: цвета плавно меняются сами", cx, y);
            y += 16;
        } else {
            label(ms, "Последние два квадрата: свой оттенок и радуга", cx, y);
            y += 16;
        }
        y = moduleCard(ms, Config.ANIMATE, cx, y, mx, my);
        card(ms, cx, y, CW, 44, 6, 0xFF12131C, 0xFF12131C, 0f);
        String name = "PrimeVisual";
        float sc = 1.6f;
        float tw = font.width(name) * sc;
        ms.pushPose();
        ms.scale(sc, sc, 1f);
        Hud.gradText(ms, font, name, Math.round((cx + (CW - tw) / 2f) / sc), Math.round((y + 15) / sc));
        ms.popPose();
        y += 52;

        y = section(ms, "Панели", cx, y);
        button(ms, cx, y, CW, 22, "Стиль панелей: " + (Config.glass ? "Liquid Glass" : "Обычный"), 503, mx, my);
        y += 26;
        y = sliderBlock(ms, cx, y, "Размер HUD", Math.round(Config.scale * 100) + "%", 100, (Config.scale - 0.7f) / 0.8f);
        y = sliderBlock(ms, cx, y, "Плотность панелей", Math.round(Config.opacity * 100) + "%", 101,
                (Config.opacity - 0.3f) / 0.7f);
        y = sliderBlock(ms, cx, y, "Скругление углов", Config.radius + " px", 103, Config.radius / 8f);
        button(ms, cx, y, CW, 22, "Инфо-панель: " + (Config.infoRight ? "справа" : "слева"), 501, mx, my);
        y += 28;
        y = optionCard(ms, cx, y, "Тень текста", "Тень под текстом HUD", 806, mx, my);
        y = optionCard(ms, cx, y, "Звуки меню", "Щелчки при нажатиях в меню", 807, mx, my);
        return y + 4;
    }

    // ============================ Вкладка «Настройки» ============================
    private int drawSettingsTab(MatrixStack ms, int cx, int y, int mx, int my) {
        y = section(ms, "Конфиги", cx, y);
        label(ms, "Название нового конфига", cx, y + 1);
        y += 12;
        field(ms, cx, y, CW - 96, 24, F_CFG, "Например: pvp", 713, mx, my);
        button(ms, cx + CW - 92, y, 92, 24, "Сохранить", 506, mx, my);
        y += 29;
        if (System.currentTimeMillis() - statusAt < 2500L && !status.isEmpty()) {
            font.draw(ms, status, cx, y, 0xFF55FF7A);
            y += 12;
        }
        if (profiles.isEmpty()) {
            label(ms, "Сохранённых конфигов пока нет", cx, y + 2);
            y += 16;
        }
        for (int i = 0; i < Math.min(profiles.size(), 20); i++) {
            String n = profiles.get(i);
            card(ms, cx, y, CW, 24, 5, 0xFF171822, 0xFF171822, 0f);
            font.drawShadow(ms, fitHead(n, CW - 120), cx + 10, y + 8, Hud.WHITE);
            button(ms, cx + CW - 98, y + 2, 70, 20, "Загрузить", 900 + i, mx, my);
            boolean hc = in(mx, my, cx + CW - 24, y + 2, 20, 20);
            card(ms, cx + CW - 24, y + 2, 20, 20, 5, 0xFF1D1E2C, 0xFF3A2030, hc ? 1f : 0f);
            font.drawShadow(ms, "×", cx + CW - 14 - font.width("×") / 2f, y + 8, hc ? Hud.RED : 0xFF8C90A8);
            hits.add(new Hit(cx + CW - 24, y + 2, 20, 20, 920 + i));
            y += 27;
        }
        y += 4;

        y = section(ms, "HUD", cx, y);
        button(ms, cx, y, CW, 22, "Редактор HUD (перетаскивание элементов)", 504, mx, my);
        y += 26;
        button(ms, cx, y, CW, 22, "Сбросить настройки", 500, mx, my);
        y += 30;

        card(ms, cx, y, CW, 52, 6, 0xFF12131C, 0xFF12131C, 0f);
        String title = "Сделано whiteshapka";
        float sc = 1.4f;
        float tw = font.width(title) * sc;
        ms.pushPose();
        ms.scale(sc, sc, 1f);
        Hud.gradText(ms, font, title, Math.round((cx + (CW - tw) / 2f) / sc), Math.round((y + 11) / sc));
        ms.popPose();
        String sub = "PrimeVisual Beta • Forge 1.16.5";
        font.draw(ms, sub, cx + (CW - font.width(sub)) / 2f, y + 34, 0xFF5D6076);
        return y + 60;
    }

    // ============================ Ввод ============================
    private void setSlider(int id, double lmx) {
        float frac = MathHelper.clamp((float) ((lmx - (px + SIDE + 12)) / CW), 0f, 1f);
        switch (id) {
            case 100: Config.scale = Math.round((0.7f + frac * 0.8f) * 20f) / 20f; break;
            case 101: Config.opacity = Math.round((0.3f + frac * 0.7f) * 20f) / 20f; break;
            case 102: Config.zoom = 0.6 - frac * 0.55; break;
            case 103: Config.radius = Math.round(frac * 8f); break;
            case 104: Config.crosshairSize = 3 + Math.round(frac * 6f); break;
            case 105: Config.hue = frac; break;
            case 106: Config.circleSize = Math.round((0.5f + frac * 2f) * 10f) / 10f; break;
            case 107: Config.autoInterval = 10 + Math.round(frac * 58f) * 5; break;
            case 108: Config.animSpeed = Math.round((0.5f + frac * 1.5f) * 10f) / 10f; break;
            case 109: Config.hbRange = 8 + Math.round(frac * 120f); break;
            case 110: Config.brightness = 1f + Math.round(frac * 14f); break;
            case 111: Config.keysSize = 20 + Math.round(frac * 10f); break;
            case 112: Config.crosshairThick = 1 + Math.round(frac * 2f); break;
            case 113: Config.trailDensity = 1 + Math.round(frac * 5f); break;
            case 114: Config.hatScale = Math.round((0.7f + frac * 0.8f) * 20f) / 20f; break;
            case 115: Config.haloScale = Math.round((0.6f + frac) * 20f) / 20f; break;
            case 116: Config.haloHeight = Math.round(frac * 0.3f * 100f) / 100f; break;
            case 117: Config.fxCount = 3 + Math.round(frac * 17f); break;
            case 118: Config.fxSize = Math.round((0.5f + frac * 1.5f) * 20f) / 20f; break;
            case 119: Config.hitVolume = Math.round((0.1f + frac * 0.9f) * 20f) / 20f; break;
            default: break;
        }
    }

    private void assignKey(int listenId, int key) {
        if (listenId == L_CALC) Config.calcKey = key;
        else if (listenId == L_HUD) Config.hudKey = key;
        else if (listenId >= 0 && listenId < Config.BIND_COUNT) Config.bindKey[listenId] = key;
    }

    @Override
    public boolean mouseClicked(double dx, double dy, int button) {
        if (closingAt != 0L) return true;
        if (button != 0) return super.mouseClicked(dx, dy, button);
        double lx = local(dx, width), ly = local(dy, height);
        listening = -1;
        editing = -1;
        searchFocus = false;
        for (int n = 0; n < hits.size(); n++) {
            Hit h = hits.get(n);
            if (n >= chromeHits && (ly < viewTop || ly >= viewBottom)) continue; // за пределами видимой области
            if (!in(lx, ly, h.x, h.y, h.w, h.h)) continue;
            handleHit(h.id, lx);
            return true;
        }
        return super.mouseClicked(dx, dy, button);
    }

    private void handleHit(int id, double lx) {
        if (id < Config.ON.length) {
            Config.ON[id] = !Config.ON[id];
            click(Config.ON[id] ? 1.3f : 0.8f);
        } else if (id >= 100 && id <= 119) {
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
        } else if (id >= 420 && id < 426) {
            Config.crosshairColor = id - 420;
            click(1.1f);
        } else if (id >= 430 && id < 437) {
            Config.particleType = id - 430;
            click(1.1f);
        } else if (id >= 440 && id < 444) {
            if (id == 440) Config.hbPlayers = !Config.hbPlayers;
            else if (id == 441) Config.hbMobs = !Config.hbMobs;
            else if (id == 442) Config.hbItems = !Config.hbItems;
            else Config.hbOthers = !Config.hbOthers;
            click(1.1f);
        } else if (id >= 450 && id < 459) {
            Config.hbColor = id - 450;
            click(1.1f);
        } else if (id >= 470 && id < 477) {
            Config.fxType = id - 470;
            click(1.1f);
        } else if (id >= 480 && id < 485) {
            Config.hitSound = id - 480;
            Fx.playSound(minecraft, 1f);
        } else if (id >= 490 && id < 493) {
            Config.vigColor = id - 490;
            click(1.1f);
        } else if (id == 822) {
            Fx.burst(minecraft, Config.fxCount);
            Fx.playSound(minecraft, 1f);
        } else if (id == 830) {
            logoClick();
        } else if (id >= 460 && id < 463) {
            Config.hatColor = id - 460;
            click(1.1f);
        } else if (id >= 465 && id < 468) {
            Config.haloColor = id - 465;
            click(1.1f);
        } else if (id == 821) {
            Cosmetics.reloadCape();
            click(1.1f);
        } else if (id == 500) {
            Config.reset();
            st.clear();
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
        } else if (id == 506) {
            String n = Config.sanitize(cfgName);
            if (n.isEmpty()) {
                say("Введите название конфига");
            } else if (Config.saveProfile(n)) {
                profiles = Config.listProfiles();
                say("Конфиг сохранён: " + n);
                click(1.2f);
            } else {
                say("Не удалось сохранить конфиг");
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
        } else if (id == 650) {
            listening = L_CALC;
            click(1.0f);
        } else if (id == 651) {
            listening = L_HUD;
            click(1.0f);
        } else if (id == 700) {
            searchFocus = true;
        } else if (id >= 710 && id <= 714) {
            editing = id - 700;
        } else if (id >= 800 && id <= 807) {
            optToggle(id);
            click(optValue(id) ? 1.3f : 0.8f);
        } else if (id >= 900 && id < 920) {
            int i = id - 900;
            if (i < profiles.size()) {
                String n = profiles.get(i);
                if (Config.loadProfile(n)) {
                    st.clear();
                    say("Загружен конфиг: " + n);
                    click(1.2f);
                } else {
                    say("Не удалось загрузить конфиг");
                }
            }
        } else if (id >= 920 && id < 940) {
            int i = id - 920;
            if (i < profiles.size()) {
                Config.deleteProfile(profiles.get(i));
                profiles = Config.listProfiles();
                say("Конфиг удалён");
                click(0.7f);
            }
        }
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
    public boolean mouseScrolled(double mx, double my, double delta) {
        double lx = local(mx, width), ly = local(my, height);
        if (in(lx, ly, px + SIDE, viewTop, W - SIDE, viewBottom - viewTop)) {
            scrollTarget = MathHelper.clamp(scrollTarget - (float) delta * 28f, 0f, (float) maxScroll);
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (listening != -1) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                listening = -1;
            } else if (key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) {
                assignKey(listening, -1);
                listening = -1;
            } else if (!ClientEvents.MENU.matches(key, scan) && !ClientEvents.ZOOM.matches(key, scan)) {
                assignKey(listening, key);
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
            if (SharedConstants.isAllowedChatCharacter(c) && search.length() < 24) {
                search += c;
                scrollTarget = 0f;
            }
            return true;
        }
        return super.charTyped(c, mods);
    }

    @Override
    public void onClose() {
        if (closingAt == 0L) closingAt = System.currentTimeMillis();
    }

    @Override
    public void removed() {
        SAVED_SCROLL[tab] = scrollTarget;
        Config.lastTab = tab;
        Config.save();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
