package org.example.prg;

import org.example.model.ExecutionResult;
import org.example.model.Matrix;
import org.example.utils.MatrixUtils;
import org.example.utils.SyncUtils;

import java.util.concurrent.CountDownLatch;

/**
 * Програма ПРГ2 — паралельне обчислення виразу:
 * <pre>    MA = MB×MC + MD×ME</pre>
 *
 * <p>Розширює {@link AbstractPRG}. Реалізує стовпцеву декомпозицію:
 * P потоків рівномірно розподіляють H стовпців результуючої матриці MA.
 *
 * <h3>Схема виконання:</h3>
 * <pre>
 *  T1:  ініціалізація → старт потоків → await(d2) → результат
 *  Ti:  [фаза 1: MT1_i, MT2_i] → signal/await(d1) → [фаза 2: MA_i] → signal(d2)
 * </pre>
 *
 * <h3>Синхронізація:</h3>
 * <ul>
 *   <li><b>d1</b> ({@link CountDownLatch}(P)) — бар'єр між фазами 1 і 2.</li>
 *   <li><b>d2</b> ({@link CountDownLatch}(P)) — сигнал завершення для T1.</li>
 * </ul>
 * Критичних ділянок немає.
 */
public class PRG2 extends AbstractPRG {

    public PRG2(int N, int P) {
        super(N, P, 2);
    }

    @Override
    public String getFormula() {
        return "MA = MB\u00d7MC + MD\u00d7ME";
    }

    // ─────────────────────────────────────────────────────────────
    //  execute() — з автозаповненням матриць
    // ─────────────────────────────────────────────────────────────

    @Override
    public ExecutionResult execute() {
        logStart();
        double[][] MB = allocateRandom("MB");
        double[][] MC = allocateRandom("MC");
        double[][] MD = allocateRandom("MD");
        double[][] ME = allocateRandom("ME");
        ExecutionResult result = runParallel(MB, MC, MD, ME);
        logFinish(result);
        return result;
    }

    // ─────────────────────────────────────────────────────────────
    //  execute(matrices...) — для бенчмарку
    //  Порядок: matrices[0]=MB, [1]=MC, [2]=MD, [3]=ME
    // ─────────────────────────────────────────────────────────────

    @Override
    public ExecutionResult execute(double[][]... matrices) {
        if (matrices.length < 4) {
            throw new IllegalArgumentException(
                    "ПРГ2.execute() потребує 4 матриці (MB, MC, MD, ME), отримано: "
                            + matrices.length);
        }
        logStartBenchmark();
        ExecutionResult result = runParallel(
                matrices[0], matrices[1], matrices[2], matrices[3]);
        logFinish(result);
        return result;
    }

    // ─────────────────────────────────────────────────────────────
    //  Внутрішня паралельна логіка
    // ─────────────────────────────────────────────────────────────

    private ExecutionResult runParallel(double[][] MB, double[][] MC,
                                        double[][] MD, double[][] ME) {
        double[][] MT1 = allocate();
        double[][] MT2 = allocate();
        double[][] MA  = allocate();

        CountDownLatch d1 = SyncUtils.latch(P);
        CountDownLatch d2 = SyncUtils.latch(P);

        PRG2WorkerThread[] workers = new PRG2WorkerThread[P];
        for (int t = 0; t < P; t++) {
            int colStart = MatrixUtils.getColStart(N, P, t);
            int colEnd   = MatrixUtils.getColEnd(N, P, t);
            logThreadAssignment(t, colStart, colEnd);
            workers[t] = new PRG2WorkerThread(
                    t, colStart, colEnd, N,
                    MB, MC, MD, ME, MT1, MT2, MA,
                    d1, d2);
        }

        long startNano = System.nanoTime();
        for (PRG2WorkerThread w : workers) w.start();
        SyncUtils.awaitLatch(d2);
        long elapsedMs = (System.nanoTime() - startNano) / 1_000_000L;

        return new ExecutionResult(new Matrix(MA, "MA"), elapsedMs, N, P, 2);
    }
}
