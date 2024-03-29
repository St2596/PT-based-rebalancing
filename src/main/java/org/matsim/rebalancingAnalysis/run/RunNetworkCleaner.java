package org.matsim.rebalancingAnalysis.run;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.misc.ArgumentParser;

import java.util.Iterator;

public class RunNetworkCleaner {

    private void printUsage() {
        System.out.println();
        System.out.println("NetworkCleaner");
        System.out.println("Reads a network-file and \"cleans\" it. Currently, it performs the following");
        System.out.println("steps to ensure a network is suited for simulation:");
        System.out.println(" - ensure every link can be reached by every other link. It looks for the");
        System.out.println("   biggest cluster of connected nodes and links and removes all other elements.");
        System.out.println();
        System.out.println("usage: NetworkCleaner [OPTIONS] input-network-file output-network-file");
        System.out.println();
        System.out.println("Options:");
        System.out.println("-h, --help:     Displays this message.");
        System.out.println();
        System.out.println("----------------");
        System.out.println("2008, matsim.org");
        System.out.println();
    }

    /** Runs the network cleaning algorithms over the network read in from <code>inputNetworkFile</code>
     * and writes the resulting ("cleaned") network to the specified file.
     *
     * @param inputNetworkFile filename of the network to be handled
     * @param outputNetworkFile filename where to write the cleaned network to
     */
    public void run(final String inputNetworkFile, final String outputNetworkFile) {
        final Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        final Network network = scenario.getNetwork();
        new MatsimNetworkReader(scenario.getNetwork()).readFile(inputNetworkFile);

        new org.matsim.core.network.algorithms.NetworkCleaner().run(network);

        new NetworkWriter(network).write(outputNetworkFile);
    }

    /** Runs the network cleaning algorithms over the network read in from the argument list, and
     * writing the resulting network out to a file again
     *
     * @param args <code>args[0]</code> filename of the network to be handled,
     * <code>args[1]</code> filename where to write the cleaned network to
     */
    public void run(final String[] args) {
        if (args.length == 0) {
            System.out.println("Too few arguments.");
            printUsage();
            System.exit(1);
        }
        Iterator<String> argIter = new ArgumentParser(args).iterator();
        String arg = argIter.next();
        if (arg.equals("-h") || arg.equals("--help")) {
            printUsage();
            System.exit(0);
        } else {
            String inputFile = arg;
            if (!argIter.hasNext()) {
                System.out.println("Too few arguments.");
                printUsage();
                System.exit(1);
            }
            String outputFile = argIter.next();
            if (argIter.hasNext()) {
                System.out.println("Too many arguments.");
                printUsage();
                System.exit(1);
            }
            run(inputFile, outputFile);
        }
    }

    public static void main(String[] args) {
        new RunNetworkCleaner().run("input.xml","output.xml.gz");
    }

}
