package org.example.ui;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
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
 * <p>Фаза 9: виправлено кольори символів легенди (раніше JavaFX
 * показував дефолтні кольори series0/series1 замість наших).
 */
public final class ChartBuilder {

    // Кольори серій
    public static final String COLOR_IDEAL     = "#e67e22";  // помаранчевий — ідеальна
    public static final String COLOR_REAL_S    = "#2980b9";  // синій — реальне S
    public static final String COLOR_REAL_E    = "#27ae60";  // зелений — реальна E

    private ChartBuilder() {}

    // ─────────────────────────────────────────────────────────────
    //  Стилізація графіків
    // ─────────────────────────────────────────────────────────────

    public static void styleSpeedupChart(
            LineChart<Number, Number> chart,
            XYChart.Series<Number, Number> seriesIdeal,
            XYChart.Series<Number, Number> seriesReal) {

        styleIdealSeries(seriesIdeal, COLOR_IDEAL);
        styleRealSeries(seriesReal, COLOR_REAL_S);
        fixLegendColors(chart, COLOR_IDEAL, COLOR_REAL_S);
    }

    public static void styleEfficiencyChart(
            LineChart<Number, Number> chart,
            XYChart.Series<Number, Number> seriesIdeal,
            XYChart.Series<Number, Number> seriesReal) {

        styleIdealSeries(seriesIdeal, COLOR_IDEAL);
        styleRealSeries(seriesReal, COLOR_REAL_E);
        fixLegendColors(chart, COLOR_IDEAL, COLOR_REAL_E);
    }

    // ─────────────────────────────────────────────────────────────
    //  Фікс кольорів легенди (Фаза 9)
    // ─────────────────────────────────────────────────────────────

    /**
     * Виправляє кольори символів у легенді графіка.
     *
     * <p>JavaFX за замовчуванням призначає кольори легенди з вбудованої
     * палітри (series0=червоний, series1=жовтий) незалежно від програмного
     * стилю ліній. Цей метод знаходить вузли легенди та застосовує
     * правильні кольори після рендеру.
     *
     * @param chart       графік
     * @param idealColor  колір для першої серії (ідеальна)
     * @param realColor   колір для другої серії (реальна)
     */
    public static void fixLegendColors(LineChart<?, ?> chart,
                                       String idealColor, String realColor) {
        Platform.runLater(() -> {
            // Легенда — FlowPane з дочірніми HBox-елементами
            Node legendNode = chart.lookup(".chart-legend");
            if (!(legendNode instanceof Pane legend)) return;

            List<Node> items = legend.getChildrenUnmodifiable();
            String[] colors = {idealColor, realColor};

            for (int i = 0; i < Math.min(items.size(), colors.length); i++) {
                Node item = items.get(i);
                // Символ легенди — Region зі стиль-класом chart-legend-item-symbol
                Node symbol = item.lookup(".chart-legend-item-symbol");
                if (symbol == null) continue;

                boolean isIdeal = (i == 0);
                if (isIdeal) {
                    // Ідеальна — суцільне заповнення кольором (квадратний dash)
                    symbol.setStyle(
                            "-fx-background-color: " + colors[i] + ";"
                                    + "-fx-background-radius: 2;"
                                    + "-fx-padding: 4 10 4 10;");
                } else {
                    // Реальна — кружок з білою серединою
                    symbol.setStyle(
                            "-fx-background-color: " + colors[i] + ", white;"
                                    + "-fx-background-insets: 0, 2;"
                                    + "-fx-background-radius: 6;"
                                    + "-fx-padding: 6;");
                }
            }
        });
    }

    // ─────────────────────────────────────────────────────────────
    //  Стилізація серій
    // ─────────────────────────────────────────────────────────────

    private static void styleIdealSeries(
            XYChart.Series<Number, Number> series, String color) {

        String lineStyle = String.format(
                "-fx-stroke: %s; -fx-stroke-width: 2px;"
                        + "-fx-stroke-dash-array: 10 6; -fx-opacity: 0.9;", color);

        applyToSeriesLine(series, lineStyle);

        // Ховаємо символи ідеальної лінії
        for (XYChart.Data<Number, Number> d : series.getData()) {
            hideSymbol(d);
        }
    }

    private static void styleRealSeries(
            XYChart.Series<Number, Number> series, String color) {

        String lineStyle = String.format(
                "-fx-stroke: %s; -fx-stroke-width: 2.5px;", color);
        applyToSeriesLine(series, lineStyle);

        String dotStyle = String.format(
                "-fx-background-color: %s, white;"
                        + "-fx-background-insets: 0, 2;"
                        + "-fx-background-radius: 5px;"
                        + "-fx-padding: 5px;", color);

        for (XYChart.Data<Number, Number> d : series.getData()) {
            applyDotStyle(d, dotStyle);
        }
    }

    private static void applyToSeriesLine(
            XYChart.Series<Number, Number> series, String style) {
        Node node = series.getNode();
        if (node != null) {
            node.setStyle(style);
        } else {
            series.nodeProperty().addListener((obs, o, n) -> {
                if (n != null) n.setStyle(style);
            });
        }
    }

    private static void hideSymbol(XYChart.Data<Number, Number> d) {
        if (d.getNode() != null) {
            d.getNode().setVisible(false);
        } else {
            d.nodeProperty().addListener((o, ov, nv) -> {
                if (nv != null) nv.setVisible(false);
            });
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
    //  Тултипи та підписи
    // ─────────────────────────────────────────────────────────────

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

        if (node instanceof StackPane sp) {
            String label = isSpeedup
                    ? String.format("%.2f", result.getSpeedup())
                    : String.format("%.0f%%", result.getEfficiency() * 100.0);
            Label lbl = new Label(label);
            lbl.setStyle("-fx-font-size: 9px; -fx-text-fill: #cccccc;");
            lbl.setPadding(new Insets(0, 0, 18, 0));
            sp.getChildren().add(lbl);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Побудова графіків з нуля
    // ─────────────────────────────────────────────────────────────

    public static void buildCharts(
            LineChart<Number, Number> chartSpeedup,
            XYChart.Series<Number, Number> seriesIdealS,
            XYChart.Series<Number, Number> seriesRealS,
            LineChart<Number, Number> chartEfficiency,
            XYChart.Series<Number, Number> seriesIdealE,
            XYChart.Series<Number, Number> seriesRealE,
            List<BenchmarkResult> results) {

        seriesIdealS.getData().clear();
        seriesRealS.getData().clear();
        seriesIdealE.getData().clear();
        seriesRealE.getData().clear();

        for (BenchmarkResult r : results) {
            int p = r.getP();
            seriesIdealS.getData().add(new XYChart.Data<>(p, (double) p));
            seriesIdealE.getData().add(new XYChart.Data<>(p, 100.0));

            XYChart.Data<Number, Number> dS = new XYChart.Data<>(p, r.getSpeedup());
            XYChart.Data<Number, Number> dE = new XYChart.Data<>(p, r.getEfficiency() * 100.0);
            seriesRealS.getData().add(dS);
            seriesRealE.getData().add(dE);

            Platform.runLater(() -> {
                styleDataPoint(dS, r, true);
                styleDataPoint(dE, r, false);
            });
        }

        Platform.runLater(() -> {
            styleSpeedupChart(chartSpeedup, seriesIdealS, seriesRealS);
            styleEfficiencyChart(chartEfficiency, seriesIdealE, seriesRealE);
        });
    }

    // ─────────────────────────────────────────────────────────────
    //  Збереження PNG
    // ─────────────────────────────────────────────────────────────

    public static void saveToPng(Node chart, File file) throws IOException {
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.WHITE);
        WritableImage image = chart.snapshot(params, null);
        ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);
    }

    public static void saveBothToPng(
            LineChart<Number, Number> chartS,
            LineChart<Number, Number> chartE,
            File file) throws IOException {

        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.WHITE);

        java.awt.image.BufferedImage bS =
                SwingFXUtils.fromFXImage(chartS.snapshot(params, null), null);
        java.awt.image.BufferedImage bE =
                SwingFXUtils.fromFXImage(chartE.snapshot(params, null), null);

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
