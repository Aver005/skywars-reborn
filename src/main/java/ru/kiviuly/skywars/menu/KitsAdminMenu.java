package ru.kiviuly.skywars.menu;
import ru.kiviuly.mg.api.menu.Menu;
import ru.kiviuly.mg.api.menu.AnvilInputMenu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.kiviuly.skywars.SkyWarsPlugin;
import ru.kiviuly.skywars.kit.Kit;
import ru.kiviuly.mg.api.util.Items;
import ru.kiviuly.mg.api.util.Msg;

/**
 * Хаб настройки китов ({@code /sw kits}): список наборов, создание, дефолт, удаление,
 * переход в редактор предметов. ЛКМ — редактировать, ПКМ — сделать дефолтным,
 * Shift-клик — удалить.
 */
public class KitsAdminMenu extends Menu
{
    private static final int SLOT_DEFAULT_INFO = 45;
    private static final int SLOT_CREATE = 49;

    private final SkyWarsPlugin plugin;
    private final Map<Integer, String> idBySlot = new HashMap<>();

    public KitsAdminMenu(SkyWarsPlugin plugin)
    {
        super(54, Msg.get("kits-admin.title"));
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
        String def = plugin.kits().getDefault();

        int slot = 0;
        for (Kit k : plugin.kits().all())
        {
            if (slot >= PAGE_SIZE) {break;}
            ItemStack icon = k.menuIcon();
            ItemMeta meta = icon.getItemMeta();
            List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            lore.add(Items.flat(Msg.get("kits-admin.entry-id", Msg.ph("id", k.getId()))));
            lore.add(Items.flat(Msg.get("kits-admin.entry-items", Msg.ph("n", k.getItems().size()))));
            if (k.getId().equalsIgnoreCase(def)) {lore.add(Items.flat(Msg.get("kits-admin.entry-default")));}
            for (Component line : Msg.getList("kits-admin.entry-actions")) {lore.add(Items.flat(line));}
            meta.lore(lore);
            icon.setItemMeta(meta);
            inventory.setItem(slot, icon);
            idBySlot.put(slot, k.getId());
            slot++;
        }

        for (int s = PAGE_SIZE; s < inventory.getSize(); s++)
        {
            inventory.setItem(s, Items.filler(Material.BLACK_STAINED_GLASS_PANE));
        }
        inventory.setItem(SLOT_DEFAULT_INFO, Items.named(Material.PAPER, Msg.get("kits-admin.default-info", Msg.ph("kit", def))));
        inventory.setItem(SLOT_CREATE, Items.named(Material.NETHER_STAR, Msg.get("kits-admin.create-name"), Msg.getList("kits-admin.create-lore")));
    }

    @Override
    public void onClick(InventoryClickEvent e)
    {
        if (!(e.getWhoClicked() instanceof Player p)) {return;}
        int raw = e.getRawSlot();
        if (raw == SLOT_CREATE) {createKit(p); return;}
        String id = idBySlot.get(raw);
        if (id == null) {return;}
        Kit kit = plugin.kits().get(id);
        if (kit == null) {render(); return;}

        if (e.isShiftClick())
        {
            plugin.kits().remove(id);
            Msg.send(p, "kits-admin.deleted", Msg.ph("id", id));
            render();
        }
        else if (e.isRightClick())
        {
            plugin.kits().setDefault(id);
            Msg.send(p, "kits-admin.default-set", Msg.ph("id", id));
            render();
        }
        else
        {
            new KitEditorMenu(plugin, kit).open(p);
        }
    }

    private void createKit(Player p)
    {
        new AnvilInputMenu(plugin, Msg.get("kits-admin.create-anvil-title"), "", text ->
        {
            String id = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
            if (id.isEmpty())
            {
                Msg.send(p, "kits-admin.bad-id");
                new KitsAdminMenu(plugin).open(p);
                return;
            }
            if (plugin.kits().exists(id))
            {
                Msg.send(p, "kits-admin.exists", Msg.ph("id", id));
                new KitsAdminMenu(plugin).open(p);
                return;
            }
            Kit kit = plugin.kits().create(id);
            Msg.send(p, "kits-admin.created", Msg.ph("id", id));
            new KitEditorMenu(plugin, kit).open(p);
        }).open(p);
    }
}
