package org.example.service;

import java.util.prefs.Preferences;

/**
 * Збереження та відновлення налаштувань користувача між сесіями.
 *
 * <p>Використовує {@link java.util.prefs.Preferences} (реєстр Windows /
 * ~/.java/.userPrefs на Linux/Mac). Налаштування зберігаються автоматично
 * без явного виклику «Зберегти».
 *
 * <h3>Збережені параметри:</h3>
 * <ul>
 *   <li>Остання обрана програма (1/2/3) для вкладки «Обчислення»</li>
 *   <li>Останній N для вкладки «Обчислення»</li>
 *   <li>Останній P для вкладки «Обчислення»</li>
 *   <li>Остання обрана програма для вкладки «Бенчмарк»</li>
 *   <li>Останній N для вкладки «Бенчмарк»</li>
 *   <li>Тема інтерфейсу (light/dark) — вже зберігається у MatrixPakApp</li>
 * </ul>
 */
public final class UserPreferences {

    private static final Preferences PREFS =
            Preferences.userNodeForPackage(UserPreferences.class);

    // Ключі
    private static final String KEY_COMPUTE_PRG = "compute.prg";
    private static final String KEY_COMPUTE_N   = "compute.n";
    private static final String KEY_COMPUTE_P   = "compute.p";
    private static final String KEY_BENCH_PRG   = "bench.prg";
    private static final String KEY_BENCH_N     = "bench.n";
    private static final String KEY_BENCH_RUNS  = "bench.runs";

    // Значення за замовчуванням
    public static final int DEFAULT_PRG   = 1;
    public static final int DEFAULT_N     = 500;
    public static final int DEFAULT_P     = 4;
    public static final int DEFAULT_RUNS  = 3;

    private UserPreferences() {}

    // ─────────────────────────────────────────────────────────────
    //  Вкладка «Обчислення»
    // ─────────────────────────────────────────────────────────────

    public static void setComputePrg(int prg)  { PREFS.putInt(KEY_COMPUTE_PRG, prg); }
    public static int  getComputePrg()         { return PREFS.getInt(KEY_COMPUTE_PRG, DEFAULT_PRG); }

    public static void setComputeN(int n)      { PREFS.putInt(KEY_COMPUTE_N, n); }
    public static int  getComputeN()           { return clamp(PREFS.getInt(KEY_COMPUTE_N, DEFAULT_N), 2, 5000); }

    public static void setComputeP(int p)      { PREFS.putInt(KEY_COMPUTE_P, p); }
    public static int  getComputeP()           {
        int maxCores = Runtime.getRuntime().availableProcessors();
        return clamp(PREFS.getInt(KEY_COMPUTE_P, Math.min(DEFAULT_P, maxCores)), 1, maxCores);
    }

    // ─────────────────────────────────────────────────────────────
    //  Вкладка «Бенчмарк»
    // ─────────────────────────────────────────────────────────────

    public static void setBenchPrg(int prg)    { PREFS.putInt(KEY_BENCH_PRG, prg); }
    public static int  getBenchPrg()           { return PREFS.getInt(KEY_BENCH_PRG, DEFAULT_PRG); }

    public static void setBenchN(int n)        { PREFS.putInt(KEY_BENCH_N, n); }
    public static int  getBenchN()             { return clamp(PREFS.getInt(KEY_BENCH_N, DEFAULT_N), 2, 5000); }

    public static void setBenchRuns(int runs)  { PREFS.putInt(KEY_BENCH_RUNS, runs); }
    public static int  getBenchRuns()          { return clamp(PREFS.getInt(KEY_BENCH_RUNS, DEFAULT_RUNS), 1, 10); }

    // ─────────────────────────────────────────────────────────────
    //  Утиліти
    // ─────────────────────────────────────────────────────────────

    /** Скидає всі налаштування до значень за замовчуванням. */
    public static void resetAll() {
        try { PREFS.clear(); } catch (Exception ignored) {}
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
