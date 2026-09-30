package com.example.visuals;

import net.minecraft.client.Minecraft;
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

    @SubscribeEvent
    public void onMouse(InputEvent.MouseInputEvent e) {
        if (e.getAction() != GLFW.GLFW_PRESS || Minecraft.getInstance().screen != null) return;
        long now = System.currentTimeMillis();
        if (e.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) LEFT.addLast(now);
        if (e.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) RIGHT.addLast(now);
    }

    /** Прячем ванильный прицел, если включён свой. */
    @SubscribeEvent
    public void onOverlayPre(RenderGameOverlayEvent.Pre e) {
        if (e.getType() == RenderGameOverlayEvent.ElementType.CROSSHAIRS && Config.ON[Config.CROSSHAIR]) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onOverlayPost(RenderGameOverlayEvent.Post e) {
        if (e.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        Hud.render(e.getMatrixStack(), e.getWindow().getGuiScaledWidth(), e.getWindow().getGuiScaledHeight());
    }
}
