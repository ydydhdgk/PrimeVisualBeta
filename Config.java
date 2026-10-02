package com.example.visuals;

import net.minecraftforge.fml.loading.FMLPaths;

import java.awt.Color;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

public class Config {
    public static final String[] NAMES = {
            "FPS", "Координаты", "Курс", "Скорость", "Пинг", "Свет",
            "Время", "Эффекты зелий", "Прочность", "Клавиши и CPS", "Свой прицел", "Инфо о цели", "Ватермарка",
            "Биом", "Игровое время", "Счётчик предметов", "Хит-маркер", "Переливание цвета",
            "Компас", "График FPS", "Предупреждения", "Уведомления биндов",
            "Следы", "Круг прыжка", "GPS-метка", "Авто-команда",
            "Цветные хитбоксы", "Анимация чата", "Анимация таба", "Анимация окон", "Плавный хотбар",
            "Авто-спринт", "Яркость", "Точка смерти", "Время в чате", "Кинокамера при зуме", "Погода", "Голод",
            "Калькулятор", "Свой плащ", "Китайская шляпа", "Нимб"
    };
    public static final String[] DESCS = {
            "Кадры в секунду", "Позиция XYZ", "Сторона света и угол", "Блоков в секунду", "Задержка до сервера",
            "Уровень освещения", "Реальное время", "Иконки и таймеры", "Броня и предметы в руках",
            "WASD, мышь, клики в секунду", "Заменяет ванильный", "Имя, здоровье и экипировка цели", "Плашка с названием мода",
            "В каком биоме вы стоите", "Время суток в мире", "Тотемы, яблоки, жемчуг, стрелы",
            "Вспышка при ударе по цели", "Анимация градиента и текста",
            "Полоса направлений сверху", "История кадров за 8 секунд", "Мало здоровья и прочности брони",
            "Показ при отправке команды",
            "Частицы за вами при движении", "Кольцо частиц при прыжке", "Стрелка и расстояние до точки",
            "Отправка команды по таймеру",
            "Рамки сущностей (скрыты за блоками)", "Плавное появление сообщений",
            "Плавное открытие списка игроков", "Плавное открытие инвентаря и окон", "Скользящая подсветка слота",
            "Бежать без удержания Ctrl", "Освещение как днём (Fullbright)", "Запомнить место смерти и включить GPS",
            "[ЧЧ:ММ] перед сообщениями", "Плавный поворот камеры при зуме", "Ясно, дождь или гроза", "Сытость персонажа",
            "Считает примеры из чата и подсказывает ответ",
            "Плащ с вашей картинкой (видите только вы)", "Соломенная шляпа-конус на голове",
            "Светящееся кольцо над головой"
    };
    public static final int FPS = 0, COORDS = 1, DIR = 2, SPEED = 3, PING = 4, LIGHT = 5,
            TIME = 6, EFFECTS = 7, DURABILITY = 8, KEYS = 9, CROSSHAIR = 10, TARGET = 11, WATERMARK = 12,
            BIOME = 13, GAMETIME = 14, ITEMS = 15, HITMARKER = 16, ANIMATE = 17,
            COMPASS = 18, FPSGRAPH = 19, WARN = 20, NOTIFY = 21,
            TRAILS = 22, JUMPCIRCLE = 23, GPS = 24, AUTOCMD = 25,
            HITBOX = 26, ANIM_CHAT = 27, ANIM_TAB = 28, ANIM_GUI = 29, ANIM_HOTBAR = 30,
            SPRINT = 31, BRIGHT = 32, DEATHPOINT = 33, CHATTIME = 34, CINEZOOM = 35, WEATHER = 36, HUNGER = 37,
            CALC = 38, CAPE = 39, HAT = 40, HALO = 41;

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
    public static final int BIND_COUNT = 8;
    public static final int[] bindKey = new int[BIND_COUNT];
    public static final String[] bindCmd = new String[BIND_COUNT];
    public static int calcKey = -1;  // клавиша «отправить ответ калькулятора»
    public static int hudKey = -1;   // клавиша «скрыть/показать HUD мода»
    public static boolean hudHidden = false; // не сохраняется

    public static int lastTab = 0;   // последняя открытая вкладка меню
    public static int preset = 0;
    public static float hue = 0.75f;
    public static int crosshairStyle = 0;
    public static int crosshairColor = 0;
    public static int crosshairSize = 5;
    public static int crosshairThick = 1;
    public static boolean crosshairOutline = true;
    public static float scale = 1f;
    public static float opacity = 0.72f;
    public static int radius = 4;
    public static boolean infoRight = false;
    public static boolean glass = true;
    public static boolean textShadow = true;
    public static boolean sounds = true;
    public static boolean keysMouse = true, keysSpace = true;
    public static int keysSize = 22;
    public static String wmText = "PrimeVisual";
    public static boolean wmFps = true, wmTime = true;
    public static boolean hbPlayers = true, hbMobs = true, hbItems = false, hbOthers = false, hbEye = false;
    public static int hbColor = 0;
    public static int hbRange = 64;
    public static float animSpeed = 1f;
    public static int hatColor = 0, haloColor = 0;
    public static float hatScale = 1f, haloScale = 1f, haloHeight = 0f;
    public static float brightness = 10f;
    public static int particleType = 0;
    public static int trailDensity = 2;
    public static float circleSize = 1.5f;
    public static String gpsX = "", gpsZ = "";
    public static String autoCmd = "";
    public static int autoInterval = 60;
    public static double zoom = 0.25;
    // смещения элементов HUD (редактор позиций)
    public static final int[] offX = new int[10];
    public static final int[] offY = new int[10];

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

    /** Сброс: все функции выключены, внешний вид по умолчанию (бинды и текстовые поля не трогаем). */
    public static void reset() {
        Arrays.fill(ON, false);
        ON[ANIMATE] = true; // переливание - часть стиля, а не функция
        Arrays.fill(offX, 0);
        Arrays.fill(offY, 0);
        preset = 0;
        hue = 0.75f;
        crosshairStyle = 0;
        crosshairColor = 0;
        crosshairSize = 5;
        crosshairThick = 1;
        crosshairOutline = true;
        scale = 1f;
        opacity = 0.72f;
        radius = 4;
        infoRight = false;
        glass = true;
        textShadow = true;
        sounds = true;
        keysMouse = true;
        keysSpace = true;
        keysSize = 22;
        wmFps = true;
        wmTime = true;
        hbPlayers = true;
        hbMobs = true;
        hbItems = false;
        hbOthers = false;
        hbEye = false;
        hbColor = 0;
        hbRange = 64;
        animSpeed = 1f;
        brightness = 10f;
        particleType = 0;
        trailDensity = 2;
        circleSize = 1.5f;
        hatColor = 0;
        haloColor = 0;
        hatScale = 1f;
        haloScale = 1f;
        haloHeight = 0f;
        zoom = 0.25;
    }

    // ============================ файлы и конфиг-профили ============================
    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("visuals.properties");
    }

    private static Path profileDir() {
        return FMLPaths.CONFIGDIR.get().resolve("visuals-configs");
    }

    public static String sanitize(String n) {
        StringBuilder sb = new StringBuilder();
        for (char c : n.toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == ' ' || c == '_' || c == '-') sb.append(c);
        }
        String r = sb.toString().trim();
        return r.length() > 24 ? r.substring(0, 24).trim() : r;
    }

    public static List<String> listProfiles() {
        List<String> out = new ArrayList<>();
        try {
            Path dir = profileDir();
            if (Files.isDirectory(dir)) {
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.properties")) {
                    for (Path p : ds) {
                        String n = p.getFileName().toString();
                        out.add(n.substring(0, n.length() - ".properties".length()));
                    }
                }
            }
        } catch (Exception ignored) {
        }
        Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    public static boolean saveProfile(String name) {
        String n = sanitize(name);
        return !n.isEmpty() && writeTo(profileDir().resolve(n + ".properties"));
    }

    public static boolean loadProfile(String name) {
        boolean ok = readFrom(profileDir().resolve(sanitize(name) + ".properties"));
        if (ok) save();
        return ok;
    }

    public static boolean deleteProfile(String name) {
        try {
            return Files.deleteIfExists(profileDir().resolve(sanitize(name) + ".properties"));
        } catch (Exception e) {
            return false;
        }
    }

    public static void load() { readFrom(file()); }

    public static void save() { writeTo(file()); }

    private static boolean writeTo(Path f) {
        try {
            Files.createDirectories(f.getParent());
            Properties p = new Properties();
            p.setProperty("ver", "2");
            for (int i = 0; i < ON.length; i++) p.setProperty("m" + i, String.valueOf(ON[i]));
            p.setProperty("tab", String.valueOf(lastTab));
            p.setProperty("preset", String.valueOf(preset));
            p.setProperty("hue", String.valueOf(hue));
            p.setProperty("style", String.valueOf(crosshairStyle));
            p.setProperty("chcolor", String.valueOf(crosshairColor));
            p.setProperty("chsize", String.valueOf(crosshairSize));
            p.setProperty("chthick", String.valueOf(crosshairThick));
            p.setProperty("choutline", String.valueOf(crosshairOutline));
            p.setProperty("scale", String.valueOf(scale));
            p.setProperty("opacity", String.valueOf(opacity));
            p.setProperty("radius", String.valueOf(radius));
            p.setProperty("inforight", String.valueOf(infoRight));
            p.setProperty("glass", String.valueOf(glass));
            p.setProperty("textshadow", String.valueOf(textShadow));
            p.setProperty("sounds", String.valueOf(sounds));
            p.setProperty("keysmouse", String.valueOf(keysMouse));
            p.setProperty("keysspace", String.valueOf(keysSpace));
            p.setProperty("keyssize", String.valueOf(keysSize));
            p.setProperty("wmtext", wmText);
            p.setProperty("wmfps", String.valueOf(wmFps));
            p.setProperty("wmtime", String.valueOf(wmTime));
            p.setProperty("hbp", String.valueOf(hbPlayers));
            p.setProperty("hbm", String.valueOf(hbMobs));
            p.setProperty("hbi", String.valueOf(hbItems));
            p.setProperty("hbo", String.valueOf(hbOthers));
            p.setProperty("hbe", String.valueOf(hbEye));
            p.setProperty("hbc", String.valueOf(hbColor));
            p.setProperty("hbr", String.valueOf(hbRange));
            p.setProperty("animspeed", String.valueOf(animSpeed));
            p.setProperty("bright", String.valueOf(brightness));
            p.setProperty("ptype", String.valueOf(particleType));
            p.setProperty("trail", String.valueOf(trailDensity));
            p.setProperty("circle", String.valueOf(circleSize));
            p.setProperty("gpsx", gpsX);
            p.setProperty("gpsz", gpsZ);
            p.setProperty("autocmd", autoCmd);
            p.setProperty("autoint", String.valueOf(autoInterval));
            p.setProperty("zoom", String.valueOf(zoom));
            p.setProperty("hatcolor", String.valueOf(hatColor));
            p.setProperty("halocolor", String.valueOf(haloColor));
            p.setProperty("hatscale", String.valueOf(hatScale));
            p.setProperty("haloscale", String.valueOf(haloScale));
            p.setProperty("haloheight", String.valueOf(haloHeight));
            p.setProperty("calckey", String.valueOf(calcKey));
            p.setProperty("hudkey", String.valueOf(hudKey));
            for (int i = 0; i < BIND_COUNT; i++) {
                p.setProperty("bk" + i, String.valueOf(bindKey[i]));
                p.setProperty("bc" + i, bindCmd[i]);
            }
            for (int i = 0; i < offX.length; i++) {
                p.setProperty("ox" + i, String.valueOf(offX[i]));
                p.setProperty("oy" + i, String.valueOf(offY[i]));
            }
            try (OutputStream out = Files.newOutputStream(f)) { p.store(out, "Useful Visuals"); }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean readFrom(Path f) {
        try {
            if (!Files.exists(f)) return false;
            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(f)) { p.load(in); }
            // старые файлы (до версии 2): включённые функции не переносим - при старте всё выключено
            if ("2".equals(p.getProperty("ver"))) {
                for (int i = 0; i < ON.length; i++) ON[i] = getb(p, "m" + i, ON[i]);
            }
            lastTab = geti(p, "tab", lastTab, 0, 5);
            preset = geti(p, "preset", preset, 0, PRESET_COUNT - 1);
            hue = getf(p, "hue", hue, 0f, 1f);
            crosshairStyle = geti(p, "style", crosshairStyle, 0, 3);
            crosshairColor = geti(p, "chcolor", crosshairColor, 0, 5);
            crosshairSize = geti(p, "chsize", crosshairSize, 3, 9);
            crosshairThick = geti(p, "chthick", crosshairThick, 1, 3);
            crosshairOutline = getb(p, "choutline", crosshairOutline);
            scale = getf(p, "scale", scale, 0.7f, 1.5f);
            opacity = getf(p, "opacity", opacity, 0.3f, 1f);
            radius = geti(p, "radius", radius, 0, 8);
            infoRight = getb(p, "inforight", infoRight);
            glass = getb(p, "glass", glass);
            textShadow = getb(p, "textshadow", textShadow);
            sounds = getb(p, "sounds", sounds);
            keysMouse = getb(p, "keysmouse", keysMouse);
            keysSpace = getb(p, "keysspace", keysSpace);
            keysSize = geti(p, "keyssize", keysSize, 20, 30);
            wmText = p.getProperty("wmtext", wmText);
            wmFps = getb(p, "wmfps", wmFps);
            wmTime = getb(p, "wmtime", wmTime);
            hbPlayers = getb(p, "hbp", hbPlayers);
            hbMobs = getb(p, "hbm", hbMobs);
            hbItems = getb(p, "hbi", hbItems);
            hbOthers = getb(p, "hbo", hbOthers);
            hbEye = getb(p, "hbe", hbEye);
            hbColor = geti(p, "hbc", hbColor, 0, 8);
            hbRange = geti(p, "hbr", hbRange, 8, 128);
            animSpeed = getf(p, "animspeed", animSpeed, 0.5f, 2f);
            brightness = getf(p, "bright", brightness, 1f, 15f);
            particleType = geti(p, "ptype", particleType, 0, 6);
            trailDensity = geti(p, "trail", trailDensity, 1, 6);
            circleSize = getf(p, "circle", circleSize, 0.5f, 2.5f);
            gpsX = p.getProperty("gpsx", gpsX);
            gpsZ = p.getProperty("gpsz", gpsZ);
            autoCmd = p.getProperty("autocmd", autoCmd);
            autoInterval = geti(p, "autoint", autoInterval, 10, 300);
            zoom = getf(p, "zoom", (float) zoom, 0.05f, 0.6f);
            hatColor = geti(p, "hatcolor", hatColor, 0, 2);
            haloColor = geti(p, "halocolor", haloColor, 0, 2);
            hatScale = getf(p, "hatscale", hatScale, 0.7f, 1.5f);
            haloScale = getf(p, "haloscale", haloScale, 0.6f, 1.6f);
            haloHeight = getf(p, "haloheight", haloHeight, 0f, 0.3f);
            calcKey = geti(p, "calckey", calcKey, -1, 512);
            hudKey = geti(p, "hudkey", hudKey, -1, 512);
            for (int i = 0; i < BIND_COUNT; i++) {
                bindKey[i] = geti(p, "bk" + i, bindKey[i], -1, 512);
                bindCmd[i] = p.getProperty("bc" + i, bindCmd[i]);
            }
            for (int i = 0; i < offX.length; i++) {
                offX[i] = geti(p, "ox" + i, offX[i], -2000, 2000);
                offY[i] = geti(p, "oy" + i, offY[i], -2000, 2000);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static int geti(Properties p, String k, int def, int lo, int hi) {
        try {
            return Math.max(lo, Math.min(hi, Integer.parseInt(p.getProperty(k, String.valueOf(def)).trim())));
        } catch (Exception e) {
            return def;
        }
    }

    private static float getf(Properties p, String k, float def, float lo, float hi) {
        try {
            return Math.max(lo, Math.min(hi, Float.parseFloat(p.getProperty(k, String.valueOf(def)).trim())));
        } catch (Exception e) {
            return def;
        }
    }

    private static boolean getb(Properties p, String k, boolean def) {
        String v = p.getProperty(k);
        return v == null ? def : Boolean.parseBoolean(v.trim());
    }
}
