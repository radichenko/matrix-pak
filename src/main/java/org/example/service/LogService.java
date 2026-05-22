package org.example.service;

import javafx.application.Platform;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Синглтон-сервіс для ведення журналу подій застосунку.
 *
 * <p>Потокобезпечний: методи {@code log()} та {@code clear()} можуть
 * викликатись з будь-якого потоку, у тому числі з обчислювальних
 * потоків ПРГ1–ПРГ3. Доставка повідомлень до UI-слухачів завжди
 * виконується у JavaFX Application Thread через {@link Platform#runLater}.
 *
 * <h3>Рівні журналювання:</h3>
 * <ul>
 *   <li>{@code INFO}  — стандартні події (запуск, завершення, результат);</li>
 *   <li>{@code WARN}  — попередження (некоректний розмір, нульовий час);</li>
 *   <li>{@code ERROR} — помилки (виняток у потоці, помилка IO);</li>
 *   <li>{@code DEBUG} — деталі (хід виконання потоків, внутрішні стани).</li>
 * </ul>
 *
 * <h3>Використання:</h3>
 * <pre>
 *   LogService log = LogService.getInstance();
 *   log.info("Запуск ПРГ1, N=500, P=4");
 *   log.error("Помилка у потоці T2: " + e.getMessage());
 * </pre>
 */
public final class LogService {

    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    /** Максимальна кількість записів у буфері (щоб не переповнити пам'ять). */
    private static final int MAX_ENTRIES = 10_000;

    // ─────────────────────────────────────────────────────────────
    //  Singleton (thread-safe, lazy initialization on demand holder)
    // ─────────────────────────────────────────────────────────────

    private static final class Holder {
        static final LogService INSTANCE = new LogService();
    }

    /** Повертає єдиний екземпляр LogService. */
    public static LogService getInstance() {
        return Holder.INSTANCE;
    }

    // ─────────────────────────────────────────────────────────────
    //  Поля
    // ─────────────────────────────────────────────────────────────

    /**
     * Буфер записів журналу (відформатовані рядки).
     * {@link CopyOnWriteArrayList} — читання без блокувань, запис з копіюванням.
     */
    private final CopyOnWriteArrayList<String> entries = new CopyOnWriteArrayList<>();

    /**
     * Слухачі, що отримують нові записи (UI-компоненти TextArea).
     * Також CopyOnWriteArrayList — потокобезпечне додавання/видалення слухачів.
     */
    private final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();

    private LogService() {}

    // ─────────────────────────────────────────────────────────────
    //  Public API — додавання записів
    // ─────────────────────────────────────────────────────────────

    /**
     * Додає запис рівня INFO.
     *
     * @param message текст повідомлення
     */
    public void info(String message) {
        append("INFO ", message);
    }

    /**
     * Додає запис рівня WARN.
     *
     * @param message текст попередження
     */
    public void warn(String message) {
        append("WARN ", message);
    }

    /**
     * Додає запис рівня ERROR.
     *
     * @param message текст помилки
     */
    public void error(String message) {
        append("ERROR", message);
    }

    /**
     * Додає запис рівня DEBUG.
     * У production-версії може бути відключений через прапорець.
     *
     * @param message деталі відлагодження
     */
    public void debug(String message) {
        append("DEBUG", message);
    }

    /**
     * Додає запис із форматуванням (аналог {@code String.format}).
     *
     * <p>Приклад: {@code log.infof("Потік T%d завершив стовпці [%d, %d)", id, start, end)}
     *
     * @param level   рівень: "INFO ", "WARN ", "ERROR", "DEBUG"
     * @param fmt     рядок формату
     * @param args    аргументи форматування
     */
    public void infof(String fmt, Object... args) {
        info(String.format(fmt, args));
    }

    // ─────────────────────────────────────────────────────────────
    //  Внутрішнє додавання запису
    // ─────────────────────────────────────────────────────────────

    /**
     * Формує рядок запису, додає до буфера та сповіщає всіх слухачів.
     *
     * @param level   рівень журналювання (5 символів для вирівнювання)
     * @param message текст
     */
    private void append(String level, String message) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FMT);
        String entry = String.format("[%s] [%s] %s", timestamp, level, message);

        if (entries.size() >= MAX_ENTRIES) {
            entries.remove(0);
        }
        entries.add(entry);

        if (!listeners.isEmpty()) {
            try {
                Platform.runLater(() -> {
                    for (Consumer<String> listener : listeners) {
                        listener.accept(entry);
                    }
                });
            } catch (IllegalStateException ignored) {
                // JavaFX Toolkit ще не ініціалізовано (наприклад, під час тестів без UI).
                // Записи вже збережені в буфері — слухачі отримають їх при ініціалізації через getAllEntries().
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Управління слухачами
    // ─────────────────────────────────────────────────────────────

    /**
     * Реєструє слухача нових записів журналу.
     *
     * <p>Слухач викликається у JavaFX Application Thread.
     * Типовий слухач — {@code TextArea::appendText} у вкладці «Журнал».
     *
     * @param listener функція-споживач нового рядка
     */
    public void addListener(Consumer<String> listener) {
        listeners.add(listener);
    }

    /**
     * Видаляє раніше зареєстрованого слухача.
     *
     * @param listener слухач для видалення
     */
    public void removeListener(Consumer<String> listener) {
        listeners.remove(listener);
    }

    // ─────────────────────────────────────────────────────────────
    //  Читання та очищення
    // ─────────────────────────────────────────────────────────────

    /**
     * Повертає всі записи журналу у вигляді одного рядка
     * (записи розділені символом переводу рядка).
     *
     * <p>Використовується при ініціалізації TextArea в UI, щоб
     * відобразити всі попередні записи що накопичились до відкриття вкладки.
     *
     * @return весь журнал як String
     */
    public String getAllEntries() {
        return String.join("\n", entries);
    }

    /**
     * Повертає список всіх записів (незмінна копія для ітерації).
     *
     * @return список рядків журналу
     */
    public List<String> getEntries() {
        return List.copyOf(entries);
    }

    /**
     * Очищає буфер журналу та сповіщає слухачів порожнім рядком.
     * Виклик потокобезпечний.
     */
    public void clear() {
        entries.clear();
        if (!listeners.isEmpty()) {
            try {
                Platform.runLater(() -> {
                    for (Consumer<String> listener : listeners) {
                        listener.accept("");
                    }
                });
            } catch (IllegalStateException ignored) {}
        }
        info("Журнал очищено.");
    }

    /**
     * Повертає кількість записів у буфері.
     *
     * @return кількість рядків журналу
     */
    public int size() {
        return entries.size();
    }
}
