package org.example.ui;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import org.example.model.BenchmarkResult;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Утилітний клас для стилізації графіків бенчмарку та їх збереження у PNG.
 *
 * <h3>Функціонал:</h3>
 * <ul>
 *   <li>Робить ідеальну лінію пунктирною (dashed stroke).</li>
 *   <li>Додає тултипи з точними значеннями на кожну точку даних.</li>
 *   <li>Встановлює кольори серій програмно (для обох тем).</li>
 *   <li>Зберігає знімок графіку у PNG через {@link SwingFXUtils}.</li>
 * </ul>
 */
public final class ChartBuilder {

    // Кольори серій
    private static final String COLOR_IDEAL = "#e67e22";   // помаранчевий — ідеальна
    private static final String COLOR_REAL  = "#2980b9";   // синій — реальна
    private static final String COLOR_REAL_EFF = "#27ae60"; // зелений — ефективність

    private ChartBuilder() {}

    // ─────────────────────────────────────────────────────────────
    //  Стилізація після додавання даних
    // ─────────────────────────────────────────────────────────────

    /**
     * Стилізує графік прискорення після додавання всіх точок.
     * Викликати з {@code Platform.runLater} після заповнення серій.
     *
     * @param chart          графік прискорення
     * @param seriesIdeal    серія ідеальної лінії (S = P)
     * @param seriesReal     серія реальних даних
     */
    public static void styleSpeedupChart(
            LineChart<Number, Number> chart,
            XYChart.Series<Number, Number> seriesIdeal,
            XYChart.Series<Number, Number> seriesReal) {

        styleIdealSeries(seriesIdeal, COLOR_IDEAL);
        styleRealSeries(seriesReal, COLOR_REAL, "S");
    }

    /**
     * Стилізує графік ефективності після додавання всіх точок.
     *
     * @param chart          графік ефективності
     * @param seriesIdeal    серія ідеальної лінії (E = 100%)
     * @param seriesReal     серія реальних даних
     */
    public static void styleEfficiencyChart(
            LineChart<Number, Number> chart,
            XYChart.Series<Number, Number> seriesIdeal,
            XYChart.Series<Number, Number> seriesReal) {

        styleIdealSeries(seriesIdeal, COLOR_IDEAL);
        styleRealSeries(seriesReal, COLOR_REAL_EFF, "E");
    }

    /**
     * Стилізує одну точку даних одразу після її додавання на графік.
     * Викликати з {@code Platform.runLater} після кожного {@code addChartPoint}.
     *
     * @param data      об'єкт XYChart.Data щойно доданий на графік
     * @param result    відповідний BenchmarkResult для тексту тултипу
     * @param isSpeedup {@code true} → тултип показує S, {@code false} → E
     */
    public static void styleDataPoint(
            XYChart.Data<Number, Number> data,
            BenchmarkResult result,
            boolean isSpeedup) {

        Node node = data.getNode();
        if (node == null) return;

        String tipText = isSpeedup
                ? String.format("P = %d\nS = %.3f\nT = %d мс",
                result.getP(), result.getSpeedup(), result.getElapsedMs())
                : String.format("P = %d\nE = %.1f%%\nT = %d мс",
                result.getP(), result.getEfficiency() * 100.0, result.getElapsedMs());

        Tooltip tip = new Tooltip(tipText);
        tip.setStyle("-fx-font-size: 12px;");
        Tooltip.install(node, tip);

        // Підписи значень над точкою
        if (node instanceof StackPane sp) {
            String label = isSpeedup
                    ? String.format("%.2f", result.getSpeedup())
                    : String.format("%.0f%%", result.getEfficiency() * 100.0);
            Label lbl = new Label(label);
            lbl.setStyle("-fx-font-size: 9px; -fx-text-fill: #cccccc;");
            lbl.setPadding(new Insets(0, 0, 16, 0));
            sp.getChildren().add(lbl);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Внутрішні методи стилізації серій
    // ─────────────────────────────────────────────────────────────

    /**
     * Застосовує стиль пунктирної лінії до ідеальної серії.
     * Запускається через {@code addListener} на nodeProperty,
     * бо вузол стає доступним тільки після додавання серії на графік.
     */
    private static void styleIdealSeries(
            XYChart.Series<Number, Number> series, String color) {

        // Лінія серії
        Node lineNode = series.getNode();
        if (lineNode != null) {
            lineNode.setStyle(String.format(
                    "-fx-stroke: %s; -fx-stroke-width: 2px;"
                            + "-fx-stroke-dash-array: 10 6; -fx-opacity: 0.85;",
                    color));
        } else {
            series.nodeProperty().addListener((obs, o, n) -> {
                if (n != null) n.setStyle(String.format(
                        "-fx-stroke: %s; -fx-stroke-width: 2px;"
                                + "-fx-stroke-dash-array: 10 6; -fx-opacity: 0.85;",
                        color));
            });
        }

        // Символи (кружки) — приховуємо для ідеальної
        for (XYChart.Data<Number, Number> d : series.getData()) {
            Node sym = d.getNode();
            if (sym != null) sym.setVisible(false);
            else d.nodeProperty().addListener((o, ov, nv) -> {
                if (nv != null) nv.setVisible(false);
            });
        }
    }

    /**
     * Застосовує суцільну кольорову лінію та круглі символи до реальної серії.
     */
    private static void styleRealSeries(
            XYChart.Series<Number, Number> series, String color, String metricLabel) {

        Node lineNode = series.getNode();
        String lineStyle = String.format(
                "-fx-stroke: %s; -fx-stroke-width: 2.5px;", color);
        if (lineNode != null) {
            lineNode.setStyle(lineStyle);
        } else {
            series.nodeProperty().addListener((obs, o, n) -> {
                if (n != null) n.setStyle(lineStyle);
            });
        }

        // Символи — кольорові кружки
        String dotStyle = String.format(
                "-fx-background-color: %s, white;"
                        + "-fx-background-insets: 0, 2;"
                        + "-fx-background-radius: 5px;"
                        + "-fx-padding: 5px;", color);
        for (XYChart.Data<Number, Number> d : series.getData()) {
            applyDotStyle(d, dotStyle);
        }
    }

    private static void applyDotStyle(XYChart.Data<Number, Number> d, String style) {
        if (d.getNode() != null) {
            d.getNode().setStyle(style);
        } else {
            d.nodeProperty().addListener((o, ov, nv) -> {
                if (nv != null) nv.setStyle(style);
            });
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Побудова графіків з нуля (після завершення всього бенчмарку)
    // ─────────────────────────────────────────────────────────────

    /**
     * Повністю перебудовує обидва графіки на основі списку результатів.
     * Викликати після успішного завершення Task з {@code Platform.runLater}.
     *
     * @param chartSpeedup    графік прискорення
     * @param seriesIdealS    ідеальна серія S(P)
     * @param seriesRealS     реальна серія S(P)
     * @param chartEfficiency графік ефективності
     * @param seriesIdealE    ідеальна серія E(P)
     * @param seriesRealE     реальна серія E(P)
     * @param results         список результатів бенчмарку
     */
    public static void buildCharts(
            LineChart<Number, Number> chartSpeedup,
            XYChart.Series<Number, Number> seriesIdealS,
            XYChart.Series<Number, Number> seriesRealS,
            LineChart<Number, Number> chartEfficiency,
            XYChart.Series<Number, Number> seriesIdealE,
            XYChart.Series<Number, Number> seriesRealE,
            List<BenchmarkResult> results) {

        // Очищаємо
        seriesIdealS.getData().clear();
        seriesRealS.getData().clear();
        seriesIdealE.getData().clear();
        seriesRealE.getData().clear();

        for (BenchmarkResult r : results) {
            int p = r.getP();

            // Ідеальні точки
            seriesIdealS.getData().add(new XYChart.Data<>(p, (double) p));
            seriesIdealE.getData().add(new XYChart.Data<>(p, 100.0));

            // Реальні точки
            XYChart.Data<Number, Number> dS = new XYChart.Data<>(p, r.getSpeedup());
            XYChart.Data<Number, Number> dE = new XYChart.Data<>(p, r.getEfficiency() * 100.0);
            seriesRealS.getData().add(dS);
            seriesRealE.getData().add(dE);

            // Тултипи після відмальовування
            Platform.runLater(() -> {
                styleDataPoint(dS, r, true);
                styleDataPoint(dE, r, false);
            });
        }

        // Стилізація ліній після заповнення
        Platform.runLater(() -> {
            styleSpeedupChart(chartSpeedup, seriesIdealS, seriesRealS);
            styleEfficiencyChart(chartEfficiency, seriesIdealE, seriesRealE);
        });
    }

    // ─────────────────────────────────────────────────────────────
    //  Збереження графіка у PNG
    // ─────────────────────────────────────────────────────────────

    /**
     * Зберігає знімок JavaFX-вузла (графіка) у PNG-файл.
     *
     * @param chart  вузол для знімку (LineChart або будь-який Node)
     * @param file   файл для збереження
     * @throws IOException при помилці запису
     */
    public static void saveToPng(javafx.scene.Node chart, File file) throws IOException {
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);

        WritableImage image = chart.snapshot(params, null);
        ImageIO.write(
                SwingFXUtils.fromFXImage(image, null),
                "png",
                file
        );
    }

    /**
     * Зберігає обидва графіки в один PNG-файл (поруч, горизонтально).
     * Спочатку зберігає кожен окремо, потім об'єднує через AWT Graphics2D.
     *
     * @param chartS  графік прискорення
     * @param chartE  графік ефективності
     * @param file    файл для збереження
     * @throws IOException при помилці
     */
    public static void saveBothToPng(
            LineChart<Number, Number> chartS,
            LineChart<Number, Number> chartE,
            File file) throws IOException {

        // Знімаємо кожен графік окремо
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.WHITE);

        WritableImage imgS = chartS.snapshot(params, null);
        WritableImage imgE = chartE.snapshot(params, null);

        java.awt.image.BufferedImage bS = SwingFXUtils.fromFXImage(imgS, null);
        java.awt.image.BufferedImage bE = SwingFXUtils.fromFXImage(imgE, null);

        // Об'єднуємо горизонтально
        int totalW = bS.getWidth() + bE.getWidth() + 10;
        int totalH = Math.max(bS.getHeight(), bE.getHeight());

        java.awt.image.BufferedImage combined =
                new java.awt.image.BufferedImage(totalW, totalH,
                        java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = combined.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, totalW, totalH);
        g.drawImage(bS, 0, 0, null);
        g.drawImage(bE, bS.getWidth() + 10, 0, null);
        g.dispose();

        ImageIO.write(combined, "png", file);
    }
}
