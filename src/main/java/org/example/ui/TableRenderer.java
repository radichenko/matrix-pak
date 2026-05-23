package org.example.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import org.example.model.Matrix;

import java.util.ArrayList;
import java.util.List;

/**
 * Утилітний клас для відображення матриці MA у {@link TableView}.
 *
 * <p>Рендерить матрицю як справжню числову сітку:
 * кожен рядок таблиці відповідає одному рядку матриці,
 * кожна колонка — одному стовпцю матриці.
 *
 * <pre>
 * ┌──────┬──────────┬──────────┬──────────┐
 * │  i\j │    0     │    1     │    2     │
 * ├──────┼──────────┼──────────┼──────────┤
 * │   0  │  12.345  │ -67.890  │   3.141  │
 * │   1  │ -99.001  │  45.678  │  -0.001  │
 * └──────┴──────────┴──────────┴──────────┘
 * </pre>
 *
 * <h3>Обмеження для великих матриць:</h3>
 * <ul>
 *   <li>Показується максимум {@value #MAX_ROWS} рядків матриці.</li>
 *   <li>Показується максимум {@value #MAX_COLS} стовпців матриці.</li>
 *   <li>Якщо матриця більша — відображається підпис про обрізання.</li>
 * </ul>
 */
public final class TableRenderer {

    /** Максимальна кількість рядків матриці для відображення. */
    public static final int MAX_ROWS = 30;

    /** Максимальна кількість стовпців матриці для відображення. */
    public static final int MAX_COLS = 15;

    /** Формат відображення числових значень у комірках. */
    private static final String NUM_FORMAT = "%.4g";

    private TableRenderer() {}

    // ─────────────────────────────────────────────────────────────
    //  Головний метод відображення
    // ─────────────────────────────────────────────────────────────

    /**
     * Очищає та заповнює {@link TableView} даними матриці.
     *
     * <p>Метод динамічно створює стовпці таблиці відповідно до розміру матриці.
     * Перший стовпець — індекс рядка {@code i}. Наступні стовпці — значення
     * {@code MA[i][0]}, {@code MA[i][1]}, ... до {@link #MAX_COLS}.
     *
     * @param table       TableView для заповнення (тип рядка — {@code ObservableList<String>})
     * @param ma          матриця для відображення (не null)
     * @param statusLabel мітка для виведення інформації про обрізання (може бути null)
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void render(TableView table,
                              Matrix ma,
                              Label statusLabel) {
        table.getColumns().clear();
        table.getItems().clear();
        if (ma == null) return;

        int displayRows = Math.min(ma.rows, MAX_ROWS);
        int displayCols = Math.min(ma.cols, MAX_COLS);
        boolean rowsTruncated = ma.rows > MAX_ROWS;
        boolean colsTruncated = ma.cols > MAX_COLS;

        // ── Перший стовпець: індекс рядка ─────────────────────────
        TableColumn<ObservableList<String>, String> rowIndexCol =
                new TableColumn<>("i \\ j");
        rowIndexCol.setPrefWidth(52);
        rowIndexCol.setMinWidth(40);
        rowIndexCol.setStyle("-fx-alignment: CENTER; -fx-font-weight: bold;");
        rowIndexCol.setSortable(false);
        rowIndexCol.setCellValueFactory(param ->
                new SimpleStringProperty(param.getValue().get(0)));
        rowIndexCol.setCellFactory(col -> {
            TableCell<ObservableList<String>, String> cell = new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : item);
                    setStyle("-fx-font-weight: bold; -fx-alignment: CENTER;"
                            + "-fx-background-color: -fx-table-cell-border-color;");
                }
            };
            return cell;
        });
        table.getColumns().add(rowIndexCol);

        // ── Стовпці даних: MA[i][j] ────────────────────────────────
        for (int j = 0; j < displayCols; j++) {
            final int colIndex = j + 1; // +1 бо колонка 0 — це row index
            TableColumn<ObservableList<String>, String> col =
                    new TableColumn<>(String.valueOf(j));
            col.setPrefWidth(90);
            col.setMinWidth(60);
            col.setStyle("-fx-alignment: CENTER_RIGHT;");
            col.setSortable(false);
            col.setCellValueFactory(param -> {
                ObservableList<String> row = param.getValue();
                if (colIndex < row.size()) {
                    return new SimpleStringProperty(row.get(colIndex));
                }
                return new SimpleStringProperty("");
            });
            col.setCellFactory(col2 -> new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : item);
                    setAlignment(Pos.CENTER_RIGHT);
                }
            });
            table.getColumns().add(col);
        }

        // ── Заповнення рядків даними ───────────────────────────────
        double[][] data = ma.getData();
        ObservableList<ObservableList<String>> rows = FXCollections.observableArrayList();

        for (int i = 0; i < displayRows; i++) {
            ObservableList<String> rowData = FXCollections.observableArrayList();
            rowData.add(String.valueOf(i)); // індекс рядка
            for (int j = 0; j < displayCols; j++) {
                rowData.add(String.format(NUM_FORMAT, data[i][j]));
            }
            rows.add(rowData);
        }
        table.setItems(rows);

        // ── Оновлення статусного підпису ──────────────────────────
        if (statusLabel != null) {
            if (!rowsTruncated && !colsTruncated) {
                statusLabel.setText(String.format(
                        "Матриця MA: %d×%d — показано повністю",
                        ma.rows, ma.cols));
            } else {
                statusLabel.setText(String.format(
                        "Матриця MA: %d×%d — показано перші %d рядків × %d стовпців",
                        ma.rows, ma.cols, displayRows, displayCols));
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Копіювання у буфер обміну
    // ─────────────────────────────────────────────────────────────

    /**
     * Копіює вміст таблиці у системний буфер обміну у форматі TSV
     * (Tab-Separated Values), придатному для вставки у Excel або LibreOffice Calc.
     *
     * <p>Перший рядок — заголовки стовпців. Далі — рядки даних.
     *
     * @param table таблиця для копіювання
     * @return рядок що був скопійований (для тестів)
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static String copyToClipboard(TableView table) {
        StringBuilder sb = new StringBuilder();

        // Заголовок
        List<TableColumn> cols = table.getColumns();
        for (int c = 0; c < cols.size(); c++) {
            if (c > 0) sb.append('\t');
            sb.append(cols.get(c).getText());
        }
        sb.append('\n');

        // Дані
        for (Object item : table.getItems()) {
            if (item instanceof ObservableList<?> row) {
                for (int c = 0; c < row.size(); c++) {
                    if (c > 0) sb.append('\t');
                    sb.append(row.get(c));
                }
                sb.append('\n');
            }
        }

        String result = sb.toString();
        ClipboardContent content = new ClipboardContent();
        content.putString(result);
        Clipboard.getSystemClipboard().setContent(content);
        return result;
    }

    // ─────────────────────────────────────────────────────────────
    //  Збереження у CSV (рядок для запису у файл)
    // ─────────────────────────────────────────────────────────────

    /**
     * Формує CSV-рядок з відображуваних даних таблиці (з заголовком).
     * Використовується у кнопці «Зберегти CSV» як preview перших рядків.
     *
     * @param table таблиця
     * @return рядок CSV
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static String toCsv(TableView table) {
        StringBuilder sb = new StringBuilder();

        List<TableColumn> cols = table.getColumns();
        for (int c = 0; c < cols.size(); c++) {
            if (c > 0) sb.append(',');
            sb.append(cols.get(c).getText());
        }
        sb.append('\n');

        for (Object item : table.getItems()) {
            if (item instanceof ObservableList<?> row) {
                for (int c = 0; c < row.size(); c++) {
                    if (c > 0) sb.append(',');
                    sb.append(row.get(c));
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }
}
