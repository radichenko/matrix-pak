package org.example;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;
import java.util.prefs.Preferences;

/**
 * Головний JavaFX Application клас застосунку «matrix-pak».
 *
 * Відповідає за:
 * - завантаження головного FXML-макету (main.fxml)
 * - налаштування вікна (заголовок, мінімальний розмір)
 * - застосування збереженої теми (світла/темна)
 * - публічний API для перемикання теми з MainController
 */
public class MatrixPakApp extends Application {

    /** Ключ для збереження теми у java.util.prefs.Preferences */
    public static final String PREF_THEME = "theme";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK  = "dark";

    /** Шляхи до CSS-файлів тем (відносно кореня classpath) */
    private static final String CSS_LIGHT = "/css/light-theme.css";
    private static final String CSS_DARK  = "/css/dark-theme.css";

    /** Глобальна сцена — використовується для перемикання теми */
    private static Scene mainScene;

    @Override
    public void start(Stage primaryStage) throws IOException {
        // Завантаження головного FXML
        URL fxmlUrl = getClass().getResource("/fxml/main.fxml");
        if (fxmlUrl == null) {
            throw new IOException("Не знайдено /fxml/main.fxml. Перевірте структуру ресурсів.");
        }
        Parent root = FXMLLoader.load(fxmlUrl);

        // Створення сцени з мінімальним розміром 1100×720
        mainScene = new Scene(root, 1200, 750);

        // Застосування збереженої теми (або світлої за замовчуванням)
        Preferences prefs = Preferences.userNodeForPackage(MatrixPakApp.class);
        String savedTheme = prefs.get(PREF_THEME, THEME_LIGHT);
        applyTheme(savedTheme);

        // Налаштування вікна
        primaryStage.setTitle("ПАК для операцій з матрицями");
        primaryStage.setMinWidth(1100);
        primaryStage.setMinHeight(720);
        primaryStage.setScene(mainScene);
        primaryStage.show();
    }

    /**
     * Перемикає CSS-тему на головній сцені.
     * Викликається з MainController при виборі теми через меню.
     *
     * @param theme константа THEME_LIGHT або THEME_DARK
     */
    public static void applyTheme(String theme) {
        if (mainScene == null) return;

        mainScene.getStylesheets().clear();

        String cssPath = THEME_DARK.equals(theme) ? CSS_DARK : CSS_LIGHT;
        URL cssUrl = MatrixPakApp.class.getResource(cssPath);
        if (cssUrl != null) {
            mainScene.getStylesheets().add(cssUrl.toExternalForm());
        }

        // Зберегти вибір
        Preferences prefs = Preferences.userNodeForPackage(MatrixPakApp.class);
        prefs.put(PREF_THEME, theme);
    }

    /**
     * Повертає поточну тему зі збережених налаштувань.
     */
    public static String getCurrentTheme() {
        Preferences prefs = Preferences.userNodeForPackage(MatrixPakApp.class);
        return prefs.get(PREF_THEME, THEME_LIGHT);
    }
}

