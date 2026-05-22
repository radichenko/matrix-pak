package org.example.model;

/**
 * Зберігає один рядок таблиці бенчмарку — результат виміру часу
 * для заданого розміру матриць N та кількості потоків P.
 *
 * <p>Об'єкти {@code BenchmarkResult} формуються в {@code BenchmarkService}
 * і відображаються у TableView вкладки «Бенчмарк».
 * Значення прискорення та ефективності розраховуються конструктором
 * на основі базового часу T₁ (час при P=1 потоці).
 */
public final class BenchmarkResult {

    /** Розмір матриць (N×N). */
    private final int n;

    /** Кількість потоків при цьому вимірі. */
    private final int p;

    /** Середній час виконання програми в мілісекундах. */
    private final long elapsedMs;

    /**
     * Прискорення S = T(1) / T(P).
     * При P=1 завжди дорівнює 1.0.
     */
    private final double speedup;

    /**
     * Ефективність (завантаження) E = S / P.
     * При P=1 завжди дорівнює 1.0.
     */
    private final double efficiency;

    /** Номер програми: 1 — ПРГ1, 2 — ПРГ2, 3 — ПРГ3. */
    private final int prgNumber;

    // ─────────────────────────────────────────────────────────────
    //  Конструктор
    // ─────────────────────────────────────────────────────────────

    /**
     * Створює запис результату бенчмарку з автоматичним розрахунком
     * прискорення та ефективності.
     *
     * @param n          розмір матриць N
     * @param p          кількість потоків P
     * @param elapsedMs  виміряний час виконання (мс)
     * @param baselineMs час виконання при P=1 (мс) — база для розрахунку S
     * @param prgNumber  номер програми (1, 2 або 3)
     */
    public BenchmarkResult(int n, int p, long elapsedMs, long baselineMs, int prgNumber) {
        this.n         = n;
        this.p         = p;
        this.elapsedMs = elapsedMs;
        this.prgNumber = prgNumber;

        // Розрахунок прискорення: S = T(1) / T(P)
        // Захист від ділення на нуль — якщо elapsedMs=0, S=0
        this.speedup = (elapsedMs > 0 && baselineMs > 0)
                ? (double) baselineMs / elapsedMs
                : (p == 1 ? 1.0 : 0.0);

        // Ефективність: E = S / P
        this.efficiency = (p > 0) ? speedup / p : 0.0;
    }

    // ─────────────────────────────────────────────────────────────
    //  Гетери (використовуються PropertyValueFactory у TableView)
    // ─────────────────────────────────────────────────────────────

    /** @return розмір матриць N */
    public int getN() { return n; }

    /** @return кількість потоків P */
    public int getP() { return p; }

    /** @return час виконання у мілісекундах */
    public long getElapsedMs() { return elapsedMs; }

    /** @return прискорення S */
    public double getSpeedup() { return speedup; }

    /** @return ефективність E */
    public double getEfficiency() { return efficiency; }

    /** @return номер програми (1, 2 або 3) */
    public int getPrgNumber() { return prgNumber; }

    // ─────────────────────────────────────────────────────────────
    //  Рядкове представлення
    // ─────────────────────────────────────────────────────────────

    /**
     * Рядок для журналу та відлагодження.
     * Приклад: "ПРГ1 | N=500 | P=4 | T=312 мс | S=2.71 | E=0.678"
     */
    @Override
    public String toString() {
        return String.format(
                "ПРГ%d | N=%d | P=%d | T=%d мс | S=%.3f | E=%.3f",
                prgNumber, n, p, elapsedMs,
                speedup, efficiency
        );
    }
}
