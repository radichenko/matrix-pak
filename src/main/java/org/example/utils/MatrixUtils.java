package org.example.utils;

import org.example.model.Matrix;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Утилітний клас для роботи з матрицями.
 *
 * <p>Містить статичні методи:
 * <ul>
 *   <li>заповнення матриць тестовими даними;</li>
 *   <li>послідовне (однопотокове) множення та додавання — для перевірки коректності;</li>
 *   <li>пошук максимального елемента (для ПРГ3);</li>
 *   <li>порівняння двох матриць з точністю ε (для unit-тестів).</li>
 * </ul>
 *
 * <p>Паралельні версії цих операцій реалізовано у відповідних класах ПРГ1–ПРГ3.
 * Методи цього класу призначені для:
 * <ol>
 *   <li>первинного заповнення матриць перед запуском програми;</li>
 *   <li>верифікації результатів паралельних програм (порівняння з еталоном).</li>
 * </ol>
 */
public final class MatrixUtils {

    /** Діапазон значень при випадковому заповненні: [-FILL_RANGE, +FILL_RANGE]. */
    private static final double FILL_RANGE = 10.0;

    /** Точність порівняння елементів матриць (епсилон). */
    public static final double EPSILON = 1e-9;

    // Заборона створення екземплярів утилітного класу
    private MatrixUtils() {}

    // ─────────────────────────────────────────────────────────────
    //  Заповнення матриць
    // ─────────────────────────────────────────────────────────────

    /**
     * Заповнює матрицю псевдовипадковими числами у діапазоні [-10.0, +10.0].
     *
     * <p>Використовує {@link ThreadLocalRandom} — потокобезпечний генератор
     * без накладних витрат на синхронізацію. Виклик безпечний зі всіх потоків.
     *
     * @param matrix матриця для заповнення
     */
    public static void fillRandom(Matrix matrix) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double[][] data = matrix.getData();
        for (int i = 0; i < matrix.rows; i++) {
            for (int j = 0; j < matrix.cols; j++) {
                data[i][j] = rng.nextDouble(-FILL_RANGE, FILL_RANGE);
            }
        }
    }

    /**
     * Заповнює матрицю одним і тим самим значенням.
     *
     * @param matrix матриця для заповнення
     * @param value  значення для запису в кожен елемент
     */
    public static void fillValue(Matrix matrix, double value) {
        double[][] data = matrix.getData();
        for (int i = 0; i < matrix.rows; i++) {
            for (int j = 0; j < matrix.cols; j++) {
                data[i][j] = value;
            }
        }
    }

    /**
     * Заповнює матрицю нулями.
     *
     * @param matrix матриця для очищення
     */
    public static void fillZero(Matrix matrix) {
        fillValue(matrix, 0.0);
    }

    // ─────────────────────────────────────────────────────────────
    //  Послідовне (однопотокове) множення матриць
    // ─────────────────────────────────────────────────────────────

    /**
     * Послідовне множення двох матриць: C = A × B.
     *
     * <p>Реалізація використовує оптимізований порядок циклів i-k-j
     * (замість i-j-k) для кращого використання кешу процесора
     * при однопотоковому виконанні.
     *
     * @param a    ліва матриця (N×M)
     * @param b    права матриця (M×K)
     * @param name назва результуючої матриці
     * @return     нова матриця C (N×K)
     * @throws IllegalArgumentException якщо розміри несумісні (a.cols ≠ b.rows)
     */
    public static Matrix multiply(Matrix a, Matrix b, String name) {
        if (a.cols != b.rows) {
            throw new IllegalArgumentException(String.format(
                    "Несумісні розміри для множення: %s (%d×%d) × %s (%d×%d)",
                    a.getName(), a.rows, a.cols,
                    b.getName(), b.rows, b.cols
            ));
        }

        Matrix result = new Matrix(a.rows, b.cols, name);
        double[][] aData = a.getData();
        double[][] bData = b.getData();
        double[][] cData = result.getData();

        // Порядок i-k-j для кешової оптимізації
        for (int i = 0; i < a.rows; i++) {
            for (int k = 0; k < a.cols; k++) {
                double aik = aData[i][k];
                if (aik == 0.0) continue; // пропускаємо нульові рядки
                for (int j = 0; j < b.cols; j++) {
                    cData[i][j] += aik * bData[k][j];
                }
            }
        }
        return result;
    }

    /**
     * Послідовне додавання двох матриць: C = A + B.
     *
     * @param a    ліва матриця
     * @param b    права матриця (має збігатися за розміром з A)
     * @param name назва результуючої матриці
     * @return     нова матриця C = A + B
     * @throws IllegalArgumentException якщо розміри не збігаються
     */
    public static Matrix add(Matrix a, Matrix b, String name) {
        if (a.rows != b.rows || a.cols != b.cols) {
            throw new IllegalArgumentException(String.format(
                    "Несумісні розміри для додавання: %s (%d×%d) + %s (%d×%d)",
                    a.getName(), a.rows, a.cols,
                    b.getName(), b.rows, b.cols
            ));
        }

        Matrix result = new Matrix(a.rows, a.cols, name);
        double[][] aData = a.getData();
        double[][] bData = b.getData();
        double[][] cData = result.getData();

        for (int i = 0; i < a.rows; i++) {
            for (int j = 0; j < a.cols; j++) {
                cData[i][j] = aData[i][j] + bData[i][j];
            }
        }
        return result;
    }

    /**
     * Множення матриці на скаляр: C = scalar × A.
     *
     * @param a      вхідна матриця
     * @param scalar множник
     * @param name   назва результуючої матриці
     * @return       нова матриця C
     */
    public static Matrix multiplyScalar(Matrix a, double scalar, String name) {
        Matrix result = new Matrix(a.rows, a.cols, name);
        double[][] aData = a.getData();
        double[][] cData = result.getData();

        for (int i = 0; i < a.rows; i++) {
            for (int j = 0; j < a.cols; j++) {
                cData[i][j] = aData[i][j] * scalar;
            }
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────
    //  Пошук максимального елемента (для ПРГ3)
    // ─────────────────────────────────────────────────────────────

    /**
     * Знаходить максимальний елемент матриці MR (однопотоково).
     *
     * <p>Використовується як еталонний результат для порівняння
     * з результатом паралельної редукції у ПРГ3.
     *
     * @param matrix вхідна матриця MR
     * @return максимальне значення серед усіх елементів
     */
    public static double maxValue(Matrix matrix) {
        double max = Double.NEGATIVE_INFINITY;
        double[][] data = matrix.getData();
        for (int i = 0; i < matrix.rows; i++) {
            for (int j = 0; j < matrix.cols; j++) {
                if (data[i][j] > max) {
                    max = data[i][j];
                }
            }
        }
        return max;
    }

    // ─────────────────────────────────────────────────────────────
    //  Порівняння матриць (верифікація паралельних результатів)
    // ─────────────────────────────────────────────────────────────

    /**
     * Перевіряє, чи є дві матриці рівними з точністю {@link #EPSILON}.
     *
     * <p>Використовується у тестах: паралельний результат порівнюється
     * з однопотоковим еталоном для підтвердження коректності алгоритму.
     *
     * @param a перша матриця
     * @param b друга матриця
     * @return true якщо всі елементи рівні з точністю EPSILON
     */
    public static boolean equals(Matrix a, Matrix b) {
        if (a.rows != b.rows || a.cols != b.cols) return false;
        double[][] aData = a.getData();
        double[][] bData = b.getData();
        for (int i = 0; i < a.rows; i++) {
            for (int j = 0; j < a.cols; j++) {
                if (Math.abs(aData[i][j] - bData[i][j]) > EPSILON) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Обчислює максимальне відхилення між відповідними елементами двох матриць.
     *
     * <p>Використовується для звітування про точність паралельного алгоритму.
     *
     * @param a перша матриця
     * @param b друга матриця
     * @return максимальне |a[i][j] - b[i][j]| серед усіх (i,j)
     */
    public static double maxDifference(Matrix a, Matrix b) {
        if (a.rows != b.rows || a.cols != b.cols) return Double.MAX_VALUE;
        double maxDiff = 0.0;
        double[][] aData = a.getData();
        double[][] bData = b.getData();
        for (int i = 0; i < a.rows; i++) {
            for (int j = 0; j < a.cols; j++) {
                double diff = Math.abs(aData[i][j] - bData[i][j]);
                if (diff > maxDiff) maxDiff = diff;
            }
        }
        return maxDiff;
    }

    // ─────────────────────────────────────────────────────────────
    //  Розподіл стовпців між потоками
    // ─────────────────────────────────────────────────────────────

    /**
     * Обчислює індекс першого стовпця, який обробляє потік з номером {@code threadIndex}.
     *
     * <p>Реалізує рівномірний розподіл H стовпців на P потоків.
     * Перші {@code (H % P)} потоки отримують на 1 стовпець більше.
     *
     * <p>Приклад: H=10, P=3 → потік 0: [0,4), потік 1: [4,7), потік 2: [7,10)
     *
     * @param totalCols   загальна кількість стовпців H
     * @param totalThr    загальна кількість потоків P
     * @param threadIndex індекс поточного потоку (0-based)
     * @return індекс першого стовпця (включно)
     */
    public static int getColStart(int totalCols, int totalThr, int threadIndex) {
        int base  = totalCols / totalThr;
        int extra = totalCols % totalThr;
        // Перші extra потоків отримують base+1 стовпців, решта — base
        return threadIndex * base + Math.min(threadIndex, extra);
    }

    /**
     * Обчислює індекс стовпця після останнього, який обробляє потік.
     * Використовується у циклах: {@code for (int j = colStart; j < colEnd; j++)}
     *
     * @param totalCols   загальна кількість стовпців H
     * @param totalThr    загальна кількість потоків P
     * @param threadIndex індекс поточного потоку (0-based)
     * @return індекс після останнього стовпця (виключно)
     */
    public static int getColEnd(int totalCols, int totalThr, int threadIndex) {
        return getColStart(totalCols, totalThr, threadIndex + 1);
    }

    // ─────────────────────────────────────────────────────────────
    //  Відлагодження / Quick-test (закоментовано для production)
    // ─────────────────────────────────────────────────────────────

    /*
     * QUICK-TEST для ручної перевірки множення матриць:
     *
     * Matrix a = new Matrix(new double[][]{{1,2},{3,4}}, "A");
     * Matrix b = new Matrix(new double[][]{{5,6},{7,8}}, "B");
     * Matrix c = MatrixUtils.multiply(a, b, "C");
     *
     * Очікуваний результат C:
     * C[0][0] = 1*5 + 2*7 = 19
     * C[0][1] = 1*6 + 2*8 = 22
     * C[1][0] = 3*5 + 4*7 = 43
     * C[1][1] = 3*6 + 4*8 = 50
     *
     * Перевірка getColStart/getColEnd при H=10, P=3:
     * Потік 0: [0, 4) — 4 стовпці  (base=3, extra=1, +1 для потоків < extra)
     * Потік 1: [4, 7) — 3 стовпці
     * Потік 2: [7, 10) — 3 стовпці
     */
}
