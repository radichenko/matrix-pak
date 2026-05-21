package org.example;

import javafx.application.Application;

/**
 * Точка входу застосунку «matrix-pak».
 *
 * Клас Main навмисно відокремлений від MatrixPakApp (JavaFX Application),
 * щоб уникнути проблем із завантажувачем класів JavaFX у середовищах,
 * де JavaFX не є системним модулем (наприклад, при запуску через fat-jar).
 *
 * Запуск: mvn javafx:run
 */
public class Main {

    public static void main(String[] args) {
        Application.launch(MatrixPakApp.class, args);
    }
}
