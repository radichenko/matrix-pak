package org.example.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Зберігає результат одного запуску паралельної програми (ПРГ1, ПРГ2 або ПРГ3).
 *
 * <p>Об'єкт є незмінним (immutable) після створення. Містить результуючу матрицю MA,
 * час виконання в мілісекундах та параметри запуску (N, P, номер програми).
 * Надає методи для обчислення прискорення та ефективності відносно базового виміру.
 */
public final class ExecutionResult {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    /** Результуюча матриця MA після виконання програми. */
    private final Matrix resultMA;

    /** Час виконання програми в мілісекундах. */
    private final long elapsedMs;

    /** Розмір матриць N (квадратні N×N). */
    private final int n;

    /** Кількість потоків, використаних при виконанні. */
    private final int p;

    /** Номер програми: 1 — ПРГ1, 2 — ПРГ2, 3 — ПРГ3. */
    private final int prgNumber;

    /** Часова мітка запуску. */
    private final LocalDateTime timestamp;

    // ─────────────────────────────────────────────────────────────
    //  Конструктор
    // ─────────────────────────────────────────────────────────────

    /**
     * Створює об'єкт результату виконання.
     *
     * @param resultMA  результуюча матриця MA (може бути null якщо виконання скасовано)
     * @param elapsedMs час виконання в мілісекундах (≥ 0)
     * @param n         розмір матриць N
     * @param p         кількість потоків P
     * @param prgNumber номер програми (1, 2 або 3)
     */
    public ExecutionResult(Matrix resultMA, long elapsedMs, int n, int p, int prgNumber) {
        this.resultMA  = resultMA;
        this.elapsedMs = elapsedMs;
        this.n         = n;
        this.p         = p;
        this.prgNumber = prgNumber;
        this.timestamp = LocalDateTime.now();
    }

    // ─────────────────────────────────────────────────────────────
    //  Гетери
    // ─────────────────────────────────────────────────────────────

    /** @return результуюча матриця MA */
    public Matrix getResultMA() { return resultMA; }

    /** @return час виконання в мілісекундах */
    public long getElapsedMs() { return elapsedMs; }

    /** @return розмір матриць N */
    public int getN() { return n; }

    /** @return кількість потоків P */
    public int getP() { return p; }

    /** @return номер програми (1, 2 або 3) */
    public int getPrgNumber() { return prgNumber; }

    /** @return часова мітка запуску */
    public LocalDateTime getTimestamp() { return timestamp; }

    // ─────────────────────────────────────────────────────────────
    //  Обчислення метрик продуктивності
    // ─────────────────────────────────────────────────────────────

    /**
     * Обчислює прискорення відносно базового виміру (P=1).
     *
     * <p>Прискорення S = T(1) / T(P), де T(1) — час при одному потоці,
     * T(P) — час при P потоках. Якщо {@code elapsedMs == 0}, повертає 0.
     *
     * @param baselineMs час виконання при P=1 (мілісекунди)
     * @return значення прискорення S ≥ 0
     */
    public double getSpeedup(long baselineMs) {
        if (elapsedMs == 0) return 0.0;
        return (double) baselineMs / elapsedMs;
    }

    /**
     * Обчислює ефективність (завантаження) відносно базового виміру.
     *
     * <p>Ефективність E = S / P, де S — прискорення, P — кількість потоків.
     * Якщо P == 0, повертає 0.
     *
     * @param baselineMs час виконання при P=1 (мілісекунди)
     * @return значення ефективності E ∈ [0, 1]
     */
    public double getEfficiency(long baselineMs) {
        if (p == 0) return 0.0;
        return getSpeedup(baselineMs) / p;
    }

    // ─────────────────────────────────────────────────────────────
    //  Рядкове представлення
    // ─────────────────────────────────────────────────────────────

    /**
     * Повертає короткий рядок для відображення у рядку статусу або журналі.
     * Приклад: "[08:45:23] ПРГ1 | N=500 | P=4 | Час: 847 мс"
     */
    @Override
    public String toString() {
        return String.format("[%s] ПРГ%d | N=%d | P=%d | Час: %d мс",
                timestamp.format(TIME_FMT), prgNumber, n, p, elapsedMs);
    }
}
