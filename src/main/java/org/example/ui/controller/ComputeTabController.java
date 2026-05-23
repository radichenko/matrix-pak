package org.example.ui.controller;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import org.example.model.BenchmarkResult;
import org.example.model.ExecutionResult;
import org.example.model.Matrix;
import org.example.prg.AbstractPRG;
import org.example.prg.PRG1;
import org.example.prg.PRG2;
import org.example.prg.PRG3;
import org.example.service.LogService;
import org.example.service.MatrixFileService;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Контролер вкладки «Обчислення».
 *
 * <p>Відповідає за:
 * <ul>
 *   <li>вибір програми (ПРГ1 / ПРГ2 / ПРГ3) через RadioButton;</li>
 *   <li>введення параметрів N та P через Spinner;</li>
 *   <li>запуск обчислення у фоновому потоці через {@link Task}
 *       (UI не блокується);</li>
 *   <li>відображення метрик після завершення (час, N, P, розмір MA);</li>
 *   <li>відображення результуючої матриці MA у {@link TableView};</li>
 *   <li>копіювання таблиці у буфер обміну та збереження у CSV.</li>
 * </ul>
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

    // ── Кнопки керування ─────────────────────────────────────────
    @FXML private Button             btnRun;
    @FXML private Button             btnCancel;
    @FXML private ProgressIndicator  progressIndicator;
    @FXML private Label              labelRunStatus;

    // ── Метрики результату ────────────────────────────────────────
    @FXML private Label labelTime;
    @FXML private Label labelPrg;
    @FXML private Label labelN;
    @FXML private Label labelP;
    @FXML private Label labelMemory;

    // ── Таблиця ───────────────────────────────────────────────────
    @FXML private TableView<MatrixRow>      tableResult;
    @FXML private TableColumn<MatrixRow, Integer> colRow;
    @FXML private TableColumn<MatrixRow, Integer> colCol;
    @FXML private TableColumn<MatrixRow, String>  colVal;

    // ── Дії ───────────────────────────────────────────────────────
    @FXML private Button btnCopy;
    @FXML private Button btnSaveCsv;
    @FXML private Label  labelActionStatus;

    // ── Стан ─────────────────────────────────────────────────────
    private ToggleGroup    prgToggleGroup;
    private Task<ExecutionResult> currentTask;
    private ExecutionResult       lastResult;
    private final LogService      log = LogService.getInstance();

    /** Максимальна кількість рядків матриці MA у таблиці (для великих N). */
    private static final int MAX_TABLE_ROWS = 500;

    // ─────────────────────────────────────────────────────────────
    //  Ініціалізація
    // ─────────────────────────────────────────────────────────────

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupToggleGroup();
        setupSpinners();
        setupTable();
    }

    /** Групує RadioButton-и у ToggleGroup. */
    private void setupToggleGroup() {
        prgToggleGroup = new ToggleGroup();
        radioPRG1.setToggleGroup(prgToggleGroup);
        radioPRG2.setToggleGroup(prgToggleGroup);
        radioPRG3.setToggleGroup(prgToggleGroup);
        radioPRG1.setSelected(true);
    }

    /** Налаштовує Spinner-и для N та P. */
    private void setupSpinners() {
        // Spinner N: 50..5000, крок 50, дефолт 500
        spinnerN.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(50, 5000, 500, 50));
        spinnerN.getEditor().setTextFormatter(intFormatter());

        // Spinner P: 1..maxCores, крок 1, дефолт min(4, maxCores)
        int maxCores = Runtime.getRuntime().availableProcessors();
        int defaultP = Math.min(4, maxCores);
        spinnerP.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, maxCores, defaultP, 1));
        spinnerP.getEditor().setTextFormatter(intFormatter());

        labelCpuHint.setText("(ядер: " + maxCores + ")");
    }

    /** Налаштовує колонки TableView. Дані прив'язуються у 6б через TableRenderer. */
    private void setupTable() {
        colRow.setCellValueFactory(data ->
                new javafx.beans.property.SimpleObjectProperty<>(data.getValue().row));
        colCol.setCellValueFactory(data ->
                new javafx.beans.property.SimpleObjectProperty<>(data.getValue().col));
        colVal.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().value));

        tableResult.setPlaceholder(new Label("Натисніть «Запустити» для обчислення"));
    }

    // ─────────────────────────────────────────────────────────────
    //  Запуск обчислення
    // ─────────────────────────────────────────────────────────────

    /**
     * Обробник кнопки «Запустити».
     * Читає параметри з UI, створює відповідну PRG та запускає
     * обчислення у фоновому JavaFX Task (UI не блокується).
     */
    @FXML
    private void onRun() {
        // Зчитуємо та валідуємо параметри
        int N, P;
        try {
            commitSpinnerEdits();
            N = spinnerN.getValue();
            P = spinnerP.getValue();
        } catch (Exception e) {
            showError("Некоректні параметри", "Перевірте значення N та P.");
            return;
        }

        if (P > N) {
            showError("Некоректні параметри",
                    "Кількість потоків P (" + P + ") не може перевищувати N (" + N + ").");
            return;
        }

        int prgNumber = getSelectedPrgNumber();
        AbstractPRG prg = createPRG(prgNumber, N, P);

        log.info(String.format("UI: Запуск %s | N=%d | P=%d", prg.getFormula(), N, P));

        // Будуємо Task для фонового виконання
        currentTask = buildTask(prg);

        // Підключаємо обробники подій Task
        currentTask.setOnRunning(e -> onTaskRunning());
        currentTask.setOnSucceeded(e -> onTaskSucceeded(currentTask.getValue()));
        currentTask.setOnFailed(e  -> onTaskFailed(currentTask.getException()));
        currentTask.setOnCancelled(e -> onTaskCancelled());

        Thread thread = new Thread(currentTask, "PRG-Compute-Thread");
        thread.setDaemon(true);
        thread.start();
    }

    /** Обробник кнопки «Скасувати». */
    @FXML
    private void onCancel() {
        if (currentTask != null && currentTask.isRunning()) {
            currentTask.cancel();
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Task — фонове виконання
    // ─────────────────────────────────────────────────────────────

    private Task<ExecutionResult> buildTask(AbstractPRG prg) {
        return new Task<>() {
            @Override
            protected ExecutionResult call() {
                return prg.execute();
            }
        };
    }

    private void onTaskRunning() {
        btnRun.setDisable(true);
        btnCancel.setDisable(false);
        progressIndicator.setVisible(true);
        labelRunStatus.setText("Виконується...");
        labelTime.setText("—");
        clearActionButtons();
        log.debug("UI: Task запущено у фоновому потоці.");
    }

    private void onTaskSucceeded(ExecutionResult result) {
        lastResult = result;
        progressIndicator.setVisible(false);
        btnRun.setDisable(false);
        btnCancel.setDisable(true);
        labelRunStatus.setText("");

        // Оновлення метрик
        labelTime.setText(result.getElapsedMs() + " мс");
        labelPrg.setText("ПРГ" + result.getPrgNumber());
        labelN.setText(String.valueOf(result.getN()));
        labelP.setText(String.valueOf(result.getP()));

        double mb = (double) result.getN() * result.getN() * 8 / (1024 * 1024);
        labelMemory.setText(String.format("%.2f МБ", mb));

        // Заповнення таблиці
        populateTable(result.getResultMA());

        // Активація кнопок дій
        btnCopy.setDisable(false);
        btnSaveCsv.setDisable(false);
        labelActionStatus.setText("");

        log.info(String.format("UI: Обчислення завершено — %d мс", result.getElapsedMs()));
    }

    private void onTaskFailed(Throwable ex) {
        progressIndicator.setVisible(false);
        btnRun.setDisable(false);
        btnCancel.setDisable(true);
        labelRunStatus.setText("");
        String msg = ex != null ? ex.getMessage() : "Невідома помилка";
        log.error("UI: Помилка виконання — " + msg);
        showError("Помилка виконання", msg);
    }

    private void onTaskCancelled() {
        progressIndicator.setVisible(false);
        btnRun.setDisable(false);
        btnCancel.setDisable(true);
        labelRunStatus.setText("Скасовано");
        log.warn("UI: Обчислення скасовано користувачем.");
    }

    // ─────────────────────────────────────────────────────────────
    //  Таблиця результатів
    // ─────────────────────────────────────────────────────────────

    /**
     * Заповнює TableView даними матриці MA.
     * Якщо матриця велика — показує перші MAX_TABLE_ROWS рядків.
     */
    private void populateTable(Matrix ma) {
        tableResult.getItems().clear();
        if (ma == null) return;

        double[][] data = ma.getData();
        int shown = 0;
        outer:
        for (int i = 0; i < ma.rows; i++) {
            for (int j = 0; j < ma.cols; j++) {
                tableResult.getItems().add(
                        new MatrixRow(i, j, String.format("%.6g", data[i][j])));
                if (++shown >= MAX_TABLE_ROWS) break outer;
            }
        }

        if (shown >= MAX_TABLE_ROWS) {
            tableResult.getItems().add(
                    new MatrixRow(-1, -1,
                            String.format("... (показано %d з %d елементів)",
                                    MAX_TABLE_ROWS, (long) ma.rows * ma.cols)));
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Дії — копіювання та збереження
    // ─────────────────────────────────────────────────────────────

    /** Обробник «Копіювати» — копіює таблицю у буфер обміну як TSV. */
    @FXML
    private void onCopy() {
        if (lastResult == null) return;

        StringBuilder sb = new StringBuilder();
        sb.append("Рядок\tСтовпець\tMA[i][j]\n");
        for (MatrixRow row : tableResult.getItems()) {
            sb.append(row.row).append('\t')
                    .append(row.col).append('\t')
                    .append(row.value).append('\n');
        }

        ClipboardContent content = new ClipboardContent();
        content.putString(sb.toString());
        Clipboard.getSystemClipboard().setContent(content);

        labelActionStatus.setText("Скопійовано у буфер обміну.");
        log.info("UI: Результат скопійовано у буфер обміну.");
    }

    /** Обробник «Зберегти CSV» — зберігає матрицю MA у CSV через FileChooser. */
    @FXML
    private void onSaveCsv() {
        if (lastResult == null || lastResult.getResultMA() == null) return;

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Зберегти матрицю MA");
        chooser.setInitialFileName(String.format("MA_PRG%d_N%d.csv",
                lastResult.getPrgNumber(), lastResult.getN()));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV файли", "*.csv"));

        File file = chooser.showSaveDialog(btnSaveCsv.getScene().getWindow());
        if (file == null) return;

        try {
            MatrixFileService.saveMatrix(lastResult.getResultMA(), file.toPath());
            String size = MatrixFileService.humanFileSize(file.toPath());
            labelActionStatus.setText("Збережено: " + file.getName() + " (" + size + ")");
            log.info("UI: Матрицю MA збережено у " + file.getAbsolutePath());
        } catch (IOException e) {
            log.error("UI: Помилка збереження — " + e.getMessage());
            showError("Помилка збереження", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Допоміжні методи
    // ─────────────────────────────────────────────────────────────

    /** Повертає номер обраної програми (1, 2 або 3). */
    private int getSelectedPrgNumber() {
        if (radioPRG2.isSelected()) return 2;
        if (radioPRG3.isSelected()) return 3;
        return 1;
    }

    /** Створює екземпляр відповідної PRG. */
    private AbstractPRG createPRG(int prgNumber, int N, int P) {
        return switch (prgNumber) {
            case 2 -> new PRG2(N, P);
            case 3 -> new PRG3(N, P);
            default -> new PRG1(N, P);
        };
    }

    /**
     * Примусово фіксує текст у Spinner-ах як значення
     * (без цього при ручному введенні Spinner може не оновити значення).
     */
    private void commitSpinnerEdits() {
        spinnerN.getValueFactory().setValue(
                Integer.parseInt(spinnerN.getEditor().getText().trim()));
        spinnerP.getValueFactory().setValue(
                Integer.parseInt(spinnerP.getEditor().getText().trim()));
    }

    /** TextFormatter що дозволяє вводити лише цілі числа у Spinner. */
    private TextFormatter<Integer> intFormatter() {
        return new TextFormatter<>(change -> {
            String newText = change.getControlNewText();
            return (newText.matches("\\d*")) ? change : null;
        });
    }

    private void clearActionButtons() {
        btnCopy.setDisable(true);
        btnSaveCsv.setDisable(true);
        labelActionStatus.setText("");
    }

    private void showError(String header, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Помилка");
            alert.setHeaderText(header);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    // ─────────────────────────────────────────────────────────────
    //  Модель рядка таблиці
    // ─────────────────────────────────────────────────────────────

    /**
     * Модель одного рядка у TableView — одна комірка матриці MA.
     * row=-1 і col=-1 використовується для рядка "...показано N з M".
     */
    public static class MatrixRow {
        public final int    row;
        public final int    col;
        public final String value;

        public MatrixRow(int row, int col, String value) {
            this.row   = row;
            this.col   = col;
            this.value = value;
        }
    }
}
