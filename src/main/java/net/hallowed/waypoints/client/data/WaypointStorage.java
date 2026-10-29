package net.hallowed.waypoints.client.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WaypointStorage {

    private static final Logger LOGGER = LoggerFactory.getLogger("WaypointsVanillafied/Waypoints");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<WaypointData>>() {}.getType();

    private static WaypointStorage instance;
    private static String currentWorldId;

    private final List<WaypointData> waypoints = new ArrayList<>();
    private final Path saveFile;

    private WaypointStorage(String worldId) {
        this.saveFile = getBaseDir().resolve(sanitize(worldId) + ".json");
        load();
    }

    private static Path getBaseDir() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("data")
                .resolve("waypoints_vanillafied");
    }

    /** Returns the storage for the current world/server, reloading it when the world changes. */
    public static WaypointStorage getInstance() {
        String worldId = resolveWorldId();
        if (instance == null || !worldId.equals(currentWorldId)) {
            currentWorldId = worldId;
            instance = new WaypointStorage(worldId);
        }
        return instance;
    }

    /** Must be called on disconnect so stale world data is dropped. */
    public static void onDisconnect() {
        instance = null;
        currentWorldId = null;
    }

    public List<WaypointData> getWaypoints() {
        return Collections.unmodifiableList(waypoints);
    }

    public void addWaypoint(WaypointData waypoint) {
        waypoints.add(waypoint);
        save();
    }

    public void removeWaypoint(String id) {
        waypoints.removeIf(w -> w.id().equals(id));
        save();
    }

    /** Replaces the waypoint with the same id, or adds it if it does not exist yet. */
    public void updateWaypoint(WaypointData updated) {
        for (int i = 0; i < waypoints.size(); i++) {
            if (waypoints.get(i).id().equals(updated.id())) {
                waypoints.set(i, updated);
                save();
                return;
            }
        }
        waypoints.add(updated);
        save();
    }

    public void clear() {
        waypoints.clear();
        save();
    }

    private void save() {
        try {
            Files.createDirectories(saveFile.getParent());
            Files.writeString(saveFile, GSON.toJson(waypoints));
        } catch (IOException e) {
            LOGGER.error("Failed to save waypoints to {}", saveFile, e);
        }
    }

    private void load() {
        if (!Files.exists(saveFile)) return;
        try {
            List<WaypointData> loaded = GSON.fromJson(Files.readString(saveFile), LIST_TYPE);
            if (loaded != null) {
                waypoints.addAll(loaded);
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Failed to load waypoints from {}", saveFile, e);
        }
    }

    /** Builds a unique id for the current world (singleplayer name or server address). */
    private static String resolveWorldId() {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (server != null) {
            return "local_" + server.getWorldData().getLevelName();
        }
        ServerData serverData = mc.getCurrentServer();
        if (serverData != null) {
            return "server_" + serverData.ip;
        }
        return "unknown";
    }

    /** Makes a world id safe to use as a file name. */
    private static String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
