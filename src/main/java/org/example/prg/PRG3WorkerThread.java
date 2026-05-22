package org.example.prg;

import org.example.service.LogService;
import org.example.utils.SyncUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Робочий потік програми ПРГ3.
 *
 * <p>Реалізує паралельне обчислення виразу:
 * <pre>    MA = max(MR) × (MB × MC)</pre>
 *
 * <p>Алгоритм поєднує два підходи:
 * <ul>
 *   <li><b>Редукція</b> — паралельний пошук глобального максимуму матриці MR;</li>
 *   <li><b>Стовпцева декомпозиція</b> — множення матриць MB×MC та масштабування на scalar.</li>
 * </ul>
 *
 * <p>Кожен потік отримує:
 * <ul>
 *   <li>{@code rowStart}–{@code rowEnd} — діапазон <b>рядків</b> MR для редукції;</li>
 *   <li>{@code colStart}–{@code colEnd} — діапазон <b>стовпців</b> MT та MA.</li>
 * </ul>
 * Обидва діапазони рівні при квадратних матрицях N×N (рядковий = стовпцевий).
 *
 * <h3>Алгоритм виконання (3 фази):</h3>
 * <pre>
 * Фаза 1 — пошук локального максимуму в MR[rowStart..rowEnd):
 *   localMax = max( MR[i][j] ) для i ∈ [rowStart, rowEnd), j ∈ [0, N)
 *   updateAtomicMax(globalMax, localMax)    ← атомарний CAS, КД
 *
 * → signal(d1) / await(d1)    — усі потоки записали свій localMax
 * → barrier.await()           — CyclicBarrier: фіксація globalMax,
 *                               barrierAction записує підсумок у журнал
 *
 * Фаза 2 — обчислення MT (MB × MC), стовпці [colStart, colEnd):
 *   MT[i][j] = Σ_k ( MB[i][k] * MC[k][j] )
 *
 * → signal(d2) / await(d2)    — MT повністю обчислено
 *
 * Фаза 3 — масштабування: MA[:,j] = scalar × MT[:,j]:
 *   scalar = readAtomicMax(globalMax)
 *   MA[i][j] = scalar * MT[i][j]
 *
 * → signal(d3)                — сигнал T1 про завершення
 * </pre>
 *
 * <h3>Критична ділянка (КД):</h3>
 * <p>Виклик {@link SyncUtils#updateAtomicMax(AtomicLong, double)} у фазі 1
 * є КД: декілька потоків одночасно намагаються оновити {@code globalMax}.
 * Реалізовано через CAS-цикл без блокувань — блокуючого м'ютексу не потрібно.
 */
public class PRG3WorkerThread extends Thread {

    private final int threadId;

    // Діапазон рядків MR для редукції (фаза 1)
    private final int rowStart;
    private final int rowEnd;

    // Діапазон стовпців MT і MA (фази 2 і 3)
    private final int colStart;
    private final int colEnd;

    private final int N;

    // Вхідні матриці (тільки читання)
    private final double[][] MR;   // матриця для пошуку max
    private final double[][] MB;   // множник зліва
    private final double[][] MC;   // множник справа

    // Спільні матриці для запису (у власний діапазон)
    private final double[][] MT;   // проміжна: MT = MB × MC
    private final double[][] MA;   // результат: MA = scalar × MT

    // Примітиви синхронізації
    private final AtomicLong   globalMax;  // глобальний максимум MR (КД)
    private final CountDownLatch d1;        // після фази 1 (редукція)
    private final CyclicBarrier  barrier;   // між d1 і d2 (фіксація globalMax)
    private final CountDownLatch d2;        // після фази 2 (MT готова)
    private final CountDownLatch d3;        // після фази 3 (MA готова)

    private final LogService log;

    // ─────────────────────────────────────────────────────────────
    //  Конструктор
    // ─────────────────────────────────────────────────────────────

    /**
     * @param threadId  порядковий номер потоку (0-based)
     * @param rowStart  перший рядок MR для редукції (включно)
     * @param rowEnd    перший рядок після діапазону редукції (виключно)
     * @param colStart  перший стовпець MT/MA (включно)
     * @param colEnd    перший стовпець після діапазону MT/MA (виключно)
     * @param N         розмір квадратних матриць
     * @param MR        матриця для пошуку max (тільки читання)
     * @param MB        ліва матриця множення (тільки читання)
     * @param MC        права матриця множення (тільки читання)
     * @param MT        проміжна матриця (спільна, запис у свій діапазон)
     * @param MA        результуюча матриця (спільна, запис у свій діапазон)
     * @param globalMax AtomicLong для глобального максимуму (КД)
     * @param d1        CountDownLatch(P) — після фази 1
     * @param barrier   CyclicBarrier(P)  — між фазами 1 та 2
     * @param d2        CountDownLatch(P) — після фази 2
     * @param d3        CountDownLatch(P) — після фази 3
     */
    public PRG3WorkerThread(int threadId,
                            int rowStart, int rowEnd,
                            int colStart, int colEnd,
                            int N,
                            double[][] MR, double[][] MB, double[][] MC,
                            double[][] MT, double[][] MA,
                            AtomicLong globalMax,
                            CountDownLatch d1, CyclicBarrier barrier,
                            CountDownLatch d2, CountDownLatch d3) {
        super("PRG3-T" + (threadId + 1));
        this.threadId = threadId;
        this.rowStart = rowStart; this.rowEnd = rowEnd;
        this.colStart = colStart; this.colEnd = colEnd;
        this.N = N;
        this.MR = MR; this.MB = MB; this.MC = MC;
        this.MT = MT; this.MA = MA;
        this.globalMax = globalMax;
        this.d1 = d1; this.barrier = barrier;
        this.d2 = d2; this.d3 = d3;
        this.log = LogService.getInstance();
    }

    // ─────────────────────────────────────────────────────────────
    //  Логіка потоку
    // ─────────────────────────────────────────────────────────────

    @Override
    public void run() {
        log.debug(String.format(
                "  ПРГ3 | T%d СТАРТ | рядки MR [%d,%d) | стовпці MT [%d,%d)",
                threadId + 1, rowStart, rowEnd, colStart, colEnd));

        // ═══════════════════════════════════════════════════════════
        //  ФАЗА 1: Пошук локального максимуму в MR[rowStart..rowEnd)
        //
        //  Кожен потік знаходить максимум у своїй горизонтальній
        //  смузі рядків MR, потім атомарно оновлює globalMax через CAS.
        //  Це — єдина критична ділянка програми.
        // ═══════════════════════════════════════════════════════════

        double localMax = Double.NEGATIVE_INFINITY;
        for (int i = rowStart; i < rowEnd; i++) {
            for (int j = 0; j < N; j++) {
                if (MR[i][j] > localMax) {
                    localMax = MR[i][j];
                }
            }
        }

        // ── Критична ділянка: атомарне оновлення globalMax ────────
        // CAS-цикл гарантує що globalMax = max з усіх localMax.
        // Блокуючого м'ютексу не потрібно.
        SyncUtils.updateAtomicMax(globalMax, localMax);

        log.debug(String.format("  ПРГ3 | T%d localMax=%.4f → оновив globalMax",
                threadId + 1, localMax));

        // d1: сигналізуємо що наш localMax записано в globalMax
        SyncUtils.signal(d1);
        SyncUtils.awaitLatch(d1);

        // CyclicBarrier: додаткова точка синхронізації.
        // Після barrier.await() гарантовано що globalMax є фінальним
        // і видимим усім потокам. barrierAction (заданий у PRG3) виводить
        // підсумковий глобальний максимум у журнал.
        SyncUtils.awaitBarrier(barrier);

        // ═══════════════════════════════════════════════════════════
        //  ФАЗА 2: MT[:,j] = MB × MC[:,j]  для j ∈ [colStart, colEnd)
        //
        //  Стандартне стовпцеве множення. globalMax вже зафіксовано,
        //  але читаємо його лише у фазі 3.
        // ═══════════════════════════════════════════════════════════

        for (int j = colStart; j < colEnd; j++) {
            for (int i = 0; i < N; i++) {
                double sum = 0.0;
                for (int k = 0; k < N; k++) {
                    sum += MB[i][k] * MC[k][j];
                }
                MT[i][j] = sum;
            }
        }

        log.debug(String.format("  ПРГ3 | T%d фаза 2 завершена (MT готова)", threadId + 1));
        SyncUtils.signal(d2);
        SyncUtils.awaitLatch(d2);

        // ═══════════════════════════════════════════════════════════
        //  ФАЗА 3: MA[:,j] = scalar × MT[:,j]
        //
        //  scalar = max(MR) — зчитуємо з globalMax після бар'єру.
        //  Операція є незалежною per-column → КД відсутня.
        // ═══════════════════════════════════════════════════════════

        double scalar = SyncUtils.readAtomicMax(globalMax);

        for (int j = colStart; j < colEnd; j++) {
            for (int i = 0; i < N; i++) {
                MA[i][j] = scalar * MT[i][j];
            }
        }

        log.debug(String.format(
                "  ПРГ3 | T%d фаза 3 завершена (MA готова, scalar=%.4f)", threadId + 1, scalar));

        // Сигнал T1: робота завершена
        SyncUtils.signal(d3);
    }

    // ─── Гетери ──────────────────────────────────────────────────
    public int getThreadId() { return threadId; }
    public int getColStart()  { return colStart; }
    public int getColEnd()    { return colEnd; }
    public int getRowStart()  { return rowStart; }
    public int getRowEnd()    { return rowEnd; }
}
