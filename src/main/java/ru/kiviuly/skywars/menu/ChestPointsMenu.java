package ru.kiviuly.skywars.menu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.kiviuly.skywars.SkyWarsPlugin;
import ru.kiviuly.skywars.arena.Arena;
import ru.kiviuly.skywars.arena.SetupMarkers;
import ru.kiviuly.skywars.loot.LootCategory;
import ru.kiviuly.skywars.util.Items;
import ru.kiviuly.skywars.util.Msg;

/**
 * Точки-сундуки арены: выбери категорию лута — получишь маркер сундука для этой
 * категории. ПКМ маркером по блоку = точка-сундук (Shift+ПКМ убирает точку целиком).
 */
public class ChestPointsMenu extends Menu
{
    private static final int SLOT_COUNT = 45;
    private static final int SLOT_BACK = 49;

    private final SkyWarsPlugin plugin;
    private final Arena arena;
    private final Map<Integer, String> idBySlot = new HashMap<>();

    public ChestPointsMenu(SkyWarsPlugin plugin, Arena arena)
    {
        super(54, Msg.get("chest-points.title").append(Component.text(arena.getId())));
        this.plugin = plugin;
        this.arena = arena;
    }

    @Override
    public void open(Player p)
    {
        render();
        super.open(p);
    }

    private void render()
    {
        inventory.clear();
        idBySlot.clear();
        int slot = 0;
        for (LootCategory c : plugin.loot().all())
        {
            if (slot >= PAGE_SIZE) {break;}
            ItemStack icon = c.menuIcon();
            ItemMeta meta = icon.getItemMeta();
            List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            for (Component line : Msg.getList("chest-points.entry-actions")) {lore.add(Items.flat(line));}
            meta.lore(lore);
            icon.setItemMeta(meta);
            inventory.setItem(slot, icon);
            idBySlot.put(slot, c.getId());
            slot++;
        }
        if (plugin.loot().isEmpty())
        {
            inventory.setItem(22, Items.named(Material.BARRIER, Msg.get("chest-points.empty-name"), Msg.getList("chest-points.empty-lore")));
        }
        for (int s = PAGE_SIZE; s < inventory.getSize(); s++)
        {
            inventory.setItem(s, Items.filler(Material.BLACK_STAINED_GLASS_PANE));
        }
        inventory.setItem(SLOT_COUNT, Items.named(Material.CHEST, Msg.get("chest-points.count", Msg.ph("n", arena.getChestSpots().size()))));
        inventory.setItem(SLOT_BACK, Items.named(Material.OAK_DOOR, Msg.get("menu.back")));
    }

    @Override
    public void onClick(InventoryClickEvent e)
    {
        if (!(e.getWhoClicked() instanceof Player p)) {return;}
        int raw = e.getRawSlot();
        if (raw == SLOT_BACK) {new ArenaPointsMenu(plugin, arena).open(p); return;}
        String id = idBySlot.get(raw);
        if (id == null) {return;}
        if (plugin.loot().get(id) == null) {render(); return;}
        p.getInventory().addItem(SetupMarkers.markerItem(arena, "chest", Material.CHEST, id));
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.2f);
        Msg.send(p, "chest-points.given", Msg.ph("category", id));
        p.closeInventory();
    }
}
