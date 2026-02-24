package algorithms;

import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Entry point. Accepts CLI args from SPOT node:
 *   --algorithm sphere --iter 100 --agents 25 --dim 2
 *
 * Prints JSON result to stdout:
 *   {"iter":100,"fopt":0.000012,"bestPos":[0.003,-0.001]}
 */
public class Main {

    public static void main(String[] args) {
        Map<String, String> params = parseArgs(args);

        String algorithmName = params.getOrDefault("algorithm", "sphere");
        int iter             = Integer.parseInt(params.getOrDefault("iter",   "100"));
        int agents           = Integer.parseInt(params.getOrDefault("agents", "10"));
        int dim              = Integer.parseInt(params.getOrDefault("dim",    "2"));

        System.err.printf("[INFO] algorithm=%s iter=%d agents=%d dim=%d%n",
                algorithmName, iter, agents, dim);

        ObjectiveFunction function = createFunction(algorithmName);
        RandomSearch search = new RandomSearch(function, dim, agents);

        double[] bestPos = search.optimize(iter);
        double fopt = function.evaluate(bestPos);

        System.out.println(formatResult(iter, fopt, bestPos));
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> params = new HashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) {
            String key = args[i].startsWith("--") ? args[i].substring(2) : args[i];
            params.put(key, args[i + 1]);
        }
        return params;
    }

    private static ObjectiveFunction createFunction(String name) {
        switch (name.toLowerCase()) {
            case "sphere":     return new SphereFunction();
            case "rosenbrock": return new RosenbrockFunction();
            default:
                System.err.println("[WARN] Unknown algorithm: " + name + ", falling back to sphere");
                return new SphereFunction();
        }
    }

    private static String formatResult(int iter, double fopt, double[] bestPos) {
        StringJoiner posJoiner = new StringJoiner(",", "[", "]");
        for (double v : bestPos) {
            posJoiner.add(String.format("%.6f", v));
        }
        return String.format("{\"iter\":%d,\"fopt\":%.6f,\"bestPos\":%s}", iter, fopt, posJoiner);
    }
}
