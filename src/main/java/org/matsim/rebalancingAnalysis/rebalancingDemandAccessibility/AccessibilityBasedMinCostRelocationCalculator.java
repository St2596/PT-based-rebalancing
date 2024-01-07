package org.matsim.rebalancingAnalysis.rebalancingDemandAccessibility;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.common.util.DistanceUtils;
import org.matsim.contrib.drt.analysis.zonal.DrtZone;
import org.matsim.contrib.drt.analysis.zonal.DrtZoneTargetLinkSelector;
import org.matsim.contrib.drt.optimizer.rebalancing.RebalancingStrategy;
import org.matsim.contrib.dvrp.fleet.DvrpVehicle;
import org.matsim.contrib.dvrp.schedule.Schedules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class AccessibilityBasedMinCostRelocationCalculator {
    private static final Logger log = LogManager.getLogger(AccessibilityBasedMinCostRelocationCalculator.class);
    public static class DrtZoneVehicleSurplus {
        public final DrtZone zone;
        public final int surplus;

        public DrtZoneVehicleSurplus(DrtZone zone, int surplus) {
            this.zone = zone;
            this.surplus = surplus;
        }
    }
    private final DrtZoneTargetLinkSelector targetLinkSelector;

    public AccessibilityBasedMinCostRelocationCalculator(DrtZoneTargetLinkSelector targetLinkSelector) {
        this.targetLinkSelector = targetLinkSelector;
    }

    public List<RebalancingStrategy.Relocation> calcRelocations(List<AccessibilityBasedMinCostRelocationCalculator.DrtZoneVehicleSurplus> vehicleSurplus,
                                                                Map<DrtZone, List<DvrpVehicle>> rebalancableVehiclesPerZone) {

        return calcRelocations(rebalancableVehiclesPerZone, RebalancingProblemCycleCancelingAlgorithm.solveForVehicleSurplus(vehicleSurplus));
    }

    private List<RebalancingStrategy.Relocation> calcRelocations(Map<DrtZone, List<DvrpVehicle>> rebalancableVehiclesPerZone,
                                                                 List<RebalancingProblemCycleCancelingAlgorithm.Flow<DrtZone, DrtZone>> flows) {

        List<RebalancingStrategy.Relocation> relocations = new ArrayList<>();
        for (RebalancingProblemCycleCancelingAlgorithm.Flow<DrtZone, DrtZone> flow : flows) {

            List<DvrpVehicle> rebalancableVehicles = rebalancableVehiclesPerZone.get(flow.origin);

            Link targetLink = targetLinkSelector.selectTargetLink(flow.destination);
            for (int f = 0; f < flow.amount; f++) {

                DvrpVehicle nearestVehicle = findNearestVehicle(rebalancableVehicles, targetLink);

                relocations.add(new RebalancingStrategy.Relocation(nearestVehicle, targetLink));
                rebalancableVehicles.remove(nearestVehicle);
            }
        }
        return relocations;
    }

    private DvrpVehicle findNearestVehicle(List<DvrpVehicle> rebalancableVehicles, Link destinationLink) {
        Coord toCoord = destinationLink.getFromNode().getCoord();
        return rebalancableVehicles.stream()
                .min(Comparator.comparing(v -> DistanceUtils.calculateSquaredDistance(
                        Schedules.getLastLinkInSchedule(v).getToNode().getCoord(), toCoord)))
                .get();
    }
}
