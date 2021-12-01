package com.cavetale.mapload;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.World;

public final class State {
    protected String world;
    protected Vec2 minChunk;
    protected Vec2 maxChunk;
    protected final List<Vec2> regions = new ArrayList<>();
    protected final List<Vec2> chunks = new ArrayList<>();

    public World getWorld() {
        return Bukkit.getWorld(world);
    }

    public boolean isValid() {
        return minChunk != null
            && maxChunk != null
            && world != null
            && regions != null
            && chunks != null;
    }

    @Override
    public String toString() {
        return world + "(" + minChunk + "/" + maxChunk + ")"
            + ",r=" + regions.size()
            + ",c=" + chunks.size();
    }
}
