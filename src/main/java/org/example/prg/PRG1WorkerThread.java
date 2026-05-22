package org.example.prg;

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
 *     for i ∈ [0, N):
 *       MT[i][j] = Σ_k ( MC[i][k] * MD[k][j] )
 *
 * → countDown(d1)   // сигнал: моя частина MT готова
 * → await(d1)       // чекаємо поки всі потоки завершать фазу 1
 *
 * Фаза 2 — обчислення результуючої матриці MA:
 *   for j ∈ [colStart, colEnd):
 *     for i ∈ [0, N):
 *       MA[i][j] = Σ_k ( MB[i][k] * MT[k][j] )
 *
 * → countDown(d2)   // сигнал: моя частина MA готова
 * </pre>
 *
 * <p>Бар'єр d1 між фазами забезпечує коректність: хоча кожен потік
 * використовує лише власні стовпці MT, явна синхронізація відображає
 * структуру формули MA = MB × (MC × MD) і відповідає схемі взаємодії
 * потоків з розділу 3.3 дипломної роботи.
 *
 * <p>Критичних ділянок немає — кожен потік пише лише у власний
 * діапазон стовпців матриць MT та MA.
 */
public class PRG1WorkerThread extends Thread {

    // ─── Ідентифікатор потоку (для журналювання, 0-based) ────────
    private final int threadId;

    // ─── Діапазон стовпців цього потоку ──────────────────────────
    private final int colStart;
    private final int colEnd;

    // ─── Вхідні матриці (тільки читання) ─────────────────────────
    private final double[][] MB;   // множник зліва (N×N)
    private final double[][] MC;   // внутрішній лівий (N×N)
    private final double[][] MD;   // внутрішній правий (N×N)

    // ─── Матриці для запису ───────────────────────────────────────
    private final double[][] MT;   // проміжна: MT = MC × MD (N×N)
    private final double[][] MA;   // результат: MA = MB × MT (N×N)

    // ─── Розмір квадратних матриць ────────────────────────────────
    private final int N;

    // ─── Лічильники синхронізації ─────────────────────────────────
    /**
     * d1 — бар'єр після фази 1 (обчислення MT).
     * Кожен потік робить countDown після завершення своїх стовпців MT,
     * потім чекає поки всі інші також завершать.
     */
    private final CountDownLatch d1;

    /**
     * d2 — бар'єр після фази 2 (обчислення MA).
     * Після countDown головний потік (T1) отримує сигнал про завершення.
     */
    private final CountDownLatch d2;

    // ─────────────────────────────────────────────────────────────
    //  Конструктор
    // ─────────────────────────────────────────────────────────────

    /**
     * Створює робочий потік ПРГ1.
     *
     * @param threadId  порядковий номер потоку (0-based, для логування)
     * @param colStart  перший стовпець діапазону (включно)
     * @param colEnd    перший стовпець після діапазону (виключно)
     * @param N         розмір квадратних матриць
     * @param MB        вхідна матриця MB (N×N)
     * @param MC        вхідна матриця MC (N×N)
     * @param MD        вхідна матриця MD (N×N)
     * @param MT        проміжна матриця MT (N×N), спільна для всіх потоків
     * @param MA        результуюча матриця MA (N×N), спільна для всіх потоків
     * @param d1        CountDownLatch(P) — синхронізація після фази 1
     * @param d2        CountDownLatch(P) — синхронізація після фази 2
     */
    public PRG1WorkerThread(int threadId, int colStart, int colEnd,
                            int N,
                            double[][] MB, double[][] MC, double[][] MD,
                            double[][] MT, double[][] MA,
                            CountDownLatch d1, CountDownLatch d2) {
        super("PRG1-Worker-" + threadId);
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
    }

    // ─────────────────────────────────────────────────────────────
    //  Головна логіка потоку
    // ─────────────────────────────────────────────────────────────

    @Override
    public void run() {

        // ══════════════════════════════════════════════════════════
        //  ФАЗА 1: MT[:,j] = MC × MD[:,j]   для j ∈ [colStart, colEnd)
        //
        //  Обчислення ведеться у порядку i-k-j для оптимального
        //  використання кешу (рядок MC[i] залишається «гарячим»
        //  протягом внутрішнього циклу по k).
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

        // Сигналізуємо: моя частина MT готова
        SyncUtils.signal(d1);

        // Чекаємо поки всі потоки завершать фазу 1
        // (необхідно оскільки MA = MB × MT вимагає повної MT)
        SyncUtils.awaitLatch(d1);

        // ══════════════════════════════════════════════════════════
        //  ФАЗА 2: MA[:,j] = MB × MT[:,j]   для j ∈ [colStart, colEnd)
        //
        //  Аналогічна структура циклу. Читаємо з MT (вже повної),
        //  пишемо у MA — лише у власний діапазон стовпців.
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

        // Сигналізуємо: моя частина MA готова — робота завершена
        SyncUtils.signal(d2);
    }

    // ─────────────────────────────────────────────────────────────
    //  Гетери (для журналювання у Фазі 3б)
    // ─────────────────────────────────────────────────────────────

    /** @return порядковий номер потоку (0-based) */
    public int getThreadId() { return threadId; }

    /** @return індекс першого стовпця діапазону (включно) */
    public int getColStart() { return colStart; }

    /** @return індекс після останнього стовпця діапазону (виключно) */
    public int getColEnd() { return colEnd; }
}
