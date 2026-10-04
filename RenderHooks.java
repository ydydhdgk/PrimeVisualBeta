package com.example.visuals;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderBlockOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Обработчики, которые вмешиваются в отрисовку мира, рук и окон.
 * Подключаются к Forge только пока включена функция, которой они нужны,
 * поэтому с выключенными функциями мод не трогает отрисовку вообще.
 */
public class RenderHooks {
    private static final RenderHooks INSTANCE = new RenderHooks();
    private static boolean registered = false;
    private static Screen animScreen;
    private static long animStart = 0L;
    private static boolean guiPushed = false;

    private static float ease(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    private static float dur(float baseMs) {
        return baseMs / Math.max(0.3f, Config.animSpeed);
    }

    /** Вызывается каждый тик: подключает или отключает обработчики. */
    static void update() {
        try {
            boolean need = Config.ON[Config.HITBOX] || Config.ON[Config.CAPE] || Config.ON[Config.HAT]
                    || Config.ON[Config.HALO] || Config.ON[Config.SHAKE] || Config.ON[Config.VIEWMODEL]
                    || Config.ON[Config.NOFIRE] || Config.ON[Config.ANIM_GUI]
                    || ClientEvents.ZOOM.isDown() || Math.abs(ClientEvents.zoomCurrent - 1f) > 0.001f;
            if (need && !registered) {
                MinecraftForge.EVENT_BUS.register(INSTANCE);
                registered = true;
            } else if (!need && registered) {
                MinecraftForge.EVENT_BUS.unregister(INSTANCE);
                registered = false;
            }
        } catch (Throwable t) {
            Safe.fail("renderHooks", t);
        }
    }

    private static float[] hbRgb() {
        int col = Hud.hitboxColor(Config.hbColor);
        return new float[]{((col >> 16) & 255) / 255f, ((col >> 8) & 255) / 255f, (col & 255) / 255f};
    }

    @SubscribeEvent
    public void onFov(EntityViewRenderEvent.FOVModifier e) {
        if (Safe.off("onFov")) return;
        try {
        float target = ClientEvents.ZOOM.isDown() ? (float) Config.zoom : 1f;
        ClientEvents.zoomCurrent += (target - ClientEvents.zoomCurrent) * 0.2f; // плавный вход/выход
        if (Math.abs(ClientEvents.zoomCurrent - 1f) > 0.001f) e.setFOV(e.getFOV() * ClientEvents.zoomCurrent);
        } catch (Throwable t) {
            Safe.fail("onFov", t);
        }
    }

    /** Тряска камеры при ударе и получении урона. */
    @SubscribeEvent
    public void onCamera(EntityViewRenderEvent.CameraSetup e) {
        if (!Config.ON[Config.SHAKE] || Safe.off("onCamera")) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            long now = System.currentTimeMillis();
            float k = 0f;
            long dt = now - Fx.shakeAt;
            if (dt < 350L) {
                float q = 1f - dt / 350f;
                k = q * q;
            }
            if (mc.player != null && mc.player.hurtTime > 0) k = Math.max(k, mc.player.hurtTime / 10f);
            if (k > 0f) {
                float a = Config.shakeAmp * k;
                e.setRoll(e.getRoll() + (float) Math.sin(now * 0.06) * 3f * a);
                e.setPitch(e.getPitch() + (float) Math.sin(now * 0.09) * 0.8f * a);
            }
        } catch (Throwable t) {
            Safe.fail("onCamera", t);
        }
    }

    /** Положение и размер рук / предмета. */
    @SubscribeEvent
    public void onHand(RenderHandEvent e) {
        if (!Config.ON[Config.VIEWMODEL] || Safe.off("onHand")) return;
        try {
            MatrixStack ms = e.getMatrixStack();
            ms.translate(Config.handX, Config.handY, Config.handZ);
            ms.scale(Config.handScale, Config.handScale, Config.handScale);
        } catch (Throwable t) {
            Safe.fail("onHand", t);
        }
    }

    /** Без огня перед глазами. */
    @SubscribeEvent
    public void onBlockOverlay(RenderBlockOverlayEvent e) {
        if (Config.ON[Config.NOFIRE] && e.getOverlayType() == RenderBlockOverlayEvent.OverlayType.FIRE) {
            e.setCanceled(true);
        }
    }

    /** Косметика: плащ, шляпа, нимб. */
    @SubscribeEvent
    public void onPlayerPost(RenderPlayerEvent.Post e) {
        if (Safe.off("onPlayerPost")) return;
        try {
        Cosmetics.render(e);
        } catch (Throwable t) {
            Safe.fail("onPlayerPost", t);
        }
    }

    /** Плавное открытие окон (инвентарь, меню паузы и т.д.). */
    @SubscribeEvent
    public void onScreenPre(GuiScreenEvent.DrawScreenEvent.Pre e) {
        if (Safe.off("onScreenPre")) return;
        try {
        if (!Config.ON[Config.ANIM_GUI]) return;
        Screen sc = e.getGui();
        if (sc instanceof MenuScreen || sc instanceof HudEditorScreen || sc instanceof ChatScreen) return;
        long now = System.currentTimeMillis();
        if (sc != animScreen) {
            animScreen = sc;
            animStart = now;
        }
        float k = ease((now - animStart) / dur(170f));
        if (k >= 1f) return;
        float s = 0.95f + 0.05f * k;
        float cx = sc.width / 2f, cy = sc.height / 2f;
        RenderSystem.pushMatrix();
        RenderSystem.translatef(cx, cy, 0f);
        RenderSystem.scalef(s, s, 1f);
        RenderSystem.translatef(-cx, -cy, 0f);
        guiPushed = true;
        } catch (Throwable t) {
            Safe.fail("onScreenPre", t);
        }
    }

    @SubscribeEvent
    public void onScreenPost(GuiScreenEvent.DrawScreenEvent.Post e) {
        if (Safe.off("onScreenPost")) return;
        try {
        if (guiPushed) {
            RenderSystem.popMatrix();
            guiPushed = false;
        }
        } catch (Throwable t) {
            Safe.fail("onScreenPost", t);
        }
    }

    /** Игроки и мобы: рамка рисуется в том же проходе, что и сама сущность (как ванильный F3+B). */
    @SubscribeEvent
    public void onLivingPost(RenderLivingEvent.Post<?, ?> e) {
        if (Safe.off("onLivingPost")) return;
        try {
        if (!Config.ON[Config.HITBOX]) return;
        LivingEntity le = e.getEntity();
        boolean player = le instanceof PlayerEntity;
        if (!((player && Config.hbPlayers) || (!player && Config.hbMobs))) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (le.distanceToSqr(mc.player) > (double) Config.hbRange * Config.hbRange) return;
        float[] c = hbRgb();
        IVertexBuilder vb = e.getBuffers().getBuffer(RenderType.lines());
        AxisAlignedBB bb = le.getBoundingBox().move(-le.getX(), -le.getY(), -le.getZ());
        WorldRenderer.renderLineBox(e.getMatrixStack(), vb, bb, c[0], c[1], c[2], 1.0f);
        if (Config.hbEye) {
            float eye = le.getEyeHeight();
            AxisAlignedBB line = new AxisAlignedBB(bb.minX, eye - 0.01, bb.minZ, bb.maxX, eye + 0.01, bb.maxZ);
            WorldRenderer.renderLineBox(e.getMatrixStack(), vb, line, 1f, 0f, 0f, 1f);
        }
        } catch (Throwable t) {
            Safe.fail("onLivingPost", t);
        }
    }

    /** Предметы и прочие сущности (стрелы, лодки и т.д.). Рисуются с проверкой глубины, за блоками не видны. */
    @SubscribeEvent
    public void onWorldLast(RenderWorldLastEvent e) {
        if (Safe.off("onWorldLast")) return;
        try {
        if (!Config.ON[Config.HITBOX] || !(Config.hbItems || Config.hbOthers)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        MatrixStack ms = e.getMatrixStack();
        float pt = e.getPartialTicks();
        Vector3d cam = mc.gameRenderer.getMainCamera().getPosition();
        IRenderTypeBuffer.Impl buf = mc.renderBuffers().bufferSource();
        IVertexBuilder vb = buf.getBuffer(RenderType.lines());
        float[] c = hbRgb();
        double range2 = (double) Config.hbRange * Config.hbRange;
        for (Entity en : mc.level.entitiesForRendering()) {
            if (en instanceof LivingEntity) continue;
            boolean item = en instanceof ItemEntity;
            if (!((item && Config.hbItems) || (!item && Config.hbOthers))) continue;
            if (en.distanceToSqr(mc.player) > range2) continue;
            double ix = MathHelper.lerp(pt, en.xo, en.getX());
            double iy = MathHelper.lerp(pt, en.yo, en.getY());
            double iz = MathHelper.lerp(pt, en.zo, en.getZ());
            AxisAlignedBB bb = en.getBoundingBox().move(ix - en.getX() - cam.x, iy - en.getY() - cam.y,
                    iz - en.getZ() - cam.z);
            WorldRenderer.renderLineBox(ms, vb, bb, c[0], c[1], c[2], 1.0f);
        }
        buf.endBatch(RenderType.lines());
        } catch (Throwable t) {
            Safe.fail("onWorldLast", t);
        }
    }
}
