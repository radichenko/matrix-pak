package org.example;

import org.example.model.ExecutionResult;
import org.example.model.Matrix;
import org.example.prg.AbstractPRG;
import org.example.prg.PRG1;
import org.example.prg.PRG2;
import org.example.prg.PRG3;
import org.example.service.LogService;
import org.example.utils.MatrixUtils;

/**
 * Тест Фази 4 — перевірка PRG2 та PRG3.
 *
 * Запуск: через IDE або mvn compile exec:java -Dexec.mainClass="org.example.TestPhase4"
 */
public class TestPhase4 {

    private static final String GREEN  = "\u001B[32m";
    private static final String RED    = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN   = "\u001B[36m";
    private static final String RESET  = "\u001B[0m";

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║   matrix-pak — Тест Фази 4 (ПРГ2 та ПРГ3)        ║");
        System.out.println("╚══════════════════════════════════════════════════╝\n");

        testPRG2Correctness();
        testPRG2Scaling();
        testPRG3Correctness();
        testPRG3Scaling();
        testAllThreePrograms();

        System.out.println("\n══════════════════════════════════════════════════");
        System.out.printf("  Результат: %s%d passed%s, %s%d failed%s%n",
                GREEN, passed, RESET, failed > 0 ? RED : GREEN, failed, RESET);
        System.out.println("══════════════════════════════════════════════════");

        if (failed == 0) {
            System.out.println(GREEN + "  ✓ Фаза 4 завершена. Всі три програми працюють." + RESET);
        } else {
            System.out.println(RED + "  ✗ Є помилки — перевір логи вище." + RESET);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  1. PRG2 — коректність
    // ─────────────────────────────────────────────────────────────

    private static void testPRG2Correctness() {
        section("1. ПРГ2 — коректність MA = MB×MC + MD×ME");

        // MB = MC = MD = ME = I (одинична 2×2)
        // MA = I×I + I×I = I + I = [[2,0],[0,2]]
        double[][] I2 = {{1, 0}, {0, 1}};
        ExecutionResult r = new PRG2(2, 1).execute(
                copy(I2), copy(I2), copy(I2), copy(I2));
        check("MA[0][0]=2.0 (одиничні матриці)", eq(r.getResultMA().get(0, 0), 2.0));
        check("MA[0][1]=0.0 (одиничні матриці)", eq(r.getResultMA().get(0, 1), 0.0));
        check("MA[1][1]=2.0 (одиничні матриці)", eq(r.getResultMA().get(1, 1), 2.0));
        check("prgNumber=2", r.getPrgNumber() == 2);

        // Паралельний = послідовний для N=64
        int N = 64;
        Matrix mMB = rndMatrix(N, "MB"); Matrix mMC = rndMatrix(N, "MC");
        Matrix mMD = rndMatrix(N, "MD"); Matrix mME = rndMatrix(N, "ME");

        Matrix MA_seq = MatrixUtils.add(
                MatrixUtils.multiply(mMB, mMC, "T1"),
                MatrixUtils.multiply(mMD, mME, "T2"),
                "MA_seq");

        double[][] rawMB = mMB.getData(), rawMC = mMC.getData();
        double[][] rawMD = mMD.getData(), rawME = mME.getData();

        for (int p : new int[]{1, 2, 4}) {
            ExecutionResult rp = new PRG2(N, p).execute(rawMB, rawMC, rawMD, rawME);
            double diff = MatrixUtils.maxDifference(MA_seq, rp.getResultMA());
            check(String.format("P=%d збіг з еталоном (Δmax=%.2e)", p, diff),
                    diff < MatrixUtils.EPSILON);
        }

        // execute() без аргументів
        ExecutionResult auto = new PRG2(40, 2).execute();
        check("execute() без арг: prgNumber=2", auto.getPrgNumber() == 2);
        check("execute() без арг: MA не null",  auto.getResultMA() != null);

        // IllegalArgument при < 4 матрицях
        boolean threw = false;
        try { new PRG2(10, 1).execute(new double[10][10], new double[10][10]); }
        catch (IllegalArgumentException e) { threw = true; }
        check("execute(2 матриці) → IllegalArgument", threw);
    }

    // ─────────────────────────────────────────────────────────────
    //  2. PRG2 — масштабування
    // ─────────────────────────────────────────────────────────────

    private static void testPRG2Scaling() {
        section("2. ПРГ2 — масштабування (N=250, P=1..6)");

        int N = 250;
        double[][] MB = rndMatrix(N, "MB").getData();
        double[][] MC = rndMatrix(N, "MC").getData();
        double[][] MD = rndMatrix(N, "MD").getData();
        double[][] ME = rndMatrix(N, "ME").getData();

        // Прогрівання
        System.out.println("  Прогрівання JVM...");
        new PRG2(N, 1).execute(MB, MC, MD, ME);

        System.out.printf("  %-6s %-12s %-10s %-10s%n", "P", "Час (мс)", "S", "E (%)");
        System.out.println("  " + "─".repeat(42));

        long baselineMs = -1;
        for (int p : new int[]{1, 2, 3, 4, 6}) {
            long best = Long.MAX_VALUE;
            for (int run = 0; run < 3; run++) {
                ExecutionResult r = new PRG2(N, p).execute(MB, MC, MD, ME);
                if (r.getElapsedMs() < best) best = r.getElapsedMs();
            }
            if (p == 1) baselineMs = best;
            double s = (baselineMs > 0 && best > 0) ? (double) baselineMs / best : 1.0;
            double e = s / p * 100.0;
            System.out.printf("  %-6d %-12d %-10.3f %.1f%%%n", p, best, s, e);
        }
        check("ПРГ2 масштабування виконано без помилок", true);
    }

    // ─────────────────────────────────────────────────────────────
    //  3. PRG3 — коректність
    // ─────────────────────────────────────────────────────────────

    private static void testPRG3Correctness() {
        section("3. ПРГ3 — коректність MA = max(MR) × (MB × MC)");

        // MR = [[3,1],[2,4]], max=4
        // MB = MC = I → MB×MC = I → MA = 4×I = [[4,0],[0,4]]
        double[][] MR = {{3.0, 1.0}, {2.0, 4.0}};
        double[][] MB = {{1, 0}, {0, 1}};
        double[][] MC = {{1, 0}, {0, 1}};

        ExecutionResult r1 = new PRG3(2, 1).execute(MR, MB, MC);
        check("max(MR)=4, MA[0][0]=4.0", eq(r1.getResultMA().get(0, 0), 4.0));
        check("max(MR)=4, MA[0][1]=0.0", eq(r1.getResultMA().get(0, 1), 0.0));
        check("max(MR)=4, MA[1][1]=4.0", eq(r1.getResultMA().get(1, 1), 4.0));
        check("prgNumber=3", r1.getPrgNumber() == 3);

        // Перевірка з від'ємним максимумом
        double[][] MR_neg = {{-3.0, -7.0}, {-1.0, -2.0}};
        ExecutionResult r_neg = new PRG3(2, 1).execute(MR_neg, copy(MB), copy(MC));
        check("max(MR_neg)=-1, MA[0][0]=-1.0", eq(r_neg.getResultMA().get(0, 0), -1.0));

        // Паралельний = послідовний для N=64
        int N = 64;
        Matrix mMR = rndMatrix(N, "MR");
        Matrix mMB = rndMatrix(N, "MB");
        Matrix mMC = rndMatrix(N, "MC");

        double scalar = MatrixUtils.maxValue(mMR);
        Matrix MT_seq = MatrixUtils.multiply(mMB, mMC, "MT");
        Matrix MA_seq = MatrixUtils.multiplyScalar(MT_seq, scalar, "MA_seq");

        double[][] rawMR = mMR.getData();
        double[][] rawMB = mMB.getData();
        double[][] rawMC = mMC.getData();

        for (int p : new int[]{1, 2, 4}) {
            ExecutionResult rp = new PRG3(N, p).execute(rawMR, rawMB, rawMC);
            double diff = MatrixUtils.maxDifference(MA_seq, rp.getResultMA());
            check(String.format("P=%d збіг з еталоном (Δmax=%.2e)", p, diff),
                    diff < MatrixUtils.EPSILON);
        }

        // CAS-редукція при P=6
        ExecutionResult r6 = new PRG3(N, 6).execute(rawMR, rawMB, rawMC);
        double diff6 = MatrixUtils.maxDifference(MA_seq, r6.getResultMA());
        check(String.format("P=6 CAS-редукція коректна (Δmax=%.2e)", diff6),
                diff6 < MatrixUtils.EPSILON);

        // execute() без аргументів
        ExecutionResult auto = new PRG3(40, 2).execute();
        check("execute() без арг: prgNumber=3", auto.getPrgNumber() == 3);
        check("execute() без арг: MA не null",  auto.getResultMA() != null);

        // IllegalArgument при < 3 матрицях
        boolean threw = false;
        try { new PRG3(10, 1).execute(new double[10][10]); }
        catch (IllegalArgumentException e) { threw = true; }
        check("execute(1 матриця) → IllegalArgument", threw);
    }

    // ─────────────────────────────────────────────────────────────
    //  4. PRG3 — масштабування
    // ─────────────────────────────────────────────────────────────

    private static void testPRG3Scaling() {
        section("4. ПРГ3 — масштабування (N=250, P=1..6)");

        int N = 250;
        double[][] MR = rndMatrix(N, "MR").getData();
        double[][] MB = rndMatrix(N, "MB").getData();
        double[][] MC = rndMatrix(N, "MC").getData();

        // Прогрівання
        System.out.println("  Прогрівання JVM...");
        new PRG3(N, 1).execute(MR, MB, MC);

        System.out.printf("  %-6s %-12s %-10s %-10s%n", "P", "Час (мс)", "S", "E (%)");
        System.out.println("  " + "─".repeat(42));

        long baselineMs = -1;
        for (int p : new int[]{1, 2, 3, 4, 6}) {
            long best = Long.MAX_VALUE;
            for (int run = 0; run < 3; run++) {
                ExecutionResult r = new PRG3(N, p).execute(MR, MB, MC);
                if (r.getElapsedMs() < best) best = r.getElapsedMs();
            }
            if (p == 1) baselineMs = best;
            double s = (baselineMs > 0 && best > 0) ? (double) baselineMs / best : 1.0;
            double e = s / p * 100.0;
            System.out.printf("  %-6d %-12d %-10.3f %.1f%%%n", p, best, s, e);
        }
        check("ПРГ3 масштабування виконано без помилок", true);
    }

    // ─────────────────────────────────────────────────────────────
    //  5. Всі три програми через AbstractPRG (поліморфізм)
    // ─────────────────────────────────────────────────────────────

    private static void testAllThreePrograms() {
        section("5. Всі три програми через AbstractPRG (поліморфізм)");

        AbstractPRG[] programs = {
                new PRG1(50, 2),
                new PRG2(50, 2),
                new PRG3(50, 2)
        };

        LogService.getInstance().clear();

        for (AbstractPRG prg : programs) {
            ExecutionResult r = prg.execute();
            check(String.format("%s: prgNumber=%d коректний",
                            prg.getFormula(), r.getPrgNumber()),
                    r.getPrgNumber() == prg.getPrgNumber());
            check(String.format("%s: MA не null", prg.getFormula()),
                    r.getResultMA() != null);
            check(String.format("%s: час >= 0", prg.getFormula()),
                    r.getElapsedMs() >= 0);
        }

        String log = LogService.getInstance().getAllEntries();
        check("Журнал: ПРГ1 СТАРТ", log.contains("ПРГ1 СТАРТ"));
        check("Журнал: ПРГ2 СТАРТ", log.contains("ПРГ2 СТАРТ"));
        check("Журнал: ПРГ3 СТАРТ", log.contains("ПРГ3 СТАРТ"));
        check("Журнал: Редукція завершена (PRG3)", log.contains("Редукція завершена"));
    }

    // ─────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────

    private static Matrix rndMatrix(int N, String name) {
        Matrix m = new Matrix(N, N, name);
        MatrixUtils.fillRandom(m);
        return m;
    }

    private static double[][] copy(double[][] src) {
        double[][] dst = new double[src.length][src[0].length];
        for (int i = 0; i < src.length; i++)
            System.arraycopy(src[i], 0, dst[i], 0, src[i].length);
        return dst;
    }

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