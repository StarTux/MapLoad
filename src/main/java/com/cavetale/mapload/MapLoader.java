package com.cavetale.mapload;

import com.google.gson.Gson;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;

@RequiredArgsConstructor
public final class MapLoader {
    protected static final Gson GSON = new Gson();
    protected final MapLoadPlugin plugin;
    protected final String name;
    protected final File stateFile;
    protected State state = null;

    protected boolean load() {
        try (FileReader fr = new FileReader(stateFile)) {
            state = GSON.fromJson(fr, State.class);
        } catch (FileNotFoundException fnfr) {
            state = null;
        } catch (Exception e) {
            e.printStackTrace();
            state = null;
        }
        if (state == null || !state.isValid()) {
            plugin.getLogger().warning(name + ": Invalid state file!");
            return false;
        }
        if (state.getWorld() == null) {
            plugin.getLogger().warning(name + ": World not found!");
            return false;
        }
        plugin.getLogger().info(name + ": state=" + state);
        Bukkit.getScheduler().runTask(plugin, this::nextChunk);
        return true;
    }

    protected void save() {
        if (state == null) {
            return;
        }
        try (FileWriter fw = new FileWriter(stateFile)) {
            GSON.toJson(state, fw);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void begin(World world) {
        plugin.getLogger().info(name + ": Starting chunk load: " + world.getName());
        WorldBorder border = world.getWorldBorder();
        Location center = border.getCenter();
        double size = border.getSize() * 0.5;
        if (size > 100000) {
            plugin.getLogger().warning("World too large!");
            return;
        }
        double cx = center.getX();
        double cz = center.getZ();
        int ax = (int) Math.floor(cx - size);
        int az = (int) Math.floor(cz - size);
        int bx = (int) Math.floor(cx + size);
        int bz = (int) Math.floor(cz + size);
        Vec2 regA = new Vec2(ax >> 9, az >> 9);
        Vec2 regB = new Vec2(bx >> 9, bz >> 9);
        state = new State();
        state.minChunk = new Vec2(ax >> 4, az >> 4);
        state.maxChunk = new Vec2(bx >> 4, bz >> 4);
        state.world = world.getName();
        for (int z = regA.z; z <= regB.z; z += 1) {
            for (int x = regA.x; x <= regB.x; x += 1) {
                Vec2 reg = new Vec2(x, z);
                state.regions.add(reg);
            }
        }
        save();
        plugin.getLogger().info(name + ": Corner chunks: " + regA + ", " + regB);
        plugin.getLogger().info(name + ": Starting: " + state);
        Bukkit.getScheduler().runTask(plugin, this::nextRegion);
    }

    protected void nextRegion() {
        if (state == null) return;
        if (state.regions.isEmpty()) {
            plugin.getLogger().info(name + ": All regions done.");
            state = null;
            plugin.mapLoaderMap.remove(name);
            try {
                stateFile.delete();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }
        Vec2 reg = state.regions.get(0);
        for (Vec2 v : state.regions) {
            // find smallest
            if (Math.abs(v.x) < Math.abs(reg.x) && Math.abs(v.z) < Math.abs(reg.z)) {
                reg = v;
            }
        }
        state.regions.remove(reg);
        for (int z = 0; z < 32; z += 1) {
            for (int x = 0; x < 32; x += 1) {
                int rx = (reg.x << 5) + x;
                int rz = (reg.z << 5) + z;
                if (rx < state.minChunk.x || rx > state.maxChunk.x
                    || rz < state.minChunk.z || rz > state.maxChunk.z) {
                    continue;
                }
                Vec2 chunk = new Vec2(rx, rz);
                state.chunks.add(chunk);
            }
        }
        if (state.chunks.isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, this::nextRegion);
            return;
        }
        plugin.getLogger().info(name + ": Starting region " + state.regions.size()
                                + ": " + reg + ", " + state.chunks.size() + " chunks");
        Bukkit.getScheduler().runTask(plugin, this::nextChunk);
    }

    protected void nextChunk() {
        if (Bukkit.getTPS()[0] < 18.0) {
            Bukkit.getScheduler().runTaskLater(plugin, this::nextChunk, 20L);
            return;
        }
        if (state == null) return;
        if (state.chunks.isEmpty()) {
            save();
            state.getWorld().save();
            Bukkit.getScheduler().runTask(plugin, this::nextRegion);
            return;
        }
        World world = state.getWorld();
        final List<CompletableFuture<Chunk>> futures = new ArrayList<>();
        for (int i = 0; i < 64 && !state.chunks.isEmpty(); i += 1) {
            Vec2 chunk = state.chunks.remove(state.chunks.size() - 1);
            CompletableFuture<Chunk> future = world.getChunkAtAsync(chunk.x, chunk.z, true);
            futures.add(future);
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                for (CompletableFuture<Chunk> future : futures) {
                    try {
                        future.join();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                Bukkit.getScheduler().runTask(plugin, this::nextChunk);
            });
    }
}
