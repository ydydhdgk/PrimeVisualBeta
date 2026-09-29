package com.example.visuals;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.GameSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.network.play.NetworkPlayerInfo;
import net.minecraft.client.renderer.texture.PotionSpriteUploader;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.entity.LivingEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectInstance;
import net.minecraft.util.math.MathHelper;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class Hud {
    static final int LABEL  = 0xFFA0A3BD;
    static final int WHITE  = 0xFFFFFFFF;
    static final int GREEN  = 0xFF55FF7A;
    static final int YELLOW = 0xFFFFD84A;
    static final int RED    = 0xFFFF5566;
    private static final int IW = 150;

    private static int frames, fps;
    private static long lastFpsTime = System.currentTimeMillis();
    private static float speedSmooth;
    private static final float[] KEY_ANIM = new float[7];
    private static final Map<Effect, Integer> MAX_DURATION = new HashMap<>();
    private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private static class Row {
        final String label, value; final int color;
        Row(String l, String v, int c) { label = l; value = v; color = c; }
    }

    static int panelColor() {
        int a = Math.round(MathHelper.clamp(Config.opacity, 0.3f, 1f) * 255f);
        return (a << 24) | 0x101018;
    }

    public static void render(MatrixStack ms, int sw, int sh) {
        Minecraft mc = Minecraft.getInstance();
        ClientPlayerEntity p = mc.player;
        if (p == null) return;

        countFps();
        boolean noScreen = mc.screen == null;

        if (noScreen && Config.ON[Config.CROSSHAIR] && mc.options.getCameraType().isFirstPerson()) {
            crosshair(ms, mc, p, sw, sh);
        }
        if (mc.options.renderDebug) return;

        float s = MathHelper.clamp(Config.scale, 0.5f, 2f);
        int w = Math.round(sw / s), h = Math.round(sh / s);
        RenderSystem.pushMatrix();
        RenderSystem.scalef(s, s, 1f);
        try {
            int y = 6;
            y = infoPanel(ms, mc, p, 6, y);
            if (Config.ON[Config.EFFECTS]) effects(ms, mc, p, 6, y);
            if (Config.ON[Config.DURABILITY]) durability(ms, mc, p, w, h);
            if (Config.ON[Config.KEYS]) keystrokes(ms, mc, h);
            if (noScreen && Config.ON[Config.TARGET]) target(ms, mc, w);
            if (Config.ON[Config.WATERMARK]) watermark(ms, mc, w);
        } finally {
            RenderSystem.popMatrix();
        }

        if (noScreen && ClientEvents.zoomFactor() < 0.98f) {
            String z = String.format(Locale.ROOT, "Зум ×%.1f", 1f / ClientEvents.zoomFactor());
            mc.font.drawShadow(ms, z, sw / 2f - mc.font.width(z) / 2f, sh / 2f + 26, WHITE);
        }
    }

    // ================= Ватермарка (сверху справа) =================
    private static void watermark(MatrixStack ms, Minecraft mc, int w) {
        FontRenderer f = mc.font;
        String name = "PrimeVisual";
        String rest = "  |  " + fps + " fps  |  " + LocalTime.now().format(TIME_FMT);
        int tw = f.width(name) + f.width(rest);
        int pw = tw + 18, ph = 17;
        int x = w - pw - 6, y = 6;
        shadow(ms, x, y, pw, ph, 5);
        rr(ms, x, y, pw, ph, 5, panelColor());
        for (int i = 6; i < pw - 6; i += 2) { // тонкая градиентная линия снизу
            AbstractGui.fill(ms, x + i, y + ph - 1, Math.min(x + i + 2, x + pw - 6), y + ph,
                    lerp(Config.c1(), Config.c2(), i / (float) pw));
        }
        int nx = gradText(ms, f, name, x + 9, y + 5);
        f.drawShadow(ms, rest, (float) nx, (float) (y + 5), LABEL);
    }

    // ================= Информационная панель =================
    private static int infoPanel(MatrixStack ms, Minecraft mc, ClientPlayerEntity p, int x, int y) {
        List<Row> rows = new ArrayList<>();
        if (Config.ON[Config.FPS]) {
            rows.add(new Row("FPS", String.valueOf(fps), fps >= 60 ? GREEN : fps >= 30 ? YELLOW : RED));
        }
        if (Config.ON[Config.COORDS]) {
            rows.add(new Row("XYZ", (int) Math.floor(p.getX()) + "  " + (int) Math.floor(p.getY()) + "  "
                    + (int) Math.floor(p.getZ()), WHITE));
        }
        if (Config.ON[Config.DIR]) {
            rows.add(new Row("Курс", dirName(p) + "  " + Math.round(MathHelper.wrapDegrees(p.yRot)) + "°", WHITE));
        }
        if (Config.ON[Config.SPEED]) {
            double dx = p.getX() - p.xo, dz = p.getZ() - p.zo;
            float v = (float) (Math.sqrt(dx * dx + dz * dz) * 20.0);
            speedSmooth += (v - speedSmooth) * 0.1f;
            rows.add(new Row("Скорость", String.format(Locale.ROOT, "%.1f бл/с", speedSmooth), WHITE));
        }
        if (Config.ON[Config.PING]) {
            String val = "—";
            int col = WHITE;
            if (mc.getConnection() != null) {
                NetworkPlayerInfo info = mc.getConnection().getPlayerInfo(p.getUUID());
                if (info != null && info.getLatency() > 0) {
                    int ms1 = info.getLatency();
                    val = ms1 + " мс";
                    col = ms1 < 80 ? GREEN : ms1 < 150 ? YELLOW : RED;
                }
            }
            rows.add(new Row("Пинг", val, col));
        }
        if (Config.ON[Config.LIGHT] && mc.level != null) {
            int light = mc.level.getMaxLocalRawBrightness(p.blockPosition());
            rows.add(new Row("Свет", String.valueOf(light), light < 8 ? RED : light < 12 ? YELLOW : GREEN));
        }
        if (Config.ON[Config.TIME]) {
            rows.add(new Row("Время", LocalTime.now().format(TIME_FMT), WHITE));
        }
        if (rows.isEmpty()) return y;

        int hgt = 8 + rows.size() * 12;
        panel(ms, x, y, IW, hgt);
        int ry = y + 6;
        for (Row r : rows) {
            text(ms, mc.font, r.label, x + 9, ry, LABEL);
            textRight(ms, mc.font, r.value, x + IW - 7, ry, r.color);
            ry += 12;
        }
        return y + hgt + 4;
    }

    private static String dirName(ClientPlayerEntity p) {
        switch (p.getDirection()) {
            case NORTH: return "Север";
            case SOUTH: return "Юг";
            case EAST:  return "Восток";
            case WEST:  return "Запад";
            default:    return "?";
        }
    }

    // ================= Эффекты зелий =================
    private static void effects(MatrixStack ms, Minecraft mc, ClientPlayerEntity p, int x, int y) {
        Set<Effect> active = new HashSet<>();
        PotionSpriteUploader sprites = mc.getMobEffectTextures();
        int w = IW, h = 22;
        for (EffectInstance ef : p.getActiveEffects()) {
            Effect type = ef.getEffect();
            active.add(type);
            int dur = ef.getDuration();
            Integer prev = MAX_DURATION.get(type);
            if (prev == null || dur > prev) MAX_DURATION.put(type, dur);
            float frac = Math.min(1f, dur / (float) MAX_DURATION.get(type));

            panel(ms, x, y, w, h);
            TextureAtlasSprite spr = sprites.get(type);
            mc.getTextureManager().bind(spr.atlas().location());
            RenderSystem.color4f(1f, 1f, 1f, 1f);
            AbstractGui.blit(ms, x + 7, y + 4, 0, 12, 12, spr);

            int amp = ef.getAmplifier();
            String name = type.getDisplayName().getString() + " " + (amp < ROMAN.length ? ROMAN[amp] : String.valueOf(amp + 1));
            int secs = dur / 20;
            String time = dur > 32000 ? "∞" : String.format("%d:%02d", secs / 60, secs % 60);
            text(ms, mc.font, name, x + 23, y + 3, type.isBeneficial() ? WHITE : RED);
            textRight(ms, mc.font, time, x + w - 6, y + 3, LABEL);

            int barX = x + 23, barW = w - 30;
            rr(ms, barX, y + 15, barW, 3, 1, 0x40FFFFFF);
            rr(ms, barX, y + 15, Math.max(2, (int) (barW * (dur > 32000 ? 1f : frac))), 3, 1, 0xFF000000 | type.getColor());
            y += h + 3;
        }
        MAX_DURATION.keySet().retainAll(active);
    }

    // ================= Прочность (справа внизу) =================
    private static void durability(MatrixStack ms, Minecraft mc, ClientPlayerEntity p, int sw, int sh) {
        List<ItemStack> items = new ArrayList<>();
        items.add(p.getItemBySlot(EquipmentSlotType.HEAD));
        items.add(p.getItemBySlot(EquipmentSlotType.CHEST));
        items.add(p.getItemBySlot(EquipmentSlotType.LEGS));
        items.add(p.getItemBySlot(EquipmentSlotType.FEET));
        items.add(p.getMainHandItem());
        items.add(p.getOffhandItem());
        items.removeIf(ItemStack::isEmpty);

        int w = 128, h = 24, gap = 3;
        int x = sw - w - 6;
        int y = sh - 6 - items.size() * (h + gap) + gap;
        for (ItemStack st : items) {
            panel(ms, x, y, w, h);
            mc.getItemRenderer().renderGuiItem(st, x + 7, y + 4);
            if (st.isDamageableItem()) {
                int max = st.getMaxDamage();
                int left = max - st.getDamageValue();
                float frac = left / (float) max;
                int color = frac > 0.5f ? lerp(YELLOW, GREEN, (frac - 0.5f) * 2f) : lerp(RED, YELLOW, frac * 2f);
                text(ms, mc.font, left + " / " + max, x + 28, y + 4, WHITE);
                int bx = x + 28, bw = w - 36;
                rr(ms, bx, y + 15, bw, 4, 2, 0x50FFFFFF);
                rr(ms, bx, y + 15, Math.max(3, (int) (bw * frac)), 4, 2, color);
            } else {
                text(ms, mc.font, "x" + st.getCount(), x + 28, y + 8, WHITE);
            }
            y += h + gap;
        }
    }

    // ================= Клавиши + CPS =================
    private static void keystrokes(MatrixStack ms, Minecraft mc, int sh) {
        GameSettings o = mc.options;
        int s = 22, g = 2;
        int x = 6, y = sh - 6 - (s * 4 + g * 3);
        key(ms, mc, 0, "W", null, x + s + g, y, s, s, o.keyUp.isDown());
        y += s + g;
        key(ms, mc, 1, "A", null, x, y, s, s, o.keyLeft.isDown());
        key(ms, mc, 2, "S", null, x + s + g, y, s, s, o.keyDown.isDown());
        key(ms, mc, 3, "D", null, x + 2 * (s + g), y, s, s, o.keyRight.isDown());
        y += s + g;
        int bw = (s * 3 + g * 2 - g) / 2;
        key(ms, mc, 4, "ЛКМ", ClientEvents.cps(true) + " CPS", x, y, bw, s, o.keyAttack.isDown());
        key(ms, mc, 5, "ПКМ", ClientEvents.cps(false) + " CPS", x + bw + g, y, bw, s, o.keyUse.isDown());
        y += s + g;
        key(ms, mc, 6, "ПРОБЕЛ", null, x, y, s * 3 + g * 2, s, o.keyJump.isDown());
    }

    private static void key(MatrixStack ms, Minecraft mc, int id, String label, String sub,
                            int x, int y, int w, int h, boolean down) {
        KEY_ANIM[id] += ((down ? 1f : 0f) - KEY_ANIM[id]) * 0.35f;
        float a = KEY_ANIM[id];
        shadow(ms, x, y, w, h, 4);
        int pressed = (lerp(Config.c1(), Config.c2(), 0.4f) & 0x00FFFFFF) | 0xE0000000;
        rr(ms, x, y, w, h, 4, lerp(panelColor(), pressed, a));
        int textColor = lerp(0xFFD8D9EA, 0xFFFFFFFF, a);
        int tw = mc.font.width(label);
        if (sub == null) {
            mc.font.drawShadow(ms, label, x + (w - tw) / 2f, y + (h - 8) / 2f, textColor);
        } else {
            mc.font.drawShadow(ms, label, x + (w - tw) / 2f, y + 3, textColor);
            mc.font.draw(ms, sub, x + (w - mc.font.width(sub)) / 2f, y + 12, lerp(LABEL, 0xFFFFFFFF, a));
        }
    }

    // ================= Инфо о цели (сверху по центру) =================
    private static void target(MatrixStack ms, Minecraft mc, int w) {
        if (!(mc.crosshairPickEntity instanceof LivingEntity)) return;
        LivingEntity le = (LivingEntity) mc.crosshairPickEntity;
        int tw = 150, th = 27;
        int x = (w - tw) / 2, y = 6;
        float hp = le.getHealth(), max = Math.max(1f, le.getMaxHealth());
        float frac = MathHelper.clamp(hp / max, 0f, 1f);
        panel(ms, x, y, tw, th);
        text(ms, mc.font, le.getDisplayName().getString(), x + 9, y + 5, WHITE);
        textRight(ms, mc.font, String.format(Locale.ROOT, "%.1f / %.0f", hp, max), x + tw - 7, y + 5, LABEL);
        int bx = x + 9, bw = tw - 18;
        int color = frac > 0.5f ? lerp(YELLOW, GREEN, (frac - 0.5f) * 2f) : lerp(RED, YELLOW, frac * 2f);
        rr(ms, bx, y + 17, bw, 5, 2, 0x50FFFFFF);
        rr(ms, bx, y + 17, Math.max(3, (int) (bw * frac)), 5, 2, color);
    }

    // ================= Прицел =================
    private static void crosshair(MatrixStack ms, Minecraft mc, ClientPlayerEntity p, int w, int h) {
        int cx = w / 2, cy = h / 2;
        float charge = p.getAttackStrengthScale(0f);
        int gap = 3 + Math.round((1f - charge) * 4f);
        int c = mc.crosshairPickEntity != null ? RED : WHITE;
        crosshairShape(ms, cx, cy, gap, Config.crosshairStyle, c);
        if (charge < 1f) {
            int bw = 22, bx = cx - bw / 2, by = cy + 16;
            rr(ms, bx - 1, by - 1, bw + 2, 4, 1, 0x90000000);
            rr(ms, bx, by, Math.max(2, (int) (bw * charge)), 2, 1, lerp(Config.c1(), Config.c2(), charge));
        }
    }

    /** Рисует прицел выбранного стиля (используется и в меню для предпросмотра). */
    static void crosshairShape(MatrixStack ms, int cx, int cy, int gap, int style, int c) {
        int len = 5;
        if (style == 0 || style == 3) {
            tick(ms, cx - gap - len, cy, cx - gap, cy + 1, c);
            tick(ms, cx + gap + 1, cy, cx + gap + len + 1, cy + 1, c);
            if (style == 0) tick(ms, cx, cy - gap - len, cx + 1, cy - gap, c);
            tick(ms, cx, cy + gap + 1, cx + 1, cy + gap + len + 1, c);
            tick(ms, cx, cy, cx + 1, cy + 1, c);
        } else if (style == 1) {
            tick(ms, cx - 1, cy - 1, cx + 2, cy + 2, c);
        } else {
            int r = gap + 3;
            for (int a = 0; a < 360; a += 15) {
                int px = cx + (int) Math.round(Math.cos(Math.toRadians(a)) * r);
                int py = cy + (int) Math.round(Math.sin(Math.toRadians(a)) * r);
                AbstractGui.fill(ms, px, py, px + 1, py + 1, c);
            }
            tick(ms, cx, cy, cx + 1, cy + 1, c);
        }
    }

    private static void tick(MatrixStack ms, int x1, int y1, int x2, int y2, int c) {
        AbstractGui.fill(ms, x1 - 1, y1 - 1, x2 + 1, y2 + 1, 0xA0000000);
        AbstractGui.fill(ms, x1, y1, x2, y2, c);
    }

    // ================= Помощники рисования =================
    static void shadow(MatrixStack ms, int x, int y, int w, int h, int r) {
        rr(ms, x - 2, y - 2, w + 4, h + 4, r + 2, 0x1A000000);
        rr(ms, x - 1, y - 1, w + 2, h + 2, r + 1, 0x28000000);
    }

    static void panel(MatrixStack ms, int x, int y, int w, int h) {
        shadow(ms, x, y, w, h, 4);
        rr(ms, x, y, w, h, 4, panelColor());
        for (int i = 4; i < h - 4; i++) { // тонкая градиентная полоска слева
            AbstractGui.fill(ms, x, y + i, x + 2, y + i + 1, lerp(Config.c1(), Config.c2(), i / (float) h));
        }
    }

    /** Настоящий скруглённый прямоугольник (радиус r). */
    static void rr(MatrixStack ms, int x, int y, int w, int h, int r, int c) {
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            AbstractGui.fill(ms, x, y, x + w, y + h, c);
            return;
        }
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
            AbstractGui.fill(ms, x + inset, y + i, x + w - inset, y + i + 1, c);
            AbstractGui.fill(ms, x + inset, y + h - 1 - i, x + w - inset, y + h - i, c);
        }
        AbstractGui.fill(ms, x, y + r, x + w, y + h - r, c);
    }

    static void round(MatrixStack ms, int x, int y, int w, int h, int c) {
        rr(ms, x, y, w, h, 3, c);
    }

    /** Текст с градиентом акцентных цветов, возвращает x конца текста. */
    static int gradText(MatrixStack ms, FontRenderer f, String s, int x, int y) {
        int cx = x;
        for (int i = 0; i < s.length(); i++) {
            String ch = s.substring(i, i + 1);
            f.drawShadow(ms, ch, (float) cx, (float) y,
                    lerp(Config.c1(), Config.c2(), i / (float) Math.max(1, s.length() - 1)));
            cx += f.width(ch);
        }
        return cx;
    }

    private static void text(MatrixStack ms, FontRenderer f, String s, int x, int y, int color) {
        f.drawShadow(ms, s, (float) x, (float) y, color);
    }

    private static void textRight(MatrixStack ms, FontRenderer f, String s, int xRight, int y, int color) {
        f.drawShadow(ms, s, (float) (xRight - f.width(s)), (float) y, color);
    }

    static int lerp(int a, int b, float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        int r = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int ca = (a >> shift) & 255, cb = (b >> shift) & 255;
            r |= ((int) (ca + (cb - ca) * t) & 255) << shift;
        }
        return r;
    }

    private static void countFps() {
        frames++;
        long now = System.currentTimeMillis();
        if (now - lastFpsTime >= 1000) {
            fps = frames;
            frames = 0;
            lastFpsTime = now;
        }
    }
}
