package org.matsim.rebalancingAnalysis.rebalancingDemandAccessibility;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.*;
import org.matsim.contrib.common.util.DistanceUtils;
import org.matsim.contrib.drt.analysis.zonal.DrtZonalSystem;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.router.LinkWrapperFacility;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.rebalancingAnalysis.prepare.travelTimeCalculator.TravelTimeByCarCalculator;
import org.matsim.rebalancingAnalysis.prepare.travelTimeCalculator.TravelTimeByPTCalculator;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.matsim.contrib.drt.analysis.zonal.DrtGridUtils.createGridFromNetwork;

public class EstimateZonalAccessibilityScores {

    public static void main(String[] args) {
        Config config = ConfigUtils.loadConfig("input.xml");
        Scenario scenario = ScenarioUtils.loadScenario(config);
        Network network = scenario.getNetwork();
        List<TravelData> travelDataList = extractTravelData(scenario.getPopulation());

        Map<String, PreparedGeometry> geo = createGridFromNetwork(network, 2500.);
        DrtZonalSystem zonalSystem = DrtZonalSystem.createFromPreparedGeometries(network, geo);

        Map<String, Double> finalScores = calculateFinalScores(travelDataList, network, zonalSystem);
        writeScoresToFile(finalScores, "FinalZonalAccessibilityScore.csv");
    }
    private static Map<String, Double> calculateFinalScores(List<TravelData> travelDataList, Network network, DrtZonalSystem zonalSystem) {
        TravelTimeByPTCalculator travelTimeByPTCalculator = new TravelTimeByPTCalculator();
        TravelTimeByCarCalculator travelTimeByCarCalculator = new TravelTimeByCarCalculator();

        List<String> zoneIds = new ArrayList<>();
        List<Double> scoreAndDistance = new ArrayList<>();
        List<Double> euclideanDistances = new ArrayList<>();

        for (TravelData data : travelDataList) {
            double euclideanDistance = DistanceUtils.calculateDistance(data.getFromCoord(), data.getToCoord());
            double travelTime_Car = travelTimeByCarCalculator.getTravelTimeByCar(data.getFromCoord(), data.getToCoord(), data.getEndTime());
            double travelTime_PT = travelTimeByPTCalculator.findbestPTrouteByLegs(data.getFromCoord(), data.getToCoord(), data.getEndTime());
            String zoneId = zonalSystem.getZoneForLinkId((new LinkWrapperFacility(NetworkUtils.getNearestLink(network, data.getFromCoord()))).getLinkId()).getId();
            double euclideanTime = euclideanDistance / 8.83;
            double score = (travelTime_PT == 0) ? 1.0 : travelTime_PT / (Math.max(travelTime_Car, euclideanTime) + 300);

            zoneIds.add(zoneId);
            scoreAndDistance.add(score * euclideanDistance);
            euclideanDistances.add(euclideanDistance);
        }

        return aggregateScores(zoneIds, scoreAndDistance, euclideanDistances);
    }

    private static Map<String, Double> aggregateScores(List<String> zoneIds, List<Double> scores, List<Double> distances) {
        Map<String, Double> scoreSum = new HashMap<>();
        Map<String, Double> distanceSum = new HashMap<>();

        for (int i = 0; i < zoneIds.size(); i++) {
            String id = zoneIds.get(i);
            double normalizedScore = scores.get(i) / distances.get(i);
            scoreSum.put(id, scoreSum.getOrDefault(id, 0.0) + normalizedScore * distances.get(i));
            distanceSum.put(id, distanceSum.getOrDefault(id, 0.0) + distances.get(i));
        }

        Map<String, Double> finalScores = new HashMap<>();
        for (String id : scoreSum.keySet()) {
            double sumScore = scoreSum.get(id);
            double sumDistance = distanceSum.get(id);
            finalScores.put(id, sumScore / sumDistance);
        }
        return finalScores;
    }
    private static List<TravelData> extractTravelData(Population population) {
        return population.getPersons().values().stream()
                .map(Person::getSelectedPlan)
                .flatMap(plan -> plan.getPlanElements().stream()
                        .filter(element -> element instanceof Leg)
                        .map(element -> createTravelData(plan, element)))
                .collect(Collectors.toList());
    }
    private static TravelData createTravelData(Plan plan, PlanElement element) {
        int elementIndex = plan.getPlanElements().indexOf(element);
        Activity fromActivity = (Activity) plan.getPlanElements().get(elementIndex - 1);
        Activity toActivity = (Activity) plan.getPlanElements().get(elementIndex + 1);
        Leg leg = (Leg) element;

        Coord fromCoord = fromActivity.getCoord();
        Coord toCoord = toActivity.getCoord();
        double endTime = fromActivity.getEndTime().seconds();
        String mode = leg.getMode();

        return new TravelData(fromCoord, toCoord, endTime, mode);
    }
    private static void writeScoresToFile(Map<String, Double> scores, String filePath) {
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(filePath));
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader("Zone ID", "Weighted Score"))) {
            for (Map.Entry<String, Double> entry : scores.entrySet()) {
                printer.printRecord(entry.getKey(), entry.getValue());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    static class TravelData {
        Coord fromCoord;
        Coord toCoord;
        double endTime;
        String mode;

        TravelData(Coord fromCoord, Coord toCoord, double endTime, String mode) {
            this.fromCoord = fromCoord;
            this.toCoord = toCoord;
            this.endTime = endTime;
            this.mode = mode;
        }

        public Coord getFromCoord() {
            return fromCoord;
        }

        public Coord getToCoord() {
            return toCoord;
        }

        public double getEndTime() {
            return endTime;
        }

        public String getMode() {
            return mode;
        }
    }
}

