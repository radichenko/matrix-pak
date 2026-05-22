package org.example.prg;

import org.example.model.ExecutionResult;
import org.example.model.Matrix;
import org.example.utils.MatrixUtils;
import org.example.utils.SyncUtils;

import java.util.concurrent.CountDownLatch;

/**
 * Програма ПРГ1 — паралельне обчислення виразу:
 * <pre>    MA = MB × (MC × MD)</pre>
 *
 * <p>Реалізує стовпцеву декомпозицію: H стовпців результату
 * розподіляються рівномірно між P робочими потоками.
 *
 * <h3>Схема виконання (T1 — головний потік):</h3>
 * <pre>
 *  T1:  [ініціалізація] → [створення потоків] → [старт] → [await d2] → [результат]
 *  Ti:  [фаза 1: MT_i] → [countDown d1] → [await d1] → [фаза 2: MA_i] → [countDown d2]
 * </pre>
 *
 * <h3>Розподіл стовпців:</h3>
 * <p>При H стовпцях і P потоках кожен потік обробляє ⌊H/P⌋ або ⌈H/P⌉ стовпців.
 * Перші {@code H % P} потоків отримують на 1 стовпець більше.
 * Реалізація розподілу — {@link MatrixUtils#getColStart} / {@link MatrixUtils#getColEnd}.
 *
 * <h3>Синхронізація:</h3>
 * <ul>
 *   <li><b>d1</b> ({@link CountDownLatch}(P)) — бар'єр між фазами 1 і 2;</li>
 *   <li><b>d2</b> ({@link CountDownLatch}(P)) — сигнал завершення для T1.</li>
 * </ul>
 * Критичних ділянок немає — кожен потік пише виключно у свій діапазон стовпців.
 */
public class PRG1 {

    /** Розмір квадратних матриць (N×N). */
    private final int N;

    /** Кількість робочих потоків P. */
    private final int P;

    // ─────────────────────────────────────────────────────────────
    //  Конструктор
    // ─────────────────────────────────────────────────────────────

    /**
     * Створює екземпляр ПРГ1.
     *
     * @param N розмір квадратних матриць (N×N)
     * @param P кількість робочих потоків (1 ≤ P ≤ N)
     * @throws IllegalArgumentException якщо N ≤ 0 або P ≤ 0 або P > N
     */
    public PRG1(int N, int P) {
        if (N <= 0) throw new IllegalArgumentException("N має бути > 0, отримано: " + N);
        if (P <= 0) throw new IllegalArgumentException("P має бути > 0, отримано: " + P);
        if (P > N)  throw new IllegalArgumentException(
                "P не може перевищувати N (P=" + P + ", N=" + N + ")");
        this.N = N;
        this.P = P;
    }

    // ─────────────────────────────────────────────────────────────
    //  Головний метод виконання
    // ─────────────────────────────────────────────────────────────

    /**
     * Виконує обчислення MA = MB × (MC × MD) з P паралельними потоками.
     *
     * <p>Послідовність дій T1 (головний потік):
     * <ol>
     *   <li>Виділення пам'яті для MB, MC, MD, MT, MA.</li>
     *   <li>Заповнення MB, MC, MD випадковими числами.</li>
     *   <li>Створення CountDownLatch d1(P) та d2(P).</li>
     *   <li>Розподіл стовпців і створення P об'єктів {@link PRG1WorkerThread}.</li>
     *   <li>Запуск таймера та старт усіх потоків.</li>
     *   <li>Очікування на d2 (поки всі потоки завершать фазу 2).</li>
     *   <li>Зупинка таймера, формування {@link ExecutionResult}.</li>
     * </ol>
     *
     * @return результат виконання з часом, N, P та результуючою матрицею MA
     */
    public ExecutionResult execute() {

        // ── 1. Виділення пам'яті ──────────────────────────────────
        double[][] MB = new double[N][N];
        double[][] MC = new double[N][N];
        double[][] MD = new double[N][N];
        double[][] MT = new double[N][N];  // проміжна, заповнюється потоками
        double[][] MA = new double[N][N];  // результат, заповнюється потоками

        // ── 2. Заповнення вхідних матриць ─────────────────────────
        // Обгортаємо у Matrix лише для зручності fillRandom;
        // потокам передаємо сирі масиви для максимальної швидкості.
        MatrixUtils.fillRandom(new Matrix(MB, "MB"));
        MatrixUtils.fillRandom(new Matrix(MC, "MC"));
        MatrixUtils.fillRandom(new Matrix(MD, "MD"));

        // ── 3. Примітиви синхронізації ────────────────────────────
        CountDownLatch d1 = SyncUtils.latch(P);  // бар'єр фази 1
        CountDownLatch d2 = SyncUtils.latch(P);  // бар'єр фази 2

        // ── 4. Створення та конфігурація потоків ──────────────────
        PRG1WorkerThread[] workers = new PRG1WorkerThread[P];
        for (int t = 0; t < P; t++) {
            int colStart = MatrixUtils.getColStart(N, P, t);
            int colEnd   = MatrixUtils.getColEnd(N, P, t);
            workers[t] = new PRG1WorkerThread(
                    t, colStart, colEnd,
                    N, MB, MC, MD, MT, MA,
                    d1, d2
            );
        }

        // ── 5. Старт таймера та запуск потоків ────────────────────
        // Таймер стартує безпосередньо перед першим потоком,
        // щоб враховувати накладні витрати на старт усіх потоків.
        long startNano = System.nanoTime();

        for (PRG1WorkerThread worker : workers) {
            worker.start();
        }

        // ── 6. T1 очікує завершення всіх потоків ─────────────────
        SyncUtils.awaitLatch(d2);

        // ── 7. Зупинка таймера ────────────────────────────────────
        long elapsedMs = (System.nanoTime() - startNano) / 1_000_000L;

        // ── 8. Формування результату ──────────────────────────────
        Matrix resultMA = new Matrix(MA, "MA");
        return new ExecutionResult(resultMA, elapsedMs, N, P, 1);
    }

    // ─────────────────────────────────────────────────────────────
    //  Перевантажений execute() з готовими матрицями
    //  (використовується у бенчмарку для повторних запусків
    //   з тими ж вхідними даними)
    // ─────────────────────────────────────────────────────────────

    /**
     * Виконує обчислення на заздалегідь заповнених матрицях.
     *
     * <p>Використовується в {@code BenchmarkService} для серії вимірів
     * при однакових вхідних даних та різній кількості потоків.
     * Матриці MB, MC, MD мають розмір N×N (де N задано у конструкторі).
     *
     * @param MB вхідна матриця MB (N×N, тільки читання)
     * @param MC вхідна матриця MC (N×N, тільки читання)
     * @param MD вхідна матриця MD (N×N, тільки читання)
     * @return результат виконання з часом та результуючою матрицею MA
     */
    public ExecutionResult execute(double[][] MB, double[][] MC, double[][] MD) {
        double[][] MT = new double[N][N];
        double[][] MA = new double[N][N];

        CountDownLatch d1 = SyncUtils.latch(P);
        CountDownLatch d2 = SyncUtils.latch(P);

        PRG1WorkerThread[] workers = new PRG1WorkerThread[P];
        for (int t = 0; t < P; t++) {
            int colStart = MatrixUtils.getColStart(N, P, t);
            int colEnd   = MatrixUtils.getColEnd(N, P, t);
            workers[t] = new PRG1WorkerThread(
                    t, colStart, colEnd,
                    N, MB, MC, MD, MT, MA,
                    d1, d2
            );
        }

        long startNano = System.nanoTime();
        for (PRG1WorkerThread worker : workers) worker.start();
        SyncUtils.awaitLatch(d2);
        long elapsedMs = (System.nanoTime() - startNano) / 1_000_000L;

        return new ExecutionResult(new Matrix(MA, "MA"), elapsedMs, N, P, 1);
    }

    // ─────────────────────────────────────────────────────────────
    //  Гетери
    // ─────────────────────────────────────────────────────────────

    /** @return розмір матриць N */
    public int getN() { return N; }

    /** @return кількість потоків P */
    public int getP() { return P; }
}
