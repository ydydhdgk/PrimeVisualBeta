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
        int type;
        long born;
    }

    private static final List<P> LIST = new ArrayList<>();
    private static long lastFrame = System.currentTimeMillis();
    private static LivingEntity watched;

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
        if (Config.ON[Config.HITFX]) burst(mc, Config.fxCount);
        if (Config.ON[Config.HITSND]) playSound(mc, 1f);
    }

    static void onKill(Minecraft mc) {
        if (Config.ON[Config.KILLFX]) burst(mc, Config.fxCount * 2 + 4);
        if (Config.ON[Config.HITSND]) playSound(mc, 1.25f);
    }

    /** Отслеживание победы над целью (вызывается каждый тик). */
    static void track(Minecraft mc) {
        if (!Config.ON[Config.KILLFX]) {
            watched = null;
            return;
        }
        Entity pick = mc.crosshairPickEntity;
        if (pick instanceof LivingEntity && pick != mc.player) watched = (LivingEntity) pick;
        if (watched == null) return;
        long since = System.currentTimeMillis() - Hud.hitTime;
        if (watched.getHealth() <= 0f || !watched.isAlive()) {
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
            p.vy += 260f * dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.rot += p.vr * dt;
            float a = 1f - Math.max(0f, (age - p.life * 0.5f) / (p.life * 0.5f));
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
