package com.example.visuals;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.StringTextComponent;
import org.lwjgl.glfw.GLFW;

/** Редактор позиций HUD: элементы перетаскиваются мышью, позиции сохраняются. */
public class HudEditorScreen extends Screen {
    private final Screen parent;
    private int dragging = -1;
    private float accX, accY;

    public HudEditorScreen(Screen parent) {
        super(new StringTextComponent("HUD"));
        this.parent = parent;
    }

    private float scale() {
        return MathHelper.clamp(Config.scale, 0.5f, 2f);
    }

    private int hitTest(double mx, double my) {
        float s = scale();
        for (int id = Hud.EL_COUNT - 1; id >= 0; id--) {
            int[] b = Hud.bounds(id);
            if (b == null) continue;
            double x = b[0] * s, y = b[1] * s, w = b[2] * s, h = b[3] * s;
            if (mx >= x && mx < x + w && my >= y && my < y + h) return id;
        }
        return -1;
    }

    @Override
    public void render(MatrixStack ms, int mx, int my, float pt) {
        AbstractGui.fill(ms, 0, 0, width, height, 0x40000000);
        float s = scale();
        int hover = hitTest(mx, my);
        for (int id = 0; id < Hud.EL_COUNT; id++) {
            int[] b = Hud.bounds(id);
            if (b == null) continue;
            int x = Math.round(b[0] * s), y = Math.round(b[1] * s);
            int w = Math.round(b[2] * s), h = Math.round(b[3] * s);
            boolean active = id == hover || id == dragging;
            Hud.rrOutline(ms, x - 2, y - 2, w + 4, h + 4, 4, active ? Config.c2() : 0x88FFFFFF);
            if (active) AbstractGui.fill(ms, x, y, x + w, y + h, 0x22FFFFFF);
            font.drawShadow(ms, Hud.EL_NAMES[id], (float) x, (float) (y - 12), active ? Config.c2() : Hud.LABEL);
        }
        String l1 = "Редактор HUD";
        String l2 = "Тяните элементы мышью  •  ПКМ - вернуть на место  •  R - сбросить все  •  Esc - готово";
        int bw = font.width(l2) + 24;
        int bx = (width - bw) / 2;
        Hud.glassRectA(ms, bx, 10, bw, 30, 6, false, 170);
        Hud.gradText(ms, font, l1, (width - font.width(l1)) / 2, 15);
        font.draw(ms, l2, bx + 12, 27, 0xFFB4B7CC);
        if (Hud.bounds(Hud.EL_EFFECTS) == null) {
            String hint = "Часть элементов появляется только когда они видны в игре (эффекты, цель)";
            font.draw(ms, hint, (width - font.width(hint)) / 2f, height - 16, 0xFF6C6F86);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int id = hitTest(mx, my);
        if (id != -1) {
            if (button == 0) {
                dragging = id;
                accX = 0f;
                accY = 0f;
            } else if (button == 1) {
                Config.offX[id] = 0;
                Config.offY[id] = 0;
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging != -1) {
            float s = scale();
            accX += (float) (dx / s);
            accY += (float) (dy / s);
            int stepX = (int) accX, stepY = (int) accY;
            Config.offX[dragging] += stepX;
            Config.offY[dragging] += stepY;
            accX -= stepX;
            accY -= stepY;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = -1;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_R) {
            for (int i = 0; i < Config.offX.length; i++) {
                Config.offX[i] = 0;
                Config.offY[i] = 0;
            }
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() {
        Config.save();
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
