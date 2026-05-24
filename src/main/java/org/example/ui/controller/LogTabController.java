package org.example.ui.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import org.example.service.LogService;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.Consumer;

/**
 * Контролер вкладки «Журнал».
 *
 * <p>Відображає всі записи {@link LogService} у реальному часі.
 * Підтримує:
 * <ul>
 *   <li>фільтрацію за рівнями (INFO / WARN / ERROR / DEBUG);</li>
 *   <li>пошук підрядка (оновлення при кожному натисканні клавіші);</li>
 *   <li>авто-прокрутку до нового запису;</li>
 *   <li>копіювання всього журналу у буфер обміну;</li>
 *   <li>очищення журналу.</li>
 * </ul>
 */
public class LogTabController implements Initializable {

    // ── Фільтри рівнів ────────────────────────────────────────────
    @FXML private CheckBox chkInfo;
    @FXML private CheckBox chkWarn;
    @FXML private CheckBox chkError;
    @FXML private CheckBox chkDebug;
    @FXML private CheckBox chkAutoScroll;

    // ── Пошук ─────────────────────────────────────────────────────
    @FXML private TextField fieldSearch;

    // ── Журнал ────────────────────────────────────────────────────
    @FXML private TextArea logTextArea;

    // ── Статус ────────────────────────────────────────────────────
    @FXML private Label labelEntryCount;
    @FXML private Label labelFilteredCount;
    @FXML private Label labelStatus;

    // ── Стан ─────────────────────────────────────────────────────
    private final LogService         logService = LogService.getInstance();
    /** Локальна копія ВСІХ записів (включаючи відфільтровані). */
    private final List<String>       allEntries = new ArrayList<>();
    /** Слухач нових записів, що реєструємо у LogService. */
    private Consumer<String>         logListener;

    // ─────────────────────────────────────────────────────────────
    //  Ініціалізація
    // ─────────────────────────────────────────────────────────────

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Завантажуємо вже наявні записи
        allEntries.addAll(logService.getEntries());
        refreshDisplay();

        // Реєструємо слухача нових записів
        logListener = this::onNewEntry;
        logService.addListener(logListener);
    }

    /**
     * Викликається LogService при кожному новому записі або при очищенні.
     * Вже виконується у JavaFX-потоці (Platform.runLater у LogService).
     *
     * @param entry новий рядок або "" (сигнал очищення)
     */
    private void onNewEntry(String entry) {
        if (entry.isEmpty()) {
            // Сигнал очищення
            allEntries.clear();
            refreshDisplay();
            return;
        }
        allEntries.add(entry);
        updateCountLabels();

        // Показуємо тільки якщо відповідає фільтру і пошуку
        if (matchesFilter(entry) && matchesSearch(entry)) {
            logTextArea.appendText(entry + "\n");
            if (chkAutoScroll.isSelected()) {
                logTextArea.setScrollTop(Double.MAX_VALUE);
            }
            int shown = countLinesInTextArea();
            labelFilteredCount.setText("Відображається: " + shown);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Фільтри та пошук
    // ─────────────────────────────────────────────────────────────

    /** Перемальовує TextArea при зміні фільтра рівнів. */
    @FXML
    private void onFilterChanged() {
        refreshDisplay();
    }

    /** Перемальовує TextArea при введенні тексту у поле пошуку. */
    @FXML
    private void onSearch() {
        refreshDisplay();
    }

    /**
     * Повністю перебудовує вміст TextArea з урахуванням поточних фільтрів.
     * Викликається при зміні фільтрів, пошуку або очищенні.
     */
    private void refreshDisplay() {
        String search = fieldSearch != null
                ? fieldSearch.getText().toLowerCase().trim() : "";

        StringBuilder sb = new StringBuilder();
        int shown = 0;

        for (String entry : allEntries) {
            if (matchesFilter(entry) && (search.isEmpty()
                    || entry.toLowerCase().contains(search))) {
                sb.append(entry).append('\n');
                shown++;
            }
        }

        logTextArea.setText(sb.toString());

        if (chkAutoScroll != null && chkAutoScroll.isSelected()) {
            logTextArea.setScrollTop(Double.MAX_VALUE);
        }

        updateCountLabels();
        if (labelFilteredCount != null) {
            labelFilteredCount.setText("Відображається: " + shown);
        }
    }

    /** Перевіряє чи відповідає запис поточним фільтрам рівнів. */
    private boolean matchesFilter(String entry) {
        if (entry.contains("[INFO ]") && chkInfo  != null) return chkInfo.isSelected();
        if (entry.contains("[WARN ]") && chkWarn  != null) return chkWarn.isSelected();
        if (entry.contains("[ERROR]") && chkError != null) return chkError.isSelected();
        if (entry.contains("[DEBUG]") && chkDebug != null) return chkDebug.isSelected();
        return true; // невідомий рівень — показуємо
    }

    /** Перевіряє чи відповідає запис пошуковому запиту. */
    private boolean matchesSearch(String entry) {
        if (fieldSearch == null) return true;
        String search = fieldSearch.getText().trim().toLowerCase();
        return search.isEmpty() || entry.toLowerCase().contains(search);
    }

    // ─────────────────────────────────────────────────────────────
    //  Дії
    // ─────────────────────────────────────────────────────────────

    /** Копіює ВЕСЬ вміст TextArea (відфільтрований) у буфер обміну. */
    @FXML
    private void onCopyAll() {
        String text = logTextArea.getText();
        if (text.isEmpty()) {
            labelStatus.setText("Журнал порожній.");
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
        labelStatus.setText("✓ Скопійовано " + countLinesInTextArea() + " рядків.");
    }

    /** Очищає журнал у LogService і локально. */
    @FXML
    private void onClear() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Підтвердження");
        confirm.setHeaderText(null);
        confirm.setContentText("Очистити весь журнал? Ця дія незворотна.");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                logService.clear(); // генерує подію "" → onNewEntry очистить allEntries
                labelStatus.setText("Журнал очищено.");
            }
        });
    }

    // ─────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────

    private void updateCountLabels() {
        if (labelEntryCount != null) {
            labelEntryCount.setText("Записів: " + allEntries.size());
        }
    }

    private int countLinesInTextArea() {
        String text = logTextArea.getText();
        if (text.isEmpty()) return 0;
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') count++;
        }
        return count;
    }

    /**
     * Знімає реєстрацію слухача при закритті вкладки або застосунку.
     * Викликається з MainController якщо потрібно (опціонально).
     */
    public void dispose() {
        if (logListener != null) {
            logService.removeListener(logListener);
        }
    }
}
