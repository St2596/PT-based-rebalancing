package org.matsim.rebalancingAnalysis.prepare.travelTimeCalculator;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.dvrp.path.VrpPathWithTravelData;
import org.matsim.contrib.dvrp.path.VrpPaths;
import org.matsim.contrib.dvrp.router.TimeAsTravelDisutility;
import org.matsim.contrib.dvrp.trafficmonitoring.QSimFreeSpeedTravelTime;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutilityFactory;
import org.matsim.core.router.speedy.SpeedyALTFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.trafficmonitoring.FreeSpeedTravelTime;

public class TravelTimeByCarCalculator {
    Config config = ConfigUtils.loadConfig("input.xml");
    Scenario scenario = ScenarioUtils.loadScenario(config);
    Network network = scenario.getNetwork();
    TravelTime travelTime = new QSimFreeSpeedTravelTime(1);

    TravelDisutility travelDisutility = new TimeAsTravelDisutility(travelTime);
    LeastCostPathCalculator router = new SpeedyALTFactory().createPathCalculator(network, travelDisutility, travelTime);

    public double getTravelTimeByCar(Coord fromCoord, Coord toCoord, double time) {
        Link fromLink = NetworkUtils.getNearestLink(network, fromCoord);
        Link toLink = NetworkUtils.getNearestLink(network, toCoord); ;

        VrpPathWithTravelData vrpRoute = VrpPaths.calcAndCreatePath(fromLink, toLink, time, router, travelTime);
        if(vrpRoute == null){
            return 0;
        }else {
            double travelTimeByCar = vrpRoute.getTravelTime();
            return travelTimeByCar;
        }
    }
}
