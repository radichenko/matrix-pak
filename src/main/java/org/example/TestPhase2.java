package org.example;

import org.example.model.BenchmarkResult;
import org.example.model.ExecutionResult;
import org.example.model.Matrix;
import org.example.service.LogService;
import org.example.utils.MatrixUtils;
import org.example.utils.SyncUtils;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Тимчасовий тест-клас для перевірки коректності Фази 2.
 * Після перевірки — видалити або замінити оригінальним Main.java.
 *
 * Запуск: mvn compile exec:java -Dexec.mainClass="org.example.TestPhase2"
 * або просто через IDE як звичайний main.
 */
public class TestPhase2 {

    // ANSI-кольори для зручного читання у консолі
    private static final String GREEN  = "\u001B[32m";
    private static final String RED    = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RESET  = "\u001B[0m";

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║   matrix-pak — Тест Фази 2 (моделі + утиліти) ║");
        System.out.println("╚══════════════════════════════════════════════╝\n");

        testMatrix();
        testMatrixUtils();
        testColDistribution();
        testSyncUtils();
        testBenchmarkResult();
        testExecutionResult();
        testLogService();

        System.out.println("\n══════════════════════════════════════════════");
        System.out.printf("  Результат: %s%d passed%s, %s%d failed%s%n",
                GREEN, passed, RESET, failed > 0 ? RED : GREEN, failed, RESET);
        System.out.println("══════════════════════════════════════════════");

        if (failed == 0) {
            System.out.println(GREEN + "  ✓ Фаза 2 повністю справна. Можна йти до Фази 3." + RESET);
        } else {
            System.out.println(RED + "  ✗ Є помилки — перевір логи вище." + RESET);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  1. Matrix
    // ─────────────────────────────────────────────────────────────

    private static void testMatrix() {
        section("1. Matrix — базові операції");

        // Конструктор та доступ до елементів
        Matrix m = new Matrix(3, 4, "M");
        m.set(0, 0, 1.5);
        m.set(2, 3, -7.0);
        check("set/get [0][0]",   m.get(0, 0) == 1.5);
        check("set/get [2][3]",   m.get(2, 3) == -7.0);
        check("розмір rows=3",    m.rows == 3);
        check("розмір cols=4",    m.cols == 4);
        check("getName()",        "M".equals(m.getName()));
        check("isSquare() false", !m.isSquare());

        Matrix sq = new Matrix(5, 5, "SQ");
        check("isSquare() true",  sq.isSquare());

        // Глибока копія
        Matrix orig = new Matrix(2, 2, "ORIG");
        orig.set(0, 0, 42.0);
        Matrix copy = orig.copy();
        copy.set(0, 0, 99.0);
        check("copy() незалежна", orig.get(0, 0) == 42.0);

        // Конструктор з масиву
        double[][] arr = {{1, 2}, {3, 4}};
        Matrix fromArr = new Matrix(arr, "ARR");
        check("Matrix(double[][])", fromArr.get(1, 1) == 4.0);

        // IllegalArgumentException при некоректному розмірі
        boolean threw = false;
        try { new Matrix(0, 5, "BAD"); } catch (IllegalArgumentException e) { threw = true; }
        check("IllegalArgument при rows=0", threw);
    }

    // ─────────────────────────────────────────────────────────────
    //  2. MatrixUtils — арифметика
    // ─────────────────────────────────────────────────────────────

    private static void testMatrixUtils() {
        section("2. MatrixUtils — множення та додавання");

        // Множення: C = A × B
        Matrix a = new Matrix(new double[][]{{1, 2}, {3, 4}}, "A");
        Matrix b = new Matrix(new double[][]{{5, 6}, {7, 8}}, "B");
        Matrix c = MatrixUtils.multiply(a, b, "C");

        check("C[0][0] = 19", eq(c.get(0, 0), 19.0));
        check("C[0][1] = 22", eq(c.get(0, 1), 22.0));
        check("C[1][0] = 43", eq(c.get(1, 0), 43.0));
        check("C[1][1] = 50", eq(c.get(1, 1), 50.0));

        // Додавання: D = A + B_same_size
        Matrix x = new Matrix(new double[][]{{1, 2}, {3, 4}}, "X");
        Matrix y = new Matrix(new double[][]{{10, 20}, {30, 40}}, "Y");
        Matrix d = MatrixUtils.add(x, y, "D");
        check("D[0][0] = 11",  eq(d.get(0, 0), 11.0));
        check("D[1][1] = 44",  eq(d.get(1, 1), 44.0));

        // Множення на скаляр
        Matrix s = MatrixUtils.multiplyScalar(x, 3.0, "S");
        check("scalar [0][0]=3",  eq(s.get(0, 0), 3.0));
        check("scalar [1][1]=12", eq(s.get(1, 1), 12.0));

        // Несумісні розміри — має кинути виняток
        boolean threw = false;
        try {
            Matrix p = new Matrix(2, 3, "P");
            Matrix q = new Matrix(2, 2, "Q");
            MatrixUtils.multiply(p, q, "PQ");
        } catch (IllegalArgumentException e) { threw = true; }
        check("multiply: несумісні розміри → виняток", threw);

        // Пошук максимуму
        Matrix r = new Matrix(new double[][]{{-5, 3}, {7, -1}}, "R");
        check("maxValue = 7.0", eq(MatrixUtils.maxValue(r), 7.0));

        // fillRandom — перевіряємо що хоча б один елемент ненульовий
        Matrix rnd = new Matrix(100, 100, "RND");
        MatrixUtils.fillRandom(rnd);
        double[][] data = rnd.getData();
        boolean hasNonZero = false;
        outer: for (double[] row : data)
            for (double v : row) if (v != 0.0) { hasNonZero = true; break outer; }
        check("fillRandom — є ненульові елементи", hasNonZero);

        // equals та maxDifference
        Matrix e1 = new Matrix(new double[][]{{1.0, 2.0}}, "E1");
        Matrix e2 = new Matrix(new double[][]{{1.0, 2.0}}, "E2");
        Matrix e3 = new Matrix(new double[][]{{1.0, 2.1}}, "E3");
        check("equals однакові",   MatrixUtils.equals(e1, e2));
        check("equals різні",      !MatrixUtils.equals(e1, e3));
        check("maxDifference=0.1", eq(MatrixUtils.maxDifference(e1, e3), 0.1));
    }

    // ─────────────────────────────────────────────────────────────
    //  3. MatrixUtils — розподіл стовпців
    // ─────────────────────────────────────────────────────────────

    private static void testColDistribution() {
        section("3. MatrixUtils — розподіл стовпців між потоками");

        // H=10, P=3 → [0,4), [4,7), [7,10)
        check("H=10,P=3 T0 start=0",  MatrixUtils.getColStart(10, 3, 0) == 0);
        check("H=10,P=3 T0 end=4",    MatrixUtils.getColEnd(10, 3, 0)   == 4);
        check("H=10,P=3 T1 start=4",  MatrixUtils.getColStart(10, 3, 1) == 4);
        check("H=10,P=3 T1 end=7",    MatrixUtils.getColEnd(10, 3, 1)   == 7);
        check("H=10,P=3 T2 start=7",  MatrixUtils.getColStart(10, 3, 2) == 7);
        check("H=10,P=3 T2 end=10",   MatrixUtils.getColEnd(10, 3, 2)   == 10);

        // H=6, P=3 → рівний розподіл [0,2), [2,4), [4,6)
        check("H=6,P=3 T0=[0,2)",  MatrixUtils.getColStart(6,3,0)==0 && MatrixUtils.getColEnd(6,3,0)==2);
        check("H=6,P=3 T1=[2,4)",  MatrixUtils.getColStart(6,3,1)==2 && MatrixUtils.getColEnd(6,3,1)==4);
        check("H=6,P=3 T2=[4,6)",  MatrixUtils.getColStart(6,3,2)==4 && MatrixUtils.getColEnd(6,3,2)==6);

        // H=5, P=1 → весь діапазон одному потоку
        check("H=5,P=1 T0=[0,5)",  MatrixUtils.getColStart(5,1,0)==0 && MatrixUtils.getColEnd(5,1,0)==5);

        // Перевірка: сума ширин = H (для довільних H і P)
        int H = 500, P = 6;
        int totalCols = 0;
        for (int t = 0; t < P; t++) {
            totalCols += MatrixUtils.getColEnd(H, P, t) - MatrixUtils.getColStart(H, P, t);
        }
        check("сума ширин = H (500, P=6)", totalCols == H);
    }

    // ─────────────────────────────────────────────────────────────
    //  4. SyncUtils
    // ─────────────────────────────────────────────────────────────

    private static void testSyncUtils() {
        section("4. SyncUtils — синхронізація та AtomicMax");

        // atomicMaxInit — початкове значення -∞
        AtomicLong am = SyncUtils.atomicMaxInit();
        check("початковий max = -∞",
                Double.longBitsToDouble(am.get()) == Double.NEGATIVE_INFINITY);

        // updateAtomicMax — послідовно
        SyncUtils.updateAtomicMax(am, 3.14);
        check("після 3.14 → max=3.14", eq(SyncUtils.readAtomicMax(am), 3.14));

        SyncUtils.updateAtomicMax(am, 1.0);
        check("після 1.0 → max ще 3.14", eq(SyncUtils.readAtomicMax(am), 3.14));

        SyncUtils.updateAtomicMax(am, 9.99);
        check("після 9.99 → max=9.99", eq(SyncUtils.readAtomicMax(am), 9.99));

        SyncUtils.updateAtomicMax(am, -100.0);
        check("після -100 → max ще 9.99", eq(SyncUtils.readAtomicMax(am), 9.99));

        // Паралельний тест AtomicMax — 8 потоків, кожен шле своє значення
        AtomicLong par = SyncUtils.atomicMaxInit();
        double[] vals = {1.1, 5.5, 3.3, 9.9, 2.2, 7.7, 4.4, 6.6};
        Thread[] threads = new Thread[vals.length];
        for (int i = 0; i < vals.length; i++) {
            final double v = vals[i];
            threads[i] = new Thread(() -> SyncUtils.updateAtomicMax(par, v));
        }
        for (Thread t : threads) t.start();
        for (Thread t : threads) { try { t.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
        check("паралельний AtomicMax = 9.9", eq(SyncUtils.readAtomicMax(par), 9.9));

        // awaitLatch — latch(1), countDown у новому потоці
        var latch = SyncUtils.latch(1);
        new Thread(() -> { try { Thread.sleep(50); } catch (InterruptedException e) {} SyncUtils.signal(latch); }).start();
        long t0 = System.currentTimeMillis();
        SyncUtils.awaitLatch(latch);
        check("awaitLatch чекав ≥50мс", System.currentTimeMillis() - t0 >= 40);

        // barrier — 2 потоки зустрічаються
        var barrier = SyncUtils.barrier(2);
        boolean[] met = {false};
        Thread bt = new Thread(() -> { try { Thread.sleep(30); } catch (InterruptedException e) {} SyncUtils.awaitBarrier(barrier); met[0] = true; });
        bt.start();
        SyncUtils.awaitBarrier(barrier);
        try { bt.join(); } catch (InterruptedException e) {}
        check("barrier — обидва потоки зустрілись", met[0]);
    }

    // ─────────────────────────────────────────────────────────────
    //  5. BenchmarkResult
    // ─────────────────────────────────────────────────────────────

    private static void testBenchmarkResult() {
        section("5. BenchmarkResult — розрахунок S та E");

        // P=1 — baseline, S=1.0, E=1.0
        BenchmarkResult base = new BenchmarkResult(500, 1, 1000L, 1000L, 1);
        check("S при P=1 дорівнює 1.0",  eq(base.getSpeedup(), 1.0));
        check("E при P=1 дорівнює 1.0",  eq(base.getEfficiency(), 1.0));

        // P=4, час 312мс при baseline 1000мс → S≈3.205
        BenchmarkResult r4 = new BenchmarkResult(500, 4, 312L, 1000L, 1);
        check("S(P=4) ≈ 3.205", Math.abs(r4.getSpeedup() - (1000.0/312.0)) < 1e-6);
        check("E(P=4) ≈ S/4",   Math.abs(r4.getEfficiency() - r4.getSpeedup()/4) < 1e-9);

        // Гетери
        check("getN()",          r4.getN() == 500);
        check("getP()",          r4.getP() == 4);
        check("getElapsedMs()",  r4.getElapsedMs() == 312L);
        check("getPrgNumber()",  r4.getPrgNumber() == 1);

        // toString не null
        check("toString не порожній", r4.toString() != null && !r4.toString().isEmpty());
    }

    // ─────────────────────────────────────────────────────────────
    //  6. ExecutionResult
    // ─────────────────────────────────────────────────────────────

    private static void testExecutionResult() {
        section("6. ExecutionResult — гетери та метрики");

        Matrix ma = new Matrix(2, 2, "MA");
        ExecutionResult res = new ExecutionResult(ma, 847L, 500, 4, 1);

        check("getElapsedMs()", res.getElapsedMs() == 847L);
        check("getN()",         res.getN() == 500);
        check("getP()",         res.getP() == 4);
        check("getPrgNumber()", res.getPrgNumber() == 1);
        check("getResultMA()",  res.getResultMA() == ma);
        check("getTimestamp()", res.getTimestamp() != null);

        // Прискорення відносно 2000мс baseline
        double s = res.getSpeedup(2000L);
        check("getSpeedup(2000)",    Math.abs(s - (2000.0/847.0)) < 1e-6);
        check("getEfficiency(2000)", Math.abs(res.getEfficiency(2000L) - s/4) < 1e-9);

        // Захист від ділення на нуль
        ExecutionResult zero = new ExecutionResult(null, 0L, 500, 4, 1);
        check("getSpeedup при elapsedMs=0", zero.getSpeedup(1000L) == 0.0);

        check("toString не порожній", res.toString() != null && !res.toString().isEmpty());
    }

    // ─────────────────────────────────────────────────────────────
    //  7. LogService
    // ─────────────────────────────────────────────────────────────

    private static void testLogService() {
        section("7. LogService — журналювання");

        LogService log = LogService.getInstance();
        log.clear();

        // Після clear() — 1 запис ("Журнал очищено.")
        int after = log.size();

        log.info("Тестовий INFO");
        log.warn("Тестовий WARN");
        log.error("Тестовий ERROR");
        log.debug("Тестовий DEBUG");
        log.infof("Форматований: N=%d, P=%d", 500, 4);

        check("5 нових записів після clear()", log.size() == after + 5);

        // Записи містять рівні
        String all = log.getAllEntries();
        check("INFO є у журналі",  all.contains("[INFO ]"));
        check("WARN є у журналі",  all.contains("[WARN ]"));
        check("ERROR є у журналі", all.contains("[ERROR]"));
        check("DEBUG є у журналі", all.contains("[DEBUG]"));
        check("infof форматує",    all.contains("N=500, P=4"));

        // getEntries() повертає незмінний список
        boolean immutable = false;
        try { log.getEntries().add("hack"); } catch (UnsupportedOperationException e) { immutable = true; }
        check("getEntries() незмінний", immutable);

        // Слухач отримує нові записи
        boolean[] received = {false};
        log.addListener(s -> { if (!s.isEmpty()) received[0] = true; });
        // Platform.runLater не виконається без JavaFX — просто перевіряємо що не крашиться
        log.info("Перевірка слухача");
        check("addListener не кидає виняток", true);
    }

    // ─────────────────────────────────────────────────────────────
    //  Допоміжні методи
    // ─────────────────────────────────────────────────────────────

    private static void section(String title) {
        System.out.println(YELLOW + "\n▶ " + title + RESET);
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            System.out.println(GREEN + "  ✓ " + name + RESET);
            passed++;
        } else {
            System.out.println(RED + "  ✗ FAIL: " + name + RESET);
            failed++;
        }
    }

    private static boolean eq(double a, double b) {
        return Math.abs(a - b) < MatrixUtils.EPSILON;
    }
}