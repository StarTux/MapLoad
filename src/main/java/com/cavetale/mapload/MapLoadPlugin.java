package com.cavetale.mapload;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class MapLoadPlugin extends JavaPlugin {
    protected File saveFolder;
    protected final Map<String, MapLoader> mapLoaderMap = new HashMap<>();

    @Override
    public void onEnable() {
        saveFolder = new File(getDataFolder(), "saves");
        saveFolder.mkdirs();
        for (File file : saveFolder.listFiles()) {
            if (!file.isFile()) continue;
            String name = file.getName();
            if (!name.endsWith(".json")) continue;
            name = name.substring(0, name.length() - 5);
            MapLoader mapLoader = new MapLoader(this, name, file);
            if (mapLoader.load()) {
                mapLoaderMap.put(name, mapLoader);
            }
        }
    }

    @Override
    public void onDisable() {
        for (MapLoader mapLoader : mapLoaderMap.values()) {
            mapLoader.save();
        }
        mapLoaderMap.clear();
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String alias, final String[] args) {
        if (args.length == 0) return false;
        switch (args[0]) {
        case "start": {
            World world;
            if (args.length == 1 && sender instanceof Player player) {
                world = player.getWorld();
            } else if (args.length == 2) {
                String name = args[1];
                world = getServer().getWorld(name);
                if (world == null) {
                    sender.sendMessage("World not found: " + name);
                    return true;
                }
            } else {
                return false;
            }
            String name = world.getName();
            if (mapLoaderMap.get(name) != null) {
                sender.sendMessage("World already loading: " + name);
                return true;
            }
            File saveFile = new File(saveFolder, name + ".json");
            MapLoader mapLoader = new MapLoader(this, name, saveFile);
            mapLoader.begin(world);
            mapLoaderMap.put(name, mapLoader);
            sender.sendMessage("Starting world: " + name);
            return true;
        }
        case "info": {
            if (mapLoaderMap.isEmpty()) {
                sender.sendMessage("No worlds loading");
                return true;
            }
            for (Map.Entry<String, MapLoader> entry : mapLoaderMap.entrySet()) {
                sender.sendMessage(entry.getKey() + ": " + entry.getValue().state);
            }
            return true;
        }
        default: return false;
        }
    }
}
