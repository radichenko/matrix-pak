package org.example.utils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Фабрика та допоміжні методи для примітивів синхронізації.
 *
 * <p>Централізує створення об'єктів {@link CountDownLatch}, {@link CyclicBarrier}
 * та {@link AtomicLong}, які використовуються у потоках ПРГ1–ПРГ3.
 * Це спрощує тестування та унеможливлює помилки при передачі аргументів.
 *
 * <h3>Які примітиви використовує кожна програма:</h3>
 * <ul>
 *   <li><b>ПРГ1</b> — 2 CountDownLatch (d1, d2). Критичних ділянок немає.</li>
 *   <li><b>ПРГ2</b> — 2 CountDownLatch (d1, d2). Критичних ділянок немає.</li>
 *   <li><b>ПРГ3</b> — 3 CountDownLatch (d1, d2, d3) + 1 CyclicBarrier (між
 *       фазами редукції) + 1 AtomicLong (для зберігання глобального max у бітах
 *       double через {@link Double#doubleToLongBits} / {@link Double#longBitsToDouble}).</li>
 * </ul>
 */
public final class SyncUtils {

    // Заборона створення екземплярів
    private SyncUtils() {}

    // ─────────────────────────────────────────────────────────────
    //  Фабричні методи
    // ─────────────────────────────────────────────────────────────

    /**
     * Створює новий {@link CountDownLatch} із заданим лічильником.
     *
     * @param count початковий лічильник (зазвичай = кількість потоків P)
     * @return новий CountDownLatch
     */
    public static CountDownLatch latch(int count) {
        return new CountDownLatch(count);
    }

    /**
     * Створює новий {@link CyclicBarrier} для P потоків без дії після бар'єру.
     *
     * @param parties кількість потоків, що чекають на бар'єрі
     * @return новий CyclicBarrier
     */
    public static CyclicBarrier barrier(int parties) {
        return new CyclicBarrier(parties);
    }

    /**
     * Створює новий {@link CyclicBarrier} з дією (Runnable), що виконується
     * після того як усі потоки дійшли до бар'єру.
     *
     * @param parties      кількість потоків
     * @param barrierAction дія після бар'єру
     * @return новий CyclicBarrier з barrierAction
     */
    public static CyclicBarrier barrier(int parties, Runnable barrierAction) {
        return new CyclicBarrier(parties, barrierAction);
    }

    /**
     * Створює новий {@link AtomicLong} з початковим значенням, рівним
     * бітовому представленню {@code Double.NEGATIVE_INFINITY}.
     *
     * <p>Використовується у ПРГ3 для атомарного зберігання максимального
     * значення типу double без блокувань. Трюк: {@link Double#doubleToLongBits}
     * зберігає double у long, {@link Double#longBitsToDouble} відновлює значення.
     *
     * @return AtomicLong з початковим значенням -∞ (у бітах double)
     */
    public static AtomicLong atomicMaxInit() {
        return new AtomicLong(Double.doubleToLongBits(Double.NEGATIVE_INFINITY));
    }

    // ─────────────────────────────────────────────────────────────
    //  Атомарне оновлення максимуму (для ПРГ3)
    // ─────────────────────────────────────────────────────────────

    /**
     * Атомарно оновлює глобальний максимум у {@link AtomicLong} якщо
     * нове значення {@code candidate} більше поточного.
     *
     * <p>Реалізує CAS-цикл (Compare-And-Swap) без блокувань:
     * <pre>
     *   do {
     *       current = atomicMax.get();
     *       if (candidate <= longBitsToDouble(current)) break;
     *   } while (!atomicMax.compareAndSet(current, doubleToLongBits(candidate)));
     * </pre>
     *
     * <p>Метод потокобезпечний. Декілька потоків можуть викликати його
     * одночасно — в результаті {@code atomicMax} гарантовано містить
     * глобальний максимум.
     *
     * @param atomicMax AtomicLong-контейнер (ініціалізований через {@link #atomicMaxInit()})
     * @param candidate кандидат на нове максимальне значення
     */
    public static void updateAtomicMax(AtomicLong atomicMax, double candidate) {
        long candidateBits = Double.doubleToLongBits(candidate);
        long current;
        do {
            current = atomicMax.get();
            if (candidate <= Double.longBitsToDouble(current)) {
                // Кандидат не більший за поточний максимум — виходимо
                return;
            }
            // Спроба атомарно замінити current на candidateBits
        } while (!atomicMax.compareAndSet(current, candidateBits));
    }

    /**
     * Зчитує поточне значення максимуму з {@link AtomicLong}.
     *
     * @param atomicMax AtomicLong-контейнер
     * @return поточний максимум як double
     */
    public static double readAtomicMax(AtomicLong atomicMax) {
        return Double.longBitsToDouble(atomicMax.get());
    }

    // ─────────────────────────────────────────────────────────────
    //  Безпечний await (обгортки без checked-винятків)
    // ─────────────────────────────────────────────────────────────

    /**
     * Виконує {@link CountDownLatch#await()} і перетворює
     * {@link InterruptedException} на {@link RuntimeException}.
     *
     * <p>Спрощує код у потоках — не потрібно оголошувати throws.
     *
     * @param latch об'єкт CountDownLatch
     */
    public static void awaitLatch(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Потік перервано під час очікування на latch.", e);
        }
    }

    /**
     * Виконує {@link CyclicBarrier#await()} і перетворює перевірені
     * винятки ({@link InterruptedException}, {@link java.util.concurrent.BrokenBarrierException})
     * на {@link RuntimeException}.
     *
     * @param barrier об'єкт CyclicBarrier
     */
    public static void awaitBarrier(CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Потік перервано під час очікування на бар'єрі.", e);
        } catch (java.util.concurrent.BrokenBarrierException e) {
            throw new RuntimeException("Бар'єр зламано — інший потік завершився з помилкою.", e);
        }
    }

    /**
     * Виконує {@link CountDownLatch#countDown()} для заданого latch.
     * Метод-обгортка для однорядкового читання коду в потоках.
     *
     * @param latch об'єкт CountDownLatch
     */
    public static void signal(CountDownLatch latch) {
        latch.countDown();
    }
}
