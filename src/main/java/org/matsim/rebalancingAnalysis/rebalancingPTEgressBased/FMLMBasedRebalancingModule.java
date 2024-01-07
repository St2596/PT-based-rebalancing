package org.matsim.rebalancingAnalysis.rebalancingPTEgressBased;

import com.google.inject.Inject;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.drt.optimizer.DrtOptimizer;
import org.matsim.contrib.drt.optimizer.DrtRequestInsertionRetryQueue;
import org.matsim.contrib.drt.optimizer.depot.DepotFinder;
import org.matsim.contrib.drt.optimizer.insertion.UnplannedRequestInserter;
import org.matsim.contrib.drt.optimizer.rebalancing.plusOne.FastHeuristicLinkBasedRelocationCalculator;
import org.matsim.contrib.drt.optimizer.rebalancing.plusOne.LinkBasedRelocationCalculator;
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

public class FMLMBasedRebalancingModule extends AbstractDvrpModeQSimModule {
    private final DrtConfigGroup drtCfg;
    public FMLMBasedRebalancingModule(DrtConfigGroup drtCfg) {
        super(drtCfg.getMode());
        this.drtCfg = drtCfg;
    }

    @Override
    protected void configureQSim() {
        addModalComponent(DrtOptimizer.class, modalProvider(
                getter -> new PTBasedDrtOptimizer(drtCfg, getter.getModal(Fleet.class), getter.get(MobsimTimer.class),
                        getter.getModal(DepotFinder.class), getter.getModal(PTAccessEgressRebalancingStrategy.class),
                        getter.getModal(DrtScheduleInquiry.class), getter.getModal(ScheduleTimingUpdater.class),
                        getter.getModal(EmptyVehicleRelocator.class), getter.getModal(UnplannedRequestInserter.class),
                        getter.getModal(DrtRequestInsertionRetryQueue.class))));

        bindModal(PTAccessEgressRebalancingStrategy.class).toProvider(
                new ModalProviders.AbstractProvider<>(drtCfg.getMode(), DvrpModes::mode) {
                    @Inject
                    private MobsimTimer timer;

                    @Override
                    public PTAccessEgressRebalancingStrategy get() {
                        Network network = getModalInstance(Network.class);
                        LinkBasedRelocationCalculator linkRolocationCalculator = getModalInstance(LinkBasedRelocationCalculator.class);
                        return new PTAccessEgressRebalancingStrategy(network, linkRolocationCalculator);
                    }}).asEagerSingleton();

        bindModal(LinkBasedRelocationCalculator.class).to(FastHeuristicLinkBasedRelocationCalculator.class);
        addMobsimScopeEventHandlerBinding().to(modalKey(PTAccessEgressRebalancingStrategy.class));


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
    }

}
