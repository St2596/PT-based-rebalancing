package org.matsim.rebalancingAnalysis.prepare;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.*;
import org.matsim.application.analysis.DefaultAnalysisMainModeIdentifier;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.router.MainModeIdentifier;
import org.matsim.core.router.TripStructureUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class DrtRequestsExtractor {

   // Generate drt plans from population file of the open scenario
    private final String inputPlansPath = "path/to/input/plans.xml";
    private final String networkPath = "path/to/network.xml";
    private final String outputNetworkPath = "path/to/output/network.xml"; // can be an empty string
    private final String outputPath = "path/to/output.xml";
    private final String modes = "drt";
    private final String conversionRatesInput = "1.0";
    private final double startingTime = 0;
    private final double endingTime = 86400;

    private final Random random = new Random(1234);


    public static void main(String[] args) {
        new DrtRequestsExtractor().generateDrtRequests();
    }


    public void generateDrtRequests() {
        String[] modesToConvert = modes.split(",");
        String[] conversionRates = conversionRatesInput.split(",");
        if (modesToConvert.length != conversionRates.length) {
            throw new RuntimeException("The length of modes and conversion rates are not the same!");
        }

        Map<String, Double> modeConversionMap = new HashMap<>();
        for (int i = 0; i < modesToConvert.length; i++) {
            modeConversionMap.put(modesToConvert[i], Double.parseDouble(conversionRates[i]));
        }

        Population inputPlans = PopulationUtils.readPopulation(inputPlansPath);
        Network network = NetworkUtils.readNetwork(networkPath);
        Network outputNetwork = outputNetworkPath.isEmpty() ? network : NetworkUtils.readNetwork(outputNetworkPath);


        PopulationFactory populationFactory = inputPlans.getFactory();
        Population outputPopulation = PopulationUtils.createPopulation(ConfigUtils.createConfig());
        // Filter out long or non-car links
        outputNetwork.getLinks().values().removeIf(link -> link.getLength() >= 1000 || !link.getAllowedModes().contains(TransportMode.car));

        // Filter out isolated nodes
        outputNetwork.getNodes().values().removeIf(node -> node.getOutLinks().isEmpty() && node.getInLinks().isEmpty());

        MainModeIdentifier mainModeIdentifier = new DefaultAnalysisMainModeIdentifier();
        int counter = 0;
        for (Person person : inputPlans.getPersons().values()) {
            for (TripStructureUtils.Trip trip : TripStructureUtils.getTrips(person.getSelectedPlan())) {
                String mode = mainModeIdentifier.identifyMainMode(trip.getTripElements());
                if (modeConversionMap.containsKey(mode) && random.nextDouble() <= modeConversionMap.get(mode)) {
                    if (trip.getOriginActivity().getEndTime().orElse(-1) >= startingTime && trip.getOriginActivity().getEndTime().orElse(-1) <= endingTime) {
                        createDrtTripFromExistingTrip(trip, network, outputNetwork, populationFactory, outputPopulation, counter++);
                    }
                }
            }
        }

        System.out.println("There are " + counter + " DRT trips.");

        PopulationWriter populationWriter = new PopulationWriter(outputPopulation);
        populationWriter.write(outputPath.toString());
    }

    private void createDrtTripFromExistingTrip(TripStructureUtils.Trip trip, Network network, Network outputNetwork, PopulationFactory populationFactory, Population outputPopulation, int counter) {
        Coord fromCoord = getCoordFromActivity(trip.getOriginActivity(), network);
        Coord toCoord = getCoordFromActivity(trip.getDestinationActivity(), network);

        if (fromCoord == null || toCoord == null) return;

        Person dummyPerson = populationFactory.createPerson(Id.createPersonId("drt_person_" + counter));
        Plan plan = populationFactory.createPlan();
        Activity fromAct = populationFactory.createActivityFromCoord("dummy", fromCoord);
        fromAct.setEndTime(trip.getOriginActivity().getEndTime().orElse(-1));
        Leg leg = populationFactory.createLeg(TransportMode.drt);
        Activity toAct = populationFactory.createActivityFromCoord("dummy", toCoord);

        plan.addActivity(fromAct);
        plan.addLeg(leg);
        plan.addActivity(toAct);
        dummyPerson.addPlan(plan);
        outputPopulation.addPerson(dummyPerson);
    }

    private Coord getCoordFromActivity(Activity activity, Network network) {
        if (activity.getCoord() != null) {
            return activity.getCoord();
        } else {
            Link link = network.getLinks().get(activity.getLinkId());
            return link != null ? link.getToNode().getCoord() : null;
        }
    }
}

