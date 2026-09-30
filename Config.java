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
            "Компас", "График FPS", "Предупреждения", "Уведомления биндов",
            "Следы", "Круг прыжка", "GPS-метка", "Авто-команда",
            "Цветные хитбоксы", "Анимация чата", "Анимация таба", "Анимация окон", "Плавный хотбар"
    };
    public static final String[] DESCS = {
            "Кадры в секунду", "Позиция XYZ", "Сторона света и угол", "Блоков в секунду", "Задержка до сервера",
            "Уровень освещения", "Реальное время", "Иконки и таймеры", "Броня и предметы в руках",
            "WASD, мышь, клики в секунду", "Заменяет ванильный", "Имя и здоровье цели", "Плашка с названием мода",
            "В каком биоме вы стоите", "Время суток в мире", "Тотемы, яблоки, жемчуг, стрелы",
            "Вспышка при ударе по цели", "Анимация градиента и текста",
            "Полоса направлений сверху", "История кадров за 8 секунд", "Мало здоровья и прочности брони",
            "Показ при отправке команды",
            "Частицы за вами при движении", "Кольцо частиц при прыжке", "Стрелка и расстояние до точки",
            "Отправка команды по таймеру",
            "Рамки сущностей (скрыты за блоками)", "Плавное появление сообщений",
            "Плавное открытие списка игроков", "Плавное открытие инвентаря и окон", "Скользящая подсветка слота"
    };
    public static final int FPS = 0, COORDS = 1, DIR = 2, SPEED = 3, PING = 4, LIGHT = 5,
            TIME = 6, EFFECTS = 7, DURABILITY = 8, KEYS = 9, CROSSHAIR = 10, TARGET = 11, WATERMARK = 12,
            BIOME = 13, GAMETIME = 14, ITEMS = 15, HITMARKER = 16, ANIMATE = 17,
            COMPASS = 18, FPSGRAPH = 19, WARN = 20, NOTIFY = 21,
            TRAILS = 22, JUMPCIRCLE = 23, GPS = 24, AUTOCMD = 25,
            HITBOX = 26, ANIM_CHAT = 27, ANIM_TAB = 28, ANIM_GUI = 29, ANIM_HOTBAR = 30;

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
    public static boolean glass = true; // стиль Liquid Glass (по умолчанию включён)
    public static boolean hbPlayers = true, hbMobs = true, hbItems = false;
    public static int hbColor = 0;
    public static int hbRange = 24;
    public static float animSpeed = 1f;
    public static int particleType = 0;
    public static float circleSize = 1.5f;
    public static String gpsX = "", gpsZ = "";
    public static String autoCmd = "";
    public static int autoInterval = 60; // секунд, минимум 10
    // смещения элементов HUD (редактор позиций)
    public static final int[] offX = new int[10];
    public static final int[] offY = new int[10];
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
        ON[TRAILS] = false;
        ON[JUMPCIRCLE] = false;
        ON[GPS] = false;
        ON[AUTOCMD] = false;
        ON[HITBOX] = false;
        ON[ANIM_HOTBAR] = false;
        hbPlayers = true;
        hbMobs = true;
        hbItems = false;
        hbColor = 0;
        hbRange = 24;
        animSpeed = 1f;
        Arrays.fill(offX, 0);
        Arrays.fill(offY, 0);
        particleType = 0;
        circleSize = 1.5f;
        preset = 0;
        hue = 0.75f;
        crosshairStyle = 0;
        crosshairColor = 0;
        crosshairSize = 5;
        scale = 1f;
        opacity = 0.72f;
        radius = 4;
        infoRight = false;
        glass = true;
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
            glass = Boolean.parseBoolean(p.getProperty("glass", "true"));
            particleType = clampInt(Integer.parseInt(p.getProperty("ptype", "0")), 0, 5);
            hbPlayers = Boolean.parseBoolean(p.getProperty("hbp", "true"));
            hbMobs = Boolean.parseBoolean(p.getProperty("hbm", "true"));
            hbItems = Boolean.parseBoolean(p.getProperty("hbi", "false"));
            hbColor = clampInt(Integer.parseInt(p.getProperty("hbc", "0")), 0, 7);
            hbRange = clampInt(Integer.parseInt(p.getProperty("hbr", "24")), 8, 64);
            animSpeed = Math.max(0.5f, Math.min(2f, Float.parseFloat(p.getProperty("animspeed", "1"))));
            circleSize = Math.max(0.5f, Math.min(2.5f, Float.parseFloat(p.getProperty("circle", "1.5"))));
            gpsX = p.getProperty("gpsx", "");
            gpsZ = p.getProperty("gpsz", "");
            autoCmd = p.getProperty("autocmd", "");
            autoInterval = clampInt(Integer.parseInt(p.getProperty("autoint", "60")), 10, 300);
            for (int i = 0; i < offX.length; i++) {
                offX[i] = Integer.parseInt(p.getProperty("ox" + i, "0"));
                offY[i] = Integer.parseInt(p.getProperty("oy" + i, "0"));
            }
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
            p.setProperty("ptype", String.valueOf(particleType));
            p.setProperty("hbp", String.valueOf(hbPlayers));
            p.setProperty("hbm", String.valueOf(hbMobs));
            p.setProperty("hbi", String.valueOf(hbItems));
            p.setProperty("hbc", String.valueOf(hbColor));
            p.setProperty("hbr", String.valueOf(hbRange));
            p.setProperty("animspeed", String.valueOf(animSpeed));
            p.setProperty("circle", String.valueOf(circleSize));
            p.setProperty("gpsx", gpsX);
            p.setProperty("gpsz", gpsZ);
            p.setProperty("autocmd", autoCmd);
            p.setProperty("autoint", String.valueOf(autoInterval));
            for (int i = 0; i < offX.length; i++) {
                p.setProperty("ox" + i, String.valueOf(offX[i]));
                p.setProperty("oy" + i, String.valueOf(offY[i]));
            }
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
