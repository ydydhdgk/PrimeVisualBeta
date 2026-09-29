package com.example.visuals;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.texture.PotionSpriteUploader;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectInstance;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Hud {
    // ---- Палитра ----
    static final int ACCENT  = 0xFF8B5CFF; // фиолетовый
    static final int ACCENT2 = 0xFF2DD4FF; // голубой
    static final int PANEL   = 0xB8101018; // тёмная панель
    static final int SHADOW  = 0x38000000;
    static final int LABEL   = 0xFFA0A3BD;
    static final int WHITE   = 0xFFFFFFFF;
    static final int GREEN   = 0xFF55FF7A;
    static final int YELLOW  = 0xFFFFD84A;
    static final int RED     = 0xFFFF5566;

    private static int frames, fps;
    private static long lastFpsTime = System.currentTimeMillis();
    private static final float[] KEY_ANIM = new float[7];
    private static final Map<Effect, Integer> MAX_DURATION = new HashMap<>();
    private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    public static void render(MatrixStack ms, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        ClientPlayerEntity p = mc.player;
        if (p == null) return;

        countFps();
        crosshair(ms, mc, p, w, h);
        if (mc.options.renderDebug) return; // не мешаем экрану F3

        int y = 6;
        y = infoPanel(ms, mc, p, 6, y) + 4;
        effects(ms, mc, p, 6, y);
        durability(ms, mc, p, w, h);
        keystrokes(ms, mc, h);
    }

    // ================= Информационная панель =================
    private static int infoPanel(MatrixStack ms, Minecraft mc, ClientPlayerEntity p, int x, int y) {
        int w = 142, h = 44;
        panel(ms, x, y, w, h);
        int fpsColor = fps >= 60 ? GREEN : fps >= 30 ? YELLOW : RED;
        row(ms, mc.font, "FPS", String.valueOf(fps), fpsColor, x, y + 6, w);
        row(ms, mc.font, "XYZ", (int) Math.floor(p.getX()) + "  " + (int) Math.floor(p.getY()) + "  "
                + (int) Math.floor(p.getZ()), WHITE, x, y + 18, w);
        row(ms, mc.font, "Курс", dirName(p) + "  " + Math.round(MathHelper.wrapDegrees(p.yRot)) + "°",
                WHITE, x, y + 30, w);
        return y + h;
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
        int w = 142, h = 22;
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
            AbstractGui.blit(ms, x + 7, y + 3, 0, 12, 12, spr);

            int amp = ef.getAmplifier();
            String name = type.getDisplayName().getString() + " " + (amp < ROMAN.length ? ROMAN[amp] : String.valueOf(amp + 1));
            int secs = dur / 20;
            String time = dur > 32000 ? "∞" : String.format("%d:%02d", secs / 60, secs % 60);
            text(ms, mc.font, name, x + 23, y + 3, type.isBeneficial() ? WHITE : RED);
            textRight(ms, mc.font, time, x + w - 6, y + 3, LABEL);

            // полоска оставшегося времени в цвете эффекта
            int barX = x + 23, barW = w - 30;
            AbstractGui.fill(ms, barX, y + 15, barX + barW, y + 17, 0x40FFFFFF);
            AbstractGui.fill(ms, barX, y + 15, barX + (int) (barW * (dur > 32000 ? 1f : frac)), y + 17,
                    0xFF000000 | type.getColor());
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
                AbstractGui.fill(ms, bx, y + 15, bx + bw, y + 18, 0x50FFFFFF);
                AbstractGui.fill(ms, bx, y + 15, bx + Math.max(1, (int) (bw * frac)), y + 18, color);
            } else {
                text(ms, mc.font, "x" + st.getCount(), x + 28, y + 8, WHITE);
            }
            y += h + gap;
        }
    }

    // ================= Клавиши + CPS =================
    private static void keystrokes(MatrixStack ms, Minecraft mc, int sh) {
        net.minecraft.client.GameSettings o = mc.options;
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
        KEY_ANIM[id] += ((down ? 1f : 0f) - KEY_ANIM[id]) * 0.35f; // плавная анимация нажатия
        float a = KEY_ANIM[id];
        round(ms, x - 1, y - 1, w + 2, h + 2, SHADOW);
        round(ms, x, y, w, h, lerp(PANEL, lerp(ACCENT, ACCENT2, 0.4f) & 0xE0FFFFFF | 0xE0000000, a));
        int textColor = lerp(0xFFD8D9EA, 0xFFFFFFFF, a);
        int tw = mc.font.width(label);
        if (sub == null) {
            mc.font.drawShadow(ms, label, x + (w - tw) / 2f, y + (h - 8) / 2f, textColor);
        } else {
            mc.font.drawShadow(ms, label, x + (w - tw) / 2f, y + 3, textColor);
            mc.font.draw(ms, sub, x + (w - mc.font.width(sub)) / 2f, y + 12, lerp(LABEL, 0xFFFFFFFF, a));
        }
        // тонкая акцентная линия снизу нажатой клавиши
        if (a > 0.05f) AbstractGui.fill(ms, x + 2, y + h - 2, x + w - 2, y + h - 1, lerp(0x00FFFFFF, ACCENT2, a));
    }

    // ================= Прицел =================
    private static void crosshair(MatrixStack ms, Minecraft mc, ClientPlayerEntity p, int w, int h) {
        int cx = w / 2, cy = h / 2;
        float charge = p.getAttackStrengthScale(0f);
        int gap = 3 + Math.round((1f - charge) * 4f); // раскрывается, пока оружие «перезаряжается»
        int len = 5;
        int c = mc.crosshairPickEntity != null ? RED : WHITE;
        tick(ms, cx - gap - len, cy, cx - gap, cy + 1, c);
        tick(ms, cx + gap + 1, cy, cx + gap + len + 1, cy + 1, c);
        tick(ms, cx, cy - gap - len, cx + 1, cy - gap, c);
        tick(ms, cx, cy + gap + 1, cx + 1, cy + gap + len + 1, c);
        tick(ms, cx, cy, cx + 1, cy + 1, c); // точка
        if (charge < 1f) { // полоска перезарядки удара
            int bw = 22, bx = cx - bw / 2, by = cy + 16;
            AbstractGui.fill(ms, bx - 1, by - 1, bx + bw + 1, by + 3, 0x90000000);
            AbstractGui.fill(ms, bx, by, bx + (int) (bw * charge), by + 2, lerp(ACCENT, ACCENT2, charge));
        }
    }

    private static void tick(MatrixStack ms, int x1, int y1, int x2, int y2, int c) {
        AbstractGui.fill(ms, x1 - 1, y1 - 1, x2 + 1, y2 + 1, 0xA0000000); // обводка
        AbstractGui.fill(ms, x1, y1, x2, y2, c);
    }

    // ================= Помощники рисования =================
    private static void panel(MatrixStack ms, int x, int y, int w, int h) {
        round(ms, x - 1, y - 1, w + 2, h + 2, SHADOW);
        round(ms, x, y, w, h, PANEL);
        for (int i = 1; i < h - 1; i++) { // градиентная полоска слева
            AbstractGui.fill(ms, x, y + i, x + 2, y + i + 1, lerp(ACCENT, ACCENT2, i / (float) h));
        }
    }

    /** Прямоугольник со срезанными углами (имитация скругления). */
    private static void round(MatrixStack ms, int x, int y, int w, int h, int c) {
        AbstractGui.fill(ms, x + 1, y, x + w - 1, y + h, c);
        AbstractGui.fill(ms, x, y + 1, x + 1, y + h - 1, c);
        AbstractGui.fill(ms, x + w - 1, y + 1, x + w, y + h - 1, c);
    }

    private static void row(MatrixStack ms, FontRenderer f, String label, String value, int color, int x, int y, int w) {
        text(ms, f, label, x + 9, y, LABEL);
        textRight(ms, f, value, x + w - 7, y, color);
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
