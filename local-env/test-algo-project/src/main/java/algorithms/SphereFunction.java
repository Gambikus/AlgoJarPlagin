package algorithms;

/**
 * Функция Sphere: f(x) = sum(xi^2)
 * Глобальный минимум: f(0,...,0) = 0
 */
public class SphereFunction implements ObjectiveFunction {

    @Override
    public double evaluate(double[] position) {
        double sum = 0;
        for (double x : position) {
            sum += x * x;
        }
        return sum;
    }

    @Override
    public double[] bounds() {
        return new double[]{-5.12, 5.12};
    }
}
