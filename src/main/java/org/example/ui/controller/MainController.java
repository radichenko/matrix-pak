package org.example.ui.controller;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.MenuBar;
import javafx.scene.control.TabPane;
import org.example.MatrixPakApp;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * Головний контролер JavaFX-вікна.
 *
 * Відповідає за:
 * - ініціалізацію головної TabPane
 * - перемикання теми (Вигляд → Світла/Темна)
 * - оновлення рядка статусу (statusBar)
 * - обробку пункту меню "Вийти"
 */
public class MainController implements Initializable {

    @FXML
    private TabPane mainTabPane;

    @FXML
    private MenuBar menuBar;

    @FXML
    private Label statusBar;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Встановити початковий текст рядка статусу
        updateStatus("Готовий. Оберіть програму та натисніть «Запустити».");
    }

    /**
     * Обробник меню «Файл → Вийти».
     */
    @FXML
    private void onExit(ActionEvent event) {
        Platform.exit();
    }

    /**
     * Обробник меню «Вигляд → Світла тема».
     */
    @FXML
    private void onLightTheme(ActionEvent event) {
        MatrixPakApp.applyTheme(MatrixPakApp.THEME_LIGHT);
        updateStatus("Застосовано світлу тему.");
    }

    /**
     * Обробник меню «Вигляд → Темна тема».
     */
    @FXML
    private void onDarkTheme(ActionEvent event) {
        MatrixPakApp.applyTheme(MatrixPakApp.THEME_DARK);
        updateStatus("Застосовано темну тему.");
    }

    /**
     * Обробник меню «Довідка → Про програму».
     */
    @FXML
    private void onAbout(ActionEvent event) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.INFORMATION
        );
        alert.setTitle("Про програму");
        alert.setHeaderText("matrix-pak v1.0");
        alert.setContentText(
                "Програмна складова ПАК для операцій з матрицями.\n\n" +
                        "Дипломна робота бакалавра, КПІ.\n" +
                        "Спеціальність: Інженерія програмного забезпечення.\n\n" +
                        "Реалізовано три паралельні програми (ПРГ1, ПРГ2, ПРГ3)\n" +
                        "з використанням багатопоточності Java (CountDownLatch,\n" +
                        "CyclicBarrier, AtomicLong)."
        );
        alert.showAndWait();
    }

    /**
     * Оновлює текст рядка статусу в нижній частині вікна.
     * Метод є потокобезпечним — автоматично перенаправляє виклик до JavaFX-потоку.
     *
     * @param message текст для відображення
     */
    public void updateStatus(String message) {
        if (Platform.isFxApplicationThread()) {
            statusBar.setText(message);
        } else {
            Platform.runLater(() -> statusBar.setText(message));
        }
    }
}
