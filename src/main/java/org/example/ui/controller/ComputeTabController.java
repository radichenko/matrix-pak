package org.example.ui.controller;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import org.example.model.ExecutionResult;
import org.example.model.Matrix;
import org.example.prg.AbstractPRG;
import org.example.prg.PRG1;
import org.example.prg.PRG2;
import org.example.prg.PRG3;
import org.example.service.LogService;
import org.example.service.MatrixFileService;
import org.example.ui.TableRenderer;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ResourceBundle;

/**
 * Контролер вкладки «Обчислення».
 *
 * <p>Фаза 6б: повна інтеграція з {@link TableRenderer} —
 * таблиця відображає матрицю MA у вигляді числової сітки
 * (рядки матриці = рядки таблиці, стовпці матриці = стовпці таблиці).
 */
public class ComputeTabController implements Initializable {

    // ── Вибір програми ───────────────────────────────────────────
    @FXML private RadioButton radioPRG1;
    @FXML private RadioButton radioPRG2;
    @FXML private RadioButton radioPRG3;

    // ── Параметри ─────────────────────────────────────────────────
    @FXML private Spinner<Integer> spinnerN;
    @FXML private Spinner<Integer> spinnerP;
    @FXML private Label            labelCpuHint;
    @FXML private Label            labelMatrixHint;

    // ── Керування ─────────────────────────────────────────────────
    @FXML private Button            btnRun;
    @FXML private Button            btnCancel;
    @FXML private ProgressIndicator progressIndicator;
    @FXML private Label             labelRunStatus;

    // ── Метрики ───────────────────────────────────────────────────
    @FXML private Label labelTime;
    @FXML private Label labelPrg;
    @FXML private Label labelN;
    @FXML private Label labelP;
    @FXML private Label labelMemory;

    // ── Таблиця (тип рядка — ObservableList<String>) ──────────────
    @FXML private TableView<ObservableList<String>> tableResult;
    @FXML private Label                             labelTableStatus;

    // ── Дії ───────────────────────────────────────────────────────
    @FXML private Button btnCopy;
    @FXML private Button btnSaveCsv;
    @FXML private Label  labelActionStatus;

    // ── Стан ─────────────────────────────────────────────────────
    private ToggleGroup           prgToggleGroup;
    private Task<ExecutionResult> currentTask;
    private ExecutionResult       lastResult;
    private final LogService      log = LogService.getInstance();

    // ─────────────────────────────────────────────────────────────
    //  Ініціалізація
    // ─────────────────────────────────────────────────────────────

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupToggleGroup();
        setupSpinners();
        tableResult.setPlaceholder(
                new Label("Натисніть «Запустити» для обчислення"));
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
        int defaultP = Math.min(4, maxCores);

        // N: крок 1 — можна ввести будь-яке значення від 10 до 5000
        spinnerN.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(2, 5000, 500, 1));
        spinnerN.getEditor().setTextFormatter(intOnlyFormatter());

        // P: від 1 до кількості ядер
        spinnerP.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, maxCores, defaultP, 1));
        spinnerP.getEditor().setTextFormatter(intOnlyFormatter());

        labelCpuHint.setText("(ядер: " + maxCores + ")");
    }

    // ─────────────────────────────────────────────────────────────
    //  Запуск
    // ─────────────────────────────────────────────────────────────

    @FXML
    private void onRun() {
        int N, P;
        try {
            commitSpinners();
            N = spinnerN.getValue();
            P = spinnerP.getValue();
        } catch (Exception e) {
            showError("Некоректні параметри", "Введіть числові значення N та P.");
            return;
        }

        if (P > N) {
            showError("Некоректні параметри",
                    "Кількість потоків P (" + P + ") не може перевищувати N (" + N + ").");
            return;
        }

        int prgNumber = selectedPrg();
        AbstractPRG prg = buildPrg(prgNumber, N, P);
        log.info(String.format("UI → Запуск %s | N=%d | P=%d", prg.getFormula(), N, P));

        currentTask = new Task<>() {
            @Override protected ExecutionResult call() { return prg.execute(); }
        };
        currentTask.setOnRunning(e   -> onRunning());
        currentTask.setOnSucceeded(e -> onSucceeded(currentTask.getValue()));
        currentTask.setOnFailed(e    -> onFailed(currentTask.getException()));
        currentTask.setOnCancelled(e -> onCancelled());

        Thread t = new Thread(currentTask, "PRG-Thread");
        t.setDaemon(true);
        t.start();
    }

    @FXML
    private void onCancel() {
        if (currentTask != null && currentTask.isRunning()) currentTask.cancel();
    }

    // ─────────────────────────────────────────────────────────────
    //  Task — зворотні виклики
    // ─────────────────────────────────────────────────────────────

    private void onRunning() {
        btnRun.setDisable(true);
        btnCancel.setDisable(false);
        progressIndicator.setVisible(true);
        labelRunStatus.setText("Виконується...");
        labelTableStatus.setText("");
        tableResult.getColumns().clear();
        tableResult.getItems().clear();
        setActionButtonsEnabled(false);
    }

    private void onSucceeded(ExecutionResult result) {
        lastResult = result;
        progressIndicator.setVisible(false);
        btnRun.setDisable(false);
        btnCancel.setDisable(true);
        labelRunStatus.setText("");

        // Метрики
        labelTime.setText(result.getElapsedMs() + " мс");
        labelPrg.setText("ПРГ" + result.getPrgNumber());
        labelN.setText(String.valueOf(result.getN()));
        labelP.setText(String.valueOf(result.getP()));
        double mb = (double) result.getN() * result.getN() * 8 / (1024.0 * 1024.0);
        labelMemory.setText(String.format("%.2f МБ", mb));

        // Відображення матриці через TableRenderer
        TableRenderer.render(tableResult, result.getResultMA(), labelTableStatus);

        setActionButtonsEnabled(true);
        log.info("UI → Завершено за " + result.getElapsedMs() + " мс");
    }

    private void onFailed(Throwable ex) {
        progressIndicator.setVisible(false);
        btnRun.setDisable(false);
        btnCancel.setDisable(true);
        labelRunStatus.setText("");
        String msg = ex != null ? ex.getMessage() : "Невідома помилка";
        log.error("UI → Помилка: " + msg);
        showError("Помилка виконання", msg);
    }

    private void onCancelled() {
        progressIndicator.setVisible(false);
        btnRun.setDisable(false);
        btnCancel.setDisable(true);
        labelRunStatus.setText("Скасовано.");
        log.warn("UI → Обчислення скасовано.");
    }

    // ─────────────────────────────────────────────────────────────
    //  Дії — копіювання та збереження
    // ─────────────────────────────────────────────────────────────

    /**
     * Копіює відображувану таблицю у буфер обміну як TSV
     * (Tab-Separated Values) — вставляється у Excel / LibreOffice Calc.
     */
    @FXML
    private void onCopy() {
        if (lastResult == null) return;
        TableRenderer.copyToClipboard(tableResult);
        labelActionStatus.setText("✓ Скопійовано у буфер обміну (TSV).");
        log.info("UI → Таблицю скопійовано у буфер.");
    }

    /**
     * Зберігає повну матрицю MA у CSV через FileChooser.
     * Зберігається ПОВНА матриця (не лише відображувані рядки).
     */
    @FXML
    private void onSaveCsv() {
        if (lastResult == null || lastResult.getResultMA() == null) return;

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Зберегти матрицю MA");
        chooser.setInitialFileName(String.format("MA_PRG%d_N%d.csv",
                lastResult.getPrgNumber(), lastResult.getN()));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV файли (*.csv)", "*.csv"));

        File file = chooser.showSaveDialog(btnSaveCsv.getScene().getWindow());
        if (file == null) return;

        try {
            MatrixFileService.saveMatrix(lastResult.getResultMA(), file.toPath());
            String size = MatrixFileService.humanFileSize(file.toPath());
            labelActionStatus.setText("✓ Збережено: " + file.getName() + " (" + size + ")");
            log.info("UI → Матрицю збережено: " + file.getAbsolutePath());
        } catch (IOException e) {
            log.error("UI → Помилка збереження: " + e.getMessage());
            showError("Помилка збереження", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────

    private int selectedPrg() {
        if (radioPRG2.isSelected()) return 2;
        if (radioPRG3.isSelected()) return 3;
        return 1;
    }

    private AbstractPRG buildPrg(int n, int N, int P) {
        return switch (n) {
            case 2  -> new PRG2(N, P);
            case 3  -> new PRG3(N, P);
            default -> new PRG1(N, P);
        };
    }

    private void commitSpinners() {
        String textN = spinnerN.getEditor().getText().trim();
        String textP = spinnerP.getEditor().getText().trim();
        if (!textN.isEmpty())
            spinnerN.getValueFactory().setValue(Integer.parseInt(textN));
        if (!textP.isEmpty())
            spinnerP.getValueFactory().setValue(Integer.parseInt(textP));
    }

    private TextFormatter<Integer> intOnlyFormatter() {
        return new TextFormatter<>(change ->
                change.getControlNewText().matches("\\d*") ? change : null);
    }

    private void setActionButtonsEnabled(boolean enabled) {
        btnCopy.setDisable(!enabled);
        btnSaveCsv.setDisable(!enabled);
        if (!enabled) labelActionStatus.setText("");
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
}
