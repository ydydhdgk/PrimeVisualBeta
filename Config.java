package com.example.visuals;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;

public class Config {
    public static final String[] NAMES = {
            "FPS", "Координаты", "Курс", "Скорость", "Пинг", "Свет",
            "Время", "Эффекты зелий", "Прочность", "Клавиши и CPS", "Свой прицел", "Инфо о цели", "Ватермарка"
    };
    public static final String[] DESCS = {
            "Кадры в секунду", "Позиция XYZ", "Сторона света и угол", "Блоков в секунду", "Задержка до сервера",
            "Уровень освещения", "Реальное время", "Иконки и таймеры", "Броня и предметы в руках",
            "WASD, мышь, клики в секунду", "Заменяет ванильный", "Имя и здоровье цели", "Плашка с названием мода"
    };
    public static final int FPS = 0, COORDS = 1, DIR = 2, SPEED = 3, PING = 4, LIGHT = 5,
            TIME = 6, EFFECTS = 7, DURABILITY = 8, KEYS = 9, CROSSHAIR = 10, TARGET = 11, WATERMARK = 12;

    public static final boolean[] ON = new boolean[NAMES.length];

    public static final int[][] PRESETS = {
            {0xFF8B5CFF, 0xFF2DD4FF},
            {0xFF22C55E, 0xFFA3E635},
            {0xFFFF4D6D, 0xFFFF9F43},
            {0xFF3B82F6, 0xFF22D3EE},
            {0xFFFF5CAA, 0xFF8B5CFF},
            {0xFFF5F5F5, 0xFF9CA3AF}
    };

    public static int preset = 0;
    public static int crosshairStyle = 0;
    public static float scale = 1f;
    public static float opacity = 0.72f; // прозрачность панелей
    public static double zoom = 0.25;

    static { reset(); }

    public static int c1() { return PRESETS[preset][0]; }
    public static int c2() { return PRESETS[preset][1]; }

    public static void reset() {
        Arrays.fill(ON, true);
        ON[SPEED] = false;
        ON[PING] = false;
        ON[LIGHT] = false;
        ON[TIME] = false;
        preset = 0;
        crosshairStyle = 0;
        scale = 1f;
        opacity = 0.72f;
        zoom = 0.25;
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("visuals.properties");
    }

    public static void load() {
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(f)) { p.load(in); }
            for (int i = 0; i < ON.length; i++) {
                ON[i] = Boolean.parseBoolean(p.getProperty("m" + i, String.valueOf(ON[i])));
            }
            preset = clampInt(Integer.parseInt(p.getProperty("preset", "0")), 0, PRESETS.length - 1);
            crosshairStyle = clampInt(Integer.parseInt(p.getProperty("style", "0")), 0, 3);
            scale = Math.max(0.7f, Math.min(1.5f, Float.parseFloat(p.getProperty("scale", "1"))));
            opacity = Math.max(0.3f, Math.min(1f, Float.parseFloat(p.getProperty("opacity", "0.72"))));
            zoom = Math.max(0.05, Math.min(0.6, Double.parseDouble(p.getProperty("zoom", "0.25"))));
        } catch (Exception ignored) {
            reset();
        }
    }

    public static void save() {
        try {
            Properties p = new Properties();
            for (int i = 0; i < ON.length; i++) p.setProperty("m" + i, String.valueOf(ON[i]));
            p.setProperty("preset", String.valueOf(preset));
            p.setProperty("style", String.valueOf(crosshairStyle));
            p.setProperty("scale", String.valueOf(scale));
            p.setProperty("opacity", String.valueOf(opacity));
            p.setProperty("zoom", String.valueOf(zoom));
            try (OutputStream out = Files.newOutputStream(file())) { p.store(out, "Useful Visuals"); }
        } catch (Exception ignored) {
        }
    }

    private static int clampInt(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
