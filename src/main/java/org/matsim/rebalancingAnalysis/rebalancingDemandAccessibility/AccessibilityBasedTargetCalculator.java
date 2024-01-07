package org.matsim.rebalancingAnalysis.rebalancingDemandAccessibility;

import org.matsim.contrib.drt.analysis.zonal.DrtZone;
import org.matsim.contrib.drt.optimizer.rebalancing.demandestimator.ZonalDemandEstimator;
import org.matsim.contrib.drt.optimizer.rebalancing.targetcalculator.RebalancingTargetCalculator;
import org.matsim.contrib.dvrp.fleet.DvrpVehicle;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

public class AccessibilityBasedTargetCalculator implements RebalancingTargetCalculator {

    private final ZonalDemandEstimator demandEstimator;
    private final double demandEstimationPeriod;
    private final Map<String, Double> scores;

    public AccessibilityBasedTargetCalculator(ZonalDemandEstimator demandEstimator, double demandEstimationPeriod) {
        this.demandEstimator = demandEstimator;
        this.demandEstimationPeriod = demandEstimationPeriod;
        this.scores = readValuesFromCSV("FinalZonalAccessibilityScore.csv");
    }
    private Map<String, Double> readValuesFromCSV(String filePath) {
        Map<String, Double> scoresMap = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            String DELIMITER = ",";
            while ((line = br.readLine()) != null) {
                String[] values = line.split(DELIMITER);
                scoresMap.put(values[0], Double.parseDouble(values[1]));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return scoresMap;
    }

    @Override
    public ToDoubleFunction<DrtZone> calculate(double time,
                                               Map<DrtZone, List<DvrpVehicle>> rebalancableVehiclesPerZone) {

        return zone -> {
            if (shouldIncludeZone(zone)) {
                return demandEstimator.getExpectedDemand(time, demandEstimationPeriod).applyAsDouble(zone);
            } else {
                return 0.0; // Adjust as per requirements
            }
        };
    }

    private boolean shouldIncludeZone(DrtZone zone) {
        double percentileThreshold = calculatePercentileThreshold(scores, 40); // Example percentile value
        return isKeyInTopPercentile(scores, zone.getId(), percentileThreshold);
    }

    public static double calculatePercentileThreshold(Map<String, Double> map, int percentile) {
        double[] values = map.values().stream().mapToDouble(Double::doubleValue).toArray();

        // Sort the values in ascending order
        Arrays.sort(values);

        // Calculate the index corresponding to the percentile
        int index = (int) Math.ceil(((100-percentile) / 100.0) * (values.length - 1));

        // Retrieve the value at the calculated index
        double thresholdValue = values[index];

        return thresholdValue;
    }

    public static boolean isKeyInTopPercentile(Map<String, Double> map, String key, double percentileThreshold) {
        // Get the value for the specified key
        Double value = map.get(key);

        return value != null && value >= percentileThreshold;
    }
}
