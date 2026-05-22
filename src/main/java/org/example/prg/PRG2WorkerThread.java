package org.example.prg;

import org.example.service.LogService;
import org.example.utils.SyncUtils;

import java.util.concurrent.CountDownLatch;

/**
 * Робочий потік програми ПРГ2.
 *
 * <p>Реалізує стовпцеву декомпозицію для обчислення виразу:
 * <pre>    MA = MB×MC + MD×ME</pre>
 *
 * <p>Кожен потік обробляє стовпці [{@code colStart}, {@code colEnd}).
 *
 * <h3>Алгоритм (2 фази):</h3>
 * <pre>
 * Фаза 1 — обчислення проміжних матриць MT1 та MT2:
 *   for j ∈ [colStart, colEnd):
 *     MT1[i][j] = Σ_k ( MB[i][k] * MC[k][j] )   // стовпець j добутку MB×MC
 *     MT2[i][j] = Σ_k ( MD[i][k] * ME[k][j] )   // стовпець j добутку MD×ME
 *
 * → signal(d1) / await(d1)    — бар'єр між фазами
 *
 * Фаза 2 — сума проміжних матриць:
 *   for j ∈ [colStart, colEnd):
 *     MA[i][j] = MT1[i][j] + MT2[i][j]
 *
 * → signal(d2)                — сигнал T1 про завершення
 * </pre>
 *
 * <p>Критичних ділянок немає: кожен потік читає лише власний
 * діапазон стовпців і пише виключно в нього.
 */
public class PRG2WorkerThread extends Thread {

    private final int threadId;
    private final int colStart;
    private final int colEnd;
    private final int N;

    // Вхідні матриці (тільки читання)
    private final double[][] MB;
    private final double[][] MC;
    private final double[][] MD;
    private final double[][] ME;

    // Проміжні та результуюча матриці (запис у власний діапазон)
    private final double[][] MT1;  // MT1 = MB×MC
    private final double[][] MT2;  // MT2 = MD×ME
    private final double[][] MA;   // MA  = MT1 + MT2

    private final CountDownLatch d1;
    private final CountDownLatch d2;
    private final LogService log;

    // ─────────────────────────────────────────────────────────────

    public PRG2WorkerThread(int threadId, int colStart, int colEnd, int N,
                            double[][] MB, double[][] MC,
                            double[][] MD, double[][] ME,
                            double[][] MT1, double[][] MT2, double[][] MA,
                            CountDownLatch d1, CountDownLatch d2) {
        super("PRG2-T" + (threadId + 1));
        this.threadId = threadId;
        this.colStart = colStart;
        this.colEnd   = colEnd;
        this.N   = N;
        this.MB  = MB; this.MC = MC;
        this.MD  = MD; this.ME = ME;
        this.MT1 = MT1; this.MT2 = MT2; this.MA = MA;
        this.d1  = d1;  this.d2 = d2;
        this.log = LogService.getInstance();
    }

    // ─────────────────────────────────────────────────────────────

    @Override
    public void run() {
        log.debug(String.format("  ПРГ2 | T%d СТАРТ | стовпці [%d, %d)",
                threadId + 1, colStart, colEnd));

        // ═══ ФАЗА 1: MT1[:,j] = MB×MC[:,j]  і  MT2[:,j] = MD×ME[:,j] ═══
        for (int j = colStart; j < colEnd; j++) {
            for (int i = 0; i < N; i++) {
                double s1 = 0.0, s2 = 0.0;
                for (int k = 0; k < N; k++) {
                    s1 += MB[i][k] * MC[k][j];
                    s2 += MD[i][k] * ME[k][j];
                }
                MT1[i][j] = s1;
                MT2[i][j] = s2;
            }
        }

        log.debug(String.format("  ПРГ2 | T%d фаза 1 завершена", threadId + 1));
        SyncUtils.signal(d1);
        SyncUtils.awaitLatch(d1);

        // ═══ ФАЗА 2: MA[:,j] = MT1[:,j] + MT2[:,j] ═══
        for (int j = colStart; j < colEnd; j++) {
            for (int i = 0; i < N; i++) {
                MA[i][j] = MT1[i][j] + MT2[i][j];
            }
        }

        log.debug(String.format("  ПРГ2 | T%d фаза 2 завершена", threadId + 1));
        SyncUtils.signal(d2);
    }

    public int getThreadId() { return threadId; }
    public int getColStart()  { return colStart; }
    public int getColEnd()    { return colEnd; }
}
