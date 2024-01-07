package org.matsim.rebalancingAnalysis.rebalancingDemandAccessibility;

import org.apache.commons.lang3.tuple.Pair;
import org.matsim.contrib.common.util.DistanceUtils;
import org.matsim.contrib.drt.analysis.zonal.DrtZone;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.ToIntBiFunction;
import java.util.stream.Stream;

    public class RebalancingProblemCycleCancelingAlgorithm<P, C> {
    public static List<RebalancingProblemCycleCancelingAlgorithm.Flow<DrtZone, DrtZone>> solveForVehicleSurplus(
            List<AccessibilityBasedMinCostRelocationCalculator.DrtZoneVehicleSurplus> vehicleSurplus) {
        List<Pair<DrtZone, Integer>> supply = new ArrayList<>();
        List<Pair<DrtZone, Integer>> demand = new ArrayList<>();
        for (AccessibilityBasedMinCostRelocationCalculator.DrtZoneVehicleSurplus s : vehicleSurplus) {
            if (s.surplus > 0) {
                supply.add(Pair.of(s.zone, s.surplus));
            } else if (s.surplus < 0) {
                demand.add(Pair.of(s.zone, -s.surplus));
            }
        }

        return new RebalancingProblemCycleCancelingAlgorithm<DrtZone, DrtZone>(RebalancingProblemCycleCancelingAlgorithm::calcStraightLineDistance).solve(supply, demand);
    }

    private static int calcStraightLineDistance(DrtZone zone1, DrtZone zone2) {
        return (int) DistanceUtils.calculateDistance(zone1.getCentroid(), zone2.getCentroid());
    }

    public static class Flow<P, C> {
        public final P origin;
        public final C destination;
        public final int amount;

        private Flow(P origin, C destination, int amount) {
            this.origin = origin;
            this.destination = destination;
            this.amount = amount;
        }
    }

    private final ToIntBiFunction<P, C> costFunction;

    public RebalancingProblemCycleCancelingAlgorithm(ToIntBiFunction<P, C> costFunction) {
        this.costFunction = costFunction;
    }

    public List<RebalancingProblemCycleCancelingAlgorithm.Flow<P, C>> solve(List<Pair<P, Integer>> supply, List<Pair<C, Integer>> demand) {
        final int P = supply.size();
        final int C = demand.size();
        final int N = P + C + 2;



        @SuppressWarnings("unchecked")
        List<CycleCancelingAlgorithm.Edge>[] graph = Stream.generate(ArrayList::new).limit(N).toArray(List[]::new);

        // source -> producers
        int totalSupply = 0;
        for (int i = 0; i < P; i++) {
            int supplyValue = supply.get(i).getValue();
            CycleCancelingAlgorithm.addEdge(graph, 0, 1 + i, supplyValue, 0);
            totalSupply += supplyValue;
        }

        // producers --> consumers
        for (int i = 0; i < P; i++) {
            Pair<P, Integer> producer = supply.get(i);
            for (int j = 0; j < C; j++) {
                Pair<C, Integer> consumer = demand.get(j);
                int capacity = Math.min(producer.getValue(), consumer.getValue());
                int cost = costFunction.applyAsInt(producer.getKey(), consumer.getKey());
                CycleCancelingAlgorithm.addEdge(graph, 1 + i, 1 + P + j, capacity, cost);
            }
        }

        // consumers -> sink
        int totalDemand = 0;
        for (int j = 0; j < C; j++) {
            int demandValue = demand.get(j).getValue();
            CycleCancelingAlgorithm.addEdge(graph, 1 + P + j, N - 1, demandValue, 0);
            totalDemand += demandValue;
        }

        // solve min cost flow problem
        int[] result = CycleCancelingAlgorithm.minCostFlow(graph, 0, N - 1, Math.min(totalSupply, totalDemand));
        if (result[0] == 0) {
            return Collections.emptyList();
        }

        // extract flows
        List<RebalancingProblemCycleCancelingAlgorithm.Flow<P, C>> flows = new ArrayList<>();
        for (int i = 0; i < P; i++) {
            P from = supply.get(i).getKey();
            for (CycleCancelingAlgorithm.Edge e : graph[1 + i]) {
                int flow = e.getFlow();
                if (flow > 0) {
                    int j = e.getTo() - (1 + P);
                    C to = demand.get(j).getKey();
                    flows.add(new RebalancingProblemCycleCancelingAlgorithm.Flow<>(from, to, flow));
                }
            }
        }
        return flows;
    }
}

