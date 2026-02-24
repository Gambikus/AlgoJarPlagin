package algorithms;

import java.util.Random;

/**
 * Простой алгоритм случайного поиска с сужающейся окрестностью.
 *
 * На каждой итерации каждый агент генерирует кандидата рядом с текущим
 * лучшим решением (радиус поиска уменьшается по мере роста итераций).
 * Если кандидат лучше — запоминаем его.
 */
public class RandomSearch {

    private final ObjectiveFunction function;
    private final int dim;
    private final int agents;
    private final Random random = new Random(42);

    public RandomSearch(ObjectiveFunction function, int dim, int agents) {
        this.function = function;
        this.dim      = dim;
        this.agents   = agents;
    }

    public double[] optimize(int iterations) {
        double[] bounds = function.bounds();
        double lo    = bounds[0];
        double hi    = bounds[1];
        double range = hi - lo;

        // Инициализация: случайная популяция
        double[][] population = new double[agents][dim];
        for (int i = 0; i < agents; i++) {
            for (int j = 0; j < dim; j++) {
                population[i][j] = lo + random.nextDouble() * range;
            }
        }

        // Начальный лучший
        double[] bestPos = population[0].clone();
        double   bestFit = function.evaluate(bestPos);
        for (int i = 1; i < agents; i++) {
            double fit = function.evaluate(population[i]);
            if (fit < bestFit) {
                bestFit = fit;
                bestPos = population[i].clone();
            }
        }

        // Основной цикл
        for (int iter = 0; iter < iterations; iter++) {
            // Радиус сужается от 10% до 0.1% диапазона
            double t      = (double) iter / iterations;
            double radius = range * (0.10 - 0.099 * t);

            for (int i = 0; i < agents; i++) {
                double[] candidate = new double[dim];
                for (int j = 0; j < dim; j++) {
                    double delta = (random.nextDouble() * 2 - 1) * radius;
                    candidate[j] = Math.max(lo, Math.min(hi, bestPos[j] + delta));
                }
                double fit = function.evaluate(candidate);
                if (fit < bestFit) {
                    bestFit = fit;
                    bestPos = candidate.clone();
                }
            }
        }

        return bestPos;
    }
}
