package org.example;

import org.example.model.ExecutionResult;
import org.example.model.Matrix;
import org.example.prg.PRG1;
import org.example.utils.MatrixUtils;

/**
 * Тест Фази 3а — перевірка коректності та продуктивності ПРГ1.
 *
 * Запуск: через IDE або mvn compile exec:java -Dexec.mainClass="org.example.TestPhase3a"
 */
public class TestPhase3a {

    private static final String GREEN  = "\u001B[32m";
    private static final String RED    = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN   = "\u001B[36m";
    private static final String RESET  = "\u001B[0m";

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║   matrix-pak — Тест Фази 3а (ПРГ1)           ║");
        System.out.println("╚══════════════════════════════════════════════╝\n");

        testCorrectnessSmall();
        testCorrectnessAgainstSequential();
        testScaling();

        System.out.println("\n══════════════════════════════════════════════");
        System.out.printf("  Результат: %s%d passed%s, %s%d failed%s%n",
                GREEN, passed, RESET, failed > 0 ? RED : GREEN, failed, RESET);
        System.out.println("══════════════════════════════════════════════");

        if (failed == 0) {
            System.out.println(GREEN + "  ✓ ПРГ1 коректна. Фаза 3а завершена." + RESET);
        } else {
            System.out.println(RED + "  ✗ Є помилки — перевір логи вище." + RESET);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  1. Коректність на малих матрицях (ручна перевірка)
    // ─────────────────────────────────────────────────────────────

    private static void testCorrectnessSmall() {
        section("1. Коректність на малих матрицях (2×2)");

        // MB = [[1,0],[0,1]] (одинична)
        // MC = [[2,0],[0,2]]
        // MD = [[3,0],[0,3]]
        // Очікуємо: MC×MD = [[6,0],[0,6]], MA = MB×(MC×MD) = [[6,0],[0,6]]

        double[][] MB = {{1, 0}, {0, 1}};
        double[][] MC = {{2, 0}, {0, 2}};
        double[][] MD = {{3, 0}, {0, 3}};

        PRG1 prg = new PRG1(2, 1);
        ExecutionResult res = prg.execute(MB, MC, MD);

        Matrix MA = res.getResultMA();
        check("MA[0][0] = 6.0", eq(MA.get(0, 0), 6.0));
        check("MA[0][1] = 0.0", eq(MA.get(0, 1), 0.0));
        check("MA[1][0] = 0.0", eq(MA.get(1, 0), 0.0));
        check("MA[1][1] = 6.0", eq(MA.get(1, 1), 6.0));
        check("prgNumber = 1",  res.getPrgNumber() == 1);
        check("N = 2",          res.getN() == 2);
        check("P = 1",          res.getP() == 1);
    }

    // ─────────────────────────────────────────────────────────────
    //  2. Паралельний результат = послідовний еталон
    // ─────────────────────────────────────────────────────────────

    private static void testCorrectnessAgainstSequential() {
        section("2. Паралельний результат збігається з послідовним (N=64)");

        int N = 64;

        // Генеруємо однакові вхідні дані для обох версій
        Matrix mMB = new Matrix(N, N, "MB");
        Matrix mMC = new Matrix(N, N, "MC");
        Matrix mMD = new Matrix(N, N, "MD");
        MatrixUtils.fillRandom(mMB);
        MatrixUtils.fillRandom(mMC);
        MatrixUtils.fillRandom(mMD);

        // Послідовний еталон: MA_seq = MB × (MC × MD)
        Matrix MT_seq  = MatrixUtils.multiply(mMC, mMD, "MT_seq");
        Matrix MA_seq  = MatrixUtils.multiply(mMB, MT_seq, "MA_seq");

        double[][] rawMB = mMB.getData();
        double[][] rawMC = mMC.getData();
        double[][] rawMD = mMD.getData();

        // Паралельна версія з P=1
        ExecutionResult r1 = new PRG1(N, 1).execute(rawMB, rawMC, rawMD);
        check("P=1: збіг з послідовним",
                MatrixUtils.equals(MA_seq, r1.getResultMA()));

        // Паралельна версія з P=2
        ExecutionResult r2 = new PRG1(N, 2).execute(rawMB, rawMC, rawMD);
        check("P=2: збіг з послідовним",
                MatrixUtils.equals(MA_seq, r2.getResultMA()));

        // Паралельна версія з P=4
        ExecutionResult r4 = new PRG1(N, 4).execute(rawMB, rawMC, rawMD);
        check("P=4: збіг з послідовним",
                MatrixUtils.equals(MA_seq, r4.getResultMA()));

        // Паралельна версія з P=8
        ExecutionResult r8 = new PRG1(N, 8).execute(rawMB, rawMC, rawMD);
        check("P=8: збіг з послідовним",
                MatrixUtils.equals(MA_seq, r8.getResultMA()));

        // Перевірка максимального відхилення (має бути < EPSILON)
        double maxDiff = MatrixUtils.maxDifference(MA_seq, r4.getResultMA());
        check("max відхилення < 1e-9 (P=4)",
                maxDiff < MatrixUtils.EPSILON);

        System.out.printf("  (макс. відхилення P=4: %.2e)%n", maxDiff);
    }

    // ─────────────────────────────────────────────────────────────
    //  3. Масштабування (P=1,2,4,6) — не assert, лише вивід
    // ─────────────────────────────────────────────────────────────

    private static void testScaling() {
        section("3. Масштабування ПРГ1 (N=300, P=1..6)");

        int N = 300;
        int[] threads = {1, 2, 3, 4, 6};

        // Підготуємо спільні вхідні дані
        Matrix mMB = new Matrix(N, N, "MB");
        Matrix mMC = new Matrix(N, N, "MC");
        Matrix mMD = new Matrix(N, N, "MD");
        MatrixUtils.fillRandom(mMB);
        MatrixUtils.fillRandom(mMC);
        MatrixUtils.fillRandom(mMD);
        double[][] rawMB = mMB.getData();
        double[][] rawMC = mMC.getData();
        double[][] rawMD = mMD.getData();

        // Прогрівання JVM (1 запуск до вимірювань)
        System.out.println("  Прогрівання JVM...");
        new PRG1(N, 1).execute(rawMB, rawMC, rawMD);

        System.out.printf("  %-6s %-10s %-10s %-10s%n", "P", "Час (мс)", "S", "E (%)");
        System.out.println("  " + "─".repeat(40));

        long baselineMs = -1;

        for (int p : threads) {
            // Виконуємо 3 рази, беремо мінімальний час (щоб прибрати шум ОС)
            long best = Long.MAX_VALUE;
            for (int run = 0; run < 3; run++) {
                ExecutionResult r = new PRG1(N, p).execute(rawMB, rawMC, rawMD);
                if (r.getElapsedMs() < best) best = r.getElapsedMs();
            }

            if (p == 1) baselineMs = best;

            double speedup    = (baselineMs > 0 && best > 0) ? (double) baselineMs / best : 1.0;
            double efficiency = speedup / p * 100.0;

            System.out.printf("  %-6d %-10d %-10.3f %.1f%%%n",
                    p, best, speedup, efficiency);
        }

        System.out.println(CYAN
                + "\n  → Прискорення > 1.0 при P>1 підтверджує коректну роботу паралелізму."
                + RESET);

        check("Тест масштабування виконано без винятків", true);
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
