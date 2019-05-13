package de.geofabrik.osmi_routing;

import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.shapes.GHPoint;

import de.geofabrik.osmi_routing.flag_encoders.AllRoadsFlagEncoder;
import de.geofabrik.osmi_routing.flag_encoders.AllRoadsFlagEncoder.RoadClass;

public class MissingConnection {
    
    private GHPoint openEndPoint;
    private GHPoint snapPoint;
    private double distance;
    private double distanceGraph;
    private int internalNodeId;
    private double[] angleDiff;
    private long osmId;
    private QueryResult.Position matchType;
    private AllRoadsFlagEncoder.RoadClass roadClass;
    private boolean privateAccess;
    private int priority;

    public MissingConnection(GHPoint openEndPoint, GHPoint snapPoint, double distanceBeeline,
            double distanceGraph, int internalNodeId, double[] angleDiff, long osmId,
            QueryResult.Position matchType, AllRoadsFlagEncoder.RoadClass roadClass,
            boolean privateAccess, int priority) {
        this.openEndPoint = openEndPoint;
        this.snapPoint = snapPoint;
        this.distance = distanceBeeline;
        this.distanceGraph = distanceGraph;
        this.internalNodeId = internalNodeId;
        this.angleDiff = angleDiff;
        this.osmId = osmId;
        this.matchType = matchType;
        this.roadClass = roadClass;
        this.privateAccess = privateAccess;
        this.priority = priority;
    }

    public int getPriority() {
        return priority;
    }

    public GHPoint getOpenEndPoint() {
        return openEndPoint;
    }

    public GHPoint getSnapPoint() {
        return snapPoint;
    }

    public double getDistance() {
        return distance;
    }

    public double getRatio() {
        return Math.min(distanceGraph / distance, 2000);
    }

    public int getInternalNodeId() {
        return internalNodeId;
    }

    public double[] getAngles() {
        return angleDiff;
    }

    public long getOsmId() {
        return osmId;
    }

    public QueryResult.Position getMatchType() {
        return matchType;
    }

    public AllRoadsFlagEncoder.RoadClass getRoadClass() {
        return roadClass;
    }

    public boolean getPrivateAccess() {
        return privateAccess;
    }
}
