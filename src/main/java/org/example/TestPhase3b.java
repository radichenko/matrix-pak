package org.example;

import org.example.model.ExecutionResult;
import org.example.model.Matrix;
import org.example.prg.AbstractPRG;
import org.example.prg.PRG1;
import org.example.service.LogService;
import org.example.utils.MatrixUtils;

/**
 * Тест Фази 3б — перевірка AbstractPRG, рефакторингу PRG1 та LogService у потоках.
 *
 * Запуск: через IDE або mvn compile exec:java -Dexec.mainClass="org.example.TestPhase3b"
 */
public class TestPhase3b {

    private static final String GREEN  = "\u001B[32m";
    private static final String RED    = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN   = "\u001B[36m";
    private static final String RESET  = "\u001B[0m";

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║  matrix-pak — Тест Фази 3б (AbstractPRG + PRG1) ║");
        System.out.println("╚══════════════════════════════════════════════════╝\n");

        testAbstractPRG();
        testPRG1Correctness();
        testPRG1Logging();
        testPRG1Scaling();

        System.out.println("\n══════════════════════════════════════════════════");
        System.out.printf("  Результат: %s%d passed%s, %s%d failed%s%n",
                GREEN, passed, RESET, failed > 0 ? RED : GREEN, failed, RESET);
        System.out.println("══════════════════════════════════════════════════");

        if (failed == 0) {
            System.out.println(GREEN + "  ✓ Фаза 3 повністю завершена. Можна йти до Фази 4." + RESET);
        } else {
            System.out.println(RED + "  ✗ Є помилки — перевір логи вище." + RESET);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  1. AbstractPRG — базові контракти
    // ─────────────────────────────────────────────────────────────

    private static void testAbstractPRG() {
        section("1. AbstractPRG — базові контракти");

        AbstractPRG prg = new PRG1(100, 4);

        check("getN() = 100",       prg.getN() == 100);
        check("getP() = 4",         prg.getP() == 4);
        check("getPrgNumber() = 1", prg.getPrgNumber() == 1);
        check("getFormula() не null", prg.getFormula() != null && !prg.getFormula().isEmpty());
        check("toString() містить ПРГ1", prg.toString().contains("ПРГ1"));
        check("toString() містить N=100", prg.toString().contains("N=100"));

        // IllegalArgumentException при некоректних параметрах
        boolean badN = false, badP = false, pGtN = false;
        try { new PRG1(0, 1); }   catch (IllegalArgumentException e) { badN  = true; }
        try { new PRG1(10, 0); }  catch (IllegalArgumentException e) { badP  = true; }
        try { new PRG1(4, 10); }  catch (IllegalArgumentException e) { pGtN  = true; }
        check("N=0 → IllegalArgument", badN);
        check("P=0 → IllegalArgument", badP);
        check("P>N → IllegalArgument", pGtN);
    }

    // ─────────────────────────────────────────────────────────────
    //  2. PRG1 — коректність результатів
    // ─────────────────────────────────────────────────────────────

    private static void testPRG1Correctness() {
        section("2. PRG1 — коректність MA = MB × (MC × MD)");

        // ── 2а: Перевірка на одиничній матриці ──
        // MB = I, MC = [[2,0],[0,2]], MD = [[3,0],[0,3]]
        // MC×MD = [[6,0],[0,6]], MB×(MC×MD) = [[6,0],[0,6]]
        double[][] MB = {{1, 0}, {0, 1}};
        double[][] MC = {{2, 0}, {0, 2}};
        double[][] MD = {{3, 0}, {0, 3}};

        ExecutionResult r = new PRG1(2, 1).execute(MB, MC, MD);
        check("MA[0][0]=6.0 (одинична MB)", eq(r.getResultMA().get(0, 0), 6.0));
        check("MA[1][1]=6.0 (одинична MB)", eq(r.getResultMA().get(1, 1), 6.0));
        check("MA[0][1]=0.0 (одинична MB)", eq(r.getResultMA().get(0, 1), 0.0));

        // ── 2б: Паралельний = послідовний для N=64 ──
        int N = 64;
        Matrix mMB = new Matrix(N, N, "MB"); MatrixUtils.fillRandom(mMB);
        Matrix mMC = new Matrix(N, N, "MC"); MatrixUtils.fillRandom(mMC);
        Matrix mMD = new Matrix(N, N, "MD"); MatrixUtils.fillRandom(mMD);

        Matrix MT_seq = MatrixUtils.multiply(mMC, mMD, "MT");
        Matrix MA_seq = MatrixUtils.multiply(mMB, MT_seq, "MA_seq");

        double[][] rawMB = mMB.getData();
        double[][] rawMC = mMC.getData();
        double[][] rawMD = mMD.getData();

        for (int p : new int[]{1, 2, 4}) {
            ExecutionResult rp = new PRG1(N, p).execute(rawMB, rawMC, rawMD);
            double diff = MatrixUtils.maxDifference(MA_seq, rp.getResultMA());
            check(String.format("P=%d збіг з еталоном (Δmax=%.2e)", p, diff),
                    diff < MatrixUtils.EPSILON);
        }

        // ── 2в: execute() без аргументів (автозаповнення) ──
        ExecutionResult auto = new PRG1(50, 2).execute();
        check("execute() без арг: MA не null", auto.getResultMA() != null);
        check("execute() без арг: N=50",       auto.getN() == 50);
        check("execute() без арг: P=2",        auto.getP() == 2);
        check("execute() без арг: час >= 0",   auto.getElapsedMs() >= 0);

        // ── 2г: IllegalArgument при < 3 матрицях ──
        boolean threw = false;
        try { new PRG1(10, 1).execute(new double[10][10]); }
        catch (IllegalArgumentException e) { threw = true; }
        check("execute(1 матриця) → IllegalArgument", threw);
    }

    // ─────────────────────────────────────────────────────────────
    //  3. LogService — потоки пишуть у журнал
    // ─────────────────────────────────────────────────────────────

    private static void testPRG1Logging() {
        section("3. LogService — потоки ПРГ1 пишуть у журнал");

        LogService log = LogService.getInstance();
        log.clear();
        int before = log.size();  // "Журнал очищено."

        // Запускаємо PRG1 — має з'явитись кілька записів
        new PRG1(32, 2).execute();

        int after = log.size();
        String all = log.getAllEntries();

        check("З'явились нові записи у журналі", after > before);
        check("Журнал містить 'ПРГ1 СТАРТ'",     all.contains("ПРГ1 СТАРТ"));
        check("Журнал містить 'ПРГ1 ЗАВЕРШЕНО'",  all.contains("ПРГ1 ЗАВЕРШЕНО"));
        check("Журнал містить 'N=32'",             all.contains("N=32"));
        check("Журнал містить 'P=2'",              all.contains("P=2"));
        check("Журнал містить DEBUG-записи потоків",
                all.contains("T1 СТАРТ") || all.contains("фаза 1"));

        System.out.println(CYAN + "\n  Останні 5 записів журналу:" + RESET);
        java.util.List<String> entries = log.getEntries();
        int from = Math.max(0, entries.size() - 5);
        for (int i = from; i < entries.size(); i++) {
            System.out.println("    " + entries.get(i));
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  4. Масштабування — таблиця S та E
    // ─────────────────────────────────────────────────────────────

    private static void testPRG1Scaling() {
        section("4. Масштабування ПРГ1 (N=300, P=1..6) з AbstractPRG");

        int N = 300;
        int[] threads = {1, 2, 3, 4, 6};

        // Спільні вхідні дані
        Matrix mMB = new Matrix(N, N, "MB"); MatrixUtils.fillRandom(mMB);
        Matrix mMC = new Matrix(N, N, "MC"); MatrixUtils.fillRandom(mMC);
        Matrix mMD = new Matrix(N, N, "MD"); MatrixUtils.fillRandom(mMD);
        double[][] rawMB = mMB.getData();
        double[][] rawMC = mMC.getData();
        double[][] rawMD = mMD.getData();

        // Прогрівання JVM
        System.out.println("  Прогрівання JVM...");
        new PRG1(N, 1).execute(rawMB, rawMC, rawMD);

        System.out.printf("%n  %-6s %-12s %-10s %-10s%n", "P", "Час (мс)", "S", "E (%)");
        System.out.println("  " + "─".repeat(42));

        long baselineMs = -1;

        for (int p : threads) {
            long best = Long.MAX_VALUE;
            for (int run = 0; run < 3; run++) {
                AbstractPRG prg = new PRG1(N, p);   // через AbstractPRG
                ExecutionResult r = prg.execute(rawMB, rawMC, rawMD);
                if (r.getElapsedMs() < best) best = r.getElapsedMs();
            }
            if (p == 1) baselineMs = best;

            double s = (baselineMs > 0 && best > 0) ? (double) baselineMs / best : 1.0;
            double e = s / p * 100.0;

            System.out.printf("  %-6d %-12d %-10.3f %.1f%%%n", p, best, s, e);
        }

        System.out.println(CYAN
                + "\n  → Виклик через AbstractPRG (поліморфізм) працює коректно."
                + RESET);
        check("Масштабування через AbstractPRG завершено без помилок", true);
    }

    // ─────────────────────────────────────────────────────────────
    //  Helpers
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
