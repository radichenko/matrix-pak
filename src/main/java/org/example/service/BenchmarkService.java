package org.example.service;

import org.example.model.BenchmarkResult;
import org.example.model.Matrix;
import org.example.prg.AbstractPRG;
import org.example.prg.PRG1;
import org.example.prg.PRG2;
import org.example.prg.PRG3;
import org.example.utils.MatrixUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Сервіс для проведення серії бенчмаркових вимірювань паралельних програм.
 *
 * <p>Виконує повний цикл бенчмарку для заданої програми (ПРГ1, ПРГ2 або ПРГ3):
 * <ol>
 *   <li>Виділяє спільні вхідні матриці розміру N×N та заповнює їх один раз.</li>
 *   <li>Прогріває JVM (warmup-запуск з P=1 до початку вимірювань).</li>
 *   <li>Для кожного значення P із заданого списку виконує {@code runs} запусків,
 *       фіксує мінімальний час (найбільш стабільний результат).</li>
 *   <li>Розраховує прискорення S та ефективність E відносно базового часу T(P=1).</li>
 *   <li>Повертає список {@link BenchmarkResult} для відображення у TableView та графіках.</li>
 * </ol>
 *
 * <h3>Параметри бенчмарку за замовчуванням:</h3>
 * <ul>
 *   <li>Кількість повторів вимірювання: {@value #DEFAULT_RUNS}</li>
 *   <li>Список потоків: 1, 2, 3, 4, 6 (для 6-ядерного ноутбука)</li>
 * </ul>
 *
 * <h3>Потокобезпечність:</h3>
 * <p>Клас є stateless — кожен виклик {@link #run} незалежний.
 * Безпечний для виклику з JavaFX-потоку через {@link javafx.concurrent.Task}.
 */
public class BenchmarkService {

    /** Кількість запусків для кожного значення P за замовчуванням. */
    public static final int DEFAULT_RUNS = 3;

    /**
     * Список кількостей потоків за замовчуванням.
     * Підібрано під 6-ядерний ноутбук (стенд тестування).
     */
    public static final int[] DEFAULT_THREAD_COUNTS = {1, 2, 3, 4, 6};

    private final LogService log;

    // ─────────────────────────────────────────────────────────────
    //  Конструктор
    // ─────────────────────────────────────────────────────────────

    public BenchmarkService() {
        this.log = LogService.getInstance();
    }

    // ─────────────────────────────────────────────────────────────
    //  Головний метод
    // ─────────────────────────────────────────────────────────────

    /**
     * Запускає повний бенчмарк для заданої програми.
     *
     * @param prgNumber    номер програми: 1 — ПРГ1, 2 — ПРГ2, 3 — ПРГ3
     * @param N            розмір квадратних матриць N×N
     * @param threadCounts масив значень P для перебору (наприклад, {1,2,3,4,6})
     * @param runs         кількість повторів для кожного P (береться мінімальний час)
     * @param onProgress   колбек прогресу: викликається після кожного виміру
     *                     з поточним індексом (0-based) та загальною кількістю кроків.
     *                     Може бути {@code null}.
     * @return список {@link BenchmarkResult} — по одному запису на кожне P
     * @throws IllegalArgumentException якщо prgNumber не в діапазоні [1,3]
     */
    public List<BenchmarkResult> run(int prgNumber, int N,
                                     int[] threadCounts, int runs,
                                     ProgressCallback onProgress) {
        if (prgNumber < 1 || prgNumber > 3) {
            throw new IllegalArgumentException(
                    "Номер програми має бути 1, 2 або 3. Отримано: " + prgNumber);
        }
        if (N <= 0) {
            throw new IllegalArgumentException("N має бути > 0. Отримано: " + N);
        }
        if (threadCounts == null || threadCounts.length == 0) {
            throw new IllegalArgumentException("threadCounts не може бути порожнім.");
        }

        log.info(String.format(
                "═══ БЕНЧМАРК СТАРТ: ПРГ%d | N=%d | повторів=%d | потоки=%s",
                prgNumber, N, runs, arrayToString(threadCounts)));

        // ── 1. Виділення та заповнення спільних вхідних матриць ──
        double[][][] inputs = allocateInputs(prgNumber, N);

        // ── 2. Прогрів JVM ────────────────────────────────────────
        log.info("  Прогрівання JVM (1 запуск P=1)...");
        createPRG(prgNumber, N, 1).execute(inputs);

        // ── 3. Вимірювання ────────────────────────────────────────
        List<BenchmarkResult> results = new ArrayList<>();
        long baselineMs = -1;
        int totalSteps = threadCounts.length;

        for (int step = 0; step < totalSteps; step++) {
            int p = threadCounts[step];

            // Пропускаємо P > N (неможливо розподілити)
            if (p > N) {
                log.warn(String.format("  P=%d > N=%d — пропущено.", p, N));
                continue;
            }

            long bestMs = Long.MAX_VALUE;
            for (int run = 0; run < runs; run++) {
                AbstractPRG prg = createPRG(prgNumber, N, p);
                long ms = prg.execute(inputs).getElapsedMs();
                if (ms < bestMs) bestMs = ms;
            }

            if (p == 1 || baselineMs < 0) baselineMs = bestMs;

            BenchmarkResult result = new BenchmarkResult(N, p, bestMs, baselineMs, prgNumber);
            results.add(result);

            log.info("  " + result);

            if (onProgress != null) {
                onProgress.onStep(step, totalSteps, result);
            }
        }

        log.info(String.format("═══ БЕНЧМАРК ЗАВЕРШЕНО: ПРГ%d | N=%d | %d точок",
                prgNumber, N, results.size()));

        return results;
    }

    /**
     * Перевантажений метод із параметрами за замовчуванням.
     * Використовує {@link #DEFAULT_THREAD_COUNTS} та {@link #DEFAULT_RUNS}.
     *
     * @param prgNumber номер програми (1, 2 або 3)
     * @param N         розмір матриць
     * @return список результатів бенчмарку
     */
    public List<BenchmarkResult> run(int prgNumber, int N) {
        return run(prgNumber, N, DEFAULT_THREAD_COUNTS, DEFAULT_RUNS, null);
    }

    /**
     * Перевантажений метод з колбеком прогресу та параметрами за замовчуванням.
     *
     * @param prgNumber  номер програми
     * @param N          розмір матриць
     * @param onProgress колбек прогресу
     * @return список результатів
     */
    public List<BenchmarkResult> run(int prgNumber, int N, ProgressCallback onProgress) {
        return run(prgNumber, N, DEFAULT_THREAD_COUNTS, DEFAULT_RUNS, onProgress);
    }

    // ─────────────────────────────────────────────────────────────
    //  Фабрика програм та матриць
    // ─────────────────────────────────────────────────────────────

    /**
     * Створює екземпляр потрібної програми за номером.
     *
     * @param prgNumber номер програми (1, 2 або 3)
     * @param N         розмір матриць
     * @param P         кількість потоків
     * @return новий екземпляр AbstractPRG
     */
    private AbstractPRG createPRG(int prgNumber, int N, int P) {
        return switch (prgNumber) {
            case 1 -> new PRG1(N, P);
            case 2 -> new PRG2(N, P);
            case 3 -> new PRG3(N, P);
            default -> throw new IllegalArgumentException("Невідомий номер програми: " + prgNumber);
        };
    }

    /**
     * Виділяє та заповнює вхідні матриці для відповідної програми.
     *
     * <p>Матриці заповнюються ОДИН раз для всієї серії вимірів,
     * щоб результати для різних P були порівнянними.
     *
     * <ul>
     *   <li>ПРГ1: MB, MC, MD</li>
     *   <li>ПРГ2: MB, MC, MD, ME</li>
     *   <li>ПРГ3: MR, MB, MC</li>
     * </ul>
     *
     * @param prgNumber номер програми
     * @param N         розмір матриць
     * @return масив {@code double[][][]} з вхідними матрицями
     */
    private double[][][] allocateInputs(int prgNumber, int N) {
        int count = (prgNumber == 2) ? 4 : 3;
        double[][][] inputs = new double[count][][];

        String[] names = switch (prgNumber) {
            case 1 -> new String[]{"MB", "MC", "MD"};
            case 2 -> new String[]{"MB", "MC", "MD", "ME"};
            case 3 -> new String[]{"MR", "MB", "MC"};
            default -> throw new IllegalArgumentException("Невідомий номер програми: " + prgNumber);
        };

        for (int i = 0; i < count; i++) {
            inputs[i] = new double[N][N];
            MatrixUtils.fillRandom(new Matrix(inputs[i], names[i]));
            log.debug(String.format("  Матриця %s (%d×%d) заповнена.", names[i], N, N));
        }
        return inputs;
    }

    // ─────────────────────────────────────────────────────────────
    //  Інтерфейс колбеку прогресу
    // ─────────────────────────────────────────────────────────────

    /**
     * Функціональний інтерфейс для відстеження прогресу бенчмарку.
     * Використовується у BenchmarkTabController для оновлення ProgressBar.
     */
    @FunctionalInterface
    public interface ProgressCallback {
        /**
         * Викликається після завершення вимірювання для одного значення P.
         *
         * @param stepIndex   поточний крок (0-based)
         * @param totalSteps  загальна кількість кроків
         * @param result      результат поточного кроку
         */
        void onStep(int stepIndex, int totalSteps, BenchmarkResult result);
    }

    // ─────────────────────────────────────────────────────────────
    //  Допоміжні методи
    // ─────────────────────────────────────────────────────────────

    private static String arrayToString(int[] arr) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < arr.length; i++) {
            sb.append(arr[i]);
            if (i < arr.length - 1) sb.append(", ");
        }
        return sb.append("]").toString();
    }
}
