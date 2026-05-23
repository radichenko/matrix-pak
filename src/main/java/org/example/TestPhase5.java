package org.example;

import org.example.model.BenchmarkResult;
import org.example.model.Matrix;
import org.example.service.BenchmarkService;
import org.example.service.MatrixFileService;
import org.example.utils.MatrixUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Тест Фази 5 — BenchmarkService та MatrixFileService.
 *
 * Запуск: через IDE або mvn compile exec:java -Dexec.mainClass="org.example.TestPhase5"
 */
public class TestPhase5 {

    private static final String GREEN  = "\u001B[32m";
    private static final String RED    = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN   = "\u001B[36m";
    private static final String RESET  = "\u001B[0m";

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws IOException {
        System.out.println("╔═════════════════════════════════════════════════════════╗");
        System.out.println("║  matrix-pak — Тест Фази 5 (BenchmarkService + FileService) ║");
        System.out.println("╚═════════════════════════════════════════════════════════╝\n");

        Path tempDir = Files.createTempDirectory("matrix-pak-test-");

        testBenchmarkServicePRG1(tempDir);
        testBenchmarkServicePRG2(tempDir);
        testBenchmarkServicePRG3(tempDir);
        testBenchmarkServiceProgress(tempDir);
        testMatrixFileSaveLoad(tempDir);
        testBenchmarkFileSaveLoad(tempDir);

        // Прибираємо тимчасові файли
        cleanDir(tempDir);

        System.out.println("\n══════════════════════════════════════════════════════════");
        System.out.printf("  Результат: %s%d passed%s, %s%d failed%s%n",
                GREEN, passed, RESET, failed > 0 ? RED : GREEN, failed, RESET);
        System.out.println("══════════════════════════════════════════════════════════");

        if (failed == 0) {
            System.out.println(GREEN + "  ✓ Фаза 5 завершена. Сервіси готові." + RESET);
        } else {
            System.out.println(RED + "  ✗ Є помилки — перевір логи вище." + RESET);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  1. BenchmarkService — ПРГ1
    // ─────────────────────────────────────────────────────────────

    private static void testBenchmarkServicePRG1(Path dir) {
        section("1. BenchmarkService — ПРГ1 (N=100)");

        BenchmarkService svc = new BenchmarkService();
        List<BenchmarkResult> results = svc.run(1, 100,
                new int[]{1, 2, 4}, 2, null);

        check("Кількість результатів = 3",         results.size() == 3);
        check("P=1 є у результатах",                results.get(0).getP() == 1);
        check("S при P=1 = 1.0",                   eq(results.get(0).getSpeedup(), 1.0));
        check("E при P=1 = 1.0",                   eq(results.get(0).getEfficiency(), 1.0));
        check("Час > 0 при P=1",                   results.get(0).getElapsedMs() > 0);
        check("N = 100 у всіх результатах",         results.stream().allMatch(r -> r.getN() == 100));
        check("prgNumber = 1 у всіх результатах",   results.stream().allMatch(r -> r.getPrgNumber() == 1));
        check("S при P=4 > 0",                     results.get(2).getSpeedup() > 0);

        System.out.println(CYAN + "\n  Таблиця результатів ПРГ1:" + RESET);
        printTable(results);
    }

    // ─────────────────────────────────────────────────────────────
    //  2. BenchmarkService — ПРГ2
    // ─────────────────────────────────────────────────────────────

    private static void testBenchmarkServicePRG2(Path dir) {
        section("2. BenchmarkService — ПРГ2 (N=100)");

        BenchmarkService svc = new BenchmarkService();
        List<BenchmarkResult> results = svc.run(2, 100,
                new int[]{1, 2, 4}, 2, null);

        check("Кількість результатів = 3",       results.size() == 3);
        check("prgNumber=2 у всіх результатах",  results.stream().allMatch(r -> r.getPrgNumber() == 2));
        check("S при P=1 = 1.0",                 eq(results.get(0).getSpeedup(), 1.0));
        check("Час > 0 при P=2",                 results.get(1).getElapsedMs() > 0);

        System.out.println(CYAN + "\n  Таблиця результатів ПРГ2:" + RESET);
        printTable(results);
    }

    // ─────────────────────────────────────────────────────────────
    //  3. BenchmarkService — ПРГ3
    // ─────────────────────────────────────────────────────────────

    private static void testBenchmarkServicePRG3(Path dir) {
        section("3. BenchmarkService — ПРГ3 (N=100)");

        BenchmarkService svc = new BenchmarkService();
        List<BenchmarkResult> results = svc.run(3, 100,
                new int[]{1, 2, 4}, 2, null);

        check("Кількість результатів = 3",       results.size() == 3);
        check("prgNumber=3 у всіх результатах",  results.stream().allMatch(r -> r.getPrgNumber() == 3));
        check("S при P=1 = 1.0",                 eq(results.get(0).getSpeedup(), 1.0));
        check("E при P=4 > 0",                   results.get(2).getEfficiency() > 0);

        System.out.println(CYAN + "\n  Таблиця результатів ПРГ3:" + RESET);
        printTable(results);

        // Некоректні параметри
        boolean badPrg = false, badN = false, emptyArr = false;
        try { new BenchmarkService().run(5, 100); }
        catch (IllegalArgumentException e) { badPrg = true; }
        try { new BenchmarkService().run(1, 0); }
        catch (IllegalArgumentException e) { badN = true; }
        try { new BenchmarkService().run(1, 100, new int[]{}, 2, null); }
        catch (IllegalArgumentException e) { emptyArr = true; }
        check("prgNumber=5 → IllegalArgument", badPrg);
        check("N=0 → IllegalArgument",         badN);
        check("порожній threadCounts → IllegalArgument", emptyArr);
    }

    // ─────────────────────────────────────────────────────────────
    //  4. ProgressCallback
    // ─────────────────────────────────────────────────────────────

    private static void testBenchmarkServiceProgress(Path dir) {
        section("4. BenchmarkService — ProgressCallback");

        AtomicInteger callCount = new AtomicInteger(0);
        int[] lastStep = {-1};
        int[] lastTotal = {-1};

        BenchmarkService svc = new BenchmarkService();
        List<BenchmarkResult> results = svc.run(1, 50,
                new int[]{1, 2, 4}, 1,
                (stepIndex, totalSteps, result) -> {
                    callCount.incrementAndGet();
                    lastStep[0]  = stepIndex;
                    lastTotal[0] = totalSteps;
                });

        check("Колбек викликано 3 рази",         callCount.get() == 3);
        check("Останній stepIndex = 2",           lastStep[0] == 2);
        check("totalSteps = 3",                   lastTotal[0] == 3);
        check("Результатів = 3",                  results.size() == 3);
    }

    // ─────────────────────────────────────────────────────────────
    //  5. MatrixFileService — збереження та завантаження матриць
    // ─────────────────────────────────────────────────────────────

    private static void testMatrixFileSaveLoad(Path dir) throws IOException {
        section("5. MatrixFileService — збереження та завантаження матриць");

        // Генеруємо матрицю
        Matrix original = new Matrix(50, 50, "TestMA");
        MatrixUtils.fillRandom(original);

        // Зберігаємо
        Path saved = MatrixFileService.saveMatrixAuto(original, dir);
        check("Файл матриці створено",    Files.exists(saved));
        check("Файл не порожній",         Files.size(saved) > 0);
        check("Ім'я файлу містить 'TestMA'", saved.getFileName().toString().contains("TestMA"));

        String fileSize = MatrixFileService.humanFileSize(saved);
        System.out.println("  Розмір файлу: " + fileSize);
        check("humanFileSize не порожній", fileSize != null && !fileSize.isEmpty());

        // Завантажуємо
        Matrix loaded = MatrixFileService.loadMatrix(saved);
        check("rows збережено коректно",  loaded.rows == original.rows);
        check("cols збережено коректно",  loaded.cols == original.cols);
        check("Дані збережено точно",     MatrixUtils.equals(original, loaded));

        double diff = MatrixUtils.maxDifference(original, loaded);
        System.out.printf("  Максимальне відхилення після збереження/завантаження: %.2e%n", diff);
        check(String.format("Відхилення < 1e-7 (%.2e)", diff), diff < 1e-7);

        // Перевірка завантаження некоректного файлу
        Path badFile = dir.resolve("bad.csv");
        Files.writeString(badFile, "1.0,2.0\n3.0,abc\n");
        boolean threw = false;
        try { MatrixFileService.loadMatrix(badFile); }
        catch (IllegalArgumentException e) { threw = true; }
        check("Некоректне число → IllegalArgument", threw);

        // Перевірка завантаження файлу з нерівними рядками
        Path uneven = dir.resolve("uneven.csv");
        Files.writeString(uneven, "1.0,2.0\n3.0,4.0,5.0\n");
        boolean threw2 = false;
        try { MatrixFileService.loadMatrix(uneven); }
        catch (IllegalArgumentException e) { threw2 = true; }
        check("Нерівні рядки → IllegalArgument", threw2);
    }

    // ─────────────────────────────────────────────────────────────
    //  6. MatrixFileService — збереження та завантаження бенчмарку
    // ─────────────────────────────────────────────────────────────

    private static void testBenchmarkFileSaveLoad(Path dir) throws IOException {
        section("6. MatrixFileService — збереження та завантаження бенчмарку");

        // Генеруємо результати
        BenchmarkService svc = new BenchmarkService();
        List<BenchmarkResult> original = svc.run(1, 80,
                new int[]{1, 2, 4}, 1, null);

        // Зберігаємо
        Path saved = MatrixFileService.saveBenchmarkAuto(original, 1, 80, dir);
        check("Файл бенчмарку створено",          Files.exists(saved));
        check("Ім'я містить 'benchmark_PRG1'",     saved.getFileName().toString().startsWith("benchmark_PRG1"));

        String content = Files.readString(saved);
        check("Файл містить заголовок P,Час(мс)", content.contains("P,Час(мс)"));
        check("Файл містить коментар #",           content.startsWith("#"));

        // Завантажуємо
        List<BenchmarkResult> loaded = MatrixFileService.loadBenchmarkResults(saved);
        check("Кількість рядків збережена",        loaded.size() == original.size());

        // Порівнюємо P та час
        boolean allMatch = true;
        for (int i = 0; i < original.size(); i++) {
            if (original.get(i).getP() != loaded.get(i).getP()
                    || original.get(i).getElapsedMs() != loaded.get(i).getElapsedMs()) {
                allMatch = false;
                break;
            }
        }
        check("P та час збережено точно",    allMatch);
        check("prgNumber збережено",         loaded.get(0).getPrgNumber() == 1);
        check("N збережено",                 loaded.get(0).getN() == 80);

        // Пряме збереження за конкретним шляхом
        Path explicit = dir.resolve("explicit_bench.csv");
        MatrixFileService.saveBenchmarkResults(original, 2, 100, explicit);
        check("Збереження за явним шляхом", Files.exists(explicit));
    }

    // ─────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────

    private static void printTable(List<BenchmarkResult> results) {
        System.out.printf("  %-6s %-12s %-10s %-10s%n", "P", "Час (мс)", "S", "E (%)");
        System.out.println("  " + "─".repeat(42));
        for (BenchmarkResult r : results) {
            System.out.printf("  %-6d %-12d %-10.3f %.1f%%%n",
                    r.getP(), r.getElapsedMs(),
                    r.getSpeedup(), r.getEfficiency() * 100.0);
        }
        System.out.println();
    }

    private static void cleanDir(Path dir) throws IOException {
        try (var stream = Files.list(dir)) {
            stream.forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
        }
        Files.deleteIfExists(dir);
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
        return Math.abs(a - b) < 1e-9;
    }
}
