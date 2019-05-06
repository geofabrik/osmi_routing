/*
 *  © 2019 Geofabrik GmbH
 *
 *  This file is part of osmi_routing.
 *
 *  osmi_routing is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License.
 *
 *  osmi_routing is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with osmi_simple_views. If not, see <http://www.gnu.org/licenses/>.
 */


package de.geofabrik.osmi_routing;

import java.io.IOException;
import java.nio.file.Paths;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.graphhopper.reader.DataReader;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.reader.osm.OSMReader;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.Helper;

import de.geofabrik.osmi_routing.reader.NoExitHook;
import de.geofabrik.osmi_routing.subnetworks.RemoveAndDumpSubnetworks;


public class GraphHopperSimple extends GraphHopperOSM {

    static final Logger logger = LogManager.getLogger(OsmiRoutingMain.class.getName());

    AllRoadsFlagEncoder encoder;
    EncodingManager encodingManager;
    OsmIdAndNoExitStore nodeInfoStore;
    NoExitHook hook;
    String outputDirectory;
    UnconnectedFinderManager unconnectedFinderManager;

    public GraphHopperSimple(String args[]) throws IOException {
        super();
        nodeInfoStore = new OsmIdAndNoExitStore(getGraphHopperLocation());
        hook = new NoExitHook(nodeInfoStore);
        setDataReaderFile(args[0]);
        setGraphHopperLocation(args[1]);
        setCHEnabled(false);
        // Disable sorting of graph because that would overwrite the values stored in the additional properties field of the graph.
        setSortGraph(false);
        encoder = new AllRoadsFlagEncoder();
        encodingManager = EncodingManager.create(encoder);
        outputDirectory = args[2];
        setEncodingManager(encodingManager);
        double maxDistance = (args.length >= 4) ? Double.parseDouble(args[3]) : 10;
        int workers = (args.length == 5) ? Integer.parseInt(args[4]) : 2;
        try {
            java.nio.file.Path outputFileNameConnections = Paths.get(outputDirectory, "unconnected_nodes.json"); 
            unconnectedFinderManager = new UnconnectedFinderManager(this, encoder, outputFileNameConnections, maxDistance, workers);
        } catch (IOException e) {
            logger.fatal(e);
            e.printStackTrace();
        }
    }

    /**
     * Overrides method in GrapHopper class calling removal of subnetworks code.
     */
    @Override
    protected void cleanUp() {
        try {
            java.nio.file.Path outputFileNameSubnetworks = Paths.get(outputDirectory, "subnetworks.json"); 
            int prevNodeCount = getGraphHopperStorage().getNodes();
            RemoveAndDumpSubnetworks preparation = new RemoveAndDumpSubnetworks(getGraphHopperStorage(), getEncodingManager().fetchEdgeEncoders(), outputFileNameSubnetworks);
            preparation.setMinNetworkSize(getMinNetworkSize());
            preparation.setMinOneWayNetworkSize(getMinOneWayNetworkSize());
            preparation.doWork();
            int currNodeCount = getGraphHopperStorage().getNodes();
            logger.info("edges: " + Helper.nf(getGraphHopperStorage().getAllEdges().length()) + ", nodes " + Helper.nf(currNodeCount)
                    + ", there were " + Helper.nf(preparation.getMaxSubnetworks())
                    + " subnetworks. removed them => " + Helper.nf(prevNodeCount - currNodeCount)
                    + " less nodes");
        } catch (IOException ex) {
            logger.catching(ex);
            System.exit(1);
        }
    }

    @Override
    protected DataReader createReader(GraphHopperStorage ghStorage) {
        OSMReader reader = new OSMReader(ghStorage);
        reader.register(hook);
        return initDataReader(reader);
    }

    public void run() {
        importOrLoad();
        logger.info("OSM node ID and noexit caches consume {} MB.", hook.usedMemory() / (1024*1024));
        hook.releaseNoExitSet();
        unconnectedFinderManager.init(getGraphHopperStorage(), nodeInfoStore);
        unconnectedFinderManager.run();
        close();
    }
}
