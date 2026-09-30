package com.example.visuals;

import net.minecraftforge.fml.loading.FMLPaths;

import java.awt.Color;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;

public class Config {
    public static final String[] NAMES = {
            "FPS", "Координаты", "Курс", "Скорость", "Пинг", "Свет",
            "Время", "Эффекты зелий", "Прочность", "Клавиши и CPS", "Свой прицел", "Инфо о цели", "Ватермарка",
            "Биом", "Игровое время", "Счётчик предметов", "Хит-маркер", "Переливание цвета",
            "Компас", "График FPS", "Предупреждения", "Уведомления биндов"
    };
    public static final String[] DESCS = {
            "Кадры в секунду", "Позиция XYZ", "Сторона света и угол", "Блоков в секунду", "Задержка до сервера",
            "Уровень освещения", "Реальное время", "Иконки и таймеры", "Броня и предметы в руках",
            "WASD, мышь, клики в секунду", "Заменяет ванильный", "Имя и здоровье цели", "Плашка с названием мода",
            "В каком биоме вы стоите", "Время суток в мире", "Тотемы, яблоки, жемчуг, стрелы",
            "Вспышка при ударе по цели", "Анимация градиента и текста",
            "Полоса направлений сверху", "История кадров за 8 секунд", "Мало здоровья и прочности брони",
            "Показ при отправке команды"
    };
    public static final int FPS = 0, COORDS = 1, DIR = 2, SPEED = 3, PING = 4, LIGHT = 5,
            TIME = 6, EFFECTS = 7, DURABILITY = 8, KEYS = 9, CROSSHAIR = 10, TARGET = 11, WATERMARK = 12,
            BIOME = 13, GAMETIME = 14, ITEMS = 15, HITMARKER = 16, ANIMATE = 17,
            COMPASS = 18, FPSGRAPH = 19, WARN = 20, NOTIFY = 21;

    public static final boolean[] ON = new boolean[NAMES.length];

    // 0-5 готовые пары, 6 - «свой» оттенок, 7 - радуга
    public static final int[][] PRESETS = {
            {0xFF8B5CFF, 0xFF2DD4FF},
            {0xFF22C55E, 0xFFA3E635},
            {0xFFFF4D6D, 0xFFFF9F43},
            {0xFF3B82F6, 0xFF22D3EE},
            {0xFFFF5CAA, 0xFF8B5CFF},
            {0xFFF5F5F5, 0xFF9CA3AF}
    };
    public static final int PRESET_COUNT = 8;

    // ---- бинды: клавиша -> команда ----
    public static final int BIND_COUNT = 5;
    public static final int[] bindKey = new int[BIND_COUNT];
    public static final String[] bindCmd = new String[BIND_COUNT];

    public static int preset = 0;
    public static float hue = 0.75f;
    public static int crosshairStyle = 0;
    public static int crosshairColor = 0;
    public static int crosshairSize = 5;
    public static float scale = 1f;
    public static float opacity = 0.72f;
    public static int radius = 4;
    public static boolean infoRight = false;
    public static boolean glass = false; // стиль Liquid Glass
    public static double zoom = 0.25;

    static {
        Arrays.fill(bindKey, -1);
        Arrays.fill(bindCmd, "");
        reset();
    }

    static int hsb(float h, float s, float v) {
        return 0xFF000000 | (Color.HSBtoRGB(h, s, v) & 0x00FFFFFF);
    }

    private static float rainbow() {
        return (System.currentTimeMillis() % 8000L) / 8000f;
    }

    public static int c1() {
        if (preset == 6) return hsb(hue, 0.75f, 1f);
        if (preset == 7) return hsb(rainbow(), 0.7f, 1f);
        return PRESETS[preset][0];
    }

    public static int c2() {
        if (preset == 6) return hsb((hue + 0.12f) % 1f, 0.8f, 1f);
        if (preset == 7) return hsb((rainbow() + 0.15f) % 1f, 0.7f, 1f);
        return PRESETS[preset][1];
    }

    public static int swatch1(int k) {
        if (k == 6) return hsb(hue, 0.75f, 1f);
        if (k == 7) return hsb(rainbow(), 0.7f, 1f);
        return PRESETS[k][0];
    }

    public static int swatch2(int k) {
        if (k == 6) return hsb((hue + 0.12f) % 1f, 0.8f, 1f);
        if (k == 7) return hsb((rainbow() + 0.15f) % 1f, 0.7f, 1f);
        return PRESETS[k][1];
    }

    /** Сброс внешнего вида и модулей (бинды не трогаем). */
    public static void reset() {
        Arrays.fill(ON, true);
        ON[SPEED] = false;
        ON[PING] = false;
        ON[LIGHT] = false;
        ON[TIME] = false;
        ON[BIOME] = false;
        ON[GAMETIME] = false;
        ON[ITEMS] = false;
        ON[COMPASS] = false;
        ON[FPSGRAPH] = false;
        preset = 0;
        hue = 0.75f;
        crosshairStyle = 0;
        crosshairColor = 0;
        crosshairSize = 5;
        scale = 1f;
        opacity = 0.72f;
        radius = 4;
        infoRight = false;
        glass = false;
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
            preset = clampInt(Integer.parseInt(p.getProperty("preset", "0")), 0, PRESET_COUNT - 1);
            hue = Math.max(0f, Math.min(1f, Float.parseFloat(p.getProperty("hue", "0.75"))));
            crosshairStyle = clampInt(Integer.parseInt(p.getProperty("style", "0")), 0, 3);
            crosshairColor = clampInt(Integer.parseInt(p.getProperty("chcolor", "0")), 0, 4);
            crosshairSize = clampInt(Integer.parseInt(p.getProperty("chsize", "5")), 3, 9);
            scale = Math.max(0.7f, Math.min(1.5f, Float.parseFloat(p.getProperty("scale", "1"))));
            opacity = Math.max(0.3f, Math.min(1f, Float.parseFloat(p.getProperty("opacity", "0.72"))));
            radius = clampInt(Integer.parseInt(p.getProperty("radius", "4")), 0, 8);
            infoRight = Boolean.parseBoolean(p.getProperty("inforight", "false"));
            glass = Boolean.parseBoolean(p.getProperty("glass", "false"));
            zoom = Math.max(0.05, Math.min(0.6, Double.parseDouble(p.getProperty("zoom", "0.25"))));
            for (int i = 0; i < BIND_COUNT; i++) {
                bindKey[i] = Integer.parseInt(p.getProperty("bk" + i, "-1"));
                bindCmd[i] = p.getProperty("bc" + i, "");
            }
        } catch (Exception ignored) {
            reset();
        }
    }

    public static void save() {
        try {
            Properties p = new Properties();
            for (int i = 0; i < ON.length; i++) p.setProperty("m" + i, String.valueOf(ON[i]));
            p.setProperty("preset", String.valueOf(preset));
            p.setProperty("hue", String.valueOf(hue));
            p.setProperty("style", String.valueOf(crosshairStyle));
            p.setProperty("chcolor", String.valueOf(crosshairColor));
            p.setProperty("chsize", String.valueOf(crosshairSize));
            p.setProperty("scale", String.valueOf(scale));
            p.setProperty("opacity", String.valueOf(opacity));
            p.setProperty("radius", String.valueOf(radius));
            p.setProperty("inforight", String.valueOf(infoRight));
            p.setProperty("glass", String.valueOf(glass));
            p.setProperty("zoom", String.valueOf(zoom));
            for (int i = 0; i < BIND_COUNT; i++) {
                p.setProperty("bk" + i, String.valueOf(bindKey[i]));
                p.setProperty("bc" + i, bindCmd[i]);
            }
            try (OutputStream out = Files.newOutputStream(file())) { p.store(out, "Useful Visuals"); }
        } catch (Exception ignored) {
        }
    }

    private static int clampInt(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
