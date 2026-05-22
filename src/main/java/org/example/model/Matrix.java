package org.example.model;

/**
 * Обгортка над двовимірним масивом double для зберігання матриці.
 *
 * <p>Клас надає зручний API для роботи з матричними даними:
 * іменування, доступ до елементів, копіювання. Внутрішні дані
 * зберігаються у row-major порядку: {@code data[рядок][стовпець]}.
 *
 * <p>Посилання на внутрішній масив {@code data} передається безпосередньо
 * у потоки обчислень через {@link #getData()} — копіювання не виконується,
 * що забезпечує максимальну продуктивність при великих N.
 */
public class Matrix {

    /** Двовимірний масив даних матриці у форматі [рядок][стовпець]. */
    private final double[][] data;

    /** Кількість рядків матриці. */
    public final int rows;

    /** Кількість стовпців матриці. */
    public final int cols;

    /** Ідентифікатор матриці (наприклад, "MB", "MC", "MA"). */
    private final String name;

    // ─────────────────────────────────────────────────────────────
    //  Конструктори
    // ─────────────────────────────────────────────────────────────

    /**
     * Створює нульову матрицю заданого розміру.
     *
     * @param rows кількість рядків (> 0)
     * @param cols кількість стовпців (> 0)
     * @param name назва матриці
     */
    public Matrix(int rows, int cols, String name) {
        if (rows <= 0 || cols <= 0) {
            throw new IllegalArgumentException(
                    "Розмір матриці має бути > 0. Отримано: " + rows + "×" + cols
            );
        }
        this.rows = rows;
        this.cols = cols;
        this.name = name;
        this.data = new double[rows][cols];
    }

    /**
     * Створює матрицю з готового двовимірного масиву.
     * Масив передається за посиланням — копія не створюється.
     *
     * @param data  двовимірний масив даних (не null, не порожній)
     * @param name  назва матриці
     */
    public Matrix(double[][] data, String name) {
        if (data == null || data.length == 0 || data[0].length == 0) {
            throw new IllegalArgumentException("Масив даних матриці не може бути null або порожнім.");
        }
        this.data = data;
        this.rows = data.length;
        this.cols = data[0].length;
        this.name = name;
    }

    // ─────────────────────────────────────────────────────────────
    //  Доступ до елементів
    // ─────────────────────────────────────────────────────────────

    /**
     * Повертає значення елемента матриці.
     *
     * @param row рядок (0-based)
     * @param col стовпець (0-based)
     * @return значення елемента
     */
    public double get(int row, int col) {
        return data[row][col];
    }

    /**
     * Встановлює значення елемента матриці.
     *
     * @param row   рядок (0-based)
     * @param col   стовпець (0-based)
     * @param value нове значення
     */
    public void set(int row, int col, double value) {
        data[row][col] = value;
    }

    /**
     * Повертає пряме посилання на внутрішній масив даних.
     *
     * <p><b>Увага:</b> зміни у поверненому масиві змінюють дані матриці.
     * Метод призначений для передачі в обчислювальні потоки без зайвого копіювання.
     *
     * @return посилання на {@code double[][]}
     */
    public double[][] getData() {
        return data;
    }

    // ─────────────────────────────────────────────────────────────
    //  Перевірки та допоміжні методи
    // ─────────────────────────────────────────────────────────────

    /**
     * Перевіряє, чи є матриця квадратною (rows == cols).
     *
     * @return true якщо квадратна
     */
    public boolean isSquare() {
        return rows == cols;
    }

    /**
     * Повертає назву матриці.
     *
     * @return рядок-ідентифікатор (наприклад, "MB")
     */
    public String getName() {
        return name;
    }

    /**
     * Створює глибоку копію матриці (новий масив з тими самими значеннями).
     *
     * @return новий об'єкт Matrix з незалежним масивом даних
     */
    public Matrix copy() {
        double[][] newData = new double[rows][cols];
        for (int i = 0; i < rows; i++) {
            System.arraycopy(data[i], 0, newData[i], 0, cols);
        }
        return new Matrix(newData, name + "_copy");
    }

    /**
     * Повертає рядкове представлення розміру матриці.
     * Використовується в UI для підпису (наприклад, "MB: 500×500, double").
     *
     * @return рядок виду "NAME: RxC, double"
     */
    @Override
    public String toString() {
        return name + ": " + rows + "×" + cols + ", double";
    }
}
