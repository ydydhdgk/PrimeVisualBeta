package com.example.visuals;

import org.apache.logging.log4j.LogManager;

import java.util.HashMap;
import java.util.Map;

/** Защита от вылетов: ошибка в функции мода не должна ронять игру. */
public class Safe {
    private static final Map<String, Integer> FAILS = new HashMap<>();

    /** После 3 ошибок подряд обработчик отключается до перезапуска игры. */
    static boolean off(String name) {
        Integer n = FAILS.get(name);
        return n != null && n >= 3;
    }

    static void fail(String name, Throwable t) {
        int n = FAILS.merge(name, 1, Integer::sum);
        try {
            if (n <= 3) LogManager.getLogger("PrimeVisual").error("Ошибка в " + name + " (" + n + "/3)", t);
            Hud.toast("PrimeVisual: ошибка в " + name, Hud.RED);
        } catch (Throwable ignored) {
        }
    }
}
