package org.matsim.rebalancingAnalysis.prepare;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;

public class PrepareTestingPlan {
    public static void main(String[] args) {
        Config config = ConfigUtils.createConfig();
        Population plans = PopulationUtils.createPopulation(config);
        PopulationFactory populationFactory = plans.getFactory();

        Person person = populationFactory.createPerson(Id.createPersonId("0"));
        Plan plan = populationFactory.createPlan();
        Activity fromAct = populationFactory.createActivityFromLinkId("dummy", Id.createLinkId("1"));
        fromAct.setEndTime(36700);
        Activity toAct = populationFactory.createActivityFromLinkId("dummy", Id.createLinkId("2"));
        Leg leg = populationFactory.createLeg(TransportMode.drt);
        plan.addActivity(fromAct);
        plan.addLeg(leg);
        plan.addActivity(toAct);
        person.addPlan(plan);
        plans.addPerson(person);

        PopulationWriter populationWriter = new PopulationWriter(plans);
        populationWriter.write(args[0]);
    }

}
