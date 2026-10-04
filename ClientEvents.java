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
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.client.event.RenderBlockOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
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
    private static boolean welcomed = false;
    private static String calcAnswer = null;
    private static long calcTime = 0L;
    private static double savedGamma = -1.0;
    private static boolean sprintForced = false, cineState = false, deathHandled = false;
    private static double lastX, lastY, lastZ;
    private static final DateTimeFormatter CHAT_TIME = DateTimeFormatter.ofPattern("HH:mm");
    static float zoomCurrent = 1f; // плавное значение FOV-множителя
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
        if (Safe.off("onTick")) return;
        try {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        while (MENU.consumeClick()) {
            if (mc.screen == null && mc.player != null) mc.setScreen(new MenuScreen());
        }
        tickExtras(mc);
        RenderHooks.update();
        if (mc.player != null && mc.level != null) {
            if (!welcomed) {
                welcomed = true;
                mc.gui.getChat().addMessage(new StringTextComponent("[PrimeVisual] загружен. Меню: Right Shift"));
            }
        } else {
            welcomed = false;
        }
        } catch (Throwable t) {
            Safe.fail("onTick", t);
        }
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

        if (mc.level != null) { // клиентское время суток и погода
            if (Config.ON[Config.TIMECHG]) mc.level.setDayTime((long) Config.timeOfDay);
            if (Config.ON[Config.NORAIN]) {
                mc.level.setRainLevel(0f);
                mc.level.setThunderLevel(0f);
            }
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
        Fx.track(mc);
        boolean onGround = p.isOnGround();
        Vector3d v = p.getDeltaMovement();
        if (!mc.isPaused()) {
            if (Config.ON[Config.TRAILS] && (v.x * v.x + v.z * v.z > 0.0009 || !onGround)) {
                for (int i = 0; i < Config.trailDensity; i++) {
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
    public void onScroll(InputEvent.MouseScrollEvent e) {
        if (Safe.off("onScroll")) return;
        try {
        if (ZOOM.isDown()) { // колесо при зуме меняет силу приближения
            Config.zoom = MathHelper.clamp(Config.zoom - e.getScrollDelta() * 0.03, 0.05, 0.6);
            e.setCanceled(true);
        }
        } catch (Throwable t) {
            Safe.fail("onScroll", t);
        }
    }

    /** Бинды и клавиши мода. Команды отправляются только по нажатию (пауза 0,4 с). */
    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent e) {
        if (Safe.off("onKey")) return;
        try {
        if (e.getAction() != GLFW.GLFW_PRESS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null) return;
        int key = e.getKey();
        long now = System.currentTimeMillis();
        if (Config.hudKey >= 0 && key == Config.hudKey) {
            Config.hudHidden = !Config.hudHidden;
            Hud.toast(Config.hudHidden ? "HUD мода скрыт" : "HUD мода показан", Hud.WHITE);
            return;
        }
        if (Config.calcKey >= 0 && key == Config.calcKey && calcAnswer != null && now - calcTime < 60000L) {
            mc.player.chat(calcAnswer);
            Hud.toast("Отправлено: " + calcAnswer, Hud.GREEN);
            calcAnswer = null;
            return;
        }
        if (now - lastBind < 400L) return;
        for (int i = 0; i < Config.BIND_COUNT; i++) {
            String cmd = Config.bindCmd[i] == null ? "" : Config.bindCmd[i].trim();
            if (Config.bindKey[i] == key && !cmd.isEmpty()) {
                mc.player.chat(cmd);
                lastBind = now;
                if (Config.ON[Config.NOTIFY]) Hud.toast("Отправлено: " + cmd, Hud.GREEN);
                break;
            }
        }
        } catch (Throwable t) {
            Safe.fail("onKey", t);
        }
    }

    @SubscribeEvent
    public void onMouse(InputEvent.MouseInputEvent e) {
        if (Safe.off("onMouse")) return;
        try {
        if (e.getAction() != GLFW.GLFW_PRESS || Minecraft.getInstance().screen != null) return;
        long now = System.currentTimeMillis();
        if (e.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            LEFT.addLast(now);
            if (Minecraft.getInstance().crosshairPickEntity != null) {
                Hud.hitTime = now; // для хит-маркера
                Fx.onHit(Minecraft.getInstance());
            }
        }
        if (e.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) RIGHT.addLast(now);
        } catch (Throwable t) {
            Safe.fail("onMouse", t);
        }
    }

    // ======================= Анимации =======================
    private static long chatAnim = 0L;
    private static long tabStart = 0L, tabLastSeen = 0L;
    private static boolean tabPushed = false;
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
        if (Safe.off("onOverlayPre")) return;
        try {
        RenderGameOverlayEvent.ElementType type = e.getType();
        if (type == RenderGameOverlayEvent.ElementType.CROSSHAIRS && Config.ON[Config.CROSSHAIR]) {
            e.setCanceled(true);
            return;
        }
        if ((type == RenderGameOverlayEvent.ElementType.POTION_ICONS && Config.ON[Config.HIDEPOT])
                || (type == RenderGameOverlayEvent.ElementType.BOSSINFO && Config.ON[Config.HIDEBOSS])
                || (type == RenderGameOverlayEvent.ElementType.VIGNETTE && Config.ON[Config.HIDEVIG])) {
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
        } catch (Throwable t) {
            Safe.fail("onOverlayPre", t);
        }
    }

    @SubscribeEvent
    public void onChatReceived(ClientChatReceivedEvent e) {
        if (Safe.off("onChatReceived")) return;
        try {
        long now = System.currentTimeMillis();
        chatAnim = now;
        ITextComponent msg = e.getMessage();
        if (Config.ON[Config.CALC]) {
            String[] r = Calc.find(msg.getString());
            if (r != null) {
                calcAnswer = r[1];
                calcTime = now;
                msg = msg.copy().append(new StringTextComponent("  = " + r[1]).withStyle(TextFormatting.GREEN));
                Hud.toast(r[0] + " = " + r[1], Hud.GREEN);
            }
        }
        if (Config.ON[Config.CHATTIME]) {
            msg = new StringTextComponent("[" + LocalTime.now().format(CHAT_TIME) + "] ")
                    .withStyle(TextFormatting.GRAY).append(msg);
        }
        if (msg != e.getMessage()) e.setMessage(msg);
        } catch (Throwable t) {
            Safe.fail("onChatReceived", t);
        }
    }

    /** Плавный сдвиг чата: при новом сообщении блок «выезжает» снизу. */
    @SubscribeEvent
    public void onChatPos(RenderGameOverlayEvent.Chat e) {
        if (Safe.off("onChatPos")) return;
        try {
        if (!Config.ON[Config.ANIM_CHAT]) return;
        float k = ease((System.currentTimeMillis() - chatAnim) / dur(180f));
        if (k < 1f) e.setPosY(e.getPosY() + Math.round(9f * (1f - k)));
        } catch (Throwable t) {
            Safe.fail("onChatPos", t);
        }
    }

    @SubscribeEvent
    public void onOverlayPost(RenderGameOverlayEvent.Post e) {
        if (Safe.off("onOverlayPost")) return;
        try {
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
        } catch (Throwable t) {
            Safe.fail("onOverlayPost", t);
        }
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

    // ======================= Цветные хитбоксы =======================
}
