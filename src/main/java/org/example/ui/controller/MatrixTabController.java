package org.example.ui.controller;

import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.example.model.Matrix;
import org.example.service.LogService;
import org.example.service.MatrixFileService;
import org.example.service.MatrixStore;
import org.example.ui.TableRenderer;
import org.example.utils.MatrixUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Контролер вкладки «Матриці».
 *
 * <p>Дозволяє генерувати, завантажувати, переглядати та зберігати матриці.
 * Після генерації або завантаження матриці зберігаються у {@link MatrixStore},
 * звідки їх читає вкладка «Обчислення» при запуску PRG.
 */
public class MatrixTabController implements Initializable {

    // ── Параметри ─────────────────────────────────────────────────
    @FXML private RadioButton radioPRG1;
    @FXML private RadioButton radioPRG2;
    @FXML private RadioButton radioPRG3;
    @FXML private Spinner<Integer> spinnerN;
    @FXML private Label labelHint;

    // ── Дії ───────────────────────────────────────────────────────
    @FXML private Button btnGenRandom;
    @FXML private Button btnLoadCsv;
    @FXML private Button btnClearAll;

    // ── Відображення ─────────────────────────────────────────────
    @FXML private TabPane matrixTabPane;
    @FXML private Label   labelMatrixStatus;

    // ── Збереження ───────────────────────────────────────────────
    @FXML private Button btnSaveCurrent;
    @FXML private Button btnSaveAll;
    @FXML private Label  labelActionStatus;

    // ── Стан ─────────────────────────────────────────────────────
    private ToggleGroup prgGroup;
    private final Map<String, Matrix> currentMatrices = new LinkedHashMap<>();
    private final LogService log = LogService.getInstance();

    // ─────────────────────────────────────────────────────────────
    //  Ініціалізація
    // ─────────────────────────────────────────────────────────────

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        prgGroup = new ToggleGroup();
        radioPRG1.setToggleGroup(prgGroup);
        radioPRG2.setToggleGroup(prgGroup);
        radioPRG3.setToggleGroup(prgGroup);
        radioPRG1.setSelected(true);

        spinnerN.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(2, 5000, 6, 1));
        spinnerN.getEditor().setTextFormatter(
                new TextFormatter<>(ch ->
                        ch.getControlNewText().matches("\\d*") ? ch : null));

        prgGroup.selectedToggleProperty().addListener((obs, o, n) -> rebuildTabs());
        rebuildTabs();
    }

    // ─────────────────────────────────────────────────────────────
    //  Генерація
    // ─────────────────────────────────────────────────────────────

    @FXML
    private void onGenRandom() {
        int N = commitAndGetN();
        if (N < 2) { showError("N має бути ≥ 2"); return; }

        currentMatrices.clear();
        for (String name : matrixNamesForPrg()) {
            Matrix m = new Matrix(N, N, name);
            MatrixUtils.fillRandom(m);
            currentMatrices.put(name, m);
        }

        renderAllTabs();
        MatrixStore.getInstance().store(selectedPrg(), currentMatrices);
        setActionButtonsEnabled(true);
        labelMatrixStatus.setText(String.format(
                "Згенеровано %d матриць %d×%d — випадкові числа [-10, +10]. " +
                        "Вкладка «Обчислення» використовуватиме ці матриці.",
                currentMatrices.size(), N, N));
        log.info(String.format("Матриці: згенеровано %d×%d для %s, збережено у MatrixStore",
                N, N, prgLabel()));
    }

    // ─────────────────────────────────────────────────────────────
    //  Завантаження з CSV
    // ─────────────────────────────────────────────────────────────

    @FXML
    private void onLoadCsv() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Завантажити матрицю з CSV");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV файли (*.csv)", "*.csv"));
        File file = chooser.showOpenDialog(btnLoadCsv.getScene().getWindow());
        if (file == null) return;

        try {
            Matrix loaded = MatrixFileService.loadMatrix(file.toPath());
            String name = extractMatrixName(file.getName(), loaded.getName());
            Matrix namedMatrix = new Matrix(loaded.getData(), name);
            currentMatrices.put(name, namedMatrix);

            renderAllTabs();
            MatrixStore.getInstance().store(selectedPrg(), currentMatrices);

            matrixTabPane.getTabs().stream()
                    .filter(t -> t.getText().equals(name))
                    .findFirst()
                    .ifPresent(t -> matrixTabPane.getSelectionModel().select(t));

            setActionButtonsEnabled(true);
            labelMatrixStatus.setText(String.format(
                    "Завантажено: %s (%d×%d) з файлу %s. " +
                            "Вкладка «Обчислення» використовуватиме ці матриці.",
                    name, namedMatrix.rows, namedMatrix.cols, file.getName()));
            log.info("Матриці: завантажено " + name + " з " + file.getAbsolutePath());
        } catch (IOException | IllegalArgumentException e) {
            showError("Помилка завантаження: " + e.getMessage());
            log.error("Матриці: помилка завантаження — " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Очищення
    // ─────────────────────────────────────────────────────────────

    @FXML
    private void onClearAll() {
        currentMatrices.clear();
        MatrixStore.getInstance().clear();
        rebuildTabs();
        setActionButtonsEnabled(false);
        labelMatrixStatus.setText("Матриці очищено. Вкладка «Обчислення» генеруватиме випадкові.");
        labelActionStatus.setText("");
        log.info("Матриці: очищено, MatrixStore скинуто.");
    }

    // ─────────────────────────────────────────────────────────────
    //  Збереження
    // ─────────────────────────────────────────────────────────────

    @FXML
    private void onSaveCurrent() {
        Tab selectedTab = matrixTabPane.getSelectionModel().getSelectedItem();
        if (selectedTab == null) return;
        String name = selectedTab.getText();
        Matrix m = currentMatrices.get(name);
        if (m == null) return;

        FileChooser chooser = buildSaveChooser(
                String.format("%s_%dx%d.csv", name, m.rows, m.cols));
        File file = chooser.showSaveDialog(btnSaveCurrent.getScene().getWindow());
        if (file == null) return;
        trySaveMatrix(m, file);
    }

    @FXML
    private void onSaveAll() {
        if (currentMatrices.isEmpty()) return;

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Обрати директорію (введіть будь-яке ім'я файлу)");
        chooser.setInitialFileName("matrices_folder");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV файли (*.csv)", "*.csv"));
        File ref = chooser.showSaveDialog(btnSaveAll.getScene().getWindow());
        if (ref == null) return;

        java.nio.file.Path dir = ref.toPath().getParent();
        int saved = 0;
        for (Matrix m : currentMatrices.values()) {
            try {
                java.nio.file.Path out = MatrixFileService.saveMatrixAuto(m, dir);
                log.info("Матриці: збережено " + out.getFileName());
                saved++;
            } catch (IOException e) {
                log.error("Матриці: помилка збереження " + m.getName()
                        + " — " + e.getMessage());
            }
        }
        labelActionStatus.setText(String.format(
                "✓ Збережено %d матриць у %s", saved, dir.getFileName()));
    }

    // ─────────────────────────────────────────────────────────────
    //  Відображення вкладок
    // ─────────────────────────────────────────────────────────────

    private void rebuildTabs() {
        matrixTabPane.getTabs().clear();
        for (String name : matrixNamesForPrg()) {
            matrixTabPane.getTabs().add(buildTab(name, currentMatrices.get(name)));
        }
    }

    private void renderAllTabs() {
        matrixTabPane.getTabs().clear();
        for (String name : matrixNamesForPrg()) {
            matrixTabPane.getTabs().add(buildTab(name, currentMatrices.get(name)));
        }
        for (Map.Entry<String, Matrix> e : currentMatrices.entrySet()) {
            boolean already = matrixTabPane.getTabs().stream()
                    .anyMatch(t -> t.getText().equals(e.getKey()));
            if (!already) {
                matrixTabPane.getTabs().add(buildTab(e.getKey(), e.getValue()));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Tab buildTab(String name, Matrix matrix) {
        Tab tab = new Tab(name);
        tab.setClosable(false);

        VBox content = new VBox(8);
        content.setPadding(new Insets(10));

        Label info = new Label();
        info.getStyleClass().add("hint-label");

        TableView<ObservableList<String>> table = new TableView<>();
        table.setPrefHeight(320);
        table.getStyleClass().add("result-table");
        table.setPlaceholder(new Label(
                matrix == null
                        ? "Матриця порожня. Натисніть «Генерувати» або завантажте з CSV."
                        : "Завантаження..."));

        if (matrix != null) {
            TableRenderer.render(table, matrix, info);
        } else {
            info.setText(name + ": не заповнена");
        }

        content.getChildren().addAll(info, table);
        tab.setContent(content);
        return tab;
    }

    // ─────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────

    private String[] matrixNamesForPrg() {
        if (radioPRG2.isSelected()) return new String[]{"MB", "MC", "MD", "ME"};
        if (radioPRG3.isSelected()) return new String[]{"MR", "MB", "MC"};
        return new String[]{"MB", "MC", "MD"};
    }

    private int selectedPrg() {
        if (radioPRG2.isSelected()) return 2;
        if (radioPRG3.isSelected()) return 3;
        return 1;
    }

    private String prgLabel() {
        if (radioPRG2.isSelected()) return "ПРГ2";
        if (radioPRG3.isSelected()) return "ПРГ3";
        return "ПРГ1";
    }

    private int commitAndGetN() {
        try {
            String t = spinnerN.getEditor().getText().trim();
            if (!t.isEmpty()) spinnerN.getValueFactory().setValue(Integer.parseInt(t));
            return spinnerN.getValue();
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String extractMatrixName(String fileName, String fallback) {
        String base = fileName.replaceAll("\\.csv$", "");
        String[] parts = base.split("[_0-9]");
        String candidate = parts[0].toUpperCase();
        if (candidate.matches("[A-Z]{1,3}")) return candidate;
        return fallback != null ? fallback : base;
    }

    private void trySaveMatrix(Matrix m, File file) {
        try {
            MatrixFileService.saveMatrix(m, file.toPath());
            String size = MatrixFileService.humanFileSize(file.toPath());
            labelActionStatus.setText("✓ Збережено: " + file.getName()
                    + " (" + size + ")");
            log.info("Матриці: збережено " + m.getName()
                    + " → " + file.getAbsolutePath());
        } catch (IOException e) {
            showError("Помилка збереження: " + e.getMessage());
            log.error("Матриці: помилка збереження — " + e.getMessage());
        }
    }

    private FileChooser buildSaveChooser(String initialName) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Зберегти матрицю");
        chooser.setInitialFileName(initialName);
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV файли (*.csv)", "*.csv"));
        return chooser;
    }

    private void setActionButtonsEnabled(boolean on) {
        btnSaveCurrent.setDisable(!on);
        btnSaveAll.setDisable(!on);
        if (!on) labelActionStatus.setText("");
    }

    private void showError(String msg) {
        javafx.application.Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.ERROR);
            a.setTitle("Помилка");
            a.setHeaderText(null);
            a.setContentText(msg);
            a.showAndWait();
        });
    }
}