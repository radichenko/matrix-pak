package org.example.prg;

import org.example.service.LogService;
import org.example.utils.SyncUtils;

import java.util.concurrent.CountDownLatch;

/**
 * Робочий потік програми ПРГ1.
 *
 * <p>Реалізує стовпцеву декомпозицію для обчислення виразу:
 * <pre>    MA = MB × (MC × MD)</pre>
 *
 * <p>Кожен екземпляр обробляє підмножину стовпців матриці результату
 * у діапазоні [{@code colStart}, {@code colEnd}).
 *
 * <h3>Алгоритм виконання (2 фази):</h3>
 * <pre>
 * Фаза 1 — обчислення проміжної матриці MT:
 *   for j ∈ [colStart, colEnd):
 *     MT[i][j] = Σ_k ( MC[i][k] * MD[k][j] )   для всіх i
 *
 * → signal(d1)   // сигнал: моя частина MT готова
 * → await(d1)    // чекаємо поки всі потоки завершать фазу 1
 *
 * Фаза 2 — обчислення результуючої матриці MA:
 *   for j ∈ [colStart, colEnd):
 *     MA[i][j] = Σ_k ( MB[i][k] * MT[k][j] )   для всіх i
 *
 * → signal(d2)   // сигнал: робота завершена
 * </pre>
 *
 * <p>Критичних ділянок немає — кожен потік пише виключно
 * у свій діапазон стовпців MT та MA.
 */
public class PRG1WorkerThread extends Thread {

    // ─── Ідентифікатор потоку (0-based, для журналювання) ────────
    private final int threadId;

    // ─── Діапазон стовпців цього потоку ──────────────────────────
    private final int colStart;
    private final int colEnd;

    // ─── Вхідні матриці (тільки читання) ─────────────────────────
    private final double[][] MB;
    private final double[][] MC;
    private final double[][] MD;

    // ─── Матриці для запису ───────────────────────────────────────
    private final double[][] MT;   // проміжна: MT = MC × MD (спільна)
    private final double[][] MA;   // результат: MA = MB × MT (спільна)

    // ─── Розмір квадратних матриць ────────────────────────────────
    private final int N;

    // ─── Примітиви синхронізації ──────────────────────────────────
    private final CountDownLatch d1;
    private final CountDownLatch d2;

    // ─── Журналювання ─────────────────────────────────────────────
    private final LogService log;

    // ─────────────────────────────────────────────────────────────
    //  Конструктор
    // ─────────────────────────────────────────────────────────────

    /**
     * Створює робочий потік ПРГ1.
     *
     * @param threadId  порядковий номер потоку (0-based)
     * @param colStart  перший стовпець діапазону (включно)
     * @param colEnd    перший стовпець після діапазону (виключно)
     * @param N         розмір квадратних матриць
     * @param MB        вхідна матриця MB (тільки читання)
     * @param MC        вхідна матриця MC (тільки читання)
     * @param MD        вхідна матриця MD (тільки читання)
     * @param MT        проміжна матриця MT (спільна, запис у свій діапазон)
     * @param MA        результуюча матриця MA (спільна, запис у свій діапазон)
     * @param d1        бар'єр після фази 1 (CountDownLatch(P))
     * @param d2        бар'єр після фази 2 (CountDownLatch(P))
     */
    public PRG1WorkerThread(int threadId, int colStart, int colEnd,
                            int N,
                            double[][] MB, double[][] MC, double[][] MD,
                            double[][] MT, double[][] MA,
                            CountDownLatch d1, CountDownLatch d2) {
        super("PRG1-T" + (threadId + 1));
        this.threadId = threadId;
        this.colStart = colStart;
        this.colEnd   = colEnd;
        this.N  = N;
        this.MB = MB;
        this.MC = MC;
        this.MD = MD;
        this.MT = MT;
        this.MA = MA;
        this.d1 = d1;
        this.d2 = d2;
        this.log = LogService.getInstance();
    }

    // ─────────────────────────────────────────────────────────────
    //  Логіка потоку
    // ─────────────────────────────────────────────────────────────

    @Override
    public void run() {

        log.debug(String.format("  ПРГ1 | T%d СТАРТ | стовпці [%d, %d) | %d стовп.",
                threadId + 1, colStart, colEnd, colEnd - colStart));

        // ══════════════════════════════════════════════════════════
        //  ФАЗА 1: MT[:,j] = MC × MD[:,j]   для j ∈ [colStart, colEnd)
        //
        //  Порядок циклів j→i→k: зовнішній цикл по стовпцях,
        //  внутрішній по рядках — рядок MC[i] залишається "гарячим"
        //  у кеші L1 протягом усього внутрішнього циклу по k.
        // ══════════════════════════════════════════════════════════

        for (int j = colStart; j < colEnd; j++) {
            for (int i = 0; i < N; i++) {
                double sum = 0.0;
                for (int k = 0; k < N; k++) {
                    sum += MC[i][k] * MD[k][j];
                }
                MT[i][j] = sum;
            }
        }

        log.debug(String.format("  ПРГ1 | T%d фаза 1 завершена (MT готова)", threadId + 1));

        // Сигналізуємо та чекаємо: всі потоки мають завершити фазу 1
        // перед початком фази 2, оскільки MA = MB × MT вимагає повної MT.
        SyncUtils.signal(d1);
        SyncUtils.awaitLatch(d1);

        // ══════════════════════════════════════════════════════════
        //  ФАЗА 2: MA[:,j] = MB × MT[:,j]   для j ∈ [colStart, colEnd)
        //
        //  Аналогічна структура. Читаємо з повної MT (фаза 1 завершена
        //  для всіх потоків), пишемо у MA лише у свій діапазон.
        // ══════════════════════════════════════════════════════════

        for (int j = colStart; j < colEnd; j++) {
            for (int i = 0; i < N; i++) {
                double sum = 0.0;
                for (int k = 0; k < N; k++) {
                    sum += MB[i][k] * MT[k][j];
                }
                MA[i][j] = sum;
            }
        }

        log.debug(String.format("  ПРГ1 | T%d фаза 2 завершена (MA готова)", threadId + 1));

        // Сигналізуємо головному потоку T1: робота завершена
        SyncUtils.signal(d2);
    }

    // ─────────────────────────────────────────────────────────────
    //  Гетери
    // ─────────────────────────────────────────────────────────────

    public int getThreadId() { return threadId; }
    public int getColStart()  { return colStart; }
    public int getColEnd()    { return colEnd; }
}
