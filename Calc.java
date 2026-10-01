package com.example.visuals;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Калькулятор: находит в тексте пример и считает ответ. */
public class Calc {
    private static final String NUM = "\\(*-?\\d+(?:[.,]\\d+)?\\)*";
    private static final String OP = "(?:\\s+[-+*/xXхХ×÷]\\s+|[-+*/xXхХ×÷])";
    private static final Pattern EXPR = Pattern.compile(
            "(?<![\\p{L}\\p{N}.,])" + NUM + "(?:" + OP + NUM + ")+(?![\\p{L}\\p{N}])");

    /** Возвращает {пример, ответ} или null, если в тексте нет примера. */
    public static String[] find(String text) {
        if (text == null) return null;
        Matcher m = EXPR.matcher(text);
        while (m.find()) {
            String expr = m.group().trim();
            String compact = expr.replaceAll("\\s+", "");
            boolean hasSpace = !expr.equals(compact);
            // даты, счёт и телефоны вида 12-05-2024 не считаем
            if (!hasSpace && compact.matches("[0-9.,()\\-]+")) continue;
            Double v = eval(compact);
            if (v == null || Math.abs(v) > 1e12) continue;
            return new String[]{expr, format(v)};
        }
        return null;
    }

    static Double eval(String s) {
        try {
            String t = s.replace('×', '*').replace('x', '*').replace('X', '*')
                    .replace('х', '*').replace('Х', '*').replace('÷', '/').replace(',', '.');
            Parser p = new Parser(t);
            double v = p.parseExpr();
            if (p.pos != t.length()) return null;
            if (Double.isNaN(v) || Double.isInfinite(v)) return null;
            return v;
        } catch (RuntimeException e) {
            return null;
        }
    }

    static String format(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9) return String.valueOf((long) Math.rint(v));
        return new BigDecimal(v).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static class Parser {
        final String s;
        int pos = 0;

        Parser(String s) { this.s = s; }

        double parseExpr() {
            double v = parseTerm();
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == '+') { pos++; v += parseTerm(); }
                else if (c == '-') { pos++; v -= parseTerm(); }
                else break;
            }
            return v;
        }

        double parseTerm() {
            double v = parseFactor();
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == '*') { pos++; v *= parseFactor(); }
                else if (c == '/') { pos++; v /= parseFactor(); }
                else break;
            }
            return v;
        }

        double parseFactor() {
            if (pos >= s.length()) throw new IllegalStateException();
            char c = s.charAt(pos);
            if (c == '-') { pos++; return -parseFactor(); }
            if (c == '+') { pos++; return parseFactor(); }
            if (c == '(') {
                pos++;
                double v = parseExpr();
                if (pos >= s.length() || s.charAt(pos) != ')') throw new IllegalStateException();
                pos++;
                return v;
            }
            int st = pos;
            while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) pos++;
            if (st == pos) throw new IllegalStateException();
            return Double.parseDouble(s.substring(st, pos));
        }
    }
}
