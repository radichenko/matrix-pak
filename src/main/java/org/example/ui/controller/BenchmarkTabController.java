package org.example.ui.controller;

import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import org.example.model.BenchmarkResult;
import org.example.service.BenchmarkService;
import org.example.service.LogService;
import org.example.service.MatrixFileService;
import org.example.ui.ChartBuilder;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Контролер вкладки «Бенчмарк» — Фаза 7б.
 *
 * <p>Додано відносно 7а:
 * <ul>
 *   <li>Інтеграція з {@link ChartBuilder}: пунктирна ідеальна лінія,
 *       тултипи на точках, підписи значень.</li>
 *   <li>Збереження обох графіків у PNG через {@code ChartBuilder.saveBothToPng}.</li>
 *   <li>Оновлення стилів у реальному часі після кожного кроку бенчмарку.</li>
 * </ul>
 */
public class BenchmarkTabController implements Initializable {

    // ── Параметри ─────────────────────────────────────────────────
    @FXML private RadioButton radioPRG1;
    @FXML private RadioButton radioPRG2;
    @FXML private RadioButton radioPRG3;
    @FXML private Spinner<Integer> spinnerN;
    @FXML private Spinner<Integer> spinnerRuns;
    @FXML private CheckBox chk1, chk2, chk3, chk4, chk6, chk8, chk12;
    @FXML private TextField fieldCustomP;

    // ── Керування ─────────────────────────────────────────────────
    @FXML private Button      btnRun;
    @FXML private Button      btnCancel;
    @FXML private Button      btnClear;
    @FXML private ProgressBar progressBar;
    @FXML private Label       labelProgress;

    // ── Таблиця ───────────────────────────────────────────────────
    @FXML private TableView<BenchmarkResult>           tableResults;
    @FXML private TableColumn<BenchmarkResult, Number> colP;
    @FXML private TableColumn<BenchmarkResult, Number> colTime;
    @FXML private TableColumn<BenchmarkResult, Number> colS;
    @FXML private TableColumn<BenchmarkResult, Number> colE;
    @FXML private TableColumn<BenchmarkResult, Number> colN;
    @FXML private TableColumn<BenchmarkResult, String> colPrg;
    @FXML private Label labelTableInfo;

    // ── Графіки ───────────────────────────────────────────────────
    @FXML private LineChart<Number, Number> chartSpeedup;
    @FXML private LineChart<Number, Number> chartEfficiency;
    @FXML private NumberAxis xAxisSpeedup;
    @FXML private NumberAxis yAxisSpeedup;
    @FXML private NumberAxis xAxisEfficiency;
    @FXML private NumberAxis yAxisEfficiency;

    // ── Дії ───────────────────────────────────────────────────────
    @FXML private Button btnSaveCsv;
    @FXML private Button btnSavePng;
    @FXML private Label  labelActionStatus;

    // ── Стан ─────────────────────────────────────────────────────
    private ToggleGroup prgToggleGroup;
    private Task<List<BenchmarkResult>> currentTask;
    private final ObservableList<BenchmarkResult> tableData =
            FXCollections.observableArrayList();
    private final LogService log = LogService.getInstance();

    // Серії даних
    private XYChart.Series<Number, Number> seriesSpeedup;
    private XYChart.Series<Number, Number> seriesIdealSpeedup;
    private XYChart.Series<Number, Number> seriesEfficiency;
    private XYChart.Series<Number, Number> seriesIdealEfficiency;

    // ─────────────────────────────────────────────────────────────
    //  Ініціалізація
    // ─────────────────────────────────────────────────────────────

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupToggleGroup();
        setupSpinners();
        setupTable();
        setupCharts();
    }

    private void setupToggleGroup() {
        prgToggleGroup = new ToggleGroup();
        radioPRG1.setToggleGroup(prgToggleGroup);
        radioPRG2.setToggleGroup(prgToggleGroup);
        radioPRG3.setToggleGroup(prgToggleGroup);
        radioPRG1.setSelected(true);
    }

    private void setupSpinners() {
        int maxCores = Runtime.getRuntime().availableProcessors();
        spinnerN.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(2, 5000, 500, 1));
        spinnerN.getEditor().setTextFormatter(intFormatter());
        spinnerRuns.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 10, 3, 1));
        spinnerRuns.getEditor().setTextFormatter(intFormatter());
    }

    private void setupTable() {
        colP.setCellValueFactory(d ->
                new SimpleIntegerProperty(d.getValue().getP()));
        colTime.setCellValueFactory(d ->
                new SimpleLongProperty(d.getValue().getElapsedMs()));
        colS.setCellValueFactory(d ->
                new SimpleDoubleProperty(
                        Math.round(d.getValue().getSpeedup() * 1000.0) / 1000.0));
        colE.setCellValueFactory(d ->
                new SimpleDoubleProperty(
                        Math.round(d.getValue().getEfficiency() * 1000.0) / 10.0));
        colN.setCellValueFactory(d ->
                new SimpleIntegerProperty(d.getValue().getN()));
        colPrg.setCellValueFactory(d ->
                new SimpleStringProperty("ПРГ" + d.getValue().getPrgNumber()));

        // Виділення рядка P=1 жирним
        tableResults.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(BenchmarkResult item, boolean empty) {
                super.updateItem(item, empty);
                setStyle(item != null && !empty && item.getP() == 1
                        ? "-fx-font-weight: bold;" : "");
            }
        });

        tableResults.setItems(tableData);
    }

    private void setupCharts() {
        seriesSpeedup       = new XYChart.Series<>();
        seriesIdealSpeedup  = new XYChart.Series<>();
        seriesEfficiency    = new XYChart.Series<>();
        seriesIdealEfficiency = new XYChart.Series<>();

        seriesSpeedup.setName("Реальне S");
        seriesIdealSpeedup.setName("Ідеальне S = P");
        seriesEfficiency.setName("Реальна E (%)");
        seriesIdealEfficiency.setName("Ідеальна E = 100%");

        // Ідеальна серія — першою (щоб йшла знизу у легенді)
        chartSpeedup.getData().addAll(seriesIdealSpeedup, seriesSpeedup);
        chartEfficiency.getData().addAll(seriesIdealEfficiency, seriesEfficiency);

        chartSpeedup.setAnimated(false);
        chartEfficiency.setAnimated(false);
        chartSpeedup.setCreateSymbols(true);
        chartEfficiency.setCreateSymbols(true);
    }

    // ─────────────────────────────────────────────────────────────
    //  Запуск
    // ─────────────────────────────────────────────────────────────

    @FXML
    private void onRun() {
        int N, runs;
        int[] threadCounts;
        try {
            commitSpinners();
            N    = spinnerN.getValue();
            runs = spinnerRuns.getValue();
            threadCounts = buildThreadCounts();
        } catch (Exception e) {
            showError("Некоректні параметри", e.getMessage());
            return;
        }

        if (threadCounts.length == 0) {
            showError("Параметри", "Оберіть хоча б одне значення P.");
            return;
        }

        int prgNumber = selectedPrg();
        log.info(String.format("БЕНЧМАРК: ПРГ%d | N=%d | runs=%d | P=%s",
                prgNumber, N, runs, arrayStr(threadCounts)));

        tableData.clear();
        clearCharts();
        labelTableInfo.setText("");
        setActionButtonsEnabled(false);
        labelActionStatus.setText("");

        final int[] tCounts = threadCounts;
        final int finalN = N, finalRuns = runs, finalPrg = prgNumber;

        currentTask = new Task<>() {
            @Override
            protected List<BenchmarkResult> call() {
                BenchmarkService svc = new BenchmarkService();
                return svc.run(finalPrg, finalN, tCounts, finalRuns,
                        (stepIndex, totalSteps, result) ->
                                Platform.runLater(() ->
                                        onProgressStep(stepIndex, totalSteps, result)));
            }
        };

        currentTask.setOnRunning(e   -> onTaskRunning());
        currentTask.setOnSucceeded(e -> onTaskSucceeded(currentTask.getValue()));
        currentTask.setOnFailed(e    -> onTaskFailed(currentTask.getException()));
        currentTask.setOnCancelled(e -> onTaskCancelled());

        new Thread(currentTask, "Benchmark-Thread") {{ setDaemon(true); }}.start();
    }

    @FXML private void onCancel() {
        if (currentTask != null && currentTask.isRunning()) currentTask.cancel();
    }

    @FXML
    private void onClear() {
        tableData.clear();
        clearCharts();
        labelTableInfo.setText("");
        progressBar.setProgress(0);
        progressBar.setVisible(false);
        labelProgress.setText("");
        setActionButtonsEnabled(false);
        labelActionStatus.setText("");
        log.info("UI → Результати бенчмарку очищено.");
    }

    // ─────────────────────────────────────────────────────────────
    //  Task callbacks
    // ─────────────────────────────────────────────────────────────

    private void onTaskRunning() {
        btnRun.setDisable(true);
        btnCancel.setDisable(false);
        btnClear.setDisable(true);
        progressBar.setVisible(true);
        progressBar.setProgress(0);
        labelProgress.setText("Прогрівання JVM...");
    }

    /**
     * Після кожного виміру: додає рядок у таблицю, оновлює графіки та стилізує нові точки.
     */
    private void onProgressStep(int stepIndex, int totalSteps, BenchmarkResult result) {
        // Таблиця
        tableData.add(result);
        tableResults.scrollTo(result);

        // Прогрес
        progressBar.setProgress((double)(stepIndex + 1) / totalSteps);
        labelProgress.setText(String.format(
                "Крок %d/%d — P=%d → %d мс  |  S=%.3f  |  E=%.1f%%",
                stepIndex + 1, totalSteps,
                result.getP(), result.getElapsedMs(),
                result.getSpeedup(), result.getEfficiency() * 100.0));

        // Додаємо точки на графіки
        int p = result.getP();
        XYChart.Data<Number, Number> dS =
                new XYChart.Data<>(p, result.getSpeedup());
        XYChart.Data<Number, Number> dE =
                new XYChart.Data<>(p, result.getEfficiency() * 100.0);
        XYChart.Data<Number, Number> dIS = new XYChart.Data<>(p, (double) p);
        XYChart.Data<Number, Number> dIE = new XYChart.Data<>(p, 100.0);

        seriesRealPointLater(dS, dE, dIS, dIE, result);
    }

    /**
     * Додає точки на графіки та застосовує стилі після відмальовування.
     */
    private void seriesRealPointLater(
            XYChart.Data<Number, Number> dS,
            XYChart.Data<Number, Number> dE,
            XYChart.Data<Number, Number> dIS,
            XYChart.Data<Number, Number> dIE,
            BenchmarkResult result) {

        seriesSpeedup.getData().add(dS);
        seriesEfficiency.getData().add(dE);
        seriesIdealSpeedup.getData().add(dIS);
        seriesIdealEfficiency.getData().add(dIE);

        // Стилізація після рендеру
        Platform.runLater(() -> {
            ChartBuilder.styleDataPoint(dS, result, true);
            ChartBuilder.styleDataPoint(dE, result, false);
            // Ховаємо символ ідеальної лінії
            if (dIS.getNode() != null) dIS.getNode().setVisible(false);
            if (dIE.getNode() != null) dIE.getNode().setVisible(false);
        });
    }

    private void onTaskSucceeded(List<BenchmarkResult> results) {
        btnRun.setDisable(false);
        btnCancel.setDisable(true);
        btnClear.setDisable(false);
        progressBar.setProgress(1.0);
        labelProgress.setText(String.format("✓ Завершено: %d вимірювань.", results.size()));

        if (!results.isEmpty()) {
            labelTableInfo.setText(String.format(
                    "Бенчмарк ПРГ%d, N=%d, %d точок",
                    results.get(0).getPrgNumber(),
                    results.get(0).getN(),
                    results.size()));
        }

        // Фінальна стилізація ліній (пунктир для ідеальних)
        Platform.runLater(() -> {
            ChartBuilder.styleSpeedupChart(chartSpeedup,
                    seriesIdealSpeedup, seriesSpeedup);
            ChartBuilder.styleEfficiencyChart(chartEfficiency,
                    seriesIdealEfficiency, seriesEfficiency);
        });

        setActionButtonsEnabled(!results.isEmpty());
        log.info("БЕНЧМАРК завершено: " + results.size() + " точок.");
    }

    private void onTaskFailed(Throwable ex) {
        btnRun.setDisable(false);
        btnCancel.setDisable(true);
        btnClear.setDisable(false);
        progressBar.setVisible(false);
        labelProgress.setText("");
        String msg = ex != null ? ex.getMessage() : "Невідома помилка";
        log.error("БЕНЧМАРК: помилка — " + msg);
        showError("Помилка бенчмарку", msg);
    }

    private void onTaskCancelled() {
        btnRun.setDisable(false);
        btnCancel.setDisable(true);
        btnClear.setDisable(false);
        labelProgress.setText("Зупинено.");
        log.warn("БЕНЧМАРК: зупинено.");
    }

    // ─────────────────────────────────────────────────────────────
    //  Збереження
    // ─────────────────────────────────────────────────────────────

    @FXML
    private void onSaveCsv() {
        if (tableData.isEmpty()) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Зберегти результати бенчмарку");
        int prg = tableData.get(0).getPrgNumber();
        int n   = tableData.get(0).getN();
        chooser.setInitialFileName(
                String.format("benchmark_PRG%d_N%d.csv", prg, n));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV (*.csv)", "*.csv"));
        File file = chooser.showSaveDialog(btnSaveCsv.getScene().getWindow());
        if (file == null) return;
        try {
            MatrixFileService.saveBenchmarkResults(
                    new ArrayList<>(tableData), prg, n, file.toPath());
            labelActionStatus.setText("✓ CSV збережено: " + file.getName());
            log.info("БЕНЧМАРК: CSV збережено — " + file.getAbsolutePath());
        } catch (IOException e) {
            showError("Помилка збереження CSV", e.getMessage());
        }
    }

    /**
     * Зберігає обидва графіки в один PNG-файл через {@link ChartBuilder#saveBothToPng}.
     */
    @FXML
    private void onSavePng() {
        if (tableData.isEmpty()) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Зберегти графіки PNG");
        int prg = tableData.get(0).getPrgNumber();
        int n   = tableData.get(0).getN();
        chooser.setInitialFileName(
                String.format("charts_PRG%d_N%d.png", prg, n));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PNG зображення (*.png)", "*.png"));
        File file = chooser.showSaveDialog(btnSavePng.getScene().getWindow());
        if (file == null) return;
        try {
            ChartBuilder.saveBothToPng(chartSpeedup, chartEfficiency, file);
            String size = MatrixFileService.humanFileSize(file.toPath());
            labelActionStatus.setText("✓ PNG збережено: " + file.getName()
                    + " (" + size + ")");
            log.info("БЕНЧМАРК: PNG збережено — " + file.getAbsolutePath());
        } catch (IOException e) {
            showError("Помилка збереження PNG", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────

    private void clearCharts() {
        seriesSpeedup.getData().clear();
        seriesEfficiency.getData().clear();
        seriesIdealSpeedup.getData().clear();
        seriesIdealEfficiency.getData().clear();
    }

    private int[] buildThreadCounts() {
        String custom = fieldCustomP.getText().trim();
        if (!custom.isEmpty()) {
            String[] parts = custom.split("[,;\\s]+");
            int[] res = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                res[i] = Integer.parseInt(parts[i].trim());
                if (res[i] <= 0)
                    throw new IllegalArgumentException("P має бути > 0: " + res[i]);
            }
            return res;
        }
        CheckBox[] boxes = {chk1, chk2, chk3, chk4, chk6, chk8, chk12};
        int[]      vals  = {1,    2,    3,    4,    6,    8,    12};
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < boxes.length; i++)
            if (boxes[i].isSelected()) list.add(vals[i]);
        return list.stream().mapToInt(Integer::intValue).toArray();
    }

    private int selectedPrg() {
        if (radioPRG2.isSelected()) return 2;
        if (radioPRG3.isSelected()) return 3;
        return 1;
    }

    private void commitSpinners() {
        String tN = spinnerN.getEditor().getText().trim();
        String tR = spinnerRuns.getEditor().getText().trim();
        if (!tN.isEmpty())
            spinnerN.getValueFactory().setValue(Integer.parseInt(tN));
        if (!tR.isEmpty())
            spinnerRuns.getValueFactory().setValue(Integer.parseInt(tR));
    }

    private TextFormatter<Integer> intFormatter() {
        return new TextFormatter<>(ch ->
                ch.getControlNewText().matches("\\d*") ? ch : null);
    }

    private void setActionButtonsEnabled(boolean on) {
        btnSaveCsv.setDisable(!on);
        btnSavePng.setDisable(!on);
        if (!on) labelActionStatus.setText("");
    }

    private void showError(String header, String msg) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.ERROR);
            a.setTitle("Помилка");
            a.setHeaderText(header);
            a.setContentText(msg);
            a.showAndWait();
        });
    }

    private static String arrayStr(int[] arr) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(arr[i]);
        }
        return sb.append("]").toString();
    }
}
