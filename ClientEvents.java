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
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.particles.IParticleData;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.particles.RedstoneParticleData;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.util.InputMappings;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;

public class ClientEvents {
    public static final KeyBinding ZOOM = new KeyBinding(
            "key.visuals.zoom", InputMappings.Type.KEYSYM, GLFW.GLFW_KEY_C, "key.categories.visuals");
    public static final KeyBinding MENU = new KeyBinding(
            "key.visuals.menu", InputMappings.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "key.categories.visuals");

    private static long lastBind = 0L;
    private static long lastAuto = 0L;
    private static boolean wasOnGround = true;
    private static double savedGamma = -1.0;
    private static boolean sprintForced = false, cineState = false, deathHandled = false;
    private static double lastX, lastY, lastZ;
    private static final DateTimeFormatter CHAT_TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static float zoomCurrent = 1f; // плавное значение FOV-множителя
    private static final ArrayDeque<Long> LEFT = new ArrayDeque<>();
    private static final ArrayDeque<Long> RIGHT = new ArrayDeque<>();

    public static float zoomFactor() { return zoomCurrent; }

    /** Кликов за последнюю секунду. */
    public static int cps(boolean left) {
        ArrayDeque<Long> q = left ? LEFT : RIGHT;
        long now = System.currentTimeMillis();
        while (!q.isEmpty() && now - q.peekFirst() > 1000) q.pollFirst();
        return q.size();
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        while (MENU.consumeClick()) {
            if (mc.screen == null && mc.player != null) mc.setScreen(new MenuScreen());
        }
        tickExtras(mc);
    }

    private static IParticleData particle(int t) {
        switch (t) {
            case 1: return ParticleTypes.FLAME;
            case 2: return ParticleTypes.HEART;
            case 3: return ParticleTypes.HAPPY_VILLAGER;
            case 4: return ParticleTypes.NOTE;
            case 5: return ParticleTypes.CRIT;
            case 6: {
                int c = Hud.rainbow(0f);
                return new RedstoneParticleData(((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f,
                        (c & 255) / 255f, 1.0f);
            }
            default: return ParticleTypes.END_ROD;
        }
    }

    private void restoreOptions(Minecraft mc) {
        if (savedGamma >= 0.0) {
            mc.options.gamma = savedGamma;
            savedGamma = -1.0;
        }
        if (cineState) {
            mc.options.smoothCamera = false;
            cineState = false;
        }
        if (sprintForced) {
            mc.options.keySprint.setDown(false);
            sprintForced = false;
        }
    }

    /** Помощники: авто-спринт, яркость, кинокамера при зуме, точка смерти. */
    private void assist(Minecraft mc, ClientPlayerEntity p) {
        if (Config.ON[Config.SPRINT]) {
            if (mc.screen == null && mc.options.keyUp.isDown() && !p.isCrouching()) {
                mc.options.keySprint.setDown(true);
                sprintForced = true;
            }
        } else if (sprintForced) {
            mc.options.keySprint.setDown(false);
            sprintForced = false;
        }

        if (Config.ON[Config.BRIGHT]) {
            if (savedGamma < 0.0) savedGamma = mc.options.gamma;
            mc.options.gamma = Config.brightness;
        } else if (savedGamma >= 0.0) {
            mc.options.gamma = savedGamma;
            savedGamma = -1.0;
        }

        boolean zoomNow = Config.ON[Config.CINEZOOM] && ZOOM.isDown();
        if (zoomNow != cineState) {
            mc.options.smoothCamera = zoomNow;
            cineState = zoomNow;
        }

        if (p.getHealth() > 0f) {
            lastX = p.getX();
            lastY = p.getY();
            lastZ = p.getZ();
            deathHandled = false;
        } else if (!deathHandled) {
            deathHandled = true;
            if (Config.ON[Config.DEATHPOINT]) {
                int dx = (int) Math.floor(lastX), dy = (int) Math.floor(lastY), dz = (int) Math.floor(lastZ);
                Config.gpsX = String.valueOf(dx);
                Config.gpsZ = String.valueOf(dz);
                Config.ON[Config.GPS] = true;
                mc.gui.getChat().addMessage(new StringTextComponent("Вы погибли: " + dx + " " + dy + " " + dz));
                Hud.toast("Точка смерти: " + dx + " " + dy + " " + dz, Hud.RED);
            }
        }
    }

    /** Следы, круг прыжка и авто-команда (всё выполняется на клиенте). */
    private void tickExtras(Minecraft mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.level == null) {
            wasOnGround = true;
            restoreOptions(mc);
            return;
        }
        assist(mc, p);
        boolean onGround = p.isOnGround();
        Vector3d v = p.getDeltaMovement();
        if (!mc.isPaused()) {
            if (Config.ON[Config.TRAILS] && (v.x * v.x + v.z * v.z > 0.0009 || !onGround)) {
                for (int i = 0; i < 2; i++) {
                    mc.level.addParticle(particle(Config.particleType),
                            p.getX() + (Math.random() - 0.5) * 0.4, p.getY() + 0.1 + Math.random() * 0.2,
                            p.getZ() + (Math.random() - 0.5) * 0.4, 0.0, 0.01, 0.0);
                }
            }
            if (Config.ON[Config.JUMPCIRCLE] && wasOnGround && !onGround && v.y > 0.1) {
                double r = Config.circleSize;
                for (int k = 0; k < 28; k++) {
                    double a = k * (Math.PI * 2.0 / 28.0);
                    mc.level.addParticle(particle(Config.particleType),
                            p.getX() + Math.cos(a) * r, p.getY() + 0.05, p.getZ() + Math.sin(a) * r,
                            Math.cos(a) * 0.04, 0.01, Math.sin(a) * 0.04);
                }
            }
        }
        wasOnGround = onGround;

        if (!Config.ON[Config.AUTOCMD]) {
            lastAuto = 0L;
        } else if (mc.screen == null) {
            String cmd = Config.autoCmd == null ? "" : Config.autoCmd.trim();
            long now = System.currentTimeMillis();
            if (lastAuto == 0L) lastAuto = now;
            long interval = Math.max(10, Config.autoInterval) * 1000L;
            if (!cmd.isEmpty() && now - lastAuto >= interval) {
                p.chat(cmd);
                lastAuto = now;
                if (Config.ON[Config.NOTIFY]) Hud.toast("Авто: " + cmd, Hud.GREEN);
            }
        }
    }

    @SubscribeEvent
    public void onFov(EntityViewRenderEvent.FOVModifier e) {
        float target = ZOOM.isDown() ? (float) Config.zoom : 1f;
        zoomCurrent += (target - zoomCurrent) * 0.2f; // плавный вход/выход
        if (Math.abs(zoomCurrent - 1f) > 0.001f) e.setFOV(e.getFOV() * zoomCurrent);
    }

    @SubscribeEvent
    public void onScroll(InputEvent.MouseScrollEvent e) {
        if (ZOOM.isDown()) { // колесо при зуме меняет силу приближения
            Config.zoom = MathHelper.clamp(Config.zoom - e.getScrollDelta() * 0.03, 0.05, 0.6);
            e.setCanceled(true);
        }
    }

    /** Бинды: нажатие клавиши отправляет выбранную пользователем команду (с задержкой 0.4 с). */
    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent e) {
        if (e.getAction() != GLFW.GLFW_PRESS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null) return;
        long now = System.currentTimeMillis();
        if (now - lastBind < 400L) return;
        for (int i = 0; i < Config.BIND_COUNT; i++) {
            String cmd = Config.bindCmd[i] == null ? "" : Config.bindCmd[i].trim();
            if (Config.bindKey[i] == e.getKey() && !cmd.isEmpty()) {
                mc.player.chat(cmd);
                lastBind = now;
                if (Config.ON[Config.NOTIFY]) Hud.toast("Отправлено: " + cmd, Hud.GREEN);
                break;
            }
        }
    }

    @SubscribeEvent
    public void onMouse(InputEvent.MouseInputEvent e) {
        if (e.getAction() != GLFW.GLFW_PRESS || Minecraft.getInstance().screen != null) return;
        long now = System.currentTimeMillis();
        if (e.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            LEFT.addLast(now);
            if (Minecraft.getInstance().crosshairPickEntity != null) Hud.hitTime = now; // для хит-маркера
        }
        if (e.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) RIGHT.addLast(now);
    }

    // ======================= Анимации =======================
    private static long chatAnim = 0L;
    private static long tabStart = 0L, tabLastSeen = 0L;
    private static boolean tabPushed = false;
    private static Screen animScreen;
    private static long animStart = 0L;
    private static boolean guiPushed = false;
    private static float hotbarPos = 0f;

    private static float ease(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    private static float dur(float baseMs) {
        return baseMs / Math.max(0.3f, Config.animSpeed);
    }

    /** Прячем ванильный прицел, если включён свой; плавный въезд списка игроков (Tab). */
    @SubscribeEvent
    public void onOverlayPre(RenderGameOverlayEvent.Pre e) {
        RenderGameOverlayEvent.ElementType type = e.getType();
        if (type == RenderGameOverlayEvent.ElementType.CROSSHAIRS && Config.ON[Config.CROSSHAIR]) {
            e.setCanceled(true);
            return;
        }
        if (type == RenderGameOverlayEvent.ElementType.PLAYER_LIST && Config.ON[Config.ANIM_TAB] && !e.isCanceled()) {
            long now = System.currentTimeMillis();
            if (now - tabLastSeen > 150L) tabStart = now; // список только что открыли
            tabLastSeen = now;
            float k = ease((now - tabStart) / dur(200f));
            if (k < 1f) {
                e.getMatrixStack().pushPose();
                e.getMatrixStack().translate(0.0, -(1f - k) * 16f, 0.0);
                tabPushed = true;
            }
        }
    }

    @SubscribeEvent
    public void onChatReceived(ClientChatReceivedEvent e) {
        chatAnim = System.currentTimeMillis();
        if (Config.ON[Config.CHATTIME]) {
            e.setMessage(new StringTextComponent("[" + LocalTime.now().format(CHAT_TIME) + "] ")
                    .withStyle(TextFormatting.GRAY).append(e.getMessage()));
        }
    }

    /** Плавный сдвиг чата: при новом сообщении блок «выезжает» снизу. */
    @SubscribeEvent
    public void onChatPos(RenderGameOverlayEvent.Chat e) {
        if (!Config.ON[Config.ANIM_CHAT]) return;
        float k = ease((System.currentTimeMillis() - chatAnim) / dur(180f));
        if (k < 1f) e.setPosY(e.getPosY() + Math.round(9f * (1f - k)));
    }

    @SubscribeEvent
    public void onOverlayPost(RenderGameOverlayEvent.Post e) {
        RenderGameOverlayEvent.ElementType type = e.getType();
        if (type == RenderGameOverlayEvent.ElementType.PLAYER_LIST && tabPushed) {
            e.getMatrixStack().popPose();
            tabPushed = false;
            return;
        }
        if (type == RenderGameOverlayEvent.ElementType.HOTBAR && Config.ON[Config.ANIM_HOTBAR]) {
            hotbarGlide(e);
            return;
        }
        if (type != RenderGameOverlayEvent.ElementType.ALL) return;
        if (tabPushed) { // страховка, если Post списка игроков не пришёл
            e.getMatrixStack().popPose();
            tabPushed = false;
        }
        Hud.render(e.getMatrixStack(), e.getWindow().getGuiScaledWidth(), e.getWindow().getGuiScaledHeight());
    }

    /** Скользящая подсветка выбранного слота хотбара. */
    private void hotbarGlide(RenderGameOverlayEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        float target = mc.player.inventory.selected;
        hotbarPos += (target - hotbarPos) * Math.min(1f, 0.3f * Config.animSpeed);
        int w = e.getWindow().getGuiScaledWidth(), h = e.getWindow().getGuiScaledHeight();
        int x = w / 2 - 91 - 1 + Math.round(hotbarPos * 20f);
        int y = h - 22 - 1;
        MatrixStack ms = e.getMatrixStack();
        Hud.rr(ms, x + 1, y + 1, 22, 22, 3, (Hud.lerp(Config.c1(), Config.c2(), 0.5f) & 0x00FFFFFF) | 0x38000000);
        Hud.rrOutline(ms, x, y, 24, 24, 4, Config.c2());
    }

    /** Плавное открытие окон (инвентарь, меню паузы и т.д.). */
    @SubscribeEvent
    public void onScreenPre(GuiScreenEvent.DrawScreenEvent.Pre e) {
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
    }

    @SubscribeEvent
    public void onScreenPost(GuiScreenEvent.DrawScreenEvent.Post e) {
        if (guiPushed) {
            RenderSystem.popMatrix();
            guiPushed = false;
        }
    }

    // ======================= Цветные хитбоксы =======================
    /** Рамки вокруг сущностей. Рисуются с проверкой глубины, поэтому за блоками не видны. */
    @SubscribeEvent
    public void onWorldLast(RenderWorldLastEvent e) {
        if (!Config.ON[Config.HITBOX]) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        MatrixStack ms = e.getMatrixStack();
        float pt = e.getPartialTicks();
        Vector3d cam = mc.gameRenderer.getMainCamera().getPosition();
        IRenderTypeBuffer.Impl buf = mc.renderBuffers().bufferSource();
        IVertexBuilder vb = buf.getBuffer(RenderType.lines());
        int col = Hud.hitboxColor(Config.hbColor);
        float r = ((col >> 16) & 255) / 255f, g = ((col >> 8) & 255) / 255f, b = (col & 255) / 255f;
        double range2 = (double) Config.hbRange * Config.hbRange;
        boolean first = mc.options.getCameraType().isFirstPerson();
        for (Entity en : mc.level.entitiesForRendering()) {
            if (en == mc.player && first) continue;
            boolean player = en instanceof PlayerEntity;
            boolean item = en instanceof ItemEntity;
            boolean mob = en instanceof LivingEntity && !player;
            if (!((player && Config.hbPlayers) || (mob && Config.hbMobs) || (item && Config.hbItems))) continue;
            if (en.distanceToSqr(mc.player) > range2) continue;
            double ix = MathHelper.lerp(pt, en.xo, en.getX());
            double iy = MathHelper.lerp(pt, en.yo, en.getY());
            double iz = MathHelper.lerp(pt, en.zo, en.getZ());
            AxisAlignedBB bb = en.getBoundingBox().move(ix - en.getX() - cam.x, iy - en.getY() - cam.y,
                    iz - en.getZ() - cam.z);
            WorldRenderer.renderLineBox(ms, vb, bb, r, g, b, 1.0f);
        }
        buf.endBatch(RenderType.lines());
    }
}
