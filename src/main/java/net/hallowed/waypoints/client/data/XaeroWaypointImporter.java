package net.hallowed.waypoints.client.data;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Environment(EnvType.CLIENT)
public final class XaeroWaypointImporter {

    private static final Logger LOGGER = LoggerFactory.getLogger("WaypointsVanillafied/XaeroImport");

    /**
     * Set to true to log detailed import steps.
     */
    private static final boolean DEBUG = false;

    private static final String DEFAULT_STYLE = "minecraft:default";

    /**
     * Ticks to wait after joining before importing (lets the world finish loading).
     */
    private static final int DELAY_TICKS = 20;

    /**
     * Xaero color index (0-15) to RGB; same palette as the Minecraft chat colors.
     */
    private static final int[] XAERO_COLORS = {
            0x000000, // 0  Black
            0x0000AA, // 1  Dark Blue
            0x00AA00, // 2  Dark Green
            0x00AAAA, // 3  Dark Cyan
            0xAA0000, // 4  Dark Red
            0xAA00AA, // 5  Dark Purple
            0xFFAA00, // 6  Gold
            0xAAAAAA, // 7  Gray
            0x555555, // 8  Dark Gray
            0x5555FF, // 9  Blue
            0x55FF55, // 10 Green
            0x55FFFF, // 11 Cyan
            0xFF5555, // 12 Red
            0xFF55FF, // 13 Pink
            0xFFFF55, // 14 Yellow
            0xFFFFFF  // 15 White
    };

    private static int ticksRemaining = -1;

    private XaeroWaypointImporter() {}

    /**
     * Registers the join/disconnect/tick hooks that schedule and run the import.
     */
    public static void register() {

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            LOGGER.info("[WV-Import] Player joined — scheduling import check in {} ticks", DELAY_TICKS);
            ticksRemaining = DELAY_TICKS;
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ticksRemaining = -1;
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (ticksRemaining < 0) return;

            ticksRemaining--;
            if (ticksRemaining > 0) return;

            ticksRemaining = -1;

            try {
                doImport(Minecraft.getInstance());
            } catch (Exception e) {
                LOGGER.error("[WV-Import] Unexpected error during import", e);
            }
        });
    }

    /**
     * Deletes the marker file so all worlds can be imported again.
     */
    public static void resetAllMarkers() {
        try {
            Path markerFile = getMarkerPath();
            Files.deleteIfExists(markerFile);
            LOGGER.info("[WV-Import] Deleted marker file: {}", markerFile);
        } catch (IOException e) {
            LOGGER.error("[WV-Import] Failed to delete marker file", e);
        }
    }

    /**
     * Runs the import for the current world unless it was already imported.
     */
    private static void doImport(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            debug("Player or level is null — aborting");
            return;
        }

        String xaeroFolder = getXaeroWorldFolder(mc);
        if (xaeroFolder == null) {
            debug("Could not determine world folder — no singleplayer server or server data available");
            return;
        }
        debug("World folder resolved: '{}'", xaeroFolder);

        Path markerFile = getMarkerPath();
        debug("Marker file path: {}", markerFile);

        Set<String> alreadyImported = readMarker(markerFile);
        debug("Already-imported worlds in marker: {}", alreadyImported);

        if (alreadyImported.contains(xaeroFolder)) {
            debug("This world is already in the marker file — skipping. Delete '{}' to re-import.", markerFile);
            return;
        }

        Path gameDir = mc.gameDirectory.toPath();
        Path minimapBase = gameDir.resolve("xaero").resolve("minimap");

        debug("Game directory: {}", gameDir);
        debug("Looking for Xaero base: {}", minimapBase);

        if (!Files.isDirectory(minimapBase)) {
            debug("xaero/minimap/ directory does NOT exist — nothing to import");
            return;
        }

        debug("xaero/minimap/ exists! Listing contents...");
        listDirectoryContents(minimapBase);

        Path xaeroWorldDir = findWorldFolder(minimapBase, xaeroFolder);
        if (xaeroWorldDir == null) {
            debug("Could not find matching world folder for '{}' — see available folders above", xaeroFolder);
            return;
        }

        debug("Using world folder: {}", xaeroWorldDir);

        debug("Listing dimension subfolders...");
        listDirectoryContents(xaeroWorldDir);

        WaypointStorage storage = WaypointStorage.getInstance();
        debug("WaypointStorage instance obtained, current waypoint count: {}", storage.getWaypoints().size());

        int count = importFromWorldDir(xaeroWorldDir, storage);

        alreadyImported.add(xaeroFolder);
        writeMarker(markerFile, alreadyImported);
        debug("Wrote marker file with worlds: {}", alreadyImported);

        if (count > 0) {
            String msg = "§a[WaypointsVanillafied] §fImported §e" + count
                    + " §fwaypoint" + (count == 1 ? "" : "s") + " from Xaero's Minimap.";
            LOGGER.info("[WV-Import] Successfully imported {} waypoint(s)", count);
            if (mc.player != null) {
                mc.player.sendSystemMessage(Component.literal(msg));
            }
        } else {
            debug("Scan complete but found 0 valid waypoints to import");
        }
    }

    /**
     * Returns the folder name Xaero uses for this world (level name or "Multiplayer_ip_port"), or null.
     */
    private static String getXaeroWorldFolder(Minecraft mc) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (server != null) {
            String name = server.getWorldData().getLevelName();
            debug("Singleplayer world detected: '{}'", name);
            return name;
        }

        ServerData serverData = mc.getCurrentServer();
        if (serverData != null) {
            String ip = serverData.ip;
            debug("Multiplayer server detected, raw IP: '{}'", ip);
            String folder = "Multiplayer_" + ip.replace(":", "_");
            debug("Computed Xaero folder name: '{}'", folder);
            return folder;
        }

        debug("Neither singleplayer server nor multiplayer ServerData available");
        return null;
    }

    /**
     * Finds the Xaero folder for the world: exact match, then case-insensitive, then multiplayer with/without the default port.
     */
    private static Path findWorldFolder(Path minimapBase, String expectedName) {
        Path exact = minimapBase.resolve(expectedName);
        if (Files.isDirectory(exact)) {
            debug("Exact match found: {}", exact);
            return exact;
        }
        debug("No exact match for '{}' at {}", expectedName, exact);

        debug("Trying case-insensitive match...");
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(minimapBase, Files::isDirectory)) {
            for (Path entry : entries) {
                String folderName = entry.getFileName().toString();
                if (folderName.equalsIgnoreCase(expectedName)) {
                    debug("Case-insensitive match found: '{}' (expected: '{}')", folderName, expectedName);
                    return entry;
                }
            }
        } catch (IOException e) {
            LOGGER.error("[WV-Import] Error scanning minimap directory", e);
        }

        if (expectedName.startsWith("Multiplayer_")) {
            debug("Trying partial match for multiplayer server...");
            String serverPart = expectedName.substring("Multiplayer_".length());
            String withoutPort = serverPart.contains("_")
                    ? serverPart.substring(0, serverPart.lastIndexOf('_')) : null;
            String withPort = serverPart + "_25565";

            try (DirectoryStream<Path> entries = Files.newDirectoryStream(minimapBase, Files::isDirectory)) {
                for (Path entry : entries) {
                    String folderName = entry.getFileName().toString();
                    if (folderName.equalsIgnoreCase("Multiplayer_" + withPort)) {
                        debug("Match with port found: '{}'", folderName);
                        return entry;
                    }
                    if (withoutPort != null && folderName.equalsIgnoreCase("Multiplayer_" + withoutPort)) {
                        debug("Match without port found: '{}'", folderName);
                        return entry;
                    }
                }
            } catch (IOException e) {
                LOGGER.error("[WV-Import] Error during partial match scan", e);
            }
        }

        debug("No matching world folder found");
        return null;
    }

    /**
     * Imports from a Xaero world folder: root .txt files (overworld) and every dimension subfolder.
     */
    private static int importFromWorldDir(Path worldDir, WaypointStorage storage) {
        int total = 0;

        try (DirectoryStream<Path> files = Files.newDirectoryStream(worldDir, "*.txt")) {
            for (Path file : files) {
                String fileName = file.getFileName().toString();
                if (fileName.contains("_") || fileName.equals("waypoints.txt")) {
                    debug("Found waypoint file in world root: {}", fileName);
                    total += parseAndImport(file, "minecraft:overworld", storage);
                }
            }
        } catch (IOException e) {
            debug("Error scanning world dir for .txt files: {}", e.getMessage());
        }

        try (DirectoryStream<Path> dimDirs = Files.newDirectoryStream(worldDir, Files::isDirectory)) {
            for (Path dimDir : dimDirs) {
                String folderName = dimDir.getFileName().toString();

                if (folderName.equals("temp_to_add") || folderName.startsWith("backup")) {
                    debug("Skipping non-dimension folder: {}", folderName);
                    continue;
                }

                String dimension = mapDimension(folderName);
                debug("Processing dimension folder: '{}' → dimension: '{}'", folderName, dimension);
                listDirectoryContents(dimDir);

                int dimCount = importFromDimensionDir(dimDir, dimension, storage);
                debug("Found {} waypoint(s) in dimension folder '{}'", dimCount, folderName);
                total += dimCount;
            }
        } catch (IOException e) {
            LOGGER.error("[WV-Import] Error scanning world dir for dimension folders", e);
        }

        return total;
    }

    /**
     * Recursively imports every .txt waypoint file in a dimension folder, skipping backup/temp folders.
     */
    private static int importFromDimensionDir(Path dimDir, String dimension, WaypointStorage storage) {
        int count = 0;

        try (DirectoryStream<Path> files = Files.newDirectoryStream(dimDir)) {
            for (Path file : files) {
                if (Files.isDirectory(file)) {
                    String subName = file.getFileName().toString();
                    if (!subName.equals("temp_to_add") && !subName.startsWith("backup")) {
                        debug("  Checking subdirectory: {}", subName);
                        count += importFromDimensionDir(file, dimension, storage);
                    }
                    continue;
                }

                String fileName = file.getFileName().toString();
                if (!fileName.endsWith(".txt") || fileName.endsWith(".temp")) {
                    continue;
                }

                debug("  Processing file: {}", fileName);
                count += parseAndImport(file, dimension, storage);
            }
        } catch (IOException e) {
            LOGGER.error("[WV-Import] Error scanning dimension dir: {}", dimDir, e);
        }

        return count;
    }

    /**
     * Reads one Xaero waypoint file and adds each valid "waypoint:" line to the storage.
     */
    private static int parseAndImport(Path file, String dimension, WaypointStorage storage) {
        int count = 0;
        List<String> lines;

        try {
            lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            }
        } catch (IOException e) {
            LOGGER.error("[WV-Import] Error reading file: {}", file, e);
            return 0;
        }

        debug("    File has {} line(s)", lines.size());

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            if (!line.startsWith("waypoint:")) {
                debug("    Line {}: metadata — skipped", i + 1);
                continue;
            }

            WaypointData wp = parseWaypointLine(line, dimension, i + 1);
            if (wp != null) {
                storage.addWaypoint(wp);
                count++;
                debug("    Line {}: IMPORTED '{}' at {},{},{} dim={} color=#{}",
                        i + 1, wp.name(),
                        WaypointData.formatCoord(wp.x()),
                        WaypointData.formatCoord(wp.y()),
                        WaypointData.formatCoord(wp.z()),
                        dimension,
                        String.format("%06X", wp.color()));
            } else {
                debug("    Line {}: FAILED to parse or disabled", i + 1);
            }
        }

        return count;
    }

    /**
     * Parses one Xaero line ("waypoint:name:initials:x:y:z:color:disabled:...").
     * Returns null for malformed or disabled waypoints; a missing Y ("~") defaults to 64.
     */
    private static WaypointData parseWaypointLine(String line, String dimension, int lineNum) {
        String[] parts = line.split(":");

        if (parts.length < 7) {
            debug("    Line {}: too few parts ({}, need at least 7)", lineNum, parts.length);
            return null;
        }

        if (!"waypoint".equals(parts[0])) {
            return null;
        }

        try {
            String name = parts[1].replace("§§", ":");
            if (name.isEmpty()) name = "Unnamed";

            double x = parseCoord(parts[3]);

            double y;
            if ("~".equals(parts[4]) || parts[4].isEmpty()) {
                y = 64.0;
            } else {
                y = parseCoord(parts[4]);
            }

            double z = parseCoord(parts[5]);

            int colorIndex = Integer.parseInt(parts[6].trim());
            int color = mapColor(colorIndex);

            if (parts.length > 7 && "true".equalsIgnoreCase(parts[7])) {
                debug("    Line {}: waypoint '{}' is disabled — skipping", lineNum, name);
                return null;
            }

            return WaypointData.create(name, x, y, z, dimension, DEFAULT_STYLE, color);
        } catch (NumberFormatException e) {
            debug("    Line {}: number parse error: {}", lineNum, e.getMessage());
            return null;
        } catch (ArrayIndexOutOfBoundsException e) {
            debug("    Line {}: array index out of bounds", lineNum);
            return null;
        }
    }

    /**
     * Parses a finite coordinate; both '.' and ',' are accepted as the decimal separator.
     */
    private static double parseCoord(String text) {
        double value = Double.parseDouble(text.trim().replace(',', '.'));
        if (!Double.isFinite(value)) throw new NumberFormatException("Not a finite number: " + text);
        return value;
    }

    /**
     * Maps a Xaero dimension folder ("dim%0", "dim%-1", "dim%1", "dim%ns$path") to a dimension id.
     */
    private static String mapDimension(String folderName) {
        return switch (folderName) {
            case "dim%0" -> "minecraft:overworld";
            case "dim%-1" -> "minecraft:the_nether";
            case "dim%1" -> "minecraft:the_end";
            default -> {
                if (folderName.startsWith("dim%")) {
                    yield folderName.substring(4).replace('$', ':');
                }
                debug("Unknown dimension folder format: '{}' — defaulting to overworld", folderName);
                yield "minecraft:overworld";
            }
        };
    }

    /**
     * Maps a Xaero color index to RGB, defaulting to white when out of range.
     */
    private static int mapColor(int index) {
        if (index < 0 || index >= XAERO_COLORS.length) {
            return 0xFFFFFF;
        }
        return XAERO_COLORS[index];
    }

    /**
     * Location of the file that lists already-imported worlds.
     */
    private static Path getMarkerPath() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("data")
                .resolve("waypoints_vanillafied")
                .resolve("xaero_imports.txt");
    }

    /**
     * Reads the set of already-imported world folders.
     */
    private static Set<String> readMarker(Path path) {
        Set<String> set = new HashSet<>();
        if (!Files.exists(path)) {
            debug("Marker file does not exist yet");
            return set;
        }
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) set.add(trimmed);
            }
            debug("Loaded marker file with {} entries", set.size());
        } catch (IOException e) {
            LOGGER.error("[WV-Import] Error reading marker file", e);
        }
        return set;
    }

    /**
     * Saves the set of already-imported world folders.
     */
    private static void writeMarker(Path path, Set<String> worlds) {
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, worlds, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("[WV-Import] Error writing marker file", e);
        }
    }

    private static void listDirectoryContents(Path dir) {
        if (!DEBUG) return;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(dir)) {
            StringBuilder sb = new StringBuilder("Contents of ").append(dir).append(": [");
            boolean first = true;
            for (Path entry : entries) {
                if (!first) sb.append(", ");
                first = false;
                sb.append(entry.getFileName());
                if (Files.isDirectory(entry)) sb.append("/");
            }
            sb.append("]");
            LOGGER.info("[WV-Import] {}", sb);
        } catch (IOException e) {
            LOGGER.warn("[WV-Import] Could not list directory: {}", dir, e);
        }
    }

    private static void debug(String message, Object... args) {
        if (!DEBUG) return;
        LOGGER.info("[WV-Import] " + message, args);
    }
}