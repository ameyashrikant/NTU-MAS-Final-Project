package tileworld.agent;

import tileworld.environment.*;
import java.util.*;

public class SmartMemory extends TWAgentMemory {
    private Set<TWHole> knownHoles;
    private Set<TWTile> knownTiles;
    private TWFuelStation fuelStation;
    private static final int MEMORY_DURATION = 100; // How long to remember objects
    
    private class MemoryEntry<T> {
        T object;
        long timestamp;
        
        MemoryEntry(T object) {
            this.object = object;
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    private Map<String, MemoryEntry<TWHole>> holeMemory;
    private Map<String, MemoryEntry<TWTile>> tileMemory;

    public SmartMemory(TWAgent agent) {
        super(agent);
        this.knownHoles = new HashSet<>();
        this.knownTiles = new HashSet<>();
        this.holeMemory = new HashMap<>();
        this.tileMemory = new HashMap<>();
    }

    @Override
    public void see(TWEntity entity) {
        super.see(entity);
        String key = getLocationKey(entity.getX(), entity.getY());
        
        if (entity instanceof TWHole) {
            TWHole hole = (TWHole) entity;
            holeMemory.put(key, new MemoryEntry<>(hole));
            knownHoles.add(hole);
        } else if (entity instanceof TWTile) {
            TWTile tile = (TWTile) entity;
            tileMemory.put(key, new MemoryEntry<>(tile));
            knownTiles.add(tile);
        } else if (entity instanceof TWFuelStation) {
            fuelStation = (TWFuelStation) entity;
        }
    }

    public Set<TWHole> getKnownHoles() {
        cleanupOldMemories();
        return new HashSet<>(knownHoles);
    }

    public Set<TWTile> getKnownTiles() {
        cleanupOldMemories();
        return new HashSet<>(knownTiles);
    }

    private void cleanupOldMemories() {
        long currentTime = System.currentTimeMillis();
        
        // Clean up holes
        Iterator<Map.Entry<String, MemoryEntry<TWHole>>> holeIterator = holeMemory.entrySet().iterator();
        while (holeIterator.hasNext()) {
            Map.Entry<String, MemoryEntry<TWHole>> entry = holeIterator.next();
            if (currentTime - entry.getValue().timestamp > MEMORY_DURATION) {
                knownHoles.remove(entry.getValue().object);
                holeIterator.remove();
            }
        }
        
        // Clean up tiles
        Iterator<Map.Entry<String, MemoryEntry<TWTile>>> tileIterator = tileMemory.entrySet().iterator();
        while (tileIterator.hasNext()) {
            Map.Entry<String, MemoryEntry<TWTile>> entry = tileIterator.next();
            if (currentTime - entry.getValue().timestamp > MEMORY_DURATION) {
                knownTiles.remove(entry.getValue().object);
                tileIterator.remove();
            }
        }
    }

    private String getLocationKey(int x, int y) {
        return x + "," + y;
    }

    public TWHole getHoleAt(int x, int y) {
        String key = getLocationKey(x, y);
        MemoryEntry<TWHole> entry = holeMemory.get(key);
        return entry != null ? entry.object : null;
    }

    public TWTile getTileAt(int x, int y) {
        String key = getLocationKey(x, y);
        MemoryEntry<TWTile> entry = tileMemory.get(key);
        return entry != null ? entry.object : null;
    }

    public boolean isFuelStationFound() {
        return fuelStation != null;
    }

    public TWFuelStation getFuelStation() {
        return fuelStation;
    }
}