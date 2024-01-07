package org.matsim.rebalancingAnalysis.prepare;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.Collections;

public class RemoveUnusedPlans {
    public static void main(String[] args) {
        int counter = 0;
        Config config;
        config = ConfigUtils.loadConfig("input.xml");
        Scenario scenario = ScenarioUtils.loadScenario(config);

        for (Id<Person> personId : scenario.getPopulation().getPersons().keySet()) {
            counter++;
            Person eachPerson = scenario.getPopulation().getPersons().get(personId);
            Plan selectedPlan = eachPerson.getSelectedPlan();
            // Clear all plans except the selected plan
            eachPerson.getPlans().retainAll(Collections.singleton(selectedPlan));

        }
        System.out.println(counter);

        PopulationWriter populationWriter = new PopulationWriter(scenario.getPopulation());
        populationWriter.write("output.xml.gz");
        System.out.println("writing done");
    }
}