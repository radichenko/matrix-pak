package org.example.prg;

import org.example.model.ExecutionResult;
import org.example.model.Matrix;
import org.example.service.LogService;
import org.example.utils.MatrixUtils;

/**
 * Абстрактний базовий клас для паралельних програм ПРГ1, ПРГ2 та ПРГ3.
 *
 * <p>Інкапсулює спільну логіку всіх трьох програм:
 * <ul>
 *   <li>зберігання параметрів N, P та номера програми;</li>
 *   <li>посилання на {@link LogService} для журналювання з потоків;</li>
 *   <li>методи виділення та заповнення матриць;</li>
 *   <li>журналювання старту, завершення та результатів виконання;</li>
 *   <li>розрахунок та виведення метрик (S, E) відносно базового часу.</li>
 * </ul>
 *
 * <h3>Шаблон використання (Template Method):</h3>
 * <p>Підкласи реалізують {@link #execute()} та {@link #execute(double[][]...)}
 * з конкретною логікою потоків, при цьому викликаючи захищені методи
 * цього класу для журналювання та підготовки даних.
 *
 * <h3>Приклад:</h3>
 * <pre>
 *   AbstractPRG prg = new PRG1(500, 4);
 *   ExecutionResult result = prg.execute();
 *   System.out.println(result); // "[08:23:11] ПРГ1 | N=500 | P=4 | Час: 312 мс"
 * </pre>
 */
public abstract class AbstractPRG {

    /** Розмір квадратних матриць (N×N). */
    protected final int N;

    /** Кількість робочих потоків. */
    protected final int P;

    /** Номер програми: 1 — ПРГ1, 2 — ПРГ2, 3 — ПРГ3. */
    protected final int prgNumber;

    /** Сервіс журналювання — спільний синглтон. */
    protected final LogService log;

    // ─────────────────────────────────────────────────────────────
    //  Конструктор
    // ─────────────────────────────────────────────────────────────

    /**
     * Ініціалізує базові параметри програми.
     *
     * @param N         розмір квадратних матриць (N×N, > 0)
     * @param P         кількість потоків (1 ≤ P ≤ N)
     * @param prgNumber номер програми (1, 2 або 3)
     * @throws IllegalArgumentException якщо N ≤ 0, P ≤ 0 або P > N
     */
    protected AbstractPRG(int N, int P, int prgNumber) {
        if (N <= 0) throw new IllegalArgumentException(
                "N має бути > 0, отримано: " + N);
        if (P <= 0) throw new IllegalArgumentException(
                "P має бути > 0, отримано: " + P);
        if (P > N)  throw new IllegalArgumentException(
                "P не може перевищувати N (P=" + P + ", N=" + N + ")");

        this.N         = N;
        this.P         = P;
        this.prgNumber = prgNumber;
        this.log       = LogService.getInstance();
    }

    // ─────────────────────────────────────────────────────────────
    //  Абстрактний інтерфейс
    // ─────────────────────────────────────────────────────────────

    /**
     * Виконує програму: виділяє матриці, заповнює випадковими числами,
     * запускає потоки, вимірює час.
     *
     * @return результат виконання з часом, N, P та результуючою матрицею MA
     */
    public abstract ExecutionResult execute();

    /**
     * Виконує програму на заздалегідь підготовлених матрицях.
     * Використовується у {@code BenchmarkService} для серії вимірів.
     *
     * @param matrices вхідні матриці (порядок залежить від підкласу)
     * @return результат виконання
     */
    public abstract ExecutionResult execute(double[][]... matrices);

    /**
     * Повертає математичну формулу програми у вигляді рядка.
     * Приклад: "MA = MB × (MC × MD)"
     *
     * @return рядок із формулою
     */
    public abstract String getFormula();

    // ─────────────────────────────────────────────────────────────
    //  Захищені методи підготовки матриць
    // ─────────────────────────────────────────────────────────────

    /**
     * Виділяє новий квадратний масив N×N для проміжної або результуючої матриці.
     *
     * @return новий нульовий масив {@code double[N][N]}
     */
    protected double[][] allocate() {
        return new double[N][N];
    }

    /**
     * Виділяє масив і заповнює його випадковими числами [-10, +10].
     *
     * @param name назва матриці (для {@link Matrix}-обгортки)
     * @return нова матриця, заповнена випадковими числами
     */
    protected double[][] allocateRandom(String name) {
        double[][] data = new double[N][N];
        MatrixUtils.fillRandom(new Matrix(data, name));
        return data;
    }

    // ─────────────────────────────────────────────────────────────
    //  Захищені методи журналювання
    // ─────────────────────────────────────────────────────────────

    /**
     * Записує у журнал подію старту виконання програми.
     * Викликається на початку {@link #execute()} у підкласах.
     */
    protected void logStart() {
        log.info(String.format("▶ ПРГ%d СТАРТ | N=%d | P=%d | Формула: %s",
                prgNumber, N, P, getFormula()));
    }

    /**
     * Записує у журнал подію старту виконання програми на готових матрицях.
     */
    protected void logStartBenchmark() {
        log.info(String.format("▶ ПРГ%d СТАРТ (бенчмарк) | N=%d | P=%d",
                prgNumber, N, P));
    }

    /**
     * Записує у журнал підсумок виконання програми.
     *
     * @param result результат виконання
     */
    protected void logFinish(ExecutionResult result) {
        log.info(String.format("■ ПРГ%d ЗАВЕРШЕНО | N=%d | P=%d | Час: %d мс",
                prgNumber, result.getN(), result.getP(), result.getElapsedMs()));
    }

    /**
     * Записує у журнал інформацію про розподіл стовпців між потоками.
     * Корисно для діагностики та перевірки рівномірності розподілу.
     *
     * @param threadId порядковий номер потоку (0-based)
     * @param colStart перший стовпець діапазону (включно)
     * @param colEnd   перший стовпець після діапазону (виключно)
     */
    protected void logThreadAssignment(int threadId, int colStart, int colEnd) {
        log.debug(String.format("  ПРГ%d | Потік T%d → стовпці [%d, %d) (%d стовп.)",
                prgNumber, threadId + 1, colStart, colEnd, colEnd - colStart));
    }

    /**
     * Записує у журнал метрики продуктивності відносно базового часу.
     *
     * @param result     результат поточного виконання
     * @param baselineMs час виконання при P=1 (база для розрахунку S)
     */
    protected void logMetrics(ExecutionResult result, long baselineMs) {
        double speedup    = result.getSpeedup(baselineMs);
        double efficiency = result.getEfficiency(baselineMs) * 100.0;
        log.info(String.format(
                "  ПРГ%d | P=%d | T=%d мс | S=%.3f | E=%.1f%%",
                prgNumber, result.getP(), result.getElapsedMs(),
                speedup, efficiency));
    }

    // ─────────────────────────────────────────────────────────────
    //  Гетери
    // ─────────────────────────────────────────────────────────────

    /** @return розмір матриць N */
    public int getN() { return N; }

    /** @return кількість потоків P */
    public int getP() { return P; }

    /** @return номер програми (1, 2 або 3) */
    public int getPrgNumber() { return prgNumber; }

    // ─────────────────────────────────────────────────────────────
    //  toString
    // ─────────────────────────────────────────────────────────────

    @Override
    public String toString() {
        return String.format("ПРГ%d [N=%d, P=%d, формула: %s]",
                prgNumber, N, P, getFormula());
    }
}
