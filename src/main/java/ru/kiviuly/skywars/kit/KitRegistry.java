package ru.kiviuly.skywars.kit;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.kiviuly.skywars.SkyWarsPlugin;

/**
 * Глобальный реестр китов (общий для всех арен). Один файл {@code kits.yml}:
 * секции {@code kits.<id>} + верхний ключ {@code default-kit} ({@code random} /
 * {@code none} / id кита). Редактируется командами/GUI и пересохраняется здесь же.
 */
public class KitRegistry
{
    private final SkyWarsPlugin plugin;
    private final Map<String, Kit> kits = new LinkedHashMap<>();
    private String defaultKit = "random";

    public KitRegistry(SkyWarsPlugin plugin) {this.plugin = plugin;}

    private File file() {return new File(plugin.getDataFolder(), "kits.yml");}

    public void load()
    {
        kits.clear();
        File f = file();
        if (!f.exists()) {plugin.saveResource("kits.yml", false);}
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
        defaultKit = cfg.getString("default-kit", "random");
        ConfigurationSection sec = cfg.getConfigurationSection("kits");
        if (sec != null)
        {
            for (String id : sec.getKeys(false))
            {
                ConfigurationSection ks = sec.getConfigurationSection(id);
                if (ks != null) {kits.put(id, Kit.load(id, ks));}
            }
        }
    }

    public void save()
    {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("default-kit", defaultKit);
        ConfigurationSection sec = cfg.createSection("kits");
        for (Kit k : kits.values()) {k.save(sec.createSection(k.getId()));}
        try {cfg.save(file());}
        catch (IOException e) {plugin.getLogger().severe("Failed to save kits.yml: " + e.getMessage());}
    }

    // ===== доступ =====

    public Collection<Kit> all() {return kits.values();}
    public Kit get(String id) {return id == null ? null : kits.get(id.toLowerCase());}
    public boolean exists(String id) {return id != null && kits.containsKey(id.toLowerCase());}
    public Set<String> ids() {return kits.keySet();}
    public boolean isEmpty() {return kits.isEmpty();}
    public String getDefault() {return defaultKit;}

    public void setDefault(String id)
    {
        this.defaultKit = id;
        save();
    }

    /** Создать пустой кит (id уже нормализован в нижний регистр вызывающим). */
    public Kit create(String id)
    {
        Kit k = new Kit(id);
        kits.put(id, k);
        save();
        return k;
    }

    public boolean remove(String id)
    {
        if (id == null) {return false;}
        boolean removed = kits.remove(id.toLowerCase()) != null;
        if (removed)
        {
            if (id.equalsIgnoreCase(defaultKit)) {defaultKit = "random";}
            save();
        }
        return removed;
    }

    /**
     * Разрешить выбор игрока в конкретный кит: {@code null} → дефолт арены/реестра;
     * {@code none} → без кита (null); {@code random} → случайный; иначе — по id.
     * Возвращает {@code null}, если китов нет или выбор не даёт набора.
     */
    public Kit resolve(String choice, Random rnd)
    {
        if (kits.isEmpty()) {return null;}
        if (choice == null) {choice = defaultKit;}
        if (choice == null || choice.equalsIgnoreCase("none")) {return null;}
        if (choice.equalsIgnoreCase("random"))
        {
            List<Kit> list = new ArrayList<>(kits.values());
            return list.get(rnd.nextInt(list.size()));
        }
        return get(choice);
    }
}
