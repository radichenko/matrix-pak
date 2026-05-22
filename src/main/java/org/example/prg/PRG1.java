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
 * <p>Розширює {@link AbstractPRG}, реалізуючи стовпцеву декомпозицію:
 * H стовпців результату рівномірно розподіляються між P робочими потоками.
 *
 * <h3>Схема виконання (T1 — головний потік програми):</h3>
 * <pre>
 *  T1:  ініціалізація → створення потоків → [СТАРТ] → await(d2) → результат
 *  Ti:  [фаза 1: MT_i] → signal(d1) → await(d1) → [фаза 2: MA_i] → signal(d2)
 * </pre>
 *
 * <h3>Синхронізація:</h3>
 * <ul>
 *   <li><b>d1</b> ({@link CountDownLatch}(P)) — бар'єр між фазою 1 та фазою 2.
 *       Гарантує що повна MT доступна перед обчисленням MA.</li>
 *   <li><b>d2</b> ({@link CountDownLatch}(P)) — сигнал T1 про завершення всіх потоків.</li>
 * </ul>
 *
 * <h3>Відсутність критичних ділянок:</h3>
 * <p>Кожен потік Ti пише виключно у власний діапазон стовпців [colStart_i, colEnd_i)
 * матриць MT та MA. Перетину діапазонів немає → race condition відсутній.
 */
public class PRG1 extends AbstractPRG {

    // ─────────────────────────────────────────────────────────────
    //  Конструктор
    // ─────────────────────────────────────────────────────────────

    /**
     * Створює екземпляр ПРГ1.
     *
     * @param N розмір квадратних матриць N×N (> 0)
     * @param P кількість робочих потоків (1 ≤ P ≤ N)
     */
    public PRG1(int N, int P) {
        super(N, P, 1);
    }

    // ─────────────────────────────────────────────────────────────
    //  Реалізація AbstractPRG
    // ─────────────────────────────────────────────────────────────

    /**
     * Повертає математичну формулу програми.
     *
     * @return "MA = MB × (MC × MD)"
     */
    @Override
    public String getFormula() {
        return "MA = MB \u00d7 (MC \u00d7 MD)";
    }

    // ─────────────────────────────────────────────────────────────
    //  execute() — з виділенням та заповненням матриць
    // ─────────────────────────────────────────────────────────────

    /**
     * Виконує ПРГ1: виділяє матриці, заповнює випадковими числами,
     * запускає P потоків, вимірює час виконання.
     *
     * @return результат з часом, N, P та результуючою матрицею MA
     */
    @Override
    public ExecutionResult execute() {
        logStart();

        // Виділення та заповнення вхідних матриць
        double[][] MB = allocateRandom("MB");
        double[][] MC = allocateRandom("MC");
        double[][] MD = allocateRandom("MD");

        ExecutionResult result = runParallel(MB, MC, MD);

        logFinish(result);
        return result;
    }

    // ─────────────────────────────────────────────────────────────
    //  execute(matrices...) — для бенчмарку з готовими матрицями
    // ─────────────────────────────────────────────────────────────

    /**
     * Виконує ПРГ1 на заздалегідь підготовлених матрицях.
     *
     * <p>Використовується у {@code BenchmarkService}: матриці MB, MC, MD
     * заповнюються один раз, а {@code execute} викликається для кожного
     * значення P, щоб результати були порівнянними.
     *
     * @param matrices масиви у порядку: {@code matrices[0]}=MB,
     *                 {@code matrices[1]}=MC, {@code matrices[2]}=MD
     * @return результат виконання
     * @throws IllegalArgumentException якщо передано менше 3 матриць
     */
    @Override
    public ExecutionResult execute(double[][]... matrices) {
        if (matrices.length < 3) {
            throw new IllegalArgumentException(
                    "ПРГ1.execute() потребує 3 матриці (MB, MC, MD), отримано: "
                            + matrices.length);
        }
        logStartBenchmark();

        ExecutionResult result = runParallel(matrices[0], matrices[1], matrices[2]);

        logFinish(result);
        return result;
    }

    // ─────────────────────────────────────────────────────────────
    //  Внутрішній метод паралельного виконання
    // ─────────────────────────────────────────────────────────────

    /**
     * Основна паралельна логіка ПРГ1.
     *
     * <p>Виконує розподіл стовпців, створює та запускає P потоків,
     * чекає на завершення, вимірює час.
     *
     * @param MB вхідна матриця MB (N×N)
     * @param MC вхідна матриця MC (N×N)
     * @param MD вхідна матриця MD (N×N)
     * @return результат виконання
     */
    private ExecutionResult runParallel(double[][] MB, double[][] MC, double[][] MD) {

        // Виділення проміжної та результуючої матриць
        double[][] MT = allocate();   // MT = MC × MD (заповнюється потоками)
        double[][] MA = allocate();   // MA = MB × MT (заповнюється потоками)

        // Примітиви синхронізації
        CountDownLatch d1 = SyncUtils.latch(P);
        CountDownLatch d2 = SyncUtils.latch(P);

        // Створення та конфігурація потоків
        PRG1WorkerThread[] workers = new PRG1WorkerThread[P];
        for (int t = 0; t < P; t++) {
            int colStart = MatrixUtils.getColStart(N, P, t);
            int colEnd   = MatrixUtils.getColEnd(N, P, t);
            logThreadAssignment(t, colStart, colEnd);
            workers[t] = new PRG1WorkerThread(
                    t, colStart, colEnd,
                    N, MB, MC, MD, MT, MA,
                    d1, d2
            );
        }

        // Старт таймера та запуск потоків
        // Таймер стартує одразу перед першим start() щоб врахувати
        // накладні витрати на ініціалізацію потоків ОС.
        long startNano = System.nanoTime();
        for (PRG1WorkerThread w : workers) w.start();

        // T1 очікує сигнал d2 від усіх потоків
        SyncUtils.awaitLatch(d2);
        long elapsedMs = (System.nanoTime() - startNano) / 1_000_000L;

        return new ExecutionResult(new Matrix(MA, "MA"), elapsedMs, N, P, 1);
    }
}
