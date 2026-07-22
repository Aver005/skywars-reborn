package ru.kiviuly.skywars.loot;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.kiviuly.skywars.SkyWarsPlugin;

/**
 * Глобальный реестр категорий лута (общий для всех арен). Один файл {@code loot.yml}:
 * секции {@code categories.<id>}. Точки-сундуки арен ссылаются на категории по id.
 */
public class LootRegistry
{
    private final SkyWarsPlugin plugin;
    private final Map<String, LootCategory> categories = new LinkedHashMap<>();

    public LootRegistry(SkyWarsPlugin plugin) {this.plugin = plugin;}

    private File file() {return new File(plugin.getDataFolder(), "loot.yml");}

    public void load()
    {
        categories.clear();
        File f = file();
        if (!f.exists()) {plugin.saveResource("loot.yml", false);}
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection sec = cfg.getConfigurationSection("categories");
        if (sec != null)
        {
            for (String id : sec.getKeys(false))
            {
                ConfigurationSection cs = sec.getConfigurationSection(id);
                if (cs != null) {categories.put(id, LootCategory.load(id, cs));}
            }
        }
    }

    public void save()
    {
        YamlConfiguration cfg = new YamlConfiguration();
        ConfigurationSection sec = cfg.createSection("categories");
        for (LootCategory c : categories.values()) {c.save(sec.createSection(c.getId()));}
        try {cfg.save(file());}
        catch (IOException e) {plugin.getLogger().severe("Failed to save loot.yml: " + e.getMessage());}
    }

    public Collection<LootCategory> all() {return categories.values();}
    public LootCategory get(String id) {return id == null ? null : categories.get(id.toLowerCase());}
    public boolean exists(String id) {return id != null && categories.containsKey(id.toLowerCase());}
    public Set<String> ids() {return categories.keySet();}
    public boolean isEmpty() {return categories.isEmpty();}

    public LootCategory create(String id)
    {
        LootCategory c = new LootCategory(id);
        categories.put(id, c);
        save();
        return c;
    }

    public boolean remove(String id)
    {
        if (id == null) {return false;}
        boolean removed = categories.remove(id.toLowerCase()) != null;
        if (removed) {save();}
        return removed;
    }
}
