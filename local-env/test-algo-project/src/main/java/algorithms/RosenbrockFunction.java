package algorithms;

/**
 * Функция Rosenbrock: f(x) = sum(100*(x[i+1] - x[i]^2)^2 + (1 - x[i])^2)
 * Глобальный минимум: f(1,...,1) = 0
 */
public class RosenbrockFunction implements ObjectiveFunction {

    @Override
    public double evaluate(double[] position) {
        double sum = 0;
        for (int i = 0; i < position.length - 1; i++) {
            double xi  = position[i];
            double xi1 = position[i + 1];
            sum += 100.0 * Math.pow(xi1 - xi * xi, 2) + Math.pow(1.0 - xi, 2);
        }
        return sum;
    }

    @Override
    public double[] bounds() {
        return new double[]{-2.048, 2.048};
    }
}
