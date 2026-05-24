package org.example.service;

import org.example.model.Matrix;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Синглтон-сховище матриць, що передаються між вкладками «Матриці» та «Обчислення».
 *
 * <p>Вкладка «Матриці» зберігає згенеровані або завантажені матриці сюди.
 * Вкладка «Обчислення» перевіряє це сховище перед запуском PRG:
 * якщо є відповідні матриці — використовує їх, інакше генерує випадкові.
 *
 * <h3>Приклад потоку:</h3>
 * <pre>
 *   // Вкладка «Матриці»:
 *   MatrixStore.getInstance().store(1, matrices); // ПРГ1, MB+MC+MD
 *
 *   // Вкладка «Обчислення»:
 *   if (MatrixStore.getInstance().hasMatricesFor(1, N)) {
 *       result = prg.execute(store.get("MB"), store.get("MC"), store.get("MD"));
 *   } else {
 *       result = prg.execute(); // випадкові
 *   }
 * </pre>
 */
public final class MatrixStore {

    private static final MatrixStore INSTANCE = new MatrixStore();

    /** Повертає єдиний екземпляр. */
    public static MatrixStore getInstance() { return INSTANCE; }

    private MatrixStore() {}

    // ─────────────────────────────────────────────────────────────

    /** Поточно збережені матриці: назва → Matrix. */
    private final Map<String, Matrix> matrices = new LinkedHashMap<>();

    /** Номер програми для якої збережені матриці (1, 2 або 3). */
    private int storedPrg = -1;

    /** Розмір N збережених матриць. */
    private int storedN = -1;

    // ─────────────────────────────────────────────────────────────
    //  Public API
    // ─────────────────────────────────────────────────────────────

    /**
     * Зберігає матриці для заданої програми.
     *
     * @param prgNumber номер програми (1, 2 або 3)
     * @param data      карта назва→Matrix (MB, MC, тощо)
     */
    public synchronized void store(int prgNumber, Map<String, Matrix> data) {
        matrices.clear();
        matrices.putAll(data);
        storedPrg = prgNumber;
        storedN   = data.values().stream()
                .findFirst()
                .map(m -> m.rows)
                .orElse(-1);
    }

    /**
     * Перевіряє чи є збережені матриці для заданої програми та розміру.
     *
     * @param prgNumber очікуваний номер програми
     * @param N         очікуваний розмір матриць
     * @return true якщо матриці є і відповідають параметрам
     */
    public synchronized boolean hasMatricesFor(int prgNumber, int N) {
        return storedPrg == prgNumber
                && storedN == N
                && !matrices.isEmpty();
    }

    /**
     * Повертає матрицю за назвою або null якщо не знайдено.
     *
     * @param name назва матриці (наприклад "MB")
     * @return матриця або null
     */
    public synchronized Matrix get(String name) {
        return matrices.get(name);
    }

    /**
     * Повертає незмінний вигляд сховища.
     */
    public synchronized Map<String, Matrix> getAll() {
        return Collections.unmodifiableMap(matrices);
    }

    /** Повертає номер збереженої програми або -1. */
    public synchronized int getStoredPrg() { return storedPrg; }

    /** Повертає N збережених матриць або -1. */
    public synchronized int getStoredN() { return storedN; }

    /** Очищає сховище. */
    public synchronized void clear() {
        matrices.clear();
        storedPrg = -1;
        storedN   = -1;
    }
}