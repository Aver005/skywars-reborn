package ru.kiviuly.skywars.menu;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import ru.kiviuly.skywars.SkyWarsPlugin;
import ru.kiviuly.skywars.loot.LootCategory;
import ru.kiviuly.skywars.util.Items;
import ru.kiviuly.skywars.util.Msg;

/**
 * Хаб категорий лута ({@code /sw loot}): список глобальных категорий, создание,
 * удаление, переход в редактор. ЛКМ — редактировать, Shift-клик — удалить.
 */
public class LootAdminMenu extends Menu
{
    private static final int SLOT_CREATE = 49;

    private final SkyWarsPlugin plugin;
    private final Map<Integer, String> idBySlot = new HashMap<>();

    public LootAdminMenu(SkyWarsPlugin plugin)
    {
        super(54, Msg.get("loot-admin.title"));
        this.plugin = plugin;
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
            inventory.setItem(slot, c.menuIcon());
            idBySlot.put(slot, c.getId());
            slot++;
        }
        for (int s = PAGE_SIZE; s < inventory.getSize(); s++)
        {
            inventory.setItem(s, Items.filler(Material.BLACK_STAINED_GLASS_PANE));
        }
        inventory.setItem(SLOT_CREATE, Items.named(Material.NETHER_STAR, Msg.get("loot-admin.create-name"), Msg.getList("loot-admin.create-lore")));
    }

    @Override
    public void onClick(InventoryClickEvent e)
    {
        if (!(e.getWhoClicked() instanceof Player p)) {return;}
        int raw = e.getRawSlot();
        if (raw == SLOT_CREATE) {createCategory(p); return;}
        String id = idBySlot.get(raw);
        if (id == null) {return;}
        LootCategory c = plugin.loot().get(id);
        if (c == null) {render(); return;}

        if (e.isShiftClick())
        {
            plugin.loot().remove(id);
            Msg.send(p, "loot-admin.deleted", Msg.ph("id", id));
            render();
        }
        else
        {
            new LootEditorMenu(plugin, c).open(p);
        }
    }

    private void createCategory(Player p)
    {
        new AnvilInputMenu(plugin, Msg.get("loot-admin.create-anvil-title"), "", text ->
        {
            String id = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
            if (id.isEmpty())
            {
                Msg.send(p, "loot-admin.bad-id");
                new LootAdminMenu(plugin).open(p);
                return;
            }
            if (plugin.loot().exists(id))
            {
                Msg.send(p, "loot-admin.exists", Msg.ph("id", id));
                new LootAdminMenu(plugin).open(p);
                return;
            }
            LootCategory c = plugin.loot().create(id);
            Msg.send(p, "loot-admin.created", Msg.ph("id", id));
            new LootEditorMenu(plugin, c).open(p);
        }).open(p);
    }
}
