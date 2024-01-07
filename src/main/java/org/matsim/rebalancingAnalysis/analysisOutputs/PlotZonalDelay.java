package org.matsim.rebalancingAnalysis.analysisOutputs;

import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.application.ApplicationUtils;
import org.matsim.contrib.drt.analysis.zonal.DrtZonalSystem;
import org.matsim.contrib.drt.analysis.zonal.DrtZone;
import org.matsim.contrib.dvrp.path.VrpPaths;
import org.matsim.contrib.dvrp.router.TimeAsTravelDisutility;
import org.matsim.contrib.dvrp.trafficmonitoring.QSimFreeSpeedTravelTime;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.router.speedy.SpeedyALTFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.ShapeFileWriter;
import org.opengis.feature.simple.SimpleFeature;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.matsim.contrib.drt.analysis.zonal.DrtGridUtils.createGridFromNetwork;

public class PlotZonalDelay {
    private static final Logger log = LogManager.getLogger(PlotZonalDelay.class);

    public static void main(String[] args) {
        try {
            new PlotZonalDelay().performAnalysis();
        } catch (Exception e) {
            log.error("Analysis failed", e);
        }
    }
    public void performAnalysis() throws Exception {
        Config config = ConfigUtils.loadConfig("input");
        Scenario scenario = ScenarioUtils.loadScenario(config);
        Network network = scenario.getNetwork();

        String directory = config.controler().getOutputDirectory();

        TravelTime travelTime = new QSimFreeSpeedTravelTime(1);
        TravelDisutility travelDisutility = new TimeAsTravelDisutility(travelTime);
        LeastCostPathCalculator router = new SpeedyALTFactory().createPathCalculator(network, travelDisutility, travelTime);

        Path servedDemandsFile = ApplicationUtils.globFile(Path.of(directory + "/ITERS/it." + "1"), "*drt_legs_drt.csv*");


        // Create Zonal system from Grid
        Map<String, PreparedGeometry> geo = createGridFromNetwork(network, 2500.);
        DrtZonalSystem zonalSystem = DrtZonalSystem.createFromPreparedGeometries(network, geo);
        Map<String, List<Double>> statsMap = new HashMap<>();

        try (CSVParser parser = new CSVParser(Files.newBufferedReader(servedDemandsFile),
                CSVFormat.DEFAULT.withDelimiter(';').withFirstRecordAsHeader())) {
            for (CSVRecord record : parser.getRecords()) {
                double departureTime = Double.parseDouble(record.get("departureTime"));
                double arrivalTime = Double.parseDouble(record.get("arrivalTime"));
                double totalTravelTime = arrivalTime - departureTime;
                Link fromLink = network.getLinks().get(Id.createLinkId(record.get("fromLinkId")));
                Link toLink = network.getLinks().get(Id.createLinkId(record.get("toLinkId")));
                double directCarTravelTime = VrpPaths.calcAndCreatePath(fromLink, toLink, departureTime, router, travelTime).getTravelTime();
                double delay = totalTravelTime / directCarTravelTime - 1;

                DrtZone zone = zonalSystem.getZoneForLinkId(fromLink.getId());
                if (zone == null) {
                    log.error("cannot find zone for link " + fromLink.getId().toString());
                } else {
                    statsMap.computeIfAbsent(zone.getId(), l -> new ArrayList<>()).add(delay);
                }
            }
        }

        // Write shp file
        String crs = "28355";
        Collection<SimpleFeature> features = convertGeometriesToSimpleFeatures(crs, zonalSystem, statsMap);
        ShapeFileWriter.writeGeometries(features, directory + "output.shp");
    }

    private Collection<SimpleFeature> convertGeometriesToSimpleFeatures(String targetCoordinateSystem, DrtZonalSystem zones, Map<String, List<Double>> statsMap) {
        SimpleFeatureTypeBuilder simpleFeatureBuilder = new SimpleFeatureTypeBuilder();
        try {
            simpleFeatureBuilder.setCRS(MGC.getCRS(targetCoordinateSystem));
        } catch (IllegalArgumentException e) {
            log.warn("Coordinate reference system \""
                    + targetCoordinateSystem
                    + "\" is unknown! ");
        }

        simpleFeatureBuilder.setName("drtZoneFeature");
        // note: column names may not be longer than 10 characters. Otherwise, the name is cut after the 10th character and the value is NULL in QGis
        simpleFeatureBuilder.add("the_geom", Polygon.class);
        simpleFeatureBuilder.add("zoneIid", String.class);
        simpleFeatureBuilder.add("centerX", Double.class);
        simpleFeatureBuilder.add("centerY", Double.class);
        simpleFeatureBuilder.add("nRequests", Integer.class);
        simpleFeatureBuilder.add("avg_delay", Double.class);
        SimpleFeatureBuilder builder = new SimpleFeatureBuilder(simpleFeatureBuilder.buildFeatureType());

        Collection<SimpleFeature> features = new ArrayList<>();

        for (DrtZone zone : zones.getZones().values()) {
            Object[] featureAttributes = new Object[6];
            Geometry geometry = zone.getPreparedGeometry() != null ? zone.getPreparedGeometry().getGeometry() : null;
            featureAttributes[0] = geometry;
            featureAttributes[1] = zone.getId();
            featureAttributes[2] = zone.getCentroid().getX();
            featureAttributes[3] = zone.getCentroid().getY();
            List<Double> delays = statsMap.getOrDefault(zone.getId(), new ArrayList<>());
            featureAttributes[4] = delays.size();
            featureAttributes[5] = delays.stream().mapToDouble(v -> v).average().orElse(0);

            try {
                features.add(builder.buildFeature(zone.getId(), featureAttributes));
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            }
        }
        return features;
    }
}