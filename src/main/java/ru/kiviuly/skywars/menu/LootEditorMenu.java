package ru.kiviuly.skywars.menu;
import ru.kiviuly.mg.api.menu.Menu;
import ru.kiviuly.mg.api.menu.AnvilInputMenu;

import java.util.ArrayList;
import java.util.List;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import ru.kiviuly.skywars.SkyWarsPlugin;
import ru.kiviuly.skywars.loot.LootCategory;
import ru.kiviuly.skywars.loot.WeightedItem;
import ru.kiviuly.mg.api.util.Items;
import ru.kiviuly.mg.api.util.Msg;

/**
 * Редактор категории лута: предметы кладутся в верхнюю область (0..44), нижний ряд —
 * кнопки настроек (min/max слотов на сундук, рефилл) + имя/иконка. Предметы
 * захватываются при закрытии/действии (используйте креатив). GUI пишет вес 1 у всех
 * предметов — для кастомных весов правьте loot.yml.
 */
public class LootEditorMenu extends Menu
{
    private static final int CONTENT = 45;
    private static final int SLOT_BACK = 45;
    private static final int SLOT_MIN = 46;
    private static final int SLOT_MAX = 47;
    private static final int SLOT_REFILL = 48;
    private static final int SLOT_NAME = 50;
    private static final int SLOT_ICON = 51;
    private static final int SLOT_DONE = 52;

    private final SkyWarsPlugin plugin;
    private final LootCategory cat;

    public LootEditorMenu(SkyWarsPlugin plugin, LootCategory cat)
    {
        super(54, Msg.get("loot-edit.title").append(Component.text(cat.getId())));
        this.plugin = plugin;
        this.cat = cat;
    }

    @Override
    public boolean allowsInteraction() {return true;}

    @Override
    public boolean isProtectedSlot(int slot) {return slot >= CONTENT;}

    @Override
    public void open(Player p)
    {
        render();
        super.open(p);
    }

    private void render()
    {
        inventory.clear();
        int i = 0;
        for (WeightedItem w : cat.getLoot())
        {
            if (i >= CONTENT) {break;}
            if (w.item() != null && !w.item().getType().isAir()) {inventory.setItem(i, w.item().clone());}
            i++;
        }
        inventory.setItem(SLOT_BACK, Items.named(Material.OAK_DOOR, Msg.get("menu.back")));
        inventory.setItem(SLOT_MIN, settingBtn(Material.HOPPER, "min", cat.getMinPerChest()));
        inventory.setItem(SLOT_MAX, settingBtn(Material.CHEST, "max", cat.getMaxPerChest()));
        inventory.setItem(SLOT_REFILL, settingBtn(Material.CLOCK, "refill", cat.getRefillSeconds()));
        inventory.setItem(SLOT_NAME, Items.named(Material.NAME_TAG, Msg.get("loot-edit.name-btn"), Msg.getList("loot-edit.name-lore")));
        inventory.setItem(SLOT_ICON, Items.named(Material.ITEM_FRAME, Msg.get("loot-edit.icon-btn"), Msg.getList("loot-edit.icon-lore")));
        inventory.setItem(SLOT_DONE, Items.named(Material.LIME_CONCRETE, Msg.get("loot-edit.done-btn"), Msg.getList("loot-edit.done-lore")));
        if (inventory.getItem(49) == null) {inventory.setItem(49, Items.filler(Material.GRAY_STAINED_GLASS_PANE));}
        if (inventory.getItem(53) == null) {inventory.setItem(53, Items.filler(Material.GRAY_STAINED_GLASS_PANE));}
    }

    private ItemStack settingBtn(Material mat, String key, int value)
    {
        List<Component> lore = new ArrayList<>();
        lore.add(Msg.get("loot-edit.value", Msg.ph("value", value)));
        lore.addAll(Msg.getList("loot-edit.adjust"));
        return Items.named(mat, Msg.get("loot-edit.name-" + key), lore);
    }

    private void captureItems()
    {
        cat.getLoot().clear();
        for (int i = 0; i < CONTENT; i++)
        {
            ItemStack it = inventory.getItem(i);
            if (it != null && !it.getType().isAir()) {cat.getLoot().add(new WeightedItem(it.clone(), 1));}
        }
    }

    @Override
    public void onClick(InventoryClickEvent e)
    {
        if (!(e.getWhoClicked() instanceof Player p)) {return;}
        int raw = e.getRawSlot();
        if (raw < CONTENT || raw >= inventory.getSize()) {return;}
        int delta = (e.isLeftClick() ? 1 : -1) * (e.isShiftClick() ? 10 : 1);
        switch (raw)
        {
            case SLOT_MIN -> adjust(p, () -> cat.setMinPerChest(cat.getMinPerChest() + delta));
            case SLOT_MAX -> adjust(p, () -> cat.setMaxPerChest(cat.getMaxPerChest() + delta));
            case SLOT_REFILL -> adjust(p, () -> cat.setRefillSeconds(cat.getRefillSeconds() + delta));
            case SLOT_BACK, SLOT_DONE -> new LootAdminMenu(plugin).open(p);
            case SLOT_NAME -> editName(p);
            case SLOT_ICON -> setIconFromFirst(p);
            default -> {}
        }
    }

    private void adjust(Player p, Runnable change)
    {
        captureItems();
        change.run();
        plugin.loot().save();
        render();
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.2f);
    }

    private void setIconFromFirst(Player p)
    {
        ItemStack first = inventory.getItem(0);
        if (first == null || first.getType().isAir()) {Msg.send(p, "loot-edit.icon-empty"); return;}
        captureItems();
        cat.setIcon(first.getType());
        plugin.loot().save();
        Msg.send(p, "loot-edit.icon-set", Msg.ph("material", first.getType().name()));
    }

    private void editName(Player p)
    {
        captureItems();
        plugin.loot().save();
        new AnvilInputMenu(plugin, Msg.get("loot-edit.name-anvil-title"), cat.getNameRaw(), text ->
        {
            cat.setNameRaw(text);
            plugin.loot().save();
            Msg.send(p, "loot-edit.name-set", Msg.phMm("name", text));
            new LootEditorMenu(plugin, cat).open(p);
        }).open(p);
    }

    @Override
    public void onClose(InventoryCloseEvent e)
    {
        captureItems();
        plugin.loot().save();
    }
}
