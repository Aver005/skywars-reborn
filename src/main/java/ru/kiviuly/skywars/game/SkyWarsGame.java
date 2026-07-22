package ru.kiviuly.skywars.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.kiviuly.skywars.SkyWarsPlugin;
import ru.kiviuly.skywars.kit.Kit;
import ru.kiviuly.skywars.loot.LootCategory;
import ru.kiviuly.skywars.util.Items;
import ru.kiviuly.skywars.util.Msg;

/**
 * SkyWars — соло last-man-standing на парящих островах.
 *
 * Правила игры целиком живут здесь (наследник {@link Minigame}); каркас
 * (арены, отсчёты, снапшоты, откат мира, HUD, стата) — обобщённый и об игре не знает.
 * Состояние матча держим в {@link GameSession#data()} / {@link MatchPlayer}, не в полях
 * этого класса (инстанция одна на плагин, матчей может идти несколько).
 *
 * Условие победы — «последний выживший»: наследуем дефолтный {@link #checkResult}
 * ({@link GameSession#defaultResult()}). Метрика «кто больше нанесёт урона» — это
 * лоббийная разминка ({@link #onLobbyAttack}), а не условие победы.
 *
 * Веха-статус: M2 (разминка) — ГОТОВО; M3 (капсулы) — ГОТОВО; M4 (киты), M5 (сундуки),
 * M6 (итоги/HUD) — впереди.
 */
public class SkyWarsGame extends Minigame
{
    /** Ключ в {@link GameSession#data()}: накопленный урон разминки, uuid -> урон. */
    private static final String WARMUP_KEY = "warmup-damage";
    /** Ключ: список поставленных блоков капсул (для снятия при раскрытии). */
    private static final String CAPSULE_BLOCKS_KEY = "capsule-blocks";
    /** Ключ: капсулы уже раскрыты (флаг, чтобы раскрыть один раз). */
    private static final String CAPSULE_OPEN_KEY = "capsule-open";

    /** Материал стенок капсулы. */
    private static final Material CAPSULE_MATERIAL = Material.GLASS;
    /** Горизонтальный радиус капсулы (1 = короб 3x3). */
    private static final int CAPSULE_RADIUS = 1;
    /** Пол капсулы — на блок ниже ног, потолок — на два блока выше. */
    private static final int CAPSULE_FLOOR_DY = -1;
    private static final int CAPSULE_TOP_DY = 2;
    /** Сколько секунд действует медленное падение при десанте (с запасом на спуск). */
    private static final int GLIDE_SECONDS = 15;

    /** Для случайного выбора кита ("random"). */
    private static final Random RANDOM = new Random();

    public SkyWarsGame(SkyWarsPlugin plugin)
    {
        super(plugin);
    }

    @Override
    public String id() {return "skywars";}

    @Override
    public String displayName() {return Msg.raw("skywars.display-name");}

    // ===== M2: разминка «Избиение в лобби» =====

    @Override
    public void onLobbyAttack(GameSession s, Player victim, Player damager, double damage)
    {
        // Реального урона нет (ядро отменило удар). Копим счёт бьющему и показываем
        // фейковый фидбек, чтобы удар в лобби ощущался как настоящий.
        warmup(s).merge(damager.getUniqueId(), damage, Double::sum);
        playWarmupHitFeedback(victim, damager);
    }

    /** Мапа урона разминки внутри состояния сессии (создаётся лениво). */
    @SuppressWarnings("unchecked")
    private static Map<UUID, Double> warmup(GameSession s)
    {
        return (Map<UUID, Double>) s.data().computeIfAbsent(WARMUP_KEY, k -> new HashMap<UUID, Double>());
    }

    /**
     * Визуал удара разминки: урона нет, но клиенту — красный флэш (анимация урона в
     * сторону атакующего), звук получения урона и лёгкая отдача от бьющего.
     */
    private void playWarmupHitFeedback(Player victim, Player damager)
    {
        Location vLoc = victim.getLocation();
        double dx = damager.getX() - vLoc.getX();
        double dz = damager.getZ() - vLoc.getZ();
        float sourceYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        victim.playHurtAnimation(sourceYaw - vLoc.getYaw());
        victim.getWorld().playSound(vLoc, Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f);
        victim.knockback(0.4, dx, dz);
    }

    /** Объявить топ-3 разминки (перед капсулами) и обнулить счёт. */
    private void announceWarmup(GameSession s)
    {
        Map<UUID, Double> warmup = warmup(s);
        s.broadcast("skywars.warmup-title");
        if (warmup.isEmpty())
        {
            s.broadcast("skywars.warmup-empty");
        }
        else
        {
            List<Map.Entry<UUID, Double>> top = warmup.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(3).toList();
            int place = 1;
            for (Map.Entry<UUID, Double> e : top)
            {
                MatchPlayer mp = s.player(e.getKey());
                String name = mp != null ? mp.getName() : "?";
                s.broadcast("skywars.warmup-entry",
                    Msg.ph("place", place++),
                    Msg.ph("player", name),
                    Msg.ph("hearts", Math.round(e.getValue() / 2.0)));
            }
        }
        warmup.clear();
    }

    // ===== M3: капсулы над островами =====

    @Override
    public void onStart(GameSession s)
    {
        announceWarmup(s);   // M2: итог разминки перед десантом
        placeChests(s);      // M5: наполнить сундуки, пока игроки ещё в капсулах
        buildCapsules(s);    // M3: запереть игроков в стеклянные капсулы над островами
    }

    @Override
    public void onTick(GameSession s)
    {
        releaseCapsulesIfDue(s);  // M3: по таймеру раскрыть капсулы и уронить игроков
    }

    /**
     * Запереть каждого игрока в стеклянную капсулу на его спавне (спавн арены = точка
     * капсулы над островом). ADVENTURE на время удержания — нельзя выломать стекло.
     */
    private void buildCapsules(GameSession s)
    {
        List<Location> placed = new ArrayList<>();
        for (Player p : s.onlinePlayers())
        {
            p.setGameMode(GameMode.ADVENTURE);
            Location center = p.getLocation().toBlockLocation();
            buildCapsule(s, center, placed);
            p.teleport(center.clone().add(0.5, 0, 0.5));
            p.setVelocity(p.getVelocity().zero());
        }
        s.data().put(CAPSULE_BLOCKS_KEY, placed);
        s.data().put(CAPSULE_OPEN_KEY, Boolean.FALSE);
        s.broadcast("skywars.capsule-ready", Msg.ph("n", s.arena().getSetting("capsule-seconds", 5)));
    }

    /** Собрать одну капсулу: короб из стекла, игрок стоит на стеклянном полу внутри. */
    private void buildCapsule(GameSession s, Location center, List<Location> placed)
    {
        World w = center.getWorld();
        if (w == null) {return;}
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        for (int dx = -CAPSULE_RADIUS; dx <= CAPSULE_RADIUS; dx++)
        {
            for (int dz = -CAPSULE_RADIUS; dz <= CAPSULE_RADIUS; dz++)
            {
                for (int dy = CAPSULE_FLOOR_DY; dy <= CAPSULE_TOP_DY; dy++)
                {
                    Block b = w.getBlockAt(cx + dx, cy + dy, cz + dz);
                    s.rememberBlock(b); // откат после матча
                    boolean shell = dx == -CAPSULE_RADIUS || dx == CAPSULE_RADIUS
                        || dz == -CAPSULE_RADIUS || dz == CAPSULE_RADIUS
                        || dy == CAPSULE_FLOOR_DY || dy == CAPSULE_TOP_DY;
                    if (shell)
                    {
                        b.setType(CAPSULE_MATERIAL, false);
                        placed.add(b.getLocation());
                    }
                    else
                    {
                        b.setType(Material.AIR, false); // внутренняя полость под игрока
                    }
                }
            }
        }
    }

    /** Когда истекло удержание — снять стекло, вернуть SURVIVAL и уронить на остров. */
    @SuppressWarnings("unchecked")
    private void releaseCapsulesIfDue(GameSession s)
    {
        if (Boolean.TRUE.equals(s.data().get(CAPSULE_OPEN_KEY))) {return;}
        int hold = s.arena().getSetting("capsule-seconds", 5);
        if (s.elapsedSeconds() < hold) {return;}
        s.data().put(CAPSULE_OPEN_KEY, Boolean.TRUE);

        List<Location> blocks = (List<Location>) s.data().getOrDefault(CAPSULE_BLOCKS_KEY, List.of());
        for (Location loc : blocks)
        {
            Block b = loc.getBlock();
            if (b.getType() == CAPSULE_MATERIAL) {b.setType(Material.AIR, false);}
        }

        int grace = s.arena().getSetting("grace-seconds", 6);
        for (Player p : s.alivePlayers())
        {
            p.setGameMode(GameMode.SURVIVAL);
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, GLIDE_SECONDS * 20, 0, false, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, grace * 20, 4, false, false));
            p.playSound(p.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 1.0f);
        }
        s.broadcast("skywars.capsule-open");
    }

    // ===== M5: сундуки с лутом =====

    /** Расставить и наполнить сундуки на назначенных точках; активные — в состояние сессии. */
    private void placeChests(GameSession s)
    {
        Map<Location, List<String>> active = new HashMap<>();
        for (Map.Entry<Location, List<String>> entry : s.arena().getChestSpots().entrySet())
        {
            Location loc = entry.getKey();
            if (loc.getWorld() == null) {continue;}
            List<LootCategory> cats = categoriesFor(entry.getValue());
            if (cats.isEmpty()) {continue;}
            Block b = loc.getBlock();
            s.rememberBlock(b); // откат вернёт исходный блок, содержимое исчезнет с ним
            b.setType(Material.CHEST, false);
            // держим одиночным: соседние точки не сольются в двойной сундук (иначе ломается рефилл)
            if (b.getBlockData() instanceof org.bukkit.block.data.type.Chest cd)
            {
                cd.setType(org.bukkit.block.data.type.Chest.Type.SINGLE);
                b.setBlockData(cd, false);
            }
            if (b.getState() instanceof Chest chest)
            {
                fillChest(chest, cats, false);
                active.put(loc, new ArrayList<>(entry.getValue()));
            }
        }
        s.data().put("chest-cats", active);
        s.data().put("refill-pending", new HashSet<Location>());
    }

    /** Категории точки по id (существующие, без дублей). */
    private List<LootCategory> categoriesFor(List<String> ids)
    {
        List<LootCategory> out = new ArrayList<>();
        for (String id : ids)
        {
            LootCategory c = plugin.loot().get(id);
            if (c != null && !out.contains(c)) {out.add(c);}
        }
        return out;
    }

    /** Наполнить сундук: initial — очистка + заполнение, refill — добить пустые слоты. */
    private void fillChest(Chest chest, List<LootCategory> cats, boolean refill)
    {
        Inventory inv = chest.getBlockInventory();
        if (!refill) {inv.clear();}
        List<Integer> free = new ArrayList<>();
        for (int i = 0; i < inv.getSize(); i++)
        {
            ItemStack cur = inv.getItem(i);
            if (cur == null || cur.getType().isAir()) {free.add(i);}
        }
        Collections.shuffle(free, RANDOM);
        int idx = 0;
        for (LootCategory cat : cats)
        {
            int count = cat.rollSlotCount(RANDOM);
            for (int k = 0; k < count && idx < free.size(); k++)
            {
                ItemStack item = cat.pickItem(RANDOM);
                if (item != null) {inv.setItem(free.get(idx++), item);}
            }
        }
    }

    /** Сундук закрыт: если у его категорий включён рефилл — запланировать пополнение пустых слотов. */
    @SuppressWarnings("unchecked")
    public void onChestClosed(GameSession s, Block block)
    {
        if (s.phase() != GamePhase.RUNNING) {return;}
        Map<Location, List<String>> active = (Map<Location, List<String>>) s.data().get("chest-cats");
        Set<Location> pending = (Set<Location>) s.data().get("refill-pending");
        if (active == null || pending == null) {return;}

        Location key = null;
        for (Location loc : active.keySet())
        {
            if (sameBlock(loc, block.getLocation())) {key = loc; break;}
        }
        if (key == null || pending.contains(key)) {return;}

        int refillSec = 0;
        for (LootCategory c : categoriesFor(active.get(key)))
        {
            if (c.getRefillSeconds() > 0)
            {
                refillSec = refillSec == 0 ? c.getRefillSeconds() : Math.min(refillSec, c.getRefillSeconds());
            }
        }
        if (refillSec <= 0) {return;}
        if (block.getState() instanceof Chest chest && isFull(chest.getBlockInventory())) {return;}

        pending.add(key);
        final Location fkey = key;
        Bukkit.getScheduler().runTaskLater(plugin, () ->
        {
            pending.remove(fkey);
            if (s.phase() != GamePhase.RUNNING) {return;}
            if (fkey.getBlock().getState() instanceof Chest c2) {fillChest(c2, categoriesFor(active.get(fkey)), true);}
        }, Math.max(5, refillSec) * 20L);
    }

    private static boolean isFull(Inventory inv)
    {
        for (ItemStack it : inv.getContents())
        {
            if (it == null || it.getType().isAir()) {return false;}
        }
        return true;
    }

    private static boolean sameBlock(Location a, Location b)
    {
        return a.getWorld() != null && a.getWorld().equals(b.getWorld())
            && a.getBlockX() == b.getBlockX() && a.getBlockY() == b.getBlockY() && a.getBlockZ() == b.getBlockZ();
    }

    // ===== хуки жизненного цикла (наполняются по вехам M4–M6) =====

    @Override
    public void onLobbyJoin(GameSession s, Player p)
    {
        // Селектор кита в лобби (если киты есть). ПКМ откроет меню (SkyWarsListener).
        if (!plugin.kits().isEmpty())
        {
            p.getInventory().setItem(0, Items.special(Material.CHEST,
                Msg.get("skywars.kit-select-name"), Msg.getList("skywars.kit-select-lore"), "kit-select"));
        }
    }

    @Override
    public void giveLoadout(GameSession s, Player p)
    {
        // Применить выбранный игроком (или дефолтный/случайный) кит.
        Kit kit = plugin.kits().resolve(kitChoice(s, p.getUniqueId()), RANDOM);
        if (kit != null) {kit.apply(p);}
    }

    /** Выбор кита игроком хранится в состоянии сессии (ключ по uuid). */
    public static String kitChoice(GameSession s, UUID id)
    {
        Object o = s.data().get("kit-choice:" + id);
        return o instanceof String str ? str : null;
    }

    public static void setKitChoice(GameSession s, UUID id, String choice)
    {
        s.data().put("kit-choice:" + id, choice);
    }

    @Override
    public void onPlayerEliminated(GameSession s, MatchPlayer mp)
    {
        // Объявление о выбывании и кредит убийств делает ядро — здесь ничего не нужно.
    }

    // checkResult НЕ переопределяем: дефолт = последний выживший — ровно то, что нужно SkyWars.

    @Override
    public void onEnd(GameSession s, MatchResult result)
    {
        if (!result.hasWinner()) {return;}
        Map<UUID, Double> dmg = matchDamage(s);
        for (UUID id : result.winners())
        {
            MatchPlayer mp = s.player(id);
            if (mp == null) {continue;}
            s.broadcast("skywars.win-summary",
                Msg.ph("player", mp.getName()),
                Msg.ph("kills", mp.getKills()),
                Msg.ph("hearts", Math.round(dmg.getOrDefault(id, 0.0) / 2.0)));
        }
    }

    @Override
    public List<Component> scoreboardLines(GameSession s, Player viewer)
    {
        // Ядро уже рисует арену/фазу/живых/время; добавляем лидера по урону в матче.
        if (s.phase() != GamePhase.RUNNING) {return List.of();}
        Map.Entry<UUID, Double> top = matchDamage(s).entrySet().stream()
            .max(Map.Entry.comparingByValue()).orElse(null);
        if (top == null) {return List.of();}
        MatchPlayer mp = s.player(top.getKey());
        return List.of(Msg.get("skywars.hud-leader",
            Msg.ph("player", mp != null ? mp.getName() : "?"),
            Msg.ph("hearts", Math.round(top.getValue() / 2.0))));
    }

    // ===== M6: учёт нанесённого в матче урона (для HUD/итогов) =====

    /** Учесть нанесённый в матче урон (вызывается из SkyWarsListener). */
    public void recordMatchDamage(GameSession s, UUID damager, double amount)
    {
        if (amount <= 0) {return;}
        matchDamage(s).merge(damager, amount, Double::sum);
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, Double> matchDamage(GameSession s)
    {
        return (Map<UUID, Double>) s.data().computeIfAbsent("match-damage", k -> new HashMap<UUID, Double>());
    }
}
