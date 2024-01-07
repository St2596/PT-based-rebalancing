package org.matsim.rebalancingAnalysis.rebalancingDemandAccessibility;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.contrib.drt.analysis.zonal.DrtZonalSystem;
import org.matsim.contrib.drt.analysis.zonal.DrtZone;
import org.matsim.contrib.drt.optimizer.rebalancing.RebalancingParams;
import org.matsim.contrib.drt.optimizer.rebalancing.RebalancingStrategy;
import org.matsim.contrib.drt.optimizer.rebalancing.RebalancingUtils;
import org.matsim.contrib.drt.optimizer.rebalancing.targetcalculator.RebalancingTargetCalculator;
import org.matsim.contrib.dvrp.fleet.DvrpVehicle;
import org.matsim.contrib.dvrp.fleet.Fleet;

import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

public class PastDemandAndAccessibilityBasedRebalancing implements RebalancingStrategy {
    private static final Logger log = LogManager.getLogger(PastDemandAndAccessibilityBasedRebalancing.class);


    private final RebalancingTargetCalculator rebalancingTargetCalculator;
    private final DrtZonalSystem zonalSystem;
    private final Fleet fleet;
    private final AccessibilityBasedMinCostRelocationCalculator relocationCalculator;
    private final RebalancingParams params;


    public PastDemandAndAccessibilityBasedRebalancing(RebalancingTargetCalculator rebalancingTargetCalculator,
                                                      DrtZonalSystem zonalSystem, Fleet fleet, AccessibilityBasedMinCostRelocationCalculator relocationCalculator,
                                                      RebalancingParams params) {
        this.rebalancingTargetCalculator = rebalancingTargetCalculator;
        this.zonalSystem = zonalSystem;
        this.fleet = fleet;
        this.relocationCalculator = relocationCalculator;
        this.params = params;
    }

    @Override
    public List<Relocation> calcRelocations(Stream<? extends DvrpVehicle> rebalancableVehicles, double time) {
        Map<DrtZone, List<DvrpVehicle>> rebalancableVehiclesPerZone = RebalancingUtils.groupRebalancableVehicles(
                zonalSystem, params, rebalancableVehicles, time);

        if (rebalancableVehiclesPerZone.isEmpty()) {
            return List.of();
        }
        Map<DrtZone, List<DvrpVehicle>> soonIdleVehiclesPerZone = RebalancingUtils.groupSoonIdleVehicles(zonalSystem,
                params, fleet, time);
        return calculateAccessibilityRelocations(time, rebalancableVehiclesPerZone, soonIdleVehiclesPerZone);
    }
    private List<Relocation> calculateAccessibilityRelocations(double time,
                                                         Map<DrtZone, List<DvrpVehicle>> rebalancableVehiclesPerZone,
                                                         Map<DrtZone, List<DvrpVehicle>> soonIdleVehiclesPerZone) {

        ToDoubleFunction<DrtZone> targetFunction = rebalancingTargetCalculator.calculate(time,
                rebalancableVehiclesPerZone);

        double alpha = 0.5;
        double beta = 0.5;
        List<AccessibilityBasedMinCostRelocationCalculator.DrtZoneVehicleSurplus> vehicleSurpluses = zonalSystem.getZones().values().stream().map(z -> {
            int rebalancable = rebalancableVehiclesPerZone.getOrDefault(z, List.of()).size();
            int soonIdle = soonIdleVehiclesPerZone.getOrDefault(z, List.of()).size();
            int target = (int)Math.floor(alpha * targetFunction.applyAsDouble(z) + beta);
            int surplus = Math.min(rebalancable + soonIdle - target, rebalancable);

            return new AccessibilityBasedMinCostRelocationCalculator.DrtZoneVehicleSurplus(z, surplus);
        }).collect(toList());

        return relocationCalculator.calcRelocations(vehicleSurpluses, rebalancableVehiclesPerZone);
    }
}
