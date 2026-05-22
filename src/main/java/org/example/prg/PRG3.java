package org.example.prg;

import org.example.model.ExecutionResult;
import org.example.model.Matrix;
import org.example.utils.MatrixUtils;
import org.example.utils.SyncUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Програма ПРГ3 — паралельне обчислення виразу:
 * <pre>    MA = max(MR) × (MB × MC)</pre>
 *
 * <p>Розширює {@link AbstractPRG}. Поєднує два паралельних підходи:
 * редукцію для пошуку глобального максимуму та стовпцеву декомпозицію
 * для множення матриць.
 *
 * <h3>Схема виконання:</h3>
 * <pre>
 *  T1:  ініціалізація → старт потоків → await(d3) → результат
 *
 *  Ti:  [фаза 1: localMax → updateAtomicMax]
 *          → signal/await(d1)
 *          → barrier.await()          ← CyclicBarrier (barrierAction: log globalMax)
 *       [фаза 2: MT_i = MB×MC_i]
 *          → signal/await(d2)
 *       [фаза 3: MA_i = scalar×MT_i]
 *          → signal(d3)
 * </pre>
 *
 * <h3>Примітиви синхронізації:</h3>
 * <ul>
 *   <li><b>d1</b> ({@link CountDownLatch}(P)) — після фази 1 (редукція завершена).</li>
 *   <li><b>barrier</b> ({@link CyclicBarrier}(P)) — між d1 та d2; barrierAction
 *       записує фінальний globalMax у журнал.</li>
 *   <li><b>d2</b> ({@link CountDownLatch}(P)) — після фази 2 (MT повністю обчислена).</li>
 *   <li><b>d3</b> ({@link CountDownLatch}(P)) — після фази 3, T1 зупиняє таймер.</li>
 *   <li><b>globalMax</b> ({@link AtomicLong}) — атомарний контейнер для max(MR),
 *       оновлюється через CAS у фазі 1 (єдина КД програми).</li>
 * </ul>
 */
public class PRG3 extends AbstractPRG {

    public PRG3(int N, int P) {
        super(N, P, 3);
    }

    @Override
    public String getFormula() {
        return "MA = max(MR) \u00d7 (MB \u00d7 MC)";
    }

    // ─────────────────────────────────────────────────────────────
    //  execute() — з автозаповненням матриць
    // ─────────────────────────────────────────────────────────────

    @Override
    public ExecutionResult execute() {
        logStart();
        double[][] MR = allocateRandom("MR");
        double[][] MB = allocateRandom("MB");
        double[][] MC = allocateRandom("MC");
        ExecutionResult result = runParallel(MR, MB, MC);
        logFinish(result);
        return result;
    }

    // ─────────────────────────────────────────────────────────────
    //  execute(matrices...) — для бенчмарку
    //  Порядок: matrices[0]=MR, [1]=MB, [2]=MC
    // ─────────────────────────────────────────────────────────────

    @Override
    public ExecutionResult execute(double[][]... matrices) {
        if (matrices.length < 3) {
            throw new IllegalArgumentException(
                    "ПРГ3.execute() потребує 3 матриці (MR, MB, MC), отримано: "
                            + matrices.length);
        }
        logStartBenchmark();
        ExecutionResult result = runParallel(matrices[0], matrices[1], matrices[2]);
        logFinish(result);
        return result;
    }

    // ─────────────────────────────────────────────────────────────
    //  Внутрішня паралельна логіка
    // ─────────────────────────────────────────────────────────────

    private ExecutionResult runParallel(double[][] MR, double[][] MB, double[][] MC) {

        double[][] MT = allocate();   // проміжна: MT = MB × MC
        double[][] MA = allocate();   // результат: MA = scalar × MT

        // ── Примітиви синхронізації ───────────────────────────────
        AtomicLong globalMax = SyncUtils.atomicMaxInit();

        CountDownLatch d1 = SyncUtils.latch(P);
        CountDownLatch d2 = SyncUtils.latch(P);
        CountDownLatch d3 = SyncUtils.latch(P);

        // CyclicBarrier з barrierAction: після того як усі потоки
        // подолали d1, записуємо фінальний globalMax у журнал.
        CyclicBarrier barrier = SyncUtils.barrier(P, () ->
                log.info(String.format("  ПРГ3 | Редукція завершена: max(MR) = %.6f",
                        SyncUtils.readAtomicMax(globalMax)))
        );

        // ── Створення та конфігурація потоків ─────────────────────
        // Кожен потік отримує однаковий діапазон для рядків MR (редукція)
        // та стовпців MT/MA (множення) — обидва розподіляються однаково
        // оскільки матриці квадратні N×N.
        PRG3WorkerThread[] workers = new PRG3WorkerThread[P];
        for (int t = 0; t < P; t++) {
            int rangeStart = MatrixUtils.getColStart(N, P, t);
            int rangeEnd   = MatrixUtils.getColEnd(N, P, t);
            logThreadAssignment(t, rangeStart, rangeEnd);
            workers[t] = new PRG3WorkerThread(
                    t,
                    rangeStart, rangeEnd,   // рядки MR (фаза 1)
                    rangeStart, rangeEnd,   // стовпці MT/MA (фази 2, 3)
                    N,
                    MR, MB, MC, MT, MA,
                    globalMax,
                    d1, barrier, d2, d3
            );
        }

        // ── Старт та вимірювання часу ─────────────────────────────
        long startNano = System.nanoTime();
        for (PRG3WorkerThread w : workers) w.start();
        SyncUtils.awaitLatch(d3);
        long elapsedMs = (System.nanoTime() - startNano) / 1_000_000L;

        return new ExecutionResult(new Matrix(MA, "MA"), elapsedMs, N, P, 3);
    }
}
