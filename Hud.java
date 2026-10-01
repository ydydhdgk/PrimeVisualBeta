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
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectInstance;
import net.minecraft.util.ResourceLocation;
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

    // идентификаторы перетаскиваемых элементов HUD
    static final int EL_INFO = 0, EL_GRAPH = 1, EL_EFFECTS = 2, EL_DURA = 3, EL_ITEMS = 4, EL_KEYS = 5,
            EL_TARGET = 6, EL_WATER = 7, EL_COMPASS = 8, EL_GPS = 9, EL_COUNT = 10;
    static final String[] EL_NAMES = {"Инфо", "График FPS", "Эффекты", "Прочность", "Предметы",
            "Клавиши", "Цель", "Ватермарка", "Компас", "GPS"};
    private static final int[][] BOUNDS = new int[EL_COUNT][4];
    private static final long[] SEEN = new long[EL_COUNT];

    static long hitTime = 0L;
    private static float gpsShown = 0f;
    private static float shownGap = 3f;
    private static final float[] DURA_SHOWN = new float[6];
    private static LivingEntity lastTarget;
    private static float targetAnim = 0f;
    private static float shownHp = 0f;
    private static String toastText = "";
    private static int toastColor = 0xFFFFFFFF;
    private static long toastTime = 0L;
    private static long lastWarn = 0L;
    private static final int[] HIST = new int[40];
    private static int sampleFrames = 0;
    private static long lastSample = System.currentTimeMillis();

    private static int frames, fps;
    private static long lastFpsTime = System.currentTimeMillis();
    private static float speedSmooth;
    private static final float[] KEY_ANIM = new float[7];
    private static final Map<Effect, Integer> MAX_DURATION = new HashMap<>();
    private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final Item[] TRACKED = {Items.TOTEM_OF_UNDYING, Items.GOLDEN_APPLE, Items.ENDER_PEARL, Items.ARROW};

    private static class Row {
        final String label, value; final int color;
        Row(String l, String v, int c) { label = l; value = v; color = c; }
    }

    static int panelColor() {
        int a = Math.round(MathHelper.clamp(Config.opacity, 0.3f, 1f) * 255f);
        return (a << 24) | 0x101018;
    }

    /** Переливающийся радужный цвет. off - сдвиг по спектру (0..1). */
    static int rainbow(float off) {
        return Config.hsb(((System.currentTimeMillis() % 4000L) / 4000f + off) % 1f, 0.75f, 1f);
    }

    static int crosshairColor(int idx) {
        switch (idx) {
            case 5: return rainbow(0f);
            case 1: return Config.c1();
            case 2: return GREEN;
            case 3: return YELLOW;
            case 4: return 0xFFFF7AD9;
            default: return WHITE;
        }
    }

    /** Цвета рамок хитбоксов: 0 - акцент, дальше готовые. */
    static int hitboxColor(int idx) {
        switch (idx) {
            case 8: return rainbow(0.3f);
            case 1: return 0xFFFFFFFF;
            case 2: return 0xFFFF4D4D;
            case 3: return 0xFF55FF7A;
            case 4: return 0xFFFFD84A;
            case 5: return 0xFF2DD4FF;
            case 6: return 0xFFFF7AD9;
            case 7: return 0xFFFF9F43;
            default: return Config.c1();
        }
    }

    public static void render(MatrixStack ms, int sw, int sh) {
        Minecraft mc = Minecraft.getInstance();
        ClientPlayerEntity p = mc.player;
        if (p == null) return;

        countFps();
        boolean noScreen = mc.screen == null;

        if (noScreen && mc.options.getCameraType().isFirstPerson()) {
            if (Config.ON[Config.CROSSHAIR]) crosshair(ms, mc, p, sw, sh);
            if (Config.ON[Config.HITMARKER]) hitMarker(ms, sw / 2, sh / 2);
        }
        if (Config.hudHidden) {
            RenderSystem.pushMatrix();
            try {
                drawToast(ms, mc, sw, sh);
            } finally {
                RenderSystem.popMatrix();
            }
            return;
        }
        if (mc.options.renderDebug) return;

        float s = MathHelper.clamp(Config.scale, 0.5f, 2f);
        int w = Math.round(sw / s), h = Math.round(sh / s);
        RenderSystem.pushMatrix();
        RenderSystem.scalef(s, s, 1f);
        try {
            if (Config.ON[Config.WARN]) warnings(p);
            int ix = Config.infoRight ? w - IW - 6 : 6;
            int y = 6;
            beginEl(EL_INFO);
            y = infoPanel(ms, mc, p, ix, y);
            endEl();
            if (Config.ON[Config.FPSGRAPH]) {
                beginEl(EL_GRAPH);
                y = fpsGraph(ms, mc, ix, y);
                endEl();
            }
            if (Config.ON[Config.GPS]) {
                beginEl(EL_GPS);
                y = gps(ms, mc, p, ix, y);
                endEl();
            }
            if (Config.ON[Config.EFFECTS]) {
                beginEl(EL_EFFECTS);
                effects(ms, mc, p, ix, y);
                endEl();
            }
            if (Config.ON[Config.DURABILITY]) {
                beginEl(EL_DURA);
                durability(ms, mc, p, w, h);
                endEl();
            }
            if (Config.ON[Config.ITEMS]) {
                beginEl(EL_ITEMS);
                itemCounter(ms, mc, p, w, h);
                endEl();
            }
            if (Config.ON[Config.KEYS]) {
                beginEl(EL_KEYS);
                keystrokes(ms, mc, h);
                endEl();
            }
            int topY = 6;
            if (Config.ON[Config.COMPASS]) {
                beginEl(EL_COMPASS);
                compass(ms, mc, p, w);
                endEl();
                topY = 6 + 24;
            }
            if (noScreen && Config.ON[Config.TARGET]) {
                beginEl(EL_TARGET);
                target(ms, mc, w, topY);
                endEl();
            }
            if (Config.ON[Config.WATERMARK]) {
                beginEl(EL_WATER);
                watermark(ms, mc, w);
                endEl();
            }
            drawToast(ms, mc, w, h);
        } finally {
            RenderSystem.popMatrix();
        }

        if (noScreen && ClientEvents.zoomFactor() < 0.98f) {
            String z = String.format(Locale.ROOT, "Зум ×%.1f", 1f / ClientEvents.zoomFactor());
            mc.font.drawShadow(ms, z, sw / 2f - mc.font.width(z) / 2f, sh / 2f + 26, WHITE);
        }
    }

    // ================= Ватермарка =================
    private static void watermark(MatrixStack ms, Minecraft mc, int w) {
        FontRenderer f = mc.font;
        String name = Config.wmText.trim().isEmpty() ? "PrimeVisual" : Config.wmText.trim();
        String rest = (Config.wmFps ? "  |  " + fps + " fps" : "")
                + (Config.wmTime ? "  |  " + LocalTime.now().format(TIME_FMT) : "");
        int tw = f.width(name) + f.width(rest);
        int pw = tw + 18, ph = 17;
        int x = Config.infoRight ? 6 : w - pw - 6, y = 6;
        mark(EL_WATER, x, y, pw, ph);
        surface(ms, x, y, pw, ph, Config.radius, panelColor(), true);
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
        if (Config.ON[Config.BIOME] && mc.level != null) {
            ResourceLocation rl = mc.level.getBiome(p.blockPosition()).getRegistryName();
            String b = rl == null ? "?" : rl.getPath().replace('_', ' ');
            if (!b.isEmpty()) b = Character.toUpperCase(b.charAt(0)) + b.substring(1);
            rows.add(new Row("Биом", b, WHITE));
        }
        if (Config.ON[Config.GAMETIME] && mc.level != null) {
            long t = mc.level.getDayTime() % 24000L;
            int hours = (int) ((t / 1000L + 6L) % 24L);
            int minutes = (int) ((t % 1000L) * 60L / 1000L);
            rows.add(new Row("Мир. время", String.format(Locale.ROOT, "%02d:%02d", hours, minutes), WHITE));
        }
        if (Config.ON[Config.WEATHER] && mc.level != null) {
            boolean thunder = mc.level.isThundering(), rain = mc.level.isRaining();
            rows.add(new Row("Погода", thunder ? "Гроза" : rain ? "Дождь" : "Ясно",
                    thunder ? RED : rain ? YELLOW : GREEN));
        }
        if (Config.ON[Config.HUNGER]) {
            int food = p.getFoodData().getFoodLevel();
            rows.add(new Row("Голод", food + " / 20", food <= 6 ? RED : food <= 12 ? YELLOW : GREEN));
        }
        if (Config.ON[Config.TIME]) {
            rows.add(new Row("Время", LocalTime.now().format(TIME_FMT), WHITE));
        }
        if (rows.isEmpty()) return y;

        int hgt = 8 + rows.size() * 12;
        panel(ms, x, y, IW, hgt);
        mark(EL_INFO, x, y, IW, hgt);
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
        int startY = y;
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
        if (y > startY) mark(EL_EFFECTS, x, startY, w, y - startY - 3);
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
        if (!items.isEmpty()) mark(EL_DURA, x, y, w, items.size() * (h + gap) - gap);
        int di = 0;
        for (ItemStack st : items) {
            final int slot = Math.min(di++, DURA_SHOWN.length - 1);
            panel(ms, x, y, w, h);
            mc.getItemRenderer().renderGuiItem(st, x + 7, y + 4);
            if (st.isDamageableItem()) {
                int max = st.getMaxDamage();
                int left = max - st.getDamageValue();
                float actual = left / (float) max;
                DURA_SHOWN[slot] += (actual - DURA_SHOWN[slot]) * 0.2f;
                float frac = DURA_SHOWN[slot];
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

    // ================= Счётчик предметов (справа по центру) =================
    private static void itemCounter(MatrixStack ms, Minecraft mc, ClientPlayerEntity p, int w, int h) {
        int[] counts = new int[TRACKED.length];
        for (int i = 0; i < p.inventory.getContainerSize(); i++) {
            ItemStack st = p.inventory.getItem(i);
            for (int k = 0; k < TRACKED.length; k++) {
                if (st.getItem() == TRACKED[k]) counts[k] += st.getCount();
            }
        }
        int n = 0;
        for (int c : counts) if (c > 0) n++;
        if (n == 0) return;
        int pw = 48, ph = 22, gap = 3;
        int x = w - pw - 6;
        int y = h / 2 - (n * (ph + gap) - gap) / 2;
        mark(EL_ITEMS, x, y, pw, n * (ph + gap) - gap);
        for (int k = 0; k < TRACKED.length; k++) {
            if (counts[k] <= 0) continue;
            panel(ms, x, y, pw, ph);
            mc.getItemRenderer().renderGuiItem(new ItemStack(TRACKED[k]), x + 6, y + 3);
            text(ms, mc.font, "x" + counts[k], x + 26, y + 7, WHITE);
            y += ph + gap;
        }
    }

    // ================= Клавиши + CPS =================
    private static void keystrokes(MatrixStack ms, Minecraft mc, int sh) {
        GameSettings o = mc.options;
        int s = MathHelper.clamp(Config.keysSize, 20, 30), g = 2;
        int rows = 2 + (Config.keysMouse ? 1 : 0) + (Config.keysSpace ? 1 : 0);
        int hgt = s * rows + g * (rows - 1);
        int x = 6, y = sh / 2 - hgt;
        mark(EL_KEYS, x, y, s * 3 + g * 2, hgt);
        key(ms, mc, 0, "W", null, x + s + g, y, s, s, o.keyUp.isDown());
        y += s + g;
        key(ms, mc, 1, "A", null, x, y, s, s, o.keyLeft.isDown());
        key(ms, mc, 2, "S", null, x + s + g, y, s, s, o.keyDown.isDown());
        key(ms, mc, 3, "D", null, x + 2 * (s + g), y, s, s, o.keyRight.isDown());
        y += s + g;
        if (Config.keysMouse) {
            int bw = (s * 3 + g * 2 - g) / 2;
            key(ms, mc, 4, "ЛКМ", ClientEvents.cps(true) + " CPS", x, y, bw, s, o.keyAttack.isDown());
            key(ms, mc, 5, "ПКМ", ClientEvents.cps(false) + " CPS", x + bw + g, y, bw, s, o.keyUse.isDown());
            y += s + g;
        }
        if (Config.keysSpace) {
            key(ms, mc, 6, "ПРОБЕЛ", null, x, y, s * 3 + g * 2, s, o.keyJump.isDown());
        }
    }

    private static void key(MatrixStack ms, Minecraft mc, int id, String label, String sub,
                            int x, int y, int w, int h, boolean down) {
        KEY_ANIM[id] += ((down ? 1f : 0f) - KEY_ANIM[id]) * 0.35f;
        float a = KEY_ANIM[id];
        int pressed = (lerp(Config.c1(), Config.c2(), 0.4f) & 0x00FFFFFF) | 0xE0000000;
        if (Config.glass) {
            surface(ms, x, y, w, h, Config.radius, panelColor(), false);
            if (a > 0.02f) rr(ms, x, y, w, h, Config.radius, (pressed & 0x00FFFFFF) | ((int) (0x99 * a) << 24));
        } else {
            shadow(ms, x, y, w, h, Config.radius);
            rr(ms, x, y, w, h, Config.radius, lerp(panelColor(), pressed, a));
        }
        int textColor = lerp(0xFFD8D9EA, 0xFFFFFFFF, a);
        int tw = mc.font.width(label);
        if (sub == null) {
            mc.font.drawShadow(ms, label, x + (w - tw) / 2f, y + (h - 8) / 2f, textColor);
        } else {
            mc.font.drawShadow(ms, label, x + (w - tw) / 2f, y + 3, textColor);
            mc.font.draw(ms, sub, x + (w - mc.font.width(sub)) / 2f, y + 12, lerp(LABEL, 0xFFFFFFFF, a));
        }
    }

    // ================= Инфо о цели (плавное появление, здоровье, экипировка) =================
    private static void target(MatrixStack ms, Minecraft mc, int w, int topY) {
        LivingEntity cur = mc.crosshairPickEntity instanceof LivingEntity ? (LivingEntity) mc.crosshairPickEntity : null;
        if (cur != null) {
            if (cur != lastTarget) {
                lastTarget = cur;
                shownHp = cur.getHealth();
            }
            targetAnim += (1f - targetAnim) * 0.25f;
        } else {
            targetAnim += (0f - targetAnim) * 0.2f;
        }
        if (lastTarget == null || targetAnim < 0.03f) return;
        LivingEntity le = lastTarget;

        List<ItemStack> gear = new ArrayList<>();
        gear.add(le.getItemBySlot(EquipmentSlotType.HEAD));
        gear.add(le.getItemBySlot(EquipmentSlotType.CHEST));
        gear.add(le.getItemBySlot(EquipmentSlotType.LEGS));
        gear.add(le.getItemBySlot(EquipmentSlotType.FEET));
        gear.add(le.getMainHandItem());
        gear.removeIf(ItemStack::isEmpty);
        int tw = 150, th = 27 + (gear.isEmpty() ? 0 : 24);
        int x = (w - tw) / 2, y = topY;
        float hp = le.getHealth(), max = Math.max(1f, le.getMaxHealth());
        shownHp += (hp - shownHp) * 0.15f;
        float frac = MathHelper.clamp(shownHp / max, 0f, 1f);

        RenderSystem.pushMatrix();
        RenderSystem.translatef(0f, -(1f - targetAnim) * 24f, 0f);
        try {
            panel(ms, x, y, tw, th);
            mark(EL_TARGET, x, y, tw, th);
            text(ms, mc.font, le.getDisplayName().getString(), x + 9, y + 5, WHITE);
            textRight(ms, mc.font, String.format(Locale.ROOT, "%.1f / %.0f", hp, max), x + tw - 7, y + 5, LABEL);
            int bx = x + 9, bw = tw - 18;
            int color = frac > 0.5f ? lerp(YELLOW, GREEN, (frac - 0.5f) * 2f) : lerp(RED, YELLOW, frac * 2f);
            rr(ms, bx, y + 17, bw, 5, 2, 0x50FFFFFF);
            rr(ms, bx, y + 17, Math.max(3, (int) (bw * frac)), 5, 2, color);
            int gx = x + 9;
            for (ItemStack st : gear) {
                mc.getItemRenderer().renderGuiItem(st, gx, y + 27);
                if (st.isDamageableItem()) {
                    float fr = (st.getMaxDamage() - st.getDamageValue()) / (float) st.getMaxDamage();
                    int col = fr > 0.5f ? lerp(YELLOW, GREEN, (fr - 0.5f) * 2f) : lerp(RED, YELLOW, fr * 2f);
                    rr(ms, gx, y + 44, 16, 3, 1, 0x50FFFFFF);
                    rr(ms, gx, y + 44, Math.max(2, (int) (16 * fr)), 3, 1, col);
                }
                gx += 22;
            }
        } finally {
            RenderSystem.popMatrix();
        }
    }

    // ================= Прицел и хит-маркер =================
    private static void crosshair(MatrixStack ms, Minecraft mc, ClientPlayerEntity p, int w, int h) {
        int cx = w / 2, cy = h / 2;
        float charge = p.getAttackStrengthScale(0f);
        shownGap += ((3f + (1f - charge) * 4f) - shownGap) * 0.35f;
        int gap = Math.round(shownGap);
        int c = mc.crosshairPickEntity != null ? RED : crosshairColor(Config.crosshairColor);
        crosshairShape(ms, cx, cy, gap, Config.crosshairSize, Config.crosshairStyle, c);
        if (charge < 1f) {
            int bw = 22, bx = cx - bw / 2, by = cy + 16;
            rr(ms, bx - 1, by - 1, bw + 2, 4, 1, 0x90000000);
            rr(ms, bx, by, Math.max(2, (int) (bw * charge)), 2, 1, lerp(Config.c1(), Config.c2(), charge));
        }
    }

    private static void hitMarker(MatrixStack ms, int cx, int cy) {
        long dt = System.currentTimeMillis() - hitTime;
        if (dt < 0 || dt > 250) return;
        int a = (int) (255 * (1f - dt / 250f));
        int col = (a << 24) | 0xFFFFFF;
        for (int i = 3; i <= 8; i++) {
            AbstractGui.fill(ms, cx + i, cy + i, cx + i + 1, cy + i + 1, col);
            AbstractGui.fill(ms, cx - i, cy - i, cx - i + 1, cy - i + 1, col);
            AbstractGui.fill(ms, cx + i, cy - i, cx + i + 1, cy - i + 1, col);
            AbstractGui.fill(ms, cx - i, cy + i, cx - i + 1, cy + i + 1, col);
        }
    }

    /** Рисует прицел выбранного стиля (используется и в меню для предпросмотра). */
    static void crosshairShape(MatrixStack ms, int cx, int cy, int gap, int len, int style, int c) {
        int t = Math.max(1, Config.crosshairThick), o = t / 2;
        if (style == 0 || style == 3) {
            tick(ms, cx - gap - len, cy - o, cx - gap, cy - o + t, c);
            tick(ms, cx + gap + 1, cy - o, cx + gap + len + 1, cy - o + t, c);
            if (style == 0) tick(ms, cx - o, cy - gap - len, cx - o + t, cy - gap, c);
            tick(ms, cx - o, cy + gap + 1, cx - o + t, cy + gap + len + 1, c);
            tick(ms, cx - o, cy - o, cx - o + t, cy - o + t, c);
        } else if (style == 1) {
            tick(ms, cx - 1 - o, cy - 1 - o, cx + 2 + o, cy + 2 + o, c);
        } else {
            int r = gap + 1 + len / 2;
            for (int a = 0; a < 360; a += 15) {
                int px = cx + (int) Math.round(Math.cos(Math.toRadians(a)) * r);
                int py = cy + (int) Math.round(Math.sin(Math.toRadians(a)) * r);
                AbstractGui.fill(ms, px - o, py - o, px - o + t, py - o + t, c);
            }
            tick(ms, cx - o, cy - o, cx - o + t, cy - o + t, c);
        }
    }

    private static void tick(MatrixStack ms, int x1, int y1, int x2, int y2, int c) {
        if (Config.crosshairOutline) AbstractGui.fill(ms, x1 - 1, y1 - 1, x2 + 1, y2 + 1, 0xA0000000);
        AbstractGui.fill(ms, x1, y1, x2, y2, c);
    }

    // ================= Управление позициями элементов =================
    private static void beginEl(int id) {
        RenderSystem.pushMatrix();
        RenderSystem.translatef((float) Config.offX[id], (float) Config.offY[id], 0f);
    }

    private static void endEl() {
        RenderSystem.popMatrix();
    }

    private static void mark(int id, int x, int y, int w, int h) {
        BOUNDS[id][0] = x + Config.offX[id];
        BOUNDS[id][1] = y + Config.offY[id];
        BOUNDS[id][2] = w;
        BOUNDS[id][3] = h;
        SEEN[id] = System.currentTimeMillis();
    }

    /** Границы элемента (в координатах масштабированного HUD), либо null, если он сейчас не нарисован. */
    static int[] bounds(int id) {
        if (System.currentTimeMillis() - SEEN[id] > 600L) return null;
        return BOUNDS[id];
    }

    // ================= GPS-метка =================
    private static int gps(MatrixStack ms, Minecraft mc, ClientPlayerEntity p, int x, int y) {
        int gx, gz;
        try {
            gx = Integer.parseInt(Config.gpsX.trim());
            gz = Integer.parseInt(Config.gpsZ.trim());
        } catch (Exception e) {
            return y;
        }
        double dx = gx + 0.5 - p.getX(), dz = gz + 0.5 - p.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        float bearing = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float rel = MathHelper.wrapDegrees(bearing - p.yRot);
        int w = IW, h = 30;
        panel(ms, x, y, w, h);
        mark(EL_GPS, x, y, w, h);
        int cx0 = x + 22, cy0 = y + h / 2;
        for (int a = 0; a < 360; a += 20) {
            int px = cx0 + (int) Math.round(Math.cos(Math.toRadians(a)) * 10);
            int py = cy0 + (int) Math.round(Math.sin(Math.toRadians(a)) * 10);
            AbstractGui.fill(ms, px, py, px + 1, py + 1, 0x55FFFFFF);
        }
        float dr = MathHelper.wrapDegrees(rel - gpsShown);
        gpsShown = MathHelper.wrapDegrees(gpsShown + dr * 0.2f);
        double rad = Math.toRadians(gpsShown);
        float vx = (float) Math.sin(rad), vy = (float) -Math.cos(rad);
        for (int i = 0; i <= 8; i++) {
            int px = cx0 + Math.round(vx * i), py = cy0 + Math.round(vy * i);
            AbstractGui.fill(ms, px, py, px + 2, py + 2, i >= 6 ? Config.c2() : WHITE);
        }
        boolean arrived = dist < 3.0;
        text(ms, mc.font, "GPS", x + 42, y + 6, LABEL);
        text(ms, mc.font, arrived ? "Прибыли!" : Math.round(dist) + " бл", x + 42, y + 17, arrived ? GREEN : WHITE);
        textRight(ms, mc.font, gx + " " + gz, x + w - 7, y + 6, LABEL);
        return y + h + 4;
    }

    // ================= Компас =================
    private static void compass(MatrixStack ms, Minecraft mc, ClientPlayerEntity p, int w) {
        int cw = 170, ch = 18;
        int x = (w - cw) / 2, y = 6;
        mark(EL_COMPASS, x, y, cw, ch);
        panel(ms, x, y, cw, ch);
        float yaw = MathHelper.wrapDegrees(p.yRot);
        String[] names = {"Ю", "ЮЗ", "З", "СЗ", "С", "СВ", "В", "ЮВ"};
        int mid = x + cw / 2;
        for (int k = 0; k < 24; k++) { // мелкие деления через каждые 15 градусов
            if (k % 3 == 0) continue;
            float d = MathHelper.wrapDegrees((float) (k * 15) - yaw);
            if (Math.abs(d) >= 80f) continue;
            int px = mid + Math.round(d * (cw / 2f - 8f) / 80f);
            AbstractGui.fill(ms, px, y + ch - 5, px + 1, y + ch - 2, 0x66FFFFFF);
        }
        for (int i = 0; i < 8; i++) {
            float d = MathHelper.wrapDegrees((float) (i * 45) - yaw);
            if (Math.abs(d) >= 80f) continue;
            int px = mid + Math.round(d * (cw / 2f - 8f) / 80f);
            boolean cardinal = i % 2 == 0;
            mc.font.drawShadow(ms, names[i], px - mc.font.width(names[i]) / 2f, (float) (y + 4),
                    cardinal ? WHITE : LABEL);
        }
        AbstractGui.fill(ms, mid - 1, y + ch - 3, mid + 2, y + ch - 1, Config.c2());
        AbstractGui.fill(ms, mid, y + ch - 4, mid + 1, y + ch - 3, Config.c2());
    }

    // ================= График FPS =================
    private static int fpsGraph(MatrixStack ms, Minecraft mc, int x, int y) {
        int w = IW, h = 38;
        panel(ms, x, y, w, h);
        mark(EL_GRAPH, x, y, w, h);
        text(ms, mc.font, "График FPS", x + 9, y + 4, LABEL);
        int max = 60;
        for (int v : HIST) max = Math.max(max, v);
        int base = y + h - 5, maxH = 18, bx = x + 9;
        for (int i = 0; i < HIST.length; i++) {
            int v = HIST[i];
            int bh = Math.max(1, Math.round(v * maxH / (float) max));
            int col = v >= 60 ? GREEN : v >= 30 ? YELLOW : RED;
            AbstractGui.fill(ms, bx + i * 3, base - bh, bx + i * 3 + 2, base, col);
        }
        return y + h + 4;
    }

    // ================= Уведомления и предупреждения =================
    static void toast(String text, int color) {
        toastText = text;
        toastColor = color;
        toastTime = System.currentTimeMillis();
    }

    private static void warnings(ClientPlayerEntity p) {
        long now = System.currentTimeMillis();
        if (now - lastWarn < 15000L) return;
        float hp = p.getHealth();
        if (hp > 0f && hp <= 6f) {
            toast("Мало здоровья!", RED);
            lastWarn = now;
            return;
        }
        EquipmentSlotType[] slots = {EquipmentSlotType.HEAD, EquipmentSlotType.CHEST,
                EquipmentSlotType.LEGS, EquipmentSlotType.FEET};
        for (EquipmentSlotType slot : slots) {
            ItemStack st = p.getItemBySlot(slot);
            if (!st.isEmpty() && st.isDamageableItem()) {
                float frac = (st.getMaxDamage() - st.getDamageValue()) / (float) st.getMaxDamage();
                if (frac < 0.1f) {
                    toast("Броня почти сломана!", YELLOW);
                    lastWarn = now;
                    return;
                }
            }
        }
    }

    private static void drawToast(MatrixStack ms, Minecraft mc, int w, int h) {
        long dt = System.currentTimeMillis() - toastTime;
        if (toastText.isEmpty() || dt > 2400L) return;
        float a = dt < 200L ? dt / 200f : dt > 2000L ? (2400L - dt) / 400f : 1f;
        a = MathHelper.clamp(a, 0f, 1f);
        if (a < 0.05f) return;
        FontRenderer f = mc.font;
        int tw = f.width(toastText) + 24, th = 18;
        int x = (w - tw) / 2;
        int y = h - 72 - Math.round((1f - Math.min(1f, dt / 200f)) * 8f);
        surface(ms, x, y, tw, th, Config.radius, panelColor(), false);
        int alpha = ((int) (255 * a)) << 24;
        rr(ms, x + 5, y + 4, 2, th - 8, 1, (toastColor & 0x00FFFFFF) | alpha);
        f.drawShadow(ms, toastText, (float) (x + 13), (float) (y + 5), (toastColor & 0x00FFFFFF) | alpha);
    }

    // ================= Liquid Glass =================
    static int glassAlpha() {
        return Math.round(MathHelper.clamp(Config.opacity, 0.3f, 1f) * 90f);
    }

    /** Универсальная поверхность: обычная тёмная или стеклянная. */
    static void surface(MatrixStack ms, int x, int y, int w, int h, int r, int color, boolean accentLine) {
        if (Config.glass) {
            glassRectA(ms, x, y, w, h, Math.max(r, 3), accentLine, glassAlpha());
            return;
        }
        shadow(ms, x, y, w, h, r);
        rr(ms, x, y, w, h, r, color);
        if (accentLine && w > 12) {
            for (int i = 6; i < w - 6; i += 2) {
                AbstractGui.fill(ms, x + i, y + h - 1, Math.min(x + i + 2, x + w - 6), y + h, grad(i / (float) w));
            }
        }
    }

    private static int insetOf(int r, int i) {
        double dy = r - i - 0.5;
        return (int) Math.round(r - Math.sqrt(r * r - dy * dy));
    }

    /** Скруглённый прямоугольник с вертикальным градиентом. */
    static void gradRR(MatrixStack ms, int x, int y, int w, int h, int r, int top, int bottom) {
        r = Math.min(r, Math.min(w, h) / 2);
        for (int j = 0; j < h; j++) {
            int inset = 0;
            if (r > 0) {
                if (j < r) inset = insetOf(r, j);
                else if (j >= h - r) inset = insetOf(r, h - 1 - j);
            }
            AbstractGui.fill(ms, x + inset, y + j, x + w - inset, y + j + 1,
                    lerp(top, bottom, j / (float) Math.max(1, h - 1)));
        }
    }

    /** Тонкая скруглённая рамка в 1 пиксель. */
    static void rrOutline(MatrixStack ms, int x, int y, int w, int h, int r, int c) {
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            AbstractGui.fill(ms, x, y, x + w, y + 1, c);
            AbstractGui.fill(ms, x, y + h - 1, x + w, y + h, c);
            AbstractGui.fill(ms, x, y + 1, x + 1, y + h - 1, c);
            AbstractGui.fill(ms, x + w - 1, y + 1, x + w, y + h - 1, c);
            return;
        }
        int prev = r;
        for (int i = 0; i < r; i++) {
            int inset = insetOf(r, i);
            if (i == 0) {
                AbstractGui.fill(ms, x + inset, y, x + w - inset, y + 1, c);
                AbstractGui.fill(ms, x + inset, y + h - 1, x + w - inset, y + h, c);
            } else {
                int end = Math.max(inset + 1, prev);
                AbstractGui.fill(ms, x + inset, y + i, x + end, y + i + 1, c);
                AbstractGui.fill(ms, x + w - end, y + i, x + w - inset, y + i + 1, c);
                AbstractGui.fill(ms, x + inset, y + h - 1 - i, x + end, y + h - i, c);
                AbstractGui.fill(ms, x + w - end, y + h - 1 - i, x + w - inset, y + h - i, c);
            }
            prev = inset;
        }
        AbstractGui.fill(ms, x, y + r, x + 1, y + h - r, c);
        AbstractGui.fill(ms, x + w - 1, y + r, x + w, y + h - r, c);
    }

    /** Стеклянная плашка: матовый градиент, яркая кромка, бегущий блик. a - плотность (0..255). */
    static void glassRectA(MatrixStack ms, int x, int y, int w, int h, int r, boolean accent, int a) {
        r = Math.min(r, Math.min(w, h) / 2);
        a = MathHelper.clamp(a, 0, 255);
        rr(ms, x - 1, y - 1, w + 2, h + 2, r + 1, 0x20000000);
        int topC = (Math.min(255, a + 28) << 24) | 0x56668C;
        int botC = (a << 24) | 0x0C1120;
        gradRR(ms, x, y, w, h, r, topC, botC);
        rrOutline(ms, x, y, w, h, r, 0x5CFFFFFF);
        if (w > 2 * r + 4) {
            AbstractGui.fill(ms, x + r, y + 1, x + w - r, y + 2, 0x24FFFFFF);
            int len = Math.max(14, w / 5);
            float ph = (System.currentTimeMillis() % 4200L) / 4200f;
            int start = x + r + Math.round((w - 2 * r + len) * ph) - len;
            int s0 = Math.max(start, x + r), s1 = Math.min(start + len, x + w - r);
            if (s1 > s0) {
                AbstractGui.fill(ms, s0, y, s1, y + 1, 0xCCFFFFFF);
                AbstractGui.fill(ms, s0, y + 1, s1, y + 2, 0x30FFFFFF);
            }
            if (accent) {
                for (int i = r; i < w - r; i += 2) {
                    AbstractGui.fill(ms, x + i, y + h - 2, Math.min(x + i + 2, x + w - r), y + h - 1,
                            (grad(i / (float) w) & 0x00FFFFFF) | 0x99000000);
                }
            }
        }
    }

    // ================= Помощники рисования =================
    static void shadow(MatrixStack ms, int x, int y, int w, int h, int r) {
        rr(ms, x - 2, y - 2, w + 4, h + 4, r + 2, 0x1A000000);
        rr(ms, x - 1, y - 1, w + 2, h + 2, r + 1, 0x28000000);
    }

    static void panel(MatrixStack ms, int x, int y, int w, int h) {
        int r = Config.radius;
        if (Config.glass) {
            glassRectA(ms, x, y, w, h, Math.max(r, 3), true, glassAlpha());
            return;
        }
        shadow(ms, x, y, w, h, r);
        rr(ms, x, y, w, h, r, panelColor());
        for (int i = Math.max(r, 2); i < h - Math.max(r, 2); i++) {
            AbstractGui.fill(ms, x, y + i, x + 2, y + i + 1, grad(i / (float) h));
        }
    }

    /** Скруглённый прямоугольник (радиус r). */
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

    /** Цвет градиента акцента в точке pos (0..1); при включённом «переливании» плавно смещается во времени. */
    static int grad(float pos) {
        float k = pos;
        if (Config.ON[Config.ANIMATE]) {
            float phase = (System.currentTimeMillis() % 3000L) / 3000f;
            float q = (pos + phase) % 1f;
            k = q < 0.5f ? q * 2f : (1f - q) * 2f;
        }
        return lerp(Config.c1(), Config.c2(), k);
    }

    /** Переливающийся текст: градиент плюс бегущий блик. Возвращает x конца текста. */
    static int gradText(MatrixStack ms, FontRenderer f, String s, int x, int y) {
        int cx = x;
        int n = s.length();
        float sweep = (System.currentTimeMillis() % 2200L) / 2200f * 1.5f - 0.25f;
        for (int i = 0; i < n; i++) {
            String ch = s.substring(i, i + 1);
            float pos = i / (float) Math.max(1, n - 1);
            int col = grad(pos);
            if (Config.ON[Config.ANIMATE]) {
                float shine = Math.max(0f, 1f - Math.abs(pos - sweep) * 5f);
                col = lerp(col, 0xFFFFFFFF, shine * 0.75f);
            }
            f.drawShadow(ms, ch, (float) cx, (float) y, col);
            cx += f.width(ch);
        }
        return cx;
    }

    private static void text(MatrixStack ms, FontRenderer f, String s, int x, int y, int color) {
        if (Config.textShadow) f.drawShadow(ms, s, (float) x, (float) y, color);
        else f.draw(ms, s, (float) x, (float) y, color);
    }

    private static void textRight(MatrixStack ms, FontRenderer f, String s, int xRight, int y, int color) {
        if (Config.textShadow) f.drawShadow(ms, s, (float) (xRight - f.width(s)), (float) y, color);
        else f.draw(ms, s, (float) (xRight - f.width(s)), (float) y, color);
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
        sampleFrames++;
        long now = System.currentTimeMillis();
        if (now - lastSample >= 200) {
            System.arraycopy(HIST, 1, HIST, 0, HIST.length - 1);
            HIST[HIST.length - 1] = sampleFrames * 5;
            sampleFrames = 0;
            lastSample = now;
        }
        if (now - lastFpsTime >= 1000) {
            fps = frames;
            frames = 0;
            lastFpsTime = now;
        }
    }
}
