package org.matsim.rebalancingAnalysis.run;


import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorModule;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.contrib.drt.analysis.zonal.DrtModeZonalSystemModule;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.run.DrtControlerCreator;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpQSimComponents;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.ControlerConfigGroup;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.rebalancingAnalysis.rebalancingDemandAccessibility.AccessibilityDemandBasedRebalancingModule;
import org.matsim.vis.otfvis.OTFVisConfigGroup;

public class RunPTDrtExample {

    public static void main(String[] args) {
                Config config = ConfigUtils.loadConfig("input.xml", new MultiModeDrtConfigGroup(),
                new DvrpConfigGroup(), new OTFVisConfigGroup());
        config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
        config.controler().setLastIteration(1);
        config.controler().setWriteTripsInterval(1);
        config.controler().setWriteEventsInterval(1);
        config.qsim().setSimStarttimeInterpretation(QSimConfigGroup.StarttimeInterpretation.onlyUseStarttime);
        config.controler().setRoutingAlgorithmType( ControlerConfigGroup.RoutingAlgorithmType.FastAStarLandmarks);


        SwissRailRaptorConfigGroup configGroup = new SwissRailRaptorConfigGroup();
        configGroup.setUseIntermodalAccessEgress(true);
        configGroup.setIntermodalAccessEgressModeSelection(SwissRailRaptorConfigGroup.IntermodalAccessEgressModeSelection.RandomSelectOneModePerRoutingRequestAndDirection);

        SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet parameterSetDrt = new SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet();
        parameterSetDrt.setMode(TransportMode.drt);
        parameterSetDrt.setInitialSearchRadius(1000);
        parameterSetDrt.setMaxRadius(20000);
        parameterSetDrt.setSearchExtensionRadius(10);
        configGroup.addIntermodalAccessEgress(parameterSetDrt);


        SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet paramSetWalk = new SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet();
        paramSetWalk.setMode(TransportMode.walk);
        paramSetWalk.setInitialSearchRadius(100);
        paramSetWalk.setMaxRadius(1000);
        paramSetWalk.setSearchExtensionRadius(0.1);
        configGroup.addIntermodalAccessEgress(paramSetWalk);
        Scenario scenario = ScenarioUtils.loadScenario(config);


        config.addModule(configGroup);
        config.qsim().setEndTime(108000);


        Controler controler = DrtControlerCreator.createControler(config, false);
        controler.configureQSimComponents(DvrpQSimComponents.activateAllModes(MultiModeDrtConfigGroup.get(config)));


        controler.addOverridingModule(new SwissRailRaptorModule());

        MultiModeDrtConfigGroup multiModeDrtConfig = MultiModeDrtConfigGroup.get(config);
        for (DrtConfigGroup drtCfg : multiModeDrtConfig.getModalElements()) {
            controler.addOverridingModule(new DrtModeZonalSystemModule(drtCfg));
            controler.addOverridingQSimModule(new AccessibilityDemandBasedRebalancingModule(drtCfg));
        }

        controler.run();
    }
}
