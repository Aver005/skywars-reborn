package ru.kiviuly.skywars.menu;
import ru.kiviuly.mg.api.menu.Menu;

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
import ru.kiviuly.mg.api.game.Match;
import ru.kiviuly.skywars.game.SkyWarsGame;
import ru.kiviuly.skywars.kit.Kit;
import ru.kiviuly.mg.api.util.Items;
import ru.kiviuly.mg.api.util.Msg;

/** Выбор стартового набора игроком в лобби. Выбор хранится в состоянии сессии. */
public class KitSelectMenu extends Menu
{
    private static final int SLOT_NONE = 48;
    private static final int SLOT_RANDOM = 50;

    private final SkyWarsPlugin plugin;
    private final Match session;
    private final Map<Integer, String> idBySlot = new HashMap<>();

    public KitSelectMenu(SkyWarsPlugin plugin, Match session)
    {
        super(54, Msg.get("kit-select.title"));
        this.plugin = plugin;
        this.session = session;
    }

    @Override
    public void open(Player p)
    {
        render(p);
        super.open(p);
    }

    private void render(Player p)
    {
        inventory.clear();
        idBySlot.clear();
        String choice = SkyWarsGame.kitChoice(session, p.getUniqueId());
        if (choice == null) {choice = plugin.kits().getDefault();}

        int slot = 0;
        for (Kit k : plugin.kits().all())
        {
            if (slot >= PAGE_SIZE) {break;}
            ItemStack icon = k.menuIcon();
            if (k.getId().equalsIgnoreCase(choice)) {icon = mark(icon);}
            inventory.setItem(slot, icon);
            idBySlot.put(slot, k.getId());
            slot++;
        }

        ItemStack none = Items.named(Material.BARRIER, Msg.get("kit-select.none-name"), Msg.getList("kit-select.none-lore"));
        if ("none".equalsIgnoreCase(choice)) {none = mark(none);}
        inventory.setItem(SLOT_NONE, none);

        ItemStack random = Items.named(Material.ENDER_PEARL, Msg.get("kit-select.random-name"), Msg.getList("kit-select.random-lore"));
        if ("random".equalsIgnoreCase(choice)) {random = mark(random);}
        inventory.setItem(SLOT_RANDOM, random);

        fillAll(Material.BLACK_STAINED_GLASS_PANE);
    }

    /** Пометить выбранный вариант дополнительной строкой лора. */
    private ItemStack mark(ItemStack icon)
    {
        ItemMeta meta = icon.getItemMeta();
        List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(Items.flat(Msg.get("kit-select.chosen-mark")));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    @Override
    public void onClick(InventoryClickEvent e)
    {
        if (!(e.getWhoClicked() instanceof Player p)) {return;}
        int raw = e.getRawSlot();
        String value;
        if (raw == SLOT_NONE) {value = "none";}
        else if (raw == SLOT_RANDOM) {value = "random";}
        else if (idBySlot.containsKey(raw)) {value = idBySlot.get(raw);}
        else {return;}

        SkyWarsGame.setKitChoice(session, p.getUniqueId(), value);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
        Msg.send(p, "kit-select.selected", Msg.phMm("kit", label(value)));
        render(p);
    }

    private String label(String value)
    {
        if ("none".equalsIgnoreCase(value)) {return Msg.raw("kit-select.none-name");}
        if ("random".equalsIgnoreCase(value)) {return Msg.raw("kit-select.random-name");}
        Kit k = plugin.kits().get(value);
        return k != null ? k.getNameRaw() : value;
    }
}
