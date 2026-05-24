package org.example.ui.controller;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import org.example.model.ExecutionResult;
import org.example.prg.PRG1;
import org.example.prg.PRG2;
import org.example.prg.PRG3;
import org.example.service.LogService;
import org.example.service.MatrixFileService;
import org.example.service.MatrixStore;
import org.example.service.UserPreferences;
import org.example.ui.TableRenderer;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * Контролер вкладки «Обчислення» — Фаза 9.
 *
 * <p>Додано збереження та відновлення останніх значень N, P та програми
 * через {@link UserPreferences}.
 */
public class ComputeTabController implements Initializable {

    @FXML private RadioButton radioPRG1;
    @FXML private RadioButton radioPRG2;
    @FXML private RadioButton radioPRG3;
    @FXML private Spinner<Integer> spinnerN;
    @FXML private Spinner<Integer> spinnerP;
    @FXML private Label            labelCpuHint;
    @FXML private Label            labelMatrixHint;
    @FXML private Button            btnRun;
    @FXML private Button            btnCancel;
    @FXML private ProgressIndicator progressIndicator;
    @FXML private Label             labelRunStatus;
    @FXML private Label labelTime;
    @FXML private Label labelPrg;
    @FXML private Label labelN;
    @FXML private Label labelP;
    @FXML private Label labelMemory;
    @FXML private TableView<ObservableList<String>> tableResult;
    @FXML private Label                             labelTableStatus;
    @FXML private Button btnCopy;
    @FXML private Button btnSaveCsv;
    @FXML private Label  labelActionStatus;

    private ToggleGroup           prgToggleGroup;
    private Task<ExecutionResult> currentTask;
    private ExecutionResult       lastResult;
    private final LogService      log = LogService.getInstance();

    // ─────────────────────────────────────────────────────────────

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupToggleGroup();
        setupSpinners();
        restorePreferences();
        tableResult.setPlaceholder(new Label("Натисніть «Запустити» для обчислення"));
    }

    private void setupToggleGroup() {
        prgToggleGroup = new ToggleGroup();
        radioPRG1.setToggleGroup(prgToggleGroup);
        radioPRG2.setToggleGroup(prgToggleGroup);
        radioPRG3.setToggleGroup(prgToggleGroup);
        radioPRG1.setSelected(true);

        // Зберігаємо вибір програми при кожній зміні
        prgToggleGroup.selectedToggleProperty().addListener((obs, o, n) ->
                UserPreferences.setComputePrg(selectedPrg()));
    }

    private void setupSpinners() {
        int maxCores = Runtime.getRuntime().availableProcessors();
        int defaultP = Math.min(UserPreferences.getComputeP(), maxCores);

        spinnerN.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(2, 5000,
                        UserPreferences.getComputeN(), 1));
        spinnerN.getEditor().setTextFormatter(intOnlyFormatter());
        // Зберігаємо N при кожній зміні
        spinnerN.valueProperty().addListener((obs, o, n) -> {
            if (n != null) UserPreferences.setComputeN(n);
        });

        spinnerP.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, maxCores, defaultP, 1));
        spinnerP.getEditor().setTextFormatter(intOnlyFormatter());
        spinnerP.valueProperty().addListener((obs, o, n) -> {
            if (n != null) UserPreferences.setComputeP(n);
        });

        labelCpuHint.setText("(ядер: " + maxCores + ")");
    }

    /** Відновлює останній вибір програми з Preferences. */
    private void restorePreferences() {
        int prg = UserPreferences.getComputePrg();
        switch (prg) {
            case 2 -> radioPRG2.setSelected(true);
            case 3 -> radioPRG3.setSelected(true);
            default -> radioPRG1.setSelected(true);
        }
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
        log.info(String.format("UI → Запуск ПРГ%d | N=%d | P=%d", prgNumber, N, P));

        currentTask = buildTask(prgNumber, N, P);
        currentTask.setOnRunning(e   -> onRunning());
        currentTask.setOnSucceeded(e -> onSucceeded(currentTask.getValue()));
        currentTask.setOnFailed(e    -> onFailed(currentTask.getException()));
        currentTask.setOnCancelled(e -> onCancelled());

        new Thread(currentTask, "PRG-Thread") {{ setDaemon(true); }}.start();
    }

    @FXML
    private void onCancel() {
        if (currentTask != null && currentTask.isRunning()) currentTask.cancel();
    }

    private Task<ExecutionResult> buildTask(int prgNumber, int N, int P) {
        return new Task<>() {
            @Override
            protected ExecutionResult call() {
                MatrixStore store = MatrixStore.getInstance();
                if (store.hasMatricesFor(prgNumber, N)) {
                    log.info("UI → Матриці з вкладки «Матриці» (N=" + N + ")");
                    return switch (prgNumber) {
                        case 2 -> new PRG2(N, P).execute(
                                store.get("MB").getData(),
                                store.get("MC").getData(),
                                store.get("MD").getData(),
                                store.get("ME").getData());
                        case 3 -> new PRG3(N, P).execute(
                                store.get("MR").getData(),
                                store.get("MB").getData(),
                                store.get("MC").getData());
                        default -> new PRG1(N, P).execute(
                                store.get("MB").getData(),
                                store.get("MC").getData(),
                                store.get("MD").getData());
                    };
                } else {
                    log.info("UI → Випадкові матриці (N=" + N + ")");
                    return switch (prgNumber) {
                        case 2  -> new PRG2(N, P).execute();
                        case 3  -> new PRG3(N, P).execute();
                        default -> new PRG1(N, P).execute();
                    };
                }
            }
        };
    }

    // ─────────────────────────────────────────────────────────────
    //  Task callbacks
    // ─────────────────────────────────────────────────────────────

    private void onRunning() {
        btnRun.setDisable(true);
        btnCancel.setDisable(false);
        progressIndicator.setVisible(true);
        boolean hasStored = MatrixStore.getInstance()
                .hasMatricesFor(selectedPrg(), spinnerN.getValue());
        labelRunStatus.setText(hasStored
                ? "Виконується з матрицями з вкладки «Матриці»..."
                : "Виконується з випадковими матрицями...");
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
        labelTime.setText(result.getElapsedMs() + " мс");
        labelPrg.setText("ПРГ" + result.getPrgNumber());
        labelN.setText(String.valueOf(result.getN()));
        labelP.setText(String.valueOf(result.getP()));
        double mb = (double) result.getN() * result.getN() * 8 / (1024.0 * 1024.0);
        labelMemory.setText(String.format("%.2f МБ", mb));
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
    //  Дії
    // ─────────────────────────────────────────────────────────────

    @FXML
    private void onCopy() {
        if (lastResult == null) return;
        TableRenderer.copyToClipboard(tableResult);
        labelActionStatus.setText("✓ Скопійовано у буфер обміну (TSV).");
    }

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
            labelActionStatus.setText("✓ Збережено: " + file.getName()
                    + " (" + MatrixFileService.humanFileSize(file.toPath()) + ")");
        } catch (IOException e) {
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

    private void commitSpinners() {
        String tN = spinnerN.getEditor().getText().trim();
        String tP = spinnerP.getEditor().getText().trim();
        if (!tN.isEmpty()) spinnerN.getValueFactory().setValue(Integer.parseInt(tN));
        if (!tP.isEmpty()) spinnerP.getValueFactory().setValue(Integer.parseInt(tP));
    }

    private TextFormatter<Integer> intOnlyFormatter() {
        return new TextFormatter<>(ch ->
                ch.getControlNewText().matches("\\d*") ? ch : null);
    }

    private void setActionButtonsEnabled(boolean on) {
        btnCopy.setDisable(!on);
        btnSaveCsv.setDisable(!on);
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
}
