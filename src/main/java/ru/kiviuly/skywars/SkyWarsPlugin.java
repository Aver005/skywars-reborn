package ru.kiviuly.skywars;

import org.bukkit.plugin.java.JavaPlugin;
import ru.kiviuly.mg.api.MgCore;
import ru.kiviuly.mg.api.arena.ArenaService;
import ru.kiviuly.mg.api.util.Msg;
import ru.kiviuly.skywars.game.SkyWarsGame;
import ru.kiviuly.skywars.kit.KitRegistry;
import ru.kiviuly.skywars.listener.SkyWarsListener;
import ru.kiviuly.skywars.loot.LootRegistry;

/**
 * SkyWars — тонкий игровой плагин поверх платформы MgCore. Каркас (арены, жизненный
 * цикл матча, меню, снапшоты, откат мира, стата, HUD, i18n, тулкит) даёт mg-core;
 * правила игры — в {@link SkyWarsGame} (наследник {@code Minigame}), который здесь
 * регистрируется в ядре через {@link MgCore#register}.
 */
public final class SkyWarsPlugin extends JavaPlugin
{
    private MgCore core;
    private KitRegistry kitRegistry;
    private LootRegistry lootRegistry;
    private SkyWarsGame game;

    @Override
    public void onEnable()
    {
        core = getServer().getServicesManager().load(MgCore.class);
        if (core == null)
        {
            getLogger().severe("MgCore не найден — SkyWars выключается (нужен плагин MgCore).");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        Msg.merge(this); // домешать свой messages.yml в общий каталог ядра (ключи skywars.*)

        kitRegistry = new KitRegistry(this);
        kitRegistry.load();
        lootRegistry = new LootRegistry(this);
        lootRegistry.load();

        game = new SkyWarsGame(this, core);
        core.register(game);

        getServer().getPluginManager().registerEvents(new SkyWarsListener(this, game), this);

        getLogger().info("SkyWars enabled, game registered: " + game.id());
    }

    /** Перечитать игро-контент (зовётся из SkyWarsGame.onReload при /mg reload). */
    public void reloadContent()
    {
        reloadConfig();
        kitRegistry.load();
        lootRegistry.load();
    }

    public ArenaService arenas() {return core.arenas();}
    public MgCore core() {return core;}
    public KitRegistry kits() {return kitRegistry;}
    public LootRegistry loot() {return lootRegistry;}
    public SkyWarsGame game() {return game;}
}
