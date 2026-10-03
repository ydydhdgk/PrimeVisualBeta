package com.example.visuals;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.SimpleSound;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.vector.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Эффекты при ударе (доллары, амогусы, айфоны), звуки и экранные эффекты. */
public class Fx {
    private static final String[] TEX = {"dollar", "amogus", "iphone", "coin", "diamond"};
    private static final ResourceLocation[] RL = new ResourceLocation[TEX.length];
    static final String[] SND = {"hit_pay", "hit_cash", "hit_pop", "hit_boing", "hit_bell"};
    private static final Random R = new Random();

    static {
        for (int i = 0; i < TEX.length; i++) RL[i] = new ResourceLocation("visuals", "textures/fx/" + TEX[i] + ".png");
    }

    private static class P {
        float x, y, vx, vy, rot, vr, size, life;
        int type, color;
        String text;
        long born;
    }

    private static final List<P> LIST = new ArrayList<>();
    private static long lastFrame = System.currentTimeMillis();
    private static LivingEntity watched;
    private static float lastHp;
    static float reach;
    static long reachAt, comboAt, shakeAt;
    static int combo, kills;

    /** Режим 0 доллары, 1 амогусы, 2 айфоны, 3 микс, 4 монеты, 5 алмазы, 6 золото (секрет). */
    private static int pick(int mode) {
        switch (mode) {
            case 0: return 0;
            case 1: return 1;
            case 2: return 2;
            case 4: return 3;
            case 5: return 4;
            case 6: return 3 + R.nextInt(2);
            default: return R.nextInt(3);
        }
    }

    static void burst(Minecraft mc, int count) {
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        long now = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            P p = new P();
            p.x = w / 2f;
            p.y = h / 2f;
            double a = R.nextDouble() * Math.PI * 2.0;
            float sp = 50f + R.nextFloat() * 150f;
            p.vx = (float) Math.cos(a) * sp;
            p.vy = (float) Math.sin(a) * sp - 70f;
            p.rot = R.nextFloat() * 360f;
            p.vr = (R.nextFloat() - 0.5f) * 400f;
            p.size = (14f + R.nextFloat() * 10f) * Config.fxSize;
            p.type = pick(Config.fxType);
            p.born = now;
            p.life = 700f + R.nextFloat() * 500f;
            LIST.add(p);
        }
        while (LIST.size() > 160) LIST.remove(0);
    }

    static void playSound(Minecraft mc, float pitch) {
        if (mc == null) return;
        try {
            ISound s = new SimpleSound(new ResourceLocation("visuals", SND[Config.hitSound]), SoundCategory.PLAYERS,
                    Config.hitVolume, pitch, false, 0, ISound.AttenuationType.NONE, 0.0, 0.0, 0.0, true);
            mc.getSoundManager().play(s);
        } catch (Exception ignored) {
        }
    }

    static void onHit(Minecraft mc) {
        long now = System.currentTimeMillis();
        try {
            if (mc.hitResult != null && mc.player != null) {
                reach = (float) mc.player.getEyePosition(1f).distanceTo(mc.hitResult.getLocation());
                reachAt = now;
            }
        } catch (Exception ignored) {
        }
        combo = (now - comboAt < 2000L) ? combo + 1 : 1;
        comboAt = now;
        shakeAt = now;
        if (Config.ON[Config.HITFX]) burst(mc, Config.fxCount);
        if (Config.ON[Config.HITSND]) playSound(mc, 1f);
    }

    static void onKill(Minecraft mc) {
        kills++;
        if (Config.ON[Config.KILLFX]) burst(mc, Config.fxCount * 2 + 4);
        if (Config.ON[Config.HITSND]) playSound(mc, 1.25f);
    }

    static void floatText(Minecraft mc, String text, int color) {
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        P p = new P();
        p.text = text;
        p.color = color;
        p.x = w / 2f + (R.nextFloat() - 0.5f) * 50f;
        p.y = h / 2f - 12f;
        p.vx = (R.nextFloat() - 0.5f) * 20f;
        p.vy = -50f;
        p.life = 900f;
        p.born = System.currentTimeMillis();
        LIST.add(p);
    }

    private static String fmt(float v) {
        return v >= 10f ? String.valueOf(Math.round(v)) : String.format(Locale.ROOT, "%.1f", v);
    }

    /** Отслеживание цели: победы, цифры урона, сброс комбо (каждый тик). */
    static void track(Minecraft mc) {
        boolean need = Config.ON[Config.KILLFX] || Config.ON[Config.DMGNUM] || Config.ON[Config.KILLS];
        if (!need || mc.player == null) {
            watched = null;
            return;
        }
        if (mc.player.hurtTime > 0) combo = 0;
        Entity pick = mc.crosshairPickEntity;
        if (pick instanceof LivingEntity && pick != mc.player && pick != watched) {
            watched = (LivingEntity) pick;
            lastHp = watched.getHealth();
        }
        if (watched == null) return;
        float hp = watched.getHealth();
        float d = lastHp - hp;
        if (Config.ON[Config.DMGNUM]) {
            if (d >= 0.05f) floatText(mc, "-" + fmt(d), 0xFF5566);
            else if (d <= -1f) floatText(mc, "+" + fmt(-d), 0x55FF7A);
        }
        lastHp = hp;
        long since = System.currentTimeMillis() - Hud.hitTime;
        if (hp <= 0f || !watched.isAlive()) {
            if (since < 2500L) onKill(mc);
            watched = null;
        } else if (pick != watched && since > 3000L) {
            watched = null;
        }
    }

    /** Рисует летящие спрайты. */
    static void render(MatrixStack ms, Minecraft mc, int sw, int sh) {
        long now = System.currentTimeMillis();
        float dt = Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        if (LIST.isEmpty()) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        for (Iterator<P> it = LIST.iterator(); it.hasNext(); ) {
            P p = it.next();
            float age = now - p.born;
            if (age > p.life) {
                it.remove();
                continue;
            }
            p.vy += (p.text != null ? 0f : 260f) * dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.rot += p.vr * dt;
            float a = 1f - Math.max(0f, (age - p.life * 0.5f) / (p.life * 0.5f));
            if (p.text != null) {
                int al = (int) (a * 255f);
                if (al > 8) {
                    mc.font.drawShadow(ms, p.text, p.x - mc.font.width(p.text) / 2f, p.y, (al << 24) | (p.color & 0x00FFFFFF));
                }
                continue;
            }
            RenderSystem.color4f(1f, 1f, 1f, a);
            mc.getTextureManager().bind(RL[p.type]);
            int s = Math.max(4, Math.round(p.size));
            ms.pushPose();
            try {
                ms.translate(p.x, p.y, 0.0);
                ms.mulPose(Vector3f.ZP.rotationDegrees(p.rot));
                AbstractGui.blit(ms, -s / 2, -s / 2, 0f, 0f, s, s, s, s);
            } finally {
                ms.popPose();
            }
        }
        RenderSystem.color4f(1f, 1f, 1f, 1f);
    }

    // ---------- экранные эффекты ----------
    static void screen(MatrixStack ms, Minecraft mc, ClientPlayerEntity p, int sw, int sh) {
        if (Config.ON[Config.VIGNETTE]) {
            int rgb = Config.vigColor == 1 ? 0x000000 : Config.vigColor == 2 ? Hud.rainbow(0f) : Config.c1();
            edges(ms, sw, sh, rgb, 0x78, 40);
        }
        if (Config.ON[Config.LOWHP] && p.getHealth() > 0f && p.getHealth() <= 6f) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 220.0);
            edges(ms, sw, sh, 0xE02020, 0x30 + (int) (0x70 * pulse), 64);
        }
        if (Config.ON[Config.SPEEDLINES] && p.isSprinting()) lines(ms, sw, sh);
    }

    private static void edges(MatrixStack ms, int sw, int sh, int rgb, int maxAlpha, int size) {
        for (int i = 0; i < size; i++) {
            float k = 1f - i / (float) size;
            int c = ((int) (maxAlpha * k * k) << 24) | (rgb & 0x00FFFFFF);
            AbstractGui.fill(ms, 0, i, sw, i + 1, c);
            AbstractGui.fill(ms, 0, sh - i - 1, sw, sh - i, c);
            AbstractGui.fill(ms, 0, i + 1, 1 + i, sh - i - 1, c);
            AbstractGui.fill(ms, sw - i - 1, i + 1, sw - i, sh - i - 1, c);
        }
    }

    private static void lines(MatrixStack ms, int sw, int sh) {
        long t = System.currentTimeMillis() / 70L;
        for (int i = 0; i < 26; i++) {
            long seed = (t + i * 37L) % 1000L;
            double ang = (i * 0.2417 + seed * 0.0007) * Math.PI * 2.0;
            double r0 = 0.42 + ((seed * 7L) % 100L) / 100.0 * 0.12;
            float len = 26f + (seed % 50L);
            float cx = sw / 2f, cy = sh / 2f;
            double dx = Math.cos(ang), dy = Math.sin(ang);
            for (float d = 0; d < len; d += 3f) {
                int x = (int) (cx + dx * (r0 * sw + d)), y = (int) (cy + dy * (r0 * sh * 1.1 + d));
                int a = (int) (0x70 * (1f - d / len));
                AbstractGui.fill(ms, x, y, x + 2, y + 2, (a << 24) | 0xFFFFFF);
            }
        }
    }
}
