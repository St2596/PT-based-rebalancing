package org.matsim.rebalancingAnalysis.rebalancingPTEgressBased;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.drt.optimizer.rebalancing.RebalancingStrategy;
import org.matsim.contrib.drt.optimizer.rebalancing.plusOne.LinkBasedRelocationCalculator;
import org.matsim.contrib.drt.passenger.events.DrtRequestSubmittedEvent;
import org.matsim.contrib.drt.passenger.events.DrtRequestSubmittedEventHandler;
import org.matsim.contrib.dvrp.fleet.DvrpVehicle;
import org.matsim.contrib.dvrp.optimizer.Request;
import org.matsim.contrib.dvrp.passenger.PassengerRequestRejectedEvent;
import org.matsim.contrib.dvrp.passenger.PassengerRequestRejectedEventHandler;
import org.matsim.contrib.dvrp.passenger.PassengerRequestScheduledEvent;
import org.matsim.contrib.dvrp.passenger.PassengerRequestScheduledEventHandler;
import org.matsim.core.events.MobsimScopeEventHandler;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

public class PTAccessEgressRebalancingStrategy implements RebalancingStrategy, PassengerRequestScheduledEventHandler, DrtRequestSubmittedEventHandler, PassengerRequestRejectedEventHandler, PersonDepartureEventHandler, MobsimScopeEventHandler  {
    private static final Logger log = LogManager.getLogger(PTAccessEgressRebalancingStrategy.class);
    private final Network network;
    private final LinkBasedRelocationCalculator linkBasedRelocationCalculator;
    private final List<Id<Link>> targetLinkIdList = new ArrayList<>();
    private final List<Id<Link>> targetLinkIdList2 = new ArrayList<>();
    private final List<Id<Link>> targetLinkIdList3 = new ArrayList<>();
    private final Map<Id<Request>, Id<Link>> potentialDrtTripMap = new HashMap<>();
    private final Map<Id<Request>, Id<Link>> rejectedDrtTripMap = new HashMap<>();
    private final Map<Id<Person>, Id<Link>> potentialEgressTrip = new HashMap<>();

    @Inject
    public PTAccessEgressRebalancingStrategy(Network network, LinkBasedRelocationCalculator linkBasedRelocationCalculator) {
        this.network = network;
        this.linkBasedRelocationCalculator = linkBasedRelocationCalculator;
    }

    public List<Relocation> calcRelocations(Stream<? extends DvrpVehicle> rebalancableVehicles, double time) {
        List<? extends DvrpVehicle> rebalancableVehicleList = rebalancableVehicles.collect(toList());

        final List<Id<Link>> copiedTargetLinkIdList;
        synchronized (this) {

            copiedTargetLinkIdList = new ArrayList<>(targetLinkIdList2);
            targetLinkIdList2.clear(); // clear the target map for next rebalancing cycle

            copiedTargetLinkIdList.addAll(targetLinkIdList3);
            targetLinkIdList3.clear();
        }
        final List<Link> targetLinkList = copiedTargetLinkIdList.stream()
                .map(network.getLinks()::get)
                .collect(toList());
        System.out.println(targetLinkList);
        return linkBasedRelocationCalculator.calcRelocations(targetLinkList, rebalancableVehicleList);
    }
    @Override
    public void handleEvent(DrtRequestSubmittedEvent event) {
        if (event.getMode().equals("drt")) {
            this.potentialDrtTripMap.put(event.getRequestId(), event.getFromLinkId());
            this.rejectedDrtTripMap.put(event.getRequestId(),event.getFromLinkId());
        }

    }
    @Override
    public void handleEvent(PassengerRequestScheduledEvent event) {
        if (event.getMode().equals("drt")) {
            Id<Link> linkId = potentialDrtTripMap.remove(event.getRequestId());
            synchronized(this) {
                this.targetLinkIdList.add(linkId);
            }
        }
    }
    @Override
    public void handleEvent(PassengerRequestRejectedEvent event) {
        if (event.getMode().equals("drt")) {
            potentialDrtTripMap.remove(event.getRequestId());
            potentialEgressTrip.remove(event.getPersonId());

            Id<Link> linkId = rejectedDrtTripMap.remove(event.getRequestId());
            synchronized(this) {
                this.targetLinkIdList3.add(linkId);
            }
        }
    }

    @Override
    public void handleEvent(PersonDepartureEvent event) {
        if (event.getLegMode().equals("drt") && event.getRoutingMode().equals("pt")){
            this.potentialEgressTrip.put(event.getPersonId(),event.getLinkId());
            Id<Link> linkId = potentialEgressTrip.remove(event.getPersonId());
            synchronized(this) {
                this.targetLinkIdList2.add(linkId);
            }
        }
    }


}
