package com.cavetale.test;

import com.google.gson.Gson;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.ToString;
import lombok.Value;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class MapLoadPlugin extends JavaPlugin {
    State state = null;
    File stateFile;
    Gson gson = new Gson();

    @Override
    public void onEnable() {
        File dir = getDataFolder();
        dir.mkdirs();
        stateFile = new File(dir, "state.json");
        try (FileReader fr = new FileReader(stateFile)) {
            state = gson.fromJson(fr, State.class);
        } catch (FileNotFoundException fnfr) {
            state = null;
        } catch (Exception e) {
            e.printStackTrace();
            state = null;
        }
        getLogger().info("state=" + state);
        if (state != null) {
            getServer().getScheduler().runTask(this, this::nextChunk);
        }
    }

    @Override
    public void onDisable() {
        save();
    }

    void save() {
        if (state == null) {
            return;
        }
        try (FileWriter fw = new FileWriter(stateFile)) {
            gson.toJson(state, fw);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCommand(final CommandSender sender,
                             final Command command,
                             final String alias,
                             final String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            World world = player.getWorld();
            begin(world);
        } else if (args.length == 1) {
            World world = getServer().getWorld(args[0]);
            begin(world);
        } else {
            return false;
        }
        return true;
    }

    @ToString
    static final class State {
        Vec2 minChunk;
        Vec2 maxChunk;
        String world;
        final List<Vec2> regions = new ArrayList<>();
        final List<Vec2> chunks = new ArrayList<>();

        World getWorld() {
            return Bukkit.getWorld(world);
        }
    }

    @Value
    final class Vec2 {
        public final int x;
        public final int z;

        @Override
        public String toString() {
            return "(" + x + "," + z + ")";
        }
    }

    void begin(World world) {
        getLogger().info("Starting chunk load: " + world.getName());
        WorldBorder border = world.getWorldBorder();
        Location center = border.getCenter();
        double size = border.getSize() * 0.5;
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
        getLogger().info("Corner chunks: " + regA + ", " + regB);
        getLogger().info("Starting: " + state);
        getServer().getScheduler().runTask(this, this::nextRegion);
    }

    void nextRegion() {
        if (state == null) return;
        if (state.regions.isEmpty()) {
            getLogger().info("All regions done.");
            state = null;
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
            getServer().getScheduler().runTask(this, this::nextRegion);
            return;
        }
        getLogger().info("Starting region " + state.regions.size() + ": "
                         + reg + ", " + state.chunks.size() + " chunks");
        getServer().getScheduler().runTask(this, this::nextChunk);
    }

    void nextChunk() {
        if (state == null) return;
        if (state.chunks.isEmpty()) {
            save();
            state.getWorld().save();
            getServer().getScheduler().runTask(this, this::nextRegion);
            return;
        }
        World world = state.getWorld();
        final List<CompletableFuture<Chunk>> futures = new ArrayList<>();
        for (int i = 0; i < 64 && !state.chunks.isEmpty(); i += 1) {
            Vec2 chunk = state.chunks.remove(0);
            CompletableFuture<Chunk> future = world.getChunkAtAsync(chunk.x, chunk.z, true);
            futures.add(future);
        }
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
                for (CompletableFuture<Chunk> future : futures) {
                    try {
                        future.join();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                Bukkit.getScheduler().runTask(this, this::nextChunk);
            });
    }
}
