package ru.kiviuly.skywars.menu;
import ru.kiviuly.mg.api.menu.Menu;
import ru.kiviuly.mg.api.menu.AnvilInputMenu;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import ru.kiviuly.skywars.SkyWarsPlugin;
import ru.kiviuly.skywars.kit.Kit;
import ru.kiviuly.mg.api.util.Items;
import ru.kiviuly.mg.api.util.Msg;

/**
 * Редактор предметов кита: админ раскладывает реальные предметы в верхней области
 * (слоты 0..44), нижний ряд — кнопки. Предметы захватываются в кит при закрытии/
 * действии (используйте креатив). Броня определяется по материалу при выдаче.
 */
public class KitEditorMenu extends Menu
{
    private static final int CONTENT = 45;   // 0..44 — предметы кита
    private static final int SLOT_BACK = 45;
    private static final int SLOT_NAME = 47;
    private static final int SLOT_ICON = 49;
    private static final int SLOT_DONE = 51;

    private final SkyWarsPlugin plugin;
    private final Kit kit;

    public KitEditorMenu(SkyWarsPlugin plugin, Kit kit)
    {
        super(54, Msg.get("kit-edit.title").append(Component.text(kit.getId())));
        this.plugin = plugin;
        this.kit = kit;
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
        for (ItemStack item : kit.getItems())
        {
            if (i >= CONTENT) {break;}
            if (item != null && !item.getType().isAir()) {inventory.setItem(i, item.clone());}
            i++;
        }
        for (int s = CONTENT; s < inventory.getSize(); s++)
        {
            inventory.setItem(s, Items.filler(Material.GRAY_STAINED_GLASS_PANE));
        }
        inventory.setItem(SLOT_BACK, Items.named(Material.OAK_DOOR, Msg.get("menu.back")));
        inventory.setItem(SLOT_NAME, Items.named(Material.NAME_TAG, Msg.get("kit-edit.name-btn"), Msg.getList("kit-edit.name-lore")));
        inventory.setItem(SLOT_ICON, Items.named(Material.ITEM_FRAME, Msg.get("kit-edit.icon-btn"), Msg.getList("kit-edit.icon-lore")));
        inventory.setItem(SLOT_DONE, Items.named(Material.LIME_CONCRETE, Msg.get("kit-edit.done-btn"), Msg.getList("kit-edit.done-lore")));
    }

    /** Считать предметы из верхней области (0..44) в кит. */
    private void captureItems()
    {
        kit.getItems().clear();
        for (int i = 0; i < CONTENT; i++)
        {
            ItemStack it = inventory.getItem(i);
            if (it != null && !it.getType().isAir()) {kit.getItems().add(it.clone());}
        }
    }

    @Override
    public void onClick(InventoryClickEvent e)
    {
        if (!(e.getWhoClicked() instanceof Player p)) {return;}
        int raw = e.getRawSlot();
        if (raw < CONTENT || raw >= inventory.getSize()) {return;} // контент/свой инвентарь — свободно
        switch (raw)
        {
            case SLOT_BACK, SLOT_DONE -> new KitsAdminMenu(plugin).open(p); // закрытие сохранит (onClose)
            case SLOT_NAME -> editName(p);
            case SLOT_ICON -> setIconFromFirst(p);
            default -> {}
        }
    }

    private void setIconFromFirst(Player p)
    {
        ItemStack first = inventory.getItem(0);
        if (first == null || first.getType().isAir()) {Msg.send(p, "kit-edit.icon-empty"); return;}
        captureItems();
        kit.setIcon(first.getType());
        plugin.kits().save();
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
        Msg.send(p, "kit-edit.icon-set", Msg.ph("material", first.getType().name()));
    }

    private void editName(Player p)
    {
        captureItems();
        plugin.kits().save();
        new AnvilInputMenu(plugin, Msg.get("kit-edit.name-anvil-title"), kit.getNameRaw(), text ->
        {
            kit.setNameRaw(text);
            plugin.kits().save();
            Msg.send(p, "kit-edit.name-set", Msg.phMm("name", text));
            new KitEditorMenu(plugin, kit).open(p);
        }).open(p);
    }

    @Override
    public void onClose(InventoryCloseEvent e)
    {
        captureItems();
        plugin.kits().save();
    }
}
