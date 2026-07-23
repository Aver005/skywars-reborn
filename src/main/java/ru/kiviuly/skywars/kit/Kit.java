package ru.kiviuly.skywars.kit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import ru.kiviuly.mg.api.util.Items;
import ru.kiviuly.mg.api.util.Msg;

/**
 * Стартовый набор (кит): предметы + иконка/имя/лор для меню выбора. Глобальный —
 * общий для всех арен (реестр {@link KitRegistry}). Броня при выдаче автоматически
 * надевается в пустые слоты по суффиксу материала, остальное кладётся в инвентарь.
 *
 * На диске предметы допускают два формата (оба читает {@link #load}): рукописная
 * спека (по {@link Items#fromSpec}, ключ {@code type}) ИЛИ сериализованный ItemStack
 * (ключ {@code item}). {@link #save} всегда пишет сериализованную форму (её же
 * выдаёт GUI-редактор), поэтому рукописные спеки живут до первого пересохранения.
 */
public class Kit
{
    private final String id;
    private String nameRaw;
    private Material icon = Material.CHEST;
    private List<String> loreRaw = new ArrayList<>();
    private final List<ItemStack> items = new ArrayList<>();

    public Kit(String id)
    {
        this.id = id;
        this.nameRaw = id;
    }

    public String getId() {return id;}
    public String getNameRaw() {return nameRaw;}
    public void setNameRaw(String v) {this.nameRaw = v;}
    public Material getIcon() {return icon;}
    public void setIcon(Material v) {this.icon = v;}
    public List<String> getLoreRaw() {return loreRaw;}
    public void setLoreRaw(List<String> v) {this.loreRaw = v;}
    public List<ItemStack> getItems() {return items;}

    /** Иконка кита для меню (имя/лор — MiniMessage). */
    public ItemStack menuIcon()
    {
        List<Component> lore = new ArrayList<>();
        for (String line : loreRaw) {lore.add(Items.flat(Msg.mm(line)));}
        return Items.named(icon, Items.flat(Msg.mm(nameRaw)), lore);
    }

    /** Выдать набор игроку: броню — в пустые слоты брони, остальное — в инвентарь. */
    public void apply(Player p)
    {
        PlayerInventory inv = p.getInventory();
        for (ItemStack raw : items)
        {
            if (raw == null || raw.getType().isAir()) {continue;}
            ItemStack item = raw.clone();
            EquipmentSlot slot = armorSlot(item.getType());
            if (slot != null && isEmpty(inv, slot)) {inv.setItem(slot, item);}
            else {inv.addItem(item);}
        }
    }

    /** Слот брони по материалу (или null — обычный предмет). */
    private static EquipmentSlot armorSlot(Material m)
    {
        String n = m.name();
        if (n.endsWith("_HELMET") || n.endsWith("_HEAD") || n.endsWith("_SKULL") || m == Material.CARVED_PUMPKIN)
        {
            return EquipmentSlot.HEAD;
        }
        if (n.endsWith("_CHESTPLATE") || m == Material.ELYTRA) {return EquipmentSlot.CHEST;}
        if (n.endsWith("_LEGGINGS")) {return EquipmentSlot.LEGS;}
        if (n.endsWith("_BOOTS")) {return EquipmentSlot.FEET;}
        return null;
    }

    private static boolean isEmpty(PlayerInventory inv, EquipmentSlot slot)
    {
        ItemStack cur = inv.getItem(slot);
        return cur == null || cur.getType().isAir();
    }

    // ===== persistence =====

    public static Kit load(String id, ConfigurationSection sec)
    {
        Kit k = new Kit(id);
        k.nameRaw = sec.getString("name", id);
        Material m = Material.matchMaterial(sec.getString("icon", "CHEST"));
        if (m != null && m.isItem()) {k.icon = m;}
        k.loreRaw = new ArrayList<>(sec.getStringList("lore"));
        for (Map<?, ?> entry : sec.getMapList("items"))
        {
            ItemStack item = null;
            if (entry.get("type") instanceof String) {item = Items.fromSpec(entry);}
            else if (entry.get("item") instanceof Map<?, ?> ser) {item = ItemStack.deserialize(castMap(ser));}
            if (item != null && !item.getType().isAir()) {k.items.add(item);}
        }
        return k;
    }

    public void save(ConfigurationSection sec)
    {
        sec.set("name", nameRaw);
        sec.set("icon", icon.name());
        sec.set("lore", loreRaw);
        List<Map<String, Object>> out = new ArrayList<>();
        for (ItemStack item : items)
        {
            if (item == null || item.getType().isAir()) {continue;}
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("item", item.serialize());
            out.add(row);
        }
        sec.set("items", out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> m)
    {
        return (Map<String, Object>) m;
    }
}
