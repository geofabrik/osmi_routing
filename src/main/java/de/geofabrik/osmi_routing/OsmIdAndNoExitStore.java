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

import java.nio.Buffer;
import java.nio.ByteBuffer;

import com.graphhopper.storage.DAType;
import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.GHDirectory;

public class OsmIdAndNoExitStore {
    
    private int NO_ENTRY = -1;
    // The size of the OSM ID is reduced from 64 to 56 bits to have space for one extra bit for the noexit=yes state.
    protected final int OSM_ID_BYTES = 7;
    private final int BUFFER_SIZE = Long.BYTES;
    
    private DataAccess nodesInfo;
    private int entryBytes = 8;
    protected int entriesCount;
    protected ByteBuffer inputByteBuffer;

    public OsmIdAndNoExitStore(String location, String fileName) {
        this.entriesCount = 0;
        GHDirectory dir = new GHDirectory(location, DAType.RAM);
        this.nodesInfo = dir.find(fileName, DAType.RAM);
        this.nodesInfo.create(100000);
        this.inputByteBuffer = ByteBuffer.allocate(BUFFER_SIZE);
        if (entriesCount > 0)
            throw new AssertionError("The nodes info storage must be initialized only once.");
    }

    public OsmIdAndNoExitStore(String location) {
        this(location, "node_info");
    }

    public int getBufferSize() {
        return BUFFER_SIZE;
    }

    public void close() {
        nodesInfo.close();
    }

    protected void ensureCapacity(int nodeIndex) {
        nodesInfo.ensureCapacity(((long) nodeIndex + 1) * entryBytes);
        // intialize with NO_ENTRY value
        for (int i = entriesCount; i <= nodeIndex; ++i) {
            setLong(i, NO_ENTRY);
        }
    }

    protected void checkNodeIdValid(long osmNodeId) {
        long upperLimit = 1l << (OSM_ID_BYTES * 8);
        if (osmNodeId > upperLimit) {
            throw new AssertionError(String.format("Unable to handle OSM node ID %d because it exceeds the upper limit of %d.", osmNodeId, upperLimit));
        }
        if (osmNodeId < 0) {
            throw new AssertionError(String.format("Unable to handle negative node ID %d.", osmNodeId));
        }
    }
    
    protected void setLong(int nodeId, long value) {
        // Casting to java.nio.Buffer is necessary because ByteBuffer.clear and .flip are have covariant return types
        // compared to their parent class since Java 9. This is incompatible to Java 8.
        // https://jira.mongodb.org/browse/JAVA-2559
        ((Buffer) inputByteBuffer).clear();
        inputByteBuffer.putLong(value);
        nodesInfo.setBytes((long) nodeId  * entryBytes, inputByteBuffer.array(), BUFFER_SIZE);
    }

    protected long getLong(int nodeId, ByteBuffer buffer) {
        byte[] bytes = new byte[BUFFER_SIZE];
        nodesInfo.getBytes((long) nodeId  * entryBytes, bytes, BUFFER_SIZE);
        // Casting to java.nio.Buffer is necessary because ByteBuffer.clear and .flip are have covariant return types
        // compared to their parent class since Java 9. This is incompatible to Java 8.
        // https://jira.mongodb.org/browse/JAVA-2559
        ((Buffer) buffer).clear();
        buffer.put(bytes);
        ((Buffer) buffer).flip();
        long result = buffer.getLong();
        return result;
    }

    /**
     * Add a OSM node ID and its noexit/entrance status to the data store.
     * 
     * This method is not thread safe!
     * 
     * @param nodeId internal node ID
     * @param osmNodeId OSM node ID
     * @param hasNoExit boolean value to store
     */
    public void addNodeInfo(int nodeId, long osmNodeId, boolean hasNoExit) {
        ensureCapacity(nodeId);
        checkNodeIdValid(osmNodeId);
        long toInsert = osmNodeId & 0x00FFFFFFFFFFFFFFl;
        if (hasNoExit) {
            toInsert |= (1l << 63);
        }
        setLong(nodeId, toInsert);
        entriesCount = nodeId + 1;
    }

    long getOsmId(int internalId, ByteBuffer buffer) {
        if (internalId == -1) {
            // no entry for this node
            return -1;
        }
        long osmId = getLong(internalId, buffer);
        if (osmId == -1) {
            return osmId;
        }
        osmId &= 0x00FFFFFFFFFFFFFFl;
        return osmId;
    }

    public long getOsmId(int internalId) {
        return getOsmId(internalId, inputByteBuffer);
    }

    boolean getNoExit(int internalId, ByteBuffer buffer) {
        if (internalId == -1) {
            // no entry for this node
            return false;
        }
        long stored = getLong(internalId, buffer);
        if (stored == -1) {
            // no entry for this node, return default
            return false;
        }
        stored = stored >>> 63;
        return stored == 1l;
    }

    public boolean getNoExit(int internalId) {
        return getNoExit(internalId, inputByteBuffer);
    }

    public ThreadSafeOsmIdNoExitStoreAccessor getThreadSafeAccessor() {
        return new ThreadSafeOsmIdNoExitStoreAccessor(this);
    }
}
