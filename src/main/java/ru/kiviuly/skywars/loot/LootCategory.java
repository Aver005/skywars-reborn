package ru.kiviuly.skywars.loot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import ru.kiviuly.skywars.util.Items;
import ru.kiviuly.skywars.util.Msg;

/**
 * Категория лута: именованный взвешенный пул предметов + сколько слотов сундука ею
 * заполнять ({@code min/max-per-chest}) и через сколько секунд пополнять пустой сундук
 * ({@code refill-seconds}, 0 = не пополнять). Категории глобальные; точки-сундуки арены
 * ссылаются на них по id (у точки может быть несколько категорий).
 *
 * Предметы на диске — в двух форматах (как у китов): спека ({@code type}) или
 * сериализованный ItemStack ({@code item}); {@link #save} пишет сериализованный.
 */
public class LootCategory
{
    public static final int CHEST_SLOTS = 27;

    private final String id;
    private String nameRaw;
    private Material icon = Material.CHEST;
    private int minPerChest = 3;
    private int maxPerChest = 6;
    private int refillSeconds = 0;
    private final List<WeightedItem> loot = new ArrayList<>();

    public LootCategory(String id)
    {
        this.id = id;
        this.nameRaw = id;
    }

    public String getId() {return id;}
    public String getNameRaw() {return nameRaw;}
    public void setNameRaw(String v) {this.nameRaw = v;}
    public Material getIcon() {return icon;}
    public void setIcon(Material v) {this.icon = v;}
    public int getMinPerChest() {return minPerChest;}
    public void setMinPerChest(int v) {this.minPerChest = clamp(v, 0, CHEST_SLOTS);}
    public int getMaxPerChest() {return maxPerChest;}
    public void setMaxPerChest(int v) {this.maxPerChest = clamp(v, 0, CHEST_SLOTS);}
    public int getRefillSeconds() {return refillSeconds;}
    public void setRefillSeconds(int v) {this.refillSeconds = Math.max(0, v);}
    public List<WeightedItem> getLoot() {return loot;}

    /** Иконка категории для меню. */
    public ItemStack menuIcon()
    {
        List<Component> lore = new ArrayList<>();
        lore.add(Items.flat(Msg.get("loot-admin.entry-id", Msg.ph("id", id))));
        lore.add(Items.flat(Msg.get("loot-admin.entry-items", Msg.ph("n", loot.size()))));
        lore.add(Items.flat(Msg.get("loot-admin.entry-per-chest", Msg.ph("min", minPerChest), Msg.ph("max", maxPerChest))));
        lore.add(Items.flat(Msg.get("loot-admin.entry-refill",
            Msg.ph("n", refillSeconds == 0 ? Msg.raw("loot-admin.refill-off") : refillSeconds + "s"))));
        return Items.named(icon, Items.flat(Msg.mm(nameRaw)), lore);
    }

    /** Сколько слотов сундука заполнить этой категорией за один заход. */
    public int rollSlotCount(Random r)
    {
        int min = clamp(minPerChest, 0, CHEST_SLOTS);
        int max = clamp(maxPerChest, min, CHEST_SLOTS);
        return max > min ? min + r.nextInt(max - min + 1) : min;
    }

    /** Взвешенный выбор одного предмета (клон) или null, если пул пуст. */
    public ItemStack pickItem(Random r)
    {
        if (loot.isEmpty()) {return null;}
        int total = 0;
        for (WeightedItem w : loot) {total += Math.max(1, w.weight());}
        int roll = r.nextInt(total);
        for (WeightedItem w : loot)
        {
            roll -= Math.max(1, w.weight());
            if (roll < 0) {return w.item().clone();}
        }
        return loot.get(loot.size() - 1).item().clone();
    }

    private static int clamp(int v, int lo, int hi) {return Math.max(lo, Math.min(hi, v));}

    // ===== persistence =====

    public static LootCategory load(String id, ConfigurationSection sec)
    {
        LootCategory c = new LootCategory(id);
        c.nameRaw = sec.getString("name", id);
        Material m = Material.matchMaterial(sec.getString("icon", "CHEST"));
        if (m != null && m.isItem()) {c.icon = m;}
        c.minPerChest = clamp(sec.getInt("min-per-chest", 3), 0, CHEST_SLOTS);
        c.maxPerChest = clamp(sec.getInt("max-per-chest", 6), 0, CHEST_SLOTS);
        c.refillSeconds = Math.max(0, sec.getInt("refill-seconds", 0));
        for (Map<?, ?> entry : sec.getMapList("loot"))
        {
            int w = entry.get("weight") instanceof Number n ? Math.max(1, n.intValue()) : 1;
            ItemStack item = null;
            if (entry.get("type") instanceof String) {item = Items.fromSpec(entry);}
            else if (entry.get("item") instanceof Map<?, ?> ser) {item = ItemStack.deserialize(castMap(ser));}
            if (item != null && !item.getType().isAir()) {c.loot.add(new WeightedItem(item, w));}
        }
        return c;
    }

    public void save(ConfigurationSection sec)
    {
        sec.set("name", nameRaw);
        sec.set("icon", icon.name());
        sec.set("min-per-chest", minPerChest);
        sec.set("max-per-chest", maxPerChest);
        sec.set("refill-seconds", refillSeconds);
        List<Map<String, Object>> out = new ArrayList<>();
        for (WeightedItem w : loot)
        {
            if (w.item() == null || w.item().getType().isAir()) {continue;}
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("item", w.item().serialize());
            row.put("weight", w.weight());
            out.add(row);
        }
        sec.set("loot", out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> m)
    {
        return (Map<String, Object>) m;
    }
}
