package net.hallowed.waypoints.client.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.hallowed.waypoints.client.config.WaypointsConfig;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class DeathPointStorage {

    private static final Logger LOGGER = LoggerFactory.getLogger("WaypointsVanillafied/DeathPoints");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<DeathPointData>>() {}.getType();
    private static final int MAX_DEATH_POINTS = 10;

    private static DeathPointStorage instance;

    private final Path filePath;
    private final List<DeathPointData> deathPoints = new ArrayList<>();

    private DeathPointStorage(Path filePath) {
        this.filePath = filePath;
        load();
    }

    /** Returns the storage for the current world/server, creating it on first use. */
    public static DeathPointStorage getInstance() {
        if (instance == null) {
            Minecraft mc = Minecraft.getInstance();
            String worldId = mc.getCurrentServer() != null
                    ? mc.getCurrentServer().ip.replaceAll("[^a-zA-Z0-9._-]", "_")
                    : "singleplayer";
            instance = new DeathPointStorage(getBaseDir().resolve(worldId + "_deathpoints.json"));
        }
        return instance;
    }

    private static Path getBaseDir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("data").resolve("waypoints_vanillafied");
    }

    /** Must be called on disconnect so the next world loads its own file. */
    public static void onDisconnect() {
        instance = null;
    }

    public List<DeathPointData> getDeathPoints() {
        pruneExpired();
        return Collections.unmodifiableList(deathPoints);
    }

    public void addDeathPoint(double x, double y, double z, String dimension) {
        pruneExpired();
        if (deathPoints.size() >= MAX_DEATH_POINTS) {
            deathPoints.removeFirst();
        }
        deathPoints.add(new DeathPointData(UUID.randomUUID().toString(), x, y, z, dimension, System.currentTimeMillis()));
        save();
    }

    public void removeDeathPoint(String id) {
        deathPoints.removeIf(dp -> dp.id().equals(id));
        save();
    }

    /** Max age in milliseconds, or 0 when death points never expire. */
    private static long getMaxAgeMs() {
        int minutes = WaypointsConfig.HANDLER.instance().deathPointExpiryMinutes;
        return minutes <= 0 ? 0 : minutes * 60_000L;
    }

    private void pruneExpired() {
        long maxAge = getMaxAgeMs();
        if (maxAge == 0) return;

        long now = System.currentTimeMillis();
        if (deathPoints.removeIf(dp -> (now - dp.createdAt()) > maxAge)) {
            save();
        }
    }

    private void load() {
        if (!Files.exists(filePath)) return;
        try {
            List<DeathPointData> loaded = GSON.fromJson(Files.readString(filePath), LIST_TYPE);
            if (loaded != null) {
                deathPoints.addAll(loaded);
            }
            pruneExpired();
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Failed to load death points from {}", filePath, e);
        }
    }

    private void save() {
        try {
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, GSON.toJson(deathPoints));
        } catch (IOException e) {
            LOGGER.error("Failed to save death points to {}", filePath, e);
        }
    }
}
