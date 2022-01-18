package org.infamousmc.airdrops;

import org.bukkit.*;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;


public final class Main extends JavaPlugin implements Listener {
    public static Main plugin;
    ChestManager manager = new ChestManager();

    FallingBlock airdrop = null;
    Map<FallingBlock, String> airdropTier = new HashMap<>();
    boolean isRunning = false;

    @Override
    public void onEnable() {
        // Plugin startup logic
        saveDefaultConfig();
        plugin = this;
        manager.lootLoader(getConfig());

        this.getServer().getPluginManager().registerEvents(this, this);
        this.getServer().getPluginManager().registerEvents(manager, this);
        this.getCommand("Airdrop").setTabCompleter(new TabAirdrop());
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (label.equalsIgnoreCase("test")) {
            return true;
        }

        if (label.equalsIgnoreCase("airdrop")) {
            if (!sender.hasPermission("airdrops.admin")) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        "&c&lOops! &7You do not have the permission &cairdrops.admin&7!"));
                return true;
            }
            if (args.length != 1) {
                helpMessage(sender);
                return true;
            }
            // Airdrop Spawn
            if (args[0].equalsIgnoreCase("spawn")) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("This command must be run by a player");
                    return true;
                }
                Player player = (Player) sender;
                spawnAirDrop();
                return true;
            }
            // Airdrop Reload
            if (args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                manager.lootLoader(getConfig());
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        "&8[&a!&8] &7Config reloaded successfully."));
                return true;
            }
        }

        return false;
    }

    @EventHandler
    public void playerJoinEvent(PlayerJoinEvent event) {
        if (Bukkit.getOnlinePlayers().size() >= getConfig().getInt("required-online-players")) {
            callAirdrop();
        }
    }

    public void callAirdrop() {
        if (isRunning) return;
        ConfigurationSection delaysSection = getConfig().getConfigurationSection("airdrop-delays");
        if (delaysSection == null) {
            getLogger().severe("Config error! The \"airdrop-delays\" section is null! Please fix or reset the config.");
            return;
        }
        long min = delaysSection.getLong("minimum-delay") * 1200;
        long max = delaysSection.getLong("maximum-delay") * 1200 + 1;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        long delay = random.nextLong(min, max);
        isRunning = true;
        new BukkitRunnable() {
            @Override
            public void run() {
                spawnAirDrop();
            }
        }.runTaskLater(this, delay);
    }

    public void spawnAirDrop() {
        ConfigurationSection boundsSection = getConfig().getConfigurationSection("airdrop-area");
        if (boundsSection == null) {
            getLogger().severe("Config error! The \"airdrop-area\" section is null! Please fix or reset the config.");
            return;
        }
        if (getConfig().getString("enabled-world") == null) {
            getLogger().severe("Config error! The \"enabled-world\" section is null! Please fix or reset the config.");
            return;
        }
        double minz = boundsSection.getDouble("minimum-z");
        double minx = boundsSection.getDouble("minimum-x");
        double maxz = boundsSection.getDouble("maximum-z");
        double maxx = boundsSection.getDouble("maximum-x");
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double x = random.nextDouble(minx, maxx + 1);
        x = Math.floor(x) + 0.5;
        double z = random.nextDouble(minz, maxz + 1);
        z = Math.floor(z) + 0.5;
        World world = Bukkit.getWorld(getConfig().getString("enabled-world"));
        Location loc = new Location(world, x, 255, z);

        if (world.getHighestBlockAt(loc).getY() >= getConfig().getInt("highest-landing-block")) {
            spawnAirDrop();
            return;
        }

        BlockData blockData = Material.BARREL.createBlockData();
        Directional block = (Directional) blockData;
        block.setFacing(BlockFace.UP);

        ConfigurationSection tierWeights = getConfig().getConfigurationSection("tier-weights");
        RandomCollection<String> randomCollection = new RandomCollection<>();
        for (String t : tierWeights.getKeys(false)) {
            int weight = tierWeights.getInt(t);
            randomCollection.add(weight, t);
        }
        String result = randomCollection.next();

        airdrop = world.spawnFallingBlock(loc, block);
        airdropTier.put(airdrop, result.toLowerCase());

        Color color = null;
        switch (result) {
            case "common":
                color = Color.GRAY;
                break;
            case "rare":
                color = Color.BLUE;
                break;
            case "poggers":
                color = Color.FUCHSIA;
                break;
        }
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                "&8[&4!&8] &7A " + result + " airdrop was spotted in the warzone! &8[&c" + x + ", " + z + "&8]"));

        new BukkitRunnable() {
            public void run() {
                if (!(airdrop.isValid())) {
                    cancel();
                    return;
                }
                world.spawnParticle(Particle.SMOKE_NORMAL, airdrop.getLocation(), 1000, 0, 0, 0, 0.05);
            }
        }.runTaskTimer(this, 0, 1);

        Color finalColor = color;
        new BukkitRunnable() {
            public void run() {
                if (!(airdrop.isValid())) {
                    cancel();
                    return;
                }
                Location loc = airdrop.getLocation();
                Firework firework = (Firework) world.spawnEntity(loc, EntityType.FIREWORK);
                FireworkMeta meta = firework.getFireworkMeta();

                meta.setPower(2);
                meta.addEffect(FireworkEffect.builder().withColor(finalColor).flicker(true).build());

                firework.setFireworkMeta(meta);
                firework.detonate();
            }
        }.runTaskTimer(this, 20, 10);
    }

    // Called when the airdrop lands on the ground
    @EventHandler
    public void onLandingBarrel(EntityChangeBlockEvent event) {
        if (event.getEntity() != airdrop) return;
        event.setCancelled(true);
        Block block = event.getBlock();
        createBarrel(block, airdrop);
    }

    // Called if the airdrop breaks on a torch, slab, or other similar blocks
    @EventHandler
    public void onBrokenBarrel(EntityDropItemEvent event) {
        Entity entity = event.getEntity();
        Material material = event.getItemDrop().getItemStack().getType();
        if (entity != airdrop) return;

        event.setCancelled(true);
        Location loc = entity.getLocation();
        Block block = loc.getBlock();
        createBarrel(block.getRelative(BlockFace.UP), airdrop);
    }

    // Create the airdrop as a barrel block placed in the world and fill it with loot
    private void createBarrel(Block block, FallingBlock airdrop) {
        Directional direction = (Directional) Material.BARREL.createBlockData();
        direction.setFacing(BlockFace.UP);
        block.setBlockData(direction);

        Barrel barrel = (Barrel) block.getState();
        Inventory inv = barrel.getInventory();
        Location loc = block.getLocation();

        String tier = airdropTier.get(airdrop);
        manager.fill(inv, tier.toUpperCase());
        manager.markAsAirdrop(loc);

        loc.getWorld().spawnParticle(Particle.EXPLOSION_HUGE, loc, 50, 1, 1, 1, 0.05);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1, 0.5F);
        }
    }

    // This is our help message sent when someone uses our command wrong
    public void helpMessage(CommandSender sender) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&8[&4&m &8] &4&m                                               &8 [&4&m &8]"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&7All airdrop commands require \"airdrops.admin\"."));
        sender.sendMessage("");
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&c/airdrop spawn &8- &7Spawns an airdrop at your location"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&c/airdrop reload &8- &7Reloads config.yml"));
        sender.sendMessage("");
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&8[&4&m &8] &4&m                                               &8 [&4&m &8]"));
    }

}
