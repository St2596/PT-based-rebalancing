package org.matsim.rebalancingAnalysis.rebalancingPTEgressBased;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.drt.optimizer.DrtOptimizer;
import org.matsim.contrib.drt.optimizer.DrtRequestInsertionRetryQueue;
import org.matsim.contrib.drt.optimizer.depot.DepotFinder;
import org.matsim.contrib.drt.optimizer.depot.Depots;
import org.matsim.contrib.drt.optimizer.insertion.UnplannedRequestInserter;
import org.matsim.contrib.drt.optimizer.rebalancing.RebalancingStrategy;
import org.matsim.contrib.drt.passenger.DrtRequest;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.schedule.DrtStayTask;
import org.matsim.contrib.drt.scheduler.DrtScheduleInquiry;
import org.matsim.contrib.drt.scheduler.EmptyVehicleRelocator;
import org.matsim.contrib.dvrp.fleet.DvrpVehicle;
import org.matsim.contrib.dvrp.fleet.Fleet;
import org.matsim.contrib.dvrp.optimizer.Request;
import org.matsim.contrib.dvrp.passenger.RequestQueue;
import org.matsim.contrib.dvrp.schedule.ScheduleTimingUpdater;
import org.matsim.core.mobsim.framework.MobsimTimer;
import org.matsim.core.mobsim.framework.events.MobsimBeforeSimStepEvent;

import java.util.List;
import java.util.stream.Stream;

public class PTBasedDrtOptimizer implements DrtOptimizer {
    private static final Logger log = LogManager.getLogger(PTBasedDrtOptimizer.class);

    private final DrtConfigGroup drtCfg;
    private final Integer rebalancingInterval;
    private final Fleet fleet;
    private final DrtScheduleInquiry scheduleInquiry;
    private final ScheduleTimingUpdater scheduleTimingUpdater;
    private final PTAccessEgressRebalancingStrategy rebalancingStrategy;

    private final MobsimTimer mobsimTimer;
    private final DepotFinder depotFinder;
    private final EmptyVehicleRelocator relocator;

    private final UnplannedRequestInserter requestInserter;
    private final DrtRequestInsertionRetryQueue insertionRetryQueue;

    private final RequestQueue<DrtRequest> unplannedRequests;

    public PTBasedDrtOptimizer(DrtConfigGroup drtCfg, Fleet fleet, MobsimTimer mobsimTimer, DepotFinder depotFinder,
                               PTAccessEgressRebalancingStrategy rebalancingStrategy, DrtScheduleInquiry scheduleInquiry,
                               ScheduleTimingUpdater scheduleTimingUpdater, EmptyVehicleRelocator relocator,
                               UnplannedRequestInserter requestInserter, DrtRequestInsertionRetryQueue insertionRetryQueue) {
        this.drtCfg = drtCfg;
        this.fleet = fleet;
        this.mobsimTimer = mobsimTimer;
        this.depotFinder = depotFinder;
        this.rebalancingStrategy = rebalancingStrategy;
        this.scheduleInquiry = scheduleInquiry;
        this.scheduleTimingUpdater = scheduleTimingUpdater;
        this.relocator = relocator;
        this.requestInserter = requestInserter;
        this.insertionRetryQueue = insertionRetryQueue;

        rebalancingInterval = 1800;
        unplannedRequests = RequestQueue.withLimitedAdvanceRequestPlanningHorizon(
                drtCfg.advanceRequestPlanningHorizon);
    }



    @Override
    public void notifyMobsimBeforeSimStep(@SuppressWarnings("rawtypes") MobsimBeforeSimStepEvent e) {
        unplannedRequests.updateQueuesOnNextTimeSteps(e.getSimulationTime());

        if (!unplannedRequests.getSchedulableRequests().isEmpty() || insertionRetryQueue.hasRequestsToRetryNow(
                e.getSimulationTime())) {
            for (DvrpVehicle v : fleet.getVehicles().values()) {
                scheduleTimingUpdater.updateTimings(v);
            }

            requestInserter.scheduleUnplannedRequests(unplannedRequests.getSchedulableRequests());
        }

        if (rebalancingInterval != null && e.getSimulationTime() % rebalancingInterval == 0) {
            rebalanceFleet();
        }
    }

    private void rebalanceFleet() {
        // right now we relocate only idle vehicles (vehicles that are being relocated cannot be relocated)
        Stream<? extends DvrpVehicle> rebalancableVehicles = fleet.getVehicles()
                .values()
                .stream()
                .filter(scheduleInquiry::isIdle);
        List<RebalancingStrategy.Relocation> relocations = rebalancingStrategy.calcRelocations(rebalancableVehicles,
                mobsimTimer.getTimeOfDay());
        System.out.println(relocations);
        if (!relocations.isEmpty()) {
            for (RebalancingStrategy.Relocation r : relocations) {
                Link currentLink = ((DrtStayTask)r.vehicle.getSchedule().getCurrentTask()).getLink();
                if (currentLink != r.link) {
                    relocator.relocateVehicle(r.vehicle, r.link);
                }
            }
        }
    }

    @Override
    public void requestSubmitted(Request request) {
        unplannedRequests.addRequest((DrtRequest)request);
    }

    @Override
    public void nextTask(DvrpVehicle vehicle) {
        scheduleTimingUpdater.updateBeforeNextTask(vehicle);

        vehicle.getSchedule().nextTask();

        // if STOP->STAY then choose the best depot
        if (drtCfg.idleVehiclesReturnToDepots && Depots.isSwitchingFromStopToStay(vehicle)) {
            Link depotLink = depotFinder.findDepot(vehicle);
            if (depotLink != null) {
                relocator.relocateVehicle(vehicle, depotLink);
            }
        }
    }
}