package org.example.service;

import org.example.model.BenchmarkResult;
import org.example.model.Matrix;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Сервіс для збереження та завантаження матриць і результатів бенчмарку у файли.
 *
 * <h3>Підтримувані формати:</h3>
 * <ul>
 *   <li><b>Матриці</b> — CSV: кожен рядок матриці = рядок файлу,
 *       елементи розділені комою. Перший рядок — заголовок-коментар
 *       з назвою матриці та розміром.</li>
 *   <li><b>Результати бенчмарку</b> — CSV з заголовком:
 *       {@code P,Час(мс),S,E(%),N,ПРГ}</li>
 * </ul>
 *
 * <p>Усі методи статичні — клас не потребує стану.
 * При помилках файлового IO кидає {@link IOException} — обробляйте у контролері.
 */
public final class MatrixFileService {

    private static final String CSV_SEPARATOR  = ",";
    private static final String LINE_SEPARATOR = System.lineSeparator();
    private static final DateTimeFormatter FILE_TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private MatrixFileService() {}

    // ═════════════════════════════════════════════════════════════
    //  МАТРИЦІ — збереження
    // ═════════════════════════════════════════════════════════════

    /**
     * Зберігає матрицю у CSV-файл.
     *
     * <p>Формат файлу:
     * <pre>
     * # Matrix: MA, 500x500, saved: 2026-05-22_14-30-00
     * 1.234567,-2.345678,3.456789,...
     * ...
     * </pre>
     *
     * @param matrix матриця для збереження
     * @param path   шлях до файлу (буде створено або перезаписано)
     * @throws IOException при помилці запису
     */
    public static void saveMatrix(Matrix matrix, Path path) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            // Заголовок-коментар
            writer.write(String.format("# Matrix: %s, %dx%d, saved: %s",
                    matrix.getName(), matrix.rows, matrix.cols,
                    LocalDateTime.now().format(FILE_TIME_FMT)));
            writer.write(LINE_SEPARATOR);

            double[][] data = matrix.getData();
            for (int i = 0; i < matrix.rows; i++) {
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < matrix.cols; j++) {
                    if (j > 0) sb.append(CSV_SEPARATOR);
                    // Формат: до 10 значущих цифр, без зайвих нулів
                    sb.append(String.format("%.10g", data[i][j]));
                }
                writer.write(sb.toString());
                writer.write(LINE_SEPARATOR);
            }
        }
    }

    /**
     * Зберігає матрицю у файл з автоматично згенерованим іменем.
     *
     * <p>Ім'я файлу: {@code MA_500x500_2026-05-22_14-30-00.csv}
     *
     * @param matrix    матриця для збереження
     * @param directory директорія для збереження
     * @return шлях до створеного файлу
     * @throws IOException при помилці запису
     */
    public static Path saveMatrixAuto(Matrix matrix, Path directory) throws IOException {
        String filename = String.format("%s_%dx%d_%s.csv",
                matrix.getName(), matrix.rows, matrix.cols,
                LocalDateTime.now().format(FILE_TIME_FMT));
        Path filePath = directory.resolve(filename);
        saveMatrix(matrix, filePath);
        return filePath;
    }

    // ═════════════════════════════════════════════════════════════
    //  МАТРИЦІ — завантаження
    // ═════════════════════════════════════════════════════════════

    /**
     * Завантажує матрицю з CSV-файлу.
     *
     * <p>Рядки що починаються з {@code #} вважаються коментарями та ігноруються.
     * Назва матриці береться з імені файлу (без розширення).
     *
     * @param path шлях до CSV-файлу
     * @return завантажена матриця
     * @throws IOException              при помилці читання
     * @throws IllegalArgumentException якщо файл має некоректний формат
     */
    public static Matrix loadMatrix(Path path) throws IOException {
        String name = path.getFileName().toString().replaceAll("\\.csv$", "");
        List<double[]> rows = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] tokens = line.split(CSV_SEPARATOR, -1);
                double[] row = new double[tokens.length];
                for (int j = 0; j < tokens.length; j++) {
                    try {
                        row[j] = Double.parseDouble(tokens[j].trim());
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException(String.format(
                                "Некоректне число '%s' у рядку %d, стовпці %d файлу: %s",
                                tokens[j], lineNumber, j + 1, path.getFileName()));
                    }
                }
                rows.add(row);
            }
        }

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Файл не містить даних матриці: " + path);
        }

        int cols = rows.get(0).length;
        for (int i = 1; i < rows.size(); i++) {
            if (rows.get(i).length != cols) {
                throw new IllegalArgumentException(String.format(
                        "Рядок %d має %d стовпців, але очікується %d. Файл: %s",
                        i + 1, rows.get(i).length, cols, path.getFileName()));
            }
        }

        double[][] data = rows.toArray(new double[0][]);
        return new Matrix(data, name);
    }

    // ═════════════════════════════════════════════════════════════
    //  РЕЗУЛЬТАТИ БЕНЧМАРКУ — збереження
    // ═════════════════════════════════════════════════════════════

    /**
     * Зберігає список результатів бенчмарку у CSV-файл.
     *
     * <p>Формат файлу:
     * <pre>
     * # Benchmark results: PRG1, N=500, saved: 2026-05-22_14-30-00
     * P,Час(мс),S,E(%),N,ПРГ
     * 1,847,1.000,100.0,500,1
     * 2,445,1.904,95.2,500,1
     * ...
     * </pre>
     *
     * @param results   список результатів бенчмарку
     * @param prgNumber номер програми (для заголовка)
     * @param N         розмір матриць (для заголовка)
     * @param path      шлях до файлу
     * @throws IOException при помилці запису
     */
    public static void saveBenchmarkResults(List<BenchmarkResult> results,
                                            int prgNumber, int N,
                                            Path path) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            // Заголовок-коментар
            writer.write(String.format("# Benchmark results: PRG%d, N=%d, saved: %s",
                    prgNumber, N, LocalDateTime.now().format(FILE_TIME_FMT)));
            writer.write(LINE_SEPARATOR);

            // Заголовок CSV
            writer.write("P,Час(мс),S,E(%),N,ПРГ");
            writer.write(LINE_SEPARATOR);

            // Дані
            for (BenchmarkResult r : results) {
                writer.write(String.format("%d,%d,%.6f,%.2f,%d,%d",
                        r.getP(),
                        r.getElapsedMs(),
                        r.getSpeedup(),
                        r.getEfficiency() * 100.0,
                        r.getN(),
                        r.getPrgNumber()));
                writer.write(LINE_SEPARATOR);
            }
        }
    }

    /**
     * Зберігає результати бенчмарку з автоматично згенерованим іменем файлу.
     *
     * <p>Ім'я: {@code benchmark_PRG1_N500_2026-05-22_14-30-00.csv}
     *
     * @param results   список результатів
     * @param prgNumber номер програми
     * @param N         розмір матриць
     * @param directory директорія для збереження
     * @return шлях до створеного файлу
     * @throws IOException при помилці запису
     */
    public static Path saveBenchmarkAuto(List<BenchmarkResult> results,
                                         int prgNumber, int N,
                                         Path directory) throws IOException {
        String filename = String.format("benchmark_PRG%d_N%d_%s.csv",
                prgNumber, N, LocalDateTime.now().format(FILE_TIME_FMT));
        Path filePath = directory.resolve(filename);
        saveBenchmarkResults(results, prgNumber, N, filePath);
        return filePath;
    }

    // ═════════════════════════════════════════════════════════════
    //  РЕЗУЛЬТАТИ БЕНЧМАРКУ — завантаження
    // ═════════════════════════════════════════════════════════════

    /**
     * Завантажує результати бенчмарку з CSV-файлу.
     *
     * <p>Очікуваний формат: {@code P,Час(мс),S,E(%),N,ПРГ} (рядки з {@code #} ігноруються).
     *
     * @param path шлях до CSV-файлу
     * @return список {@link BenchmarkResult}
     * @throws IOException при помилці читання або некоректному форматі
     */
    public static List<BenchmarkResult> loadBenchmarkResults(Path path) throws IOException {
        List<BenchmarkResult> results = new ArrayList<>();
        long baselineMs = -1;

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            boolean headerSkipped = false;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                // Пропускаємо рядок-заголовок CSV
                if (!headerSkipped) { headerSkipped = true; continue; }

                String[] t = line.split(CSV_SEPARATOR, -1);
                if (t.length < 6) continue;

                int    p         = Integer.parseInt(t[0].trim());
                long   elapsedMs = Long.parseLong(t[1].trim());
                int    n         = Integer.parseInt(t[4].trim());
                int    prgNumber = Integer.parseInt(t[5].trim());

                if (baselineMs < 0) baselineMs = elapsedMs;
                results.add(new BenchmarkResult(n, p, elapsedMs, baselineMs, prgNumber));
            }
        }
        return results;
    }

    // ═════════════════════════════════════════════════════════════
    //  Утиліти
    // ═════════════════════════════════════════════════════════════

    /**
     * Повертає розмір файлу у зручному для читання форматі (Б / КБ / МБ).
     *
     * @param path шлях до файлу
     * @return рядок, наприклад "1.23 МБ"
     */
    public static String humanFileSize(Path path) {
        try {
            long bytes = Files.size(path);
            if (bytes < 1024) return bytes + " Б";
            if (bytes < 1024 * 1024) return String.format("%.1f КБ", bytes / 1024.0);
            return String.format("%.2f МБ", bytes / (1024.0 * 1024.0));
        } catch (IOException e) {
            return "невідомо";
        }
    }
}
