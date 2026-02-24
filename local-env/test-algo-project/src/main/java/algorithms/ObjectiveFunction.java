package algorithms;

public interface ObjectiveFunction {

    /** Вычислить значение функции в точке position. Минимизируем. */
    double evaluate(double[] position);

    /** Допустимая область: [bounds()[0], bounds()[1]] по каждой оси. */
    double[] bounds();
}
