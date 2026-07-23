package ru.kiviuly.skywars.listener;

import org.bukkit.block.Chest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import ru.kiviuly.mg.api.game.GamePhase;
import ru.kiviuly.mg.api.game.Match;
import ru.kiviuly.mg.api.game.MatchPlayer;
import ru.kiviuly.mg.api.util.Items;
import ru.kiviuly.skywars.SkyWarsPlugin;
import ru.kiviuly.skywars.game.SkyWarsGame;
import ru.kiviuly.skywars.menu.KitSelectMenu;

/**
 * SkyWars-специфичные события (держим вне игро-независимого ядра): селектор кита в
 * лобби и рефилл сундуков (по закрытию сундука отдаём событие игре). Ссылку на игру
 * держим напрямую — в контракте {@link Match} метода {@code game()} нет.
 */
public class SkyWarsListener implements Listener
{
    private final SkyWarsPlugin plugin;
    private final SkyWarsGame game;

    public SkyWarsListener(SkyWarsPlugin plugin, SkyWarsGame game)
    {
        this.plugin = plugin;
        this.game = game;
    }

    /** ПКМ по селектору кита в лобби — открыть меню выбора набора. */
    @EventHandler
    public void onInteract(PlayerInteractEvent e)
    {
        Player p = e.getPlayer();
        if (!Items.isSpecial(e.getItem(), "kit-select")) {return;}
        e.setCancelled(true);
        Match s = plugin.arenas().sessionOf(p);
        if (s != null && s.acceptsPlayers()) {new KitSelectMenu(plugin, s).open(p);}
    }

    /** Закрыт сундук матча — дать игре запланировать рефилл (если включён). */
    @EventHandler
    public void onChestClose(InventoryCloseEvent e)
    {
        if (!(e.getInventory().getHolder() instanceof Chest chest)) {return;}
        if (!(e.getPlayer() instanceof Player p)) {return;}
        Match s = plugin.arenas().sessionOf(p);
        if (s != null) {game.onChestClosed(s, chest.getBlock());}
    }

    /** Учёт урона в матче (для HUD/итогов): PvP-удар между живыми участниками. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPvpDamage(EntityDamageByEntityEvent e)
    {
        if (!(e.getEntity() instanceof Player victim)) {return;}
        Player damager = resolveDamager(e.getDamager());
        if (damager == null || damager == victim) {return;}
        Match s = plugin.arenas().sessionOf(victim);
        if (s == null || s.phase() != GamePhase.RUNNING) {return;}
        if (plugin.arenas().sessionOf(damager) != s) {return;}
        MatchPlayer vm = s.player(victim.getUniqueId());
        MatchPlayer dm = s.player(damager.getUniqueId());
        if (vm == null || dm == null || !vm.isAlive() || !dm.isAlive()) {return;}
        game.recordMatchDamage(s, damager.getUniqueId(), e.getFinalDamage());
    }

    /** Игрок-источник урона (прямой или через снаряд). */
    private Player resolveDamager(Entity damager)
    {
        if (damager instanceof Player p) {return p;}
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) {return p;}
        return null;
    }
}
