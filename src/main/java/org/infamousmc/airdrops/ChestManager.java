package org.infamousmc.airdrops;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Barrel;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static org.infamousmc.airdrops.Main.plugin;

public class ChestManager implements Listener {

    private final Set<Location> openedChests = new HashSet<>();
    private final Set<Location> markedAirdrops = new HashSet<>();
    private final List<LootItem> loot = new ArrayList<>();


    public void lootLoader(FileConfiguration config) {
        ConfigurationSection lootSection = config.getConfigurationSection("loot");

        if (lootSection == null) {
            Bukkit.getLogger().severe("Config Error: \"loot\" is empty! Please check config.yml.");
            return;
        }

        loot.clear();
        for (String key : lootSection.getKeys(false)) {
            ConfigurationSection itemSection = lootSection.getConfigurationSection(key);
            loot.add(new LootItem(itemSection));
        }
    }

    @EventHandler
    private void onChestOpen(InventoryOpenEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();

        if (holder instanceof Barrel) {
            Barrel airdrop = (Barrel) holder;
            if (isAirdrop(airdrop.getLocation())) {
                if (hasBeenOpened(airdrop.getLocation())) return;
                markAsOpened(airdrop.getLocation());
                airdropDespawn(airdrop);
            }
        }
    }

    public void fill (Inventory inventory, String tier) {
        inventory.clear();

        ThreadLocalRandom random = ThreadLocalRandom.current();
        Set<LootItem> used = new HashSet<>();

        for (int slotIndex = 0; slotIndex < inventory.getSize() + 1; slotIndex++) {
            int selected = random.nextInt(loot.size());
            LootItem randomItem = loot.get(selected);

            if (used.contains(randomItem)) continue;
            used.add(randomItem);

            if (randomItem.shouldFill(random, tier)) {
                ItemStack item = randomItem.make(random);
                inventory.setItem(slotIndex, item);
            }
        }
    }

    public void markAsOpened(Location location) {
        openedChests.add(location);
    }

    public void markAsAirdrop(Location location) {
        markedAirdrops.add(location);
    }

    public boolean hasBeenOpened(Location location) {
        return openedChests.contains(location);
    }

    public boolean isAirdrop(Location location) {
        return markedAirdrops.contains(location);
    }

    public void airdropDespawn(Barrel airdrop) {
        long despawnTime = plugin.getConfig().getLong("despawn-time");
        Location loc = airdrop.getLocation();
        loc.setX(loc.getX() + 0.5);
        loc.setY(loc.getY() - 0.75);
        loc.setZ(loc.getZ() + 0.5);
        ArmorStand hologram = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        hologram.setVisible(false);
        hologram.setCustomNameVisible(true);
        hologram.setGravity(false);
        // Despawn countdown
        new BukkitRunnable() {
            int i = Math.toIntExact(despawnTime);
            public void run() {
                if (i == 0) {
                    plugin.isRunning = false;
                    airdrop.getInventory().clear();
                    airdrop.getBlock().setType(Material.AIR);
                    hologram.remove();
                    if (Bukkit.getOnlinePlayers().size() >= plugin.getConfig().getInt("required-online-players")) {
                        plugin.callAirdrop();
                    }
                    cancel();
                    return;
                }
                hologram.setCustomName(ChatColor.translateAlternateColorCodes('&',
                        "&4Despawning in &c" + i));
                i--;
            }
        }.runTaskTimer(plugin, 0, 20);
    }

}
