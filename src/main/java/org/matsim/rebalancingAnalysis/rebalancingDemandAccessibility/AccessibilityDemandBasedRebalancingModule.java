package org.matsim.rebalancingAnalysis.rebalancingDemandAccessibility;

import com.google.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.drt.analysis.zonal.DrtZonalSystem;
import org.matsim.contrib.drt.analysis.zonal.DrtZonalSystemParams;
import org.matsim.contrib.drt.analysis.zonal.DrtZoneTargetLinkSelector;
import org.matsim.contrib.drt.optimizer.DrtOptimizer;
import org.matsim.contrib.drt.optimizer.DrtRequestInsertionRetryQueue;
import org.matsim.contrib.drt.optimizer.depot.DepotFinder;
import org.matsim.contrib.drt.optimizer.insertion.UnplannedRequestInserter;
import org.matsim.contrib.drt.optimizer.rebalancing.RebalancingParams;
import org.matsim.contrib.drt.optimizer.rebalancing.demandestimator.PreviousIterationDRTDemandEstimator;
import org.matsim.contrib.drt.optimizer.rebalancing.demandestimator.ZonalDemandEstimator;
import org.matsim.contrib.drt.optimizer.rebalancing.targetcalculator.RebalancingTargetCalculator;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.schedule.DrtTaskFactory;
import org.matsim.contrib.drt.scheduler.DrtScheduleInquiry;
import org.matsim.contrib.drt.scheduler.EmptyVehicleRelocator;
import org.matsim.contrib.dvrp.fleet.Fleet;
import org.matsim.contrib.dvrp.run.AbstractDvrpModeQSimModule;
import org.matsim.contrib.dvrp.run.DvrpModes;
import org.matsim.contrib.dvrp.schedule.ScheduleTimingUpdater;
import org.matsim.core.mobsim.framework.MobsimTimer;
import org.matsim.core.modal.ModalProviders;
import org.matsim.core.router.costcalculators.TravelDisutilityFactory;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;

public class AccessibilityDemandBasedRebalancingModule extends AbstractDvrpModeQSimModule {
    private static final Logger log = LogManager.getLogger(AccessibilityDemandBasedRebalancingModule.class);
    private final DrtConfigGroup drtCfg;

    public AccessibilityDemandBasedRebalancingModule(DrtConfigGroup drtCfg) {
//        super();
        super(drtCfg.getMode());
        this.drtCfg = drtCfg;

    }

    @Override
    protected void configureQSim() {
        DrtZonalSystemParams params = drtCfg.getZonalSystemParams().orElseThrow();
        addModalComponent(DrtOptimizer.class, modalProvider(
                getter -> new PTAccessibilityDemandBasedDrtOptimizer(drtCfg, getter.getModal(Fleet.class), getter.get(MobsimTimer.class),
                        getter.getModal(DepotFinder.class), getter.getModal(PastDemandAndAccessibilityBasedRebalancing.class),
                        getter.getModal(DrtScheduleInquiry.class), getter.getModal(ScheduleTimingUpdater.class),
                        getter.getModal(EmptyVehicleRelocator.class), getter.getModal(UnplannedRequestInserter.class),
                        getter.getModal(DrtRequestInsertionRetryQueue.class))));

        bindModal(PastDemandAndAccessibilityBasedRebalancing.class).toProvider(
                new ModalProviders.AbstractProvider<>(drtCfg.getMode(), DvrpModes::mode) {
                    @Inject
                    private MobsimTimer timer;

                    @Override
                    public PastDemandAndAccessibilityBasedRebalancing get() {

                        RebalancingTargetCalculator rebalancingTargetCalculator = getModalInstance(RebalancingTargetCalculator.class);
                        DrtZonalSystem zonalSystem = getModalInstance(DrtZonalSystem.class);
                        Fleet fleet = getModalInstance(Fleet.class);
                        AccessibilityBasedMinCostRelocationCalculator relocationCalculator = getModalInstance(AccessibilityBasedMinCostRelocationCalculator.class);
                        RebalancingParams params = getModalInstance(RebalancingParams.class);
                        return new PastDemandAndAccessibilityBasedRebalancing(rebalancingTargetCalculator,
                                zonalSystem, fleet, relocationCalculator,
                                 params);
                    }}).asEagerSingleton();

        bindModal(EmptyVehicleRelocator.class).toProvider(
                new ModalProviders.AbstractProvider<>(drtCfg.getMode(), DvrpModes::mode) {
                    @Inject
                    private MobsimTimer timer;

                    @Override
                    public EmptyVehicleRelocator get() {
                        var travelTime = getModalInstance(TravelTime.class);
                        Network network = getModalInstance(Network.class);
                        DrtTaskFactory taskFactory = getModalInstance(DrtTaskFactory.class);
                        TravelDisutility travelDisutility = getModalInstance(
                                TravelDisutilityFactory.class).createTravelDisutility(travelTime);
                        return new EmptyVehicleRelocator(network, travelTime, travelDisutility, timer, taskFactory);
                    }
                }).asEagerSingleton();

        bindModal(AccessibilityBasedMinCostRelocationCalculator.class).toProvider(modalProvider(
                getter -> new AccessibilityBasedMinCostRelocationCalculator(
                        getter.getModal(DrtZoneTargetLinkSelector.class)))).asEagerSingleton();
        bindModal(RebalancingTargetCalculator.class).toProvider(modalProvider(
                getter -> new AccessibilityBasedTargetCalculator(
                        getter.getModal(ZonalDemandEstimator.class),
                        1800))).asEagerSingleton();
        bindModal(ZonalDemandEstimator.class).to(modalKey(PreviousIterationDRTDemandEstimator.class));

        bindModal(RebalancingParams.class).to(RebalancingParams.class);

    }
}
