package ru.kiviuly.skywars;

import java.sql.SQLException;

import org.bukkit.plugin.java.JavaPlugin;
import ru.kiviuly.skywars.arena.ArenaManager;
import ru.kiviuly.skywars.command.MinigameCommand;
import ru.kiviuly.skywars.game.Minigame;
import ru.kiviuly.skywars.game.SkyWarsGame;
import ru.kiviuly.skywars.kit.KitRegistry;
import ru.kiviuly.skywars.loot.LootRegistry;
import ru.kiviuly.skywars.listener.ChatListener;
import ru.kiviuly.skywars.listener.GameListener;
import ru.kiviuly.skywars.listener.ProtectionListener;
import ru.kiviuly.skywars.listener.SetupListener;
import ru.kiviuly.skywars.listener.SkyWarsListener;
import ru.kiviuly.skywars.menu.MenuListener;
import ru.kiviuly.skywars.stats.StatsRepository;
import ru.kiviuly.skywars.util.DebugLog;
import ru.kiviuly.skywars.util.DebugLog.Cat;
import ru.kiviuly.skywars.util.Keys;
import ru.kiviuly.skywars.util.Msg;

/**
 * SkyWars — соло last-man-standing на парящих островах. Каркас (арены, жизненный
 * цикл матча, меню, снапшоты, откат мира, стата, HUD) — обобщённый и игро-независимый;
 * правила самой игры живут в {@link ru.kiviuly.skywars.game.SkyWarsGame} (наследник
 * {@link Minigame}), подключённом в {@link #onEnable} через {@link #game}.
 */
public final class SkyWarsPlugin extends JavaPlugin
{
    private ArenaManager arenaManager;
    private StatsRepository statsRepository;
    private KitRegistry kitRegistry;
    private LootRegistry lootRegistry;
    private Minigame game;

    @Override
    public void onEnable()
    {
        saveDefaultConfig();
        Keys.init(this);
        Msg.init(this);
        DebugLog.init(this);

        arenaManager = new ArenaManager(this);
        statsRepository = new StatsRepository(this);
        try {statsRepository.open();}
        catch (SQLException e) {getLogger().severe("Failed to open stats.db: " + e.getMessage());}

        kitRegistry = new KitRegistry(this);
        kitRegistry.load();
        lootRegistry = new LootRegistry(this);
        lootRegistry.load();

        // Точка расширения каркаса: все правила SkyWars — в SkyWarsGame (наследник Minigame).
        game = new SkyWarsGame(this);

        arenaManager.loadAll();

        var pm = getServer().getPluginManager();
        pm.registerEvents(new MenuListener(), this);
        pm.registerEvents(new GameListener(this), this);
        pm.registerEvents(new ProtectionListener(this), this);
        pm.registerEvents(new ChatListener(this), this);
        pm.registerEvents(new SetupListener(this), this);
        pm.registerEvents(new SkyWarsListener(this), this);

        MinigameCommand command = new MinigameCommand(this);
        var mg = getCommand("sw");
        mg.setExecutor(command);
        mg.setTabCompleter(command);

        getLogger().info("SkyWars enabled, arenas loaded: " + arenaManager.all().size() + ", game: " + game.id());
        DebugLog.log(Cat.ADMIN, "plugin enable arenas=%d game=%s", arenaManager.all().size(), game.id());
    }

    @Override
    public void onDisable()
    {
        DebugLog.log(Cat.ADMIN, "plugin disable");
        if (arenaManager != null) {arenaManager.stopAll();}
        saveEverything();
        if (statsRepository != null) {statsRepository.close();}
    }

    public void saveEverything()
    {
        if (arenaManager != null) {arenaManager.saveAll();}
    }

    public void reloadEverything()
    {
        reloadConfig();
        Msg.reload();
        DebugLog.reload();
        arenaManager.loadAll();
        kitRegistry.load();
        lootRegistry.load();
    }

    public ArenaManager arenas() {return arenaManager;}
    public StatsRepository stats() {return statsRepository;}
    public KitRegistry kits() {return kitRegistry;}
    public LootRegistry loot() {return lootRegistry;}
    public Minigame game() {return game;}
}
