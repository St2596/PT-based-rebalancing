package org.matsim.rebalancingAnalysis.prepare.travelTimeCalculator;

import ch.sbb.matsim.routing.pt.raptor.RaptorUtils;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptor;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorData;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.router.DefaultRoutingRequest;
import org.matsim.core.router.LinkWrapperFacility;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.router.TransitRouter;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

import java.util.List;

public class TravelTimeByPTCalculator {
    Config config = ConfigUtils.loadConfig("input.xml");
    Scenario scenario = ScenarioUtils.loadScenario(config);
    Network network = scenario.getNetwork();
    TransitSchedule schedule = scenario.getTransitSchedule();
    TransitRouter router = createTransitRouter(schedule, config, network);
    SwissRailRaptor raptor = (SwissRailRaptor) router;

    private static SwissRailRaptor createTransitRouter (TransitSchedule schedule, Config config, Network network){
        SwissRailRaptorData data = SwissRailRaptorData.create(schedule, null, RaptorUtils.createStaticConfig(config), network, null);
        SwissRailRaptor raptor = new SwissRailRaptor.Builder(data, config).build();
        return raptor;
    }
    public double findbestPTrouteByLegs(Coord pickup, Coord dropOff, double time){
        List<? extends PlanElement> legs = router.calcRoute(DefaultRoutingRequest.withoutAttributes(new LinkWrapperFacility(NetworkUtils.getNearestLink(network,pickup)), new LinkWrapperFacility(NetworkUtils.getNearestLink(network,dropOff)), time, null));
        if(legs != null) {
            Leg firstLeg = (Leg) legs.get(0);
            double departureTime = firstLeg.getDepartureTime().orElse(0);
            Leg arrivalLeg = (Leg) legs.get(legs.size() - 1);
            double arrivalTime = (arrivalLeg.getDepartureTime().seconds() + arrivalLeg.getTravelTime().seconds());
            double travelTime = arrivalTime - departureTime;
            return travelTime;
        }else{
            return 0;
        }
    }
}
