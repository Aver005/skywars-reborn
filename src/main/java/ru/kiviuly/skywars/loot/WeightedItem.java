package ru.kiviuly.skywars.loot;

import org.bukkit.inventory.ItemStack;

/** Предмет с весом для взвешенного пула лута категории (вес чем больше — тем чаще). */
public record WeightedItem(ItemStack item, int weight) {}
