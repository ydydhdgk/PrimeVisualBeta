package com.example.visuals;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.NativeImage;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.entity.Pose;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Matrix3f;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraft.util.math.vector.Vector4f;
import net.minecraftforge.client.event.RenderPlayerEvent;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/** Косметика: свой плащ, китайская шляпа, нимб. Видна только вам (клиентская отрисовка). */
public class Cosmetics {
    private static final ResourceLocation CAPE_DEFAULT = new ResourceLocation("visuals", "textures/cape.png");
    private static final ResourceLocation HAT = new ResourceLocation("visuals", "textures/hat.png");
    private static final ResourceLocation HALO = new ResourceLocation("visuals", "textures/halo.png");
    private static final int FULL_LIGHT = 0xF000F0;

    private static ResourceLocation capeTex = CAPE_DEFAULT;
    private static boolean capeChecked = false;
    private static float capeSwing = 0f;

    /** Перечитать картинку плаща из .minecraft/primevisual/cape.png. */
    public static void reloadCape() {
        capeChecked = false;
    }

    private static ResourceLocation cape(Minecraft mc) {
        if (!capeChecked) {
            capeChecked = true;
            capeTex = CAPE_DEFAULT;
            try {
                File f = new File(mc.gameDirectory, "primevisual/cape.png");
                if (f.isFile()) {
                    try (InputStream in = new FileInputStream(f)) {
                        NativeImage img = NativeImage.read(in);
                        capeTex = mc.getTextureManager().register("primevisual_cape_" + System.nanoTime(),
                                new DynamicTexture(img));
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return capeTex;
    }

    public static void render(RenderPlayerEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        PlayerEntity p = e.getPlayer();
        if (p != mc.player) return;
        boolean cape = Config.ON[Config.CAPE], hat = Config.ON[Config.HAT], halo = Config.ON[Config.HALO];
        if (!cape && !hat && !halo) return;
        Pose pose = p.getPose();
        if (pose != Pose.STANDING && pose != Pose.CROUCHING) return;
        boolean crouch = pose == Pose.CROUCHING;
        MatrixStack ms = e.getMatrixStack();
        IRenderTypeBuffer buf = e.getBuffers();
        float pt = e.getPartialRenderTick();
        int light = e.getLight();

        if (cape) drawCape(mc, p, ms, buf, pt, light, crouch);
        if (hat || halo) {
            float headYaw = MathHelper.rotLerp(pt, p.yHeadRotO, p.yHeadRot);
            float pitch = MathHelper.lerp(pt, p.xRotO, p.xRot);
            ms.pushPose();
            ms.translate(0.0, crouch ? 1.3 : 1.5, 0.0);
            ms.mulPose(Vector3f.YP.rotationDegrees(-headYaw));
            ms.mulPose(Vector3f.XP.rotationDegrees(pitch));
            if (hat) drawHat(ms, buf, light);
            if (halo) drawHalo(ms, buf);
            ms.popPose();
        }
    }

    // ---------- плащ ----------
    private static void drawCape(Minecraft mc, PlayerEntity p, MatrixStack ms, IRenderTypeBuffer buf,
                                 float pt, int light, boolean crouch) {
        float bodyYaw = MathHelper.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
        Vector3d v = p.getDeltaMovement();
        double rad = Math.toRadians(bodyYaw);
        double fwd = v.x * -Math.sin(rad) + v.z * Math.cos(rad);
        float target = (float) MathHelper.clamp(fwd * 110.0, -4.0, 55.0);
        capeSwing += (target - capeSwing) * 0.12f;
        float ang = 6f + capeSwing + (crouch ? 25f : 0f) + (float) Math.sin((p.tickCount + pt) * 0.18) * 1.5f;

        ms.pushPose();
        ms.mulPose(Vector3f.YP.rotationDegrees(-bodyYaw));
        ms.translate(0.0, crouch ? 1.32 : 1.5, -0.125);
        ms.mulPose(Vector3f.XP.rotationDegrees(ang));
        IVertexBuilder vb = buf.getBuffer(RenderType.entityCutoutNoCull(cape(mc)));
        Matrix4f m = ms.last().pose();
        Matrix3f n = ms.last().normal();
        float hx = 0.3125f, h = 1.0f, t = 0.0625f, s = 0.01f;
        // наружная сторона с картинкой и внутренняя
        quad(vb, m, n, light, 1f, 1f, 1f, 0f, 0f, -1f,
                new float[]{hx, 0, -t, 0, 0}, new float[]{-hx, 0, -t, 1, 0},
                new float[]{-hx, -h, -t, 1, 1}, new float[]{hx, -h, -t, 0, 1});
        quad(vb, m, n, light, 1f, 1f, 1f, 0f, 0f, 1f,
                new float[]{hx, 0, 0, 0, 0}, new float[]{-hx, 0, 0, 1, 0},
                new float[]{-hx, -h, 0, 1, 1}, new float[]{hx, -h, 0, 0, 1});
        // торцы
        quad(vb, m, n, light, 1f, 1f, 1f, 1f, 0f, 0f,
                new float[]{hx, 0, -t, s, s}, new float[]{hx, 0, 0, s, s},
                new float[]{hx, -h, 0, s, s}, new float[]{hx, -h, -t, s, s});
        quad(vb, m, n, light, 1f, 1f, 1f, -1f, 0f, 0f,
                new float[]{-hx, 0, -t, s, s}, new float[]{-hx, 0, 0, s, s},
                new float[]{-hx, -h, 0, s, s}, new float[]{-hx, -h, -t, s, s});
        quad(vb, m, n, light, 1f, 1f, 1f, 0f, 1f, 0f,
                new float[]{hx, 0, -t, s, s}, new float[]{-hx, 0, -t, s, s},
                new float[]{-hx, 0, 0, s, s}, new float[]{hx, 0, 0, s, s});
        quad(vb, m, n, light, 1f, 1f, 1f, 0f, -1f, 0f,
                new float[]{hx, -h, -t, s, s}, new float[]{-hx, -h, -t, s, s},
                new float[]{-hx, -h, 0, s, s}, new float[]{hx, -h, 0, s, s});
        ms.popPose();
    }

    // ---------- китайская шляпа (конус) ----------
    private static void drawHat(MatrixStack ms, IRenderTypeBuffer buf, int light) {
        float sc = Config.hatScale;
        float rad = 0.62f * sc, hgt = 0.34f * sc, base = 0.36f;
        int col = Config.hatColor == 1 ? Config.c1() : Config.hatColor == 2 ? Hud.rainbow(0f) : 0xFFFFE6B8;
        float cr = ((col >> 16) & 255) / 255f, cg = ((col >> 8) & 255) / 255f, cb = (col & 255) / 255f;
        IVertexBuilder vb = buf.getBuffer(RenderType.entityCutoutNoCull(HAT));
        Matrix4f m = ms.last().pose();
        Matrix3f n = ms.last().normal();
        int seg = 18;
        float[] apex = {0f, base + hgt, 0f, 0.5f, 0.5f};
        for (int i = 0; i < seg; i++) {
            double a0 = i * 2.0 * Math.PI / seg, a1 = (i + 1) * 2.0 * Math.PI / seg, am = (a0 + a1) / 2.0;
            float[] p0 = {(float) (rad * Math.cos(a0)), base, (float) (rad * Math.sin(a0)),
                    (float) (0.5 + 0.5 * Math.cos(a0)), (float) (0.5 + 0.5 * Math.sin(a0))};
            float[] p1 = {(float) (rad * Math.cos(a1)), base, (float) (rad * Math.sin(a1)),
                    (float) (0.5 + 0.5 * Math.cos(a1)), (float) (0.5 + 0.5 * Math.sin(a1))};
            quad(vb, m, n, light, cr, cg, cb, (float) (0.6 * Math.cos(am)), 0.8f, (float) (0.6 * Math.sin(am)),
                    apex, apex, p1, p0);
        }
    }

    // ---------- нимб ----------
    private static void drawHalo(MatrixStack ms, IRenderTypeBuffer buf) {
        float sc = Config.haloScale;
        float rin = 0.2f * sc, rout = 0.34f * sc;
        float y = 0.72f + Config.haloHeight + (float) Math.sin(System.currentTimeMillis() / 600.0) * 0.025f;
        IVertexBuilder vb = buf.getBuffer(RenderType.entityCutoutNoCull(HALO));
        Matrix4f m = ms.last().pose();
        Matrix3f n = ms.last().normal();
        int seg = 28;
        for (int layer = 0; layer < 2; layer++) {
            float yy = y + layer * 0.02f;
            for (int i = 0; i < seg; i++) {
                double a0 = i * 2.0 * Math.PI / seg, a1 = (i + 1) * 2.0 * Math.PI / seg;
                float k = i / (float) seg;
                int col;
                if (Config.haloColor == 1) col = Hud.lerp(Config.c1(), Config.c2(), k < 0.5f ? k * 2f : (1f - k) * 2f);
                else if (Config.haloColor == 2) col = Hud.rainbow(k);
                else col = Hud.lerp(0xFFFFD84A, 0xFFFFF3B0, k < 0.5f ? k * 2f : (1f - k) * 2f);
                float cr = ((col >> 16) & 255) / 255f, cg = ((col >> 8) & 255) / 255f, cb = (col & 255) / 255f;
                quad(vb, m, n, FULL_LIGHT, cr, cg, cb, 0f, 1f, 0f,
                        new float[]{(float) (rin * Math.cos(a0)), yy, (float) (rin * Math.sin(a0)), 0, 0},
                        new float[]{(float) (rout * Math.cos(a0)), yy, (float) (rout * Math.sin(a0)), 1, 0},
                        new float[]{(float) (rout * Math.cos(a1)), yy, (float) (rout * Math.sin(a1)), 1, 1},
                        new float[]{(float) (rin * Math.cos(a1)), yy, (float) (rin * Math.sin(a1)), 0, 1});
            }
        }
    }

    // ---------- вспомогательное ----------
    private static void quad(IVertexBuilder vb, Matrix4f m, Matrix3f n, int light, float cr, float cg, float cb,
                             float nx, float ny, float nz, float[] a, float[] b, float[] c, float[] d) {
        vtx(vb, m, n, light, cr, cg, cb, nx, ny, nz, a);
        vtx(vb, m, n, light, cr, cg, cb, nx, ny, nz, b);
        vtx(vb, m, n, light, cr, cg, cb, nx, ny, nz, c);
        vtx(vb, m, n, light, cr, cg, cb, nx, ny, nz, d);
    }

    private static void vtx(IVertexBuilder vb, Matrix4f m, Matrix3f n, int light, float cr, float cg, float cb,
                            float nx, float ny, float nz, float[] p) {
        Vector4f v = new Vector4f(p[0], p[1], p[2], 1f);
        v.transform(m);
        Vector3f nn = new Vector3f(nx, ny, nz);
        nn.transform(n);
        vb.vertex(v.x(), v.y(), v.z(), cr, cg, cb, 1f, p[3], p[4], OverlayTexture.NO_OVERLAY, light,
                nn.x(), nn.y(), nn.z());
    }
}
