package me.z3dyy.endervault;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * HatEffectListener — grants or removes potion effects based on the item
 * a player is wearing in their helmet slot (including items placed there
 * with /hat).
 *
 * Effects are re-applied every TICK_INTERVAL ticks so they never expire
 * while the item is worn, and are removed the moment the item is taken off.
 */
public class HatEffectListener implements Listener {

    /* How often (in ticks) to refresh active effects. 20 ticks = 1 second. */
    private static final long TICK_INTERVAL = 40L;

    /* Duration given to each potion effect application (in ticks).
       Must be > TICK_INTERVAL so effects never flicker off between refreshes. */
    private static final int EFFECT_DURATION = 100; // 5 seconds

    /* Amplifier used for buff effects (0 = level I, 1 = level II, …). */
    private static final int BUFF_AMP  = 0; // level I buffs
    private static final int DEBUFF_AMP = 0; // level I debuffs

    private final EnderVault plugin;

    // -----------------------------------------------------------------------
    // Effect map — one entry per Material that should grant/cause an effect.
    // Each entry is a list so a single item can apply multiple effects.
    // -----------------------------------------------------------------------
    private static final Map<Material, List<EffectEntry>> HAT_EFFECTS = new LinkedHashMap<>();

    static {
        // ── WEAPONS ─────────────────────────────────────────────────────────
        // Swords → Strength I (buff) + Slowness I (debuff, heavy weapon)
        for (Material m : new Material[]{
                Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD,
                Material.GOLDEN_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD}) {
            HAT_EFFECTS.put(m, Arrays.asList(
                    new EffectEntry(PotionEffectType.STRENGTH,  BUFF_AMP,   false),
                    new EffectEntry(PotionEffectType.SLOWNESS,  DEBUFF_AMP, true)
            ));
        }

        // Axes → Strength I + Mining Fatigue I (axes are slow)
        for (Material m : new Material[]{
                Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE,
                Material.GOLDEN_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE}) {
            HAT_EFFECTS.put(m, Arrays.asList(
                    new EffectEntry(PotionEffectType.STRENGTH,      BUFF_AMP,   false),
                    new EffectEntry(PotionEffectType.MINING_FATIGUE, DEBUFF_AMP, true)
            ));
        }

        // Bows → Jump Boost I (hunter's agility) + Weakness I
        HAT_EFFECTS.put(Material.BOW, Arrays.asList(
                new EffectEntry(PotionEffectType.JUMP_BOOST, BUFF_AMP,   false),
                new EffectEntry(PotionEffectType.WEAKNESS,   DEBUFF_AMP, true)
        ));
        HAT_EFFECTS.put(Material.CROSSBOW, Arrays.asList(
                new EffectEntry(PotionEffectType.JUMP_BOOST, BUFF_AMP,   false),
                new EffectEntry(PotionEffectType.WEAKNESS,   DEBUFF_AMP, true)
        ));

        // Trident → Water Breathing + Speed I (sea warrior)
        HAT_EFFECTS.put(Material.TRIDENT, Arrays.asList(
                new EffectEntry(PotionEffectType.WATER_BREATHING, BUFF_AMP, false),
                new EffectEntry(PotionEffectType.SPEED,           BUFF_AMP, false)
        ));

        // ── TOOLS ────────────────────────────────────────────────────────────
        // Pickaxes → Haste I (miner's focus) + Slowness I
        for (Material m : new Material[]{
                Material.WOODEN_PICKAXE, Material.STONE_PICKAXE, Material.IRON_PICKAXE,
                Material.GOLDEN_PICKAXE, Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE}) {
            HAT_EFFECTS.put(m, Arrays.asList(
                    new EffectEntry(PotionEffectType.HASTE,    BUFF_AMP,   false),
                    new EffectEntry(PotionEffectType.SLOWNESS, DEBUFF_AMP, true)
            ));
        }

        // Shovels → Haste I + Hunger I (digging is exhausting)
        for (Material m : new Material[]{
                Material.WOODEN_SHOVEL, Material.STONE_SHOVEL, Material.IRON_SHOVEL,
                Material.GOLDEN_SHOVEL, Material.DIAMOND_SHOVEL, Material.NETHERITE_SHOVEL}) {
            HAT_EFFECTS.put(m, Arrays.asList(
                    new EffectEntry(PotionEffectType.HASTE,  BUFF_AMP,   false),
                    new EffectEntry(PotionEffectType.HUNGER, DEBUFF_AMP, true)
            ));
        }

        // Hoes → Saturation (farmer's reward, no debuff)
        for (Material m : new Material[]{
                Material.WOODEN_HOE, Material.STONE_HOE, Material.IRON_HOE,
                Material.GOLDEN_HOE, Material.DIAMOND_HOE, Material.NETHERITE_HOE}) {
            HAT_EFFECTS.put(m, Collections.singletonList(
                    new EffectEntry(PotionEffectType.SATURATION, BUFF_AMP, false)
            ));
        }

        // Fishing rod → Luck I + Slowness I
        HAT_EFFECTS.put(Material.FISHING_ROD, Arrays.asList(
                new EffectEntry(PotionEffectType.LUCK,     BUFF_AMP,   false),
                new EffectEntry(PotionEffectType.SLOWNESS, DEBUFF_AMP, true)
        ));

        // Shears → Speed I (nimble shearer)
        HAT_EFFECTS.put(Material.SHEARS, Collections.singletonList(
                new EffectEntry(PotionEffectType.SPEED, BUFF_AMP, false)
        ));

        // Flint and Steel → Fire Resistance (immune to your own fires)
        HAT_EFFECTS.put(Material.FLINT_AND_STEEL, Collections.singletonList(
                new EffectEntry(PotionEffectType.FIRE_RESISTANCE, BUFF_AMP, false)
        ));

        // ── FOOD & NATURE ────────────────────────────────────────────────────
        // Golden Apple / Enchanted Golden Apple → Regeneration + Absorption
        HAT_EFFECTS.put(Material.GOLDEN_APPLE, Arrays.asList(
                new EffectEntry(PotionEffectType.REGENERATION, BUFF_AMP, false),
                new EffectEntry(PotionEffectType.ABSORPTION,   BUFF_AMP, false)
        ));
        HAT_EFFECTS.put(Material.ENCHANTED_GOLDEN_APPLE, Arrays.asList(
                new EffectEntry(PotionEffectType.REGENERATION, 1,       false), // Regen II
                new EffectEntry(PotionEffectType.ABSORPTION,   1,       false), // Absorption II
                new EffectEntry(PotionEffectType.RESISTANCE,   BUFF_AMP, false)
        ));

        // Chorus Fruit → Levitation + Nausea (strange teleporting fruit)
        HAT_EFFECTS.put(Material.CHORUS_FRUIT, Arrays.asList(
                new EffectEntry(PotionEffectType.LEVITATION, BUFF_AMP,   false),
                new EffectEntry(PotionEffectType.NAUSEA,     DEBUFF_AMP, true)
        ));

        // Mushrooms → Poison I (toxic fungi)
        HAT_EFFECTS.put(Material.RED_MUSHROOM, Collections.singletonList(
                new EffectEntry(PotionEffectType.POISON, DEBUFF_AMP, true)
        ));
        HAT_EFFECTS.put(Material.BROWN_MUSHROOM, Collections.singletonList(
                new EffectEntry(PotionEffectType.POISON, DEBUFF_AMP, true)
        ));

        // Cactus → Thorns effect via Strength + Poison (wear at own risk)
        HAT_EFFECTS.put(Material.CACTUS, Arrays.asList(
                new EffectEntry(PotionEffectType.STRENGTH, BUFF_AMP,   false),
                new EffectEntry(PotionEffectType.POISON,   DEBUFF_AMP, true)
        ));

        // ── BLOCKS & MISC ────────────────────────────────────────────────────
        // Torch / Soul Torch → Night Vision (light in the darkness)
        HAT_EFFECTS.put(Material.TORCH, Collections.singletonList(
                new EffectEntry(PotionEffectType.NIGHT_VISION, BUFF_AMP, false)
        ));
        HAT_EFFECTS.put(Material.SOUL_TORCH, Collections.singletonList(
                new EffectEntry(PotionEffectType.NIGHT_VISION, BUFF_AMP, false)
        ));

        // Pumpkin → Invisibility (spooky!) + Blindness
        HAT_EFFECTS.put(Material.PUMPKIN, Arrays.asList(
                new EffectEntry(PotionEffectType.INVISIBILITY, BUFF_AMP,   false),
                new EffectEntry(PotionEffectType.BLINDNESS,    DEBUFF_AMP, true)
        ));

        // Jack-o-Lantern → Night Vision + Speed (excited pumpkin)
        HAT_EFFECTS.put(Material.JACK_O_LANTERN, Arrays.asList(
                new EffectEntry(PotionEffectType.NIGHT_VISION, BUFF_AMP, false),
                new EffectEntry(PotionEffectType.SPEED,        BUFF_AMP, false)
        ));

        // TNT → Strength II + Nausea (kaboom energy)
        HAT_EFFECTS.put(Material.TNT, Arrays.asList(
                new EffectEntry(PotionEffectType.STRENGTH, 1,           false), // Strength II
                new EffectEntry(PotionEffectType.NAUSEA,   DEBUFF_AMP, true)
        ));

        // Obsidian → Resistance I + Slowness II (extremely heavy)
        HAT_EFFECTS.put(Material.OBSIDIAN, Arrays.asList(
                new EffectEntry(PotionEffectType.RESISTANCE, BUFF_AMP, false),
                new EffectEntry(PotionEffectType.SLOWNESS,   1,        true)  // Slowness II
        ));

        // Sponge → Water Breathing + Hunger (absorbing water is hard work)
        HAT_EFFECTS.put(Material.SPONGE, Arrays.asList(
                new EffectEntry(PotionEffectType.WATER_BREATHING, BUFF_AMP,   false),
                new EffectEntry(PotionEffectType.HUNGER,          DEBUFF_AMP, true)
        ));

        // Compass → Speed I (always knows where to go)
        HAT_EFFECTS.put(Material.COMPASS, Collections.singletonList(
                new EffectEntry(PotionEffectType.SPEED, BUFF_AMP, false)
        ));

        // Clock → Haste I (time is money)
        HAT_EFFECTS.put(Material.CLOCK, Collections.singletonList(
                new EffectEntry(PotionEffectType.HASTE, BUFF_AMP, false)
        ));

        // Book / Enchanted Book → Luck I (knowledge is power)
        HAT_EFFECTS.put(Material.BOOK, Collections.singletonList(
                new EffectEntry(PotionEffectType.LUCK, BUFF_AMP, false)
        ));
        HAT_EFFECTS.put(Material.ENCHANTED_BOOK, Arrays.asList(
                new EffectEntry(PotionEffectType.LUCK,  BUFF_AMP, false),
                new EffectEntry(PotionEffectType.HASTE, BUFF_AMP, false)
        ));

        // Skull / Wither Skull → Wither effect (haunted headwear)
        for (Material m : new Material[]{
                Material.SKELETON_SKULL, Material.WITHER_SKELETON_SKULL,
                Material.ZOMBIE_HEAD, Material.CREEPER_HEAD, Material.PIGLIN_HEAD}) {
            HAT_EFFECTS.put(m, Collections.singletonList(
                    new EffectEntry(PotionEffectType.WITHER, DEBUFF_AMP, true)
            ));
        }

        // Dragon Head → Resistance I + Strength I (legendary)
        HAT_EFFECTS.put(Material.DRAGON_HEAD, Arrays.asList(
                new EffectEntry(PotionEffectType.RESISTANCE, 1, false), // Resistance II
                new EffectEntry(PotionEffectType.STRENGTH,   1, false)  // Strength II
        ));

        // Ice / Packed Ice → Slowness II + Resistance I (frozen armour)
        HAT_EFFECTS.put(Material.ICE, Arrays.asList(
                new EffectEntry(PotionEffectType.SLOWNESS,   1,         true),  // Slowness II
                new EffectEntry(PotionEffectType.RESISTANCE, BUFF_AMP, false)
        ));
        HAT_EFFECTS.put(Material.PACKED_ICE, Arrays.asList(
                new EffectEntry(PotionEffectType.SLOWNESS,   1,         true),
                new EffectEntry(PotionEffectType.RESISTANCE, BUFF_AMP, false)
        ));

        // Magma Block → Fire Resistance + Poison (hot but protective)
        HAT_EFFECTS.put(Material.MAGMA_BLOCK, Arrays.asList(
                new EffectEntry(PotionEffectType.FIRE_RESISTANCE, BUFF_AMP,   false),
                new EffectEntry(PotionEffectType.POISON,          DEBUFF_AMP, true)
        ));

        // Hay Bale → Saturation + Slowness (stuffed with straw)
        HAT_EFFECTS.put(Material.HAY_BLOCK, Arrays.asList(
                new EffectEntry(PotionEffectType.SATURATION, BUFF_AMP,   false),
                new EffectEntry(PotionEffectType.SLOWNESS,   DEBUFF_AMP, true)
        ));

        // Beacon → Regeneration + Speed + Strength (ultimate hat)
        HAT_EFFECTS.put(Material.BEACON, Arrays.asList(
                new EffectEntry(PotionEffectType.REGENERATION, 1, false),
                new EffectEntry(PotionEffectType.SPEED,        1, false),
                new EffectEntry(PotionEffectType.STRENGTH,     1, false)
        ));

        // Nether Star → Absorption II + Luck I
        HAT_EFFECTS.put(Material.NETHER_STAR, Arrays.asList(
                new EffectEntry(PotionEffectType.ABSORPTION, 1,         false),
                new EffectEntry(PotionEffectType.LUCK,       BUFF_AMP, false)
        ));

        // Ender Pearl → Levitation + Jump Boost (ender energy)
        HAT_EFFECTS.put(Material.ENDER_PEARL, Arrays.asList(
                new EffectEntry(PotionEffectType.LEVITATION, BUFF_AMP, false),
                new EffectEntry(PotionEffectType.JUMP_BOOST, BUFF_AMP, false)
        ));

        // Eye of Ender → Night Vision + Luck
        HAT_EFFECTS.put(Material.ENDER_EYE, Arrays.asList(
                new EffectEntry(PotionEffectType.NIGHT_VISION, BUFF_AMP, false),
                new EffectEntry(PotionEffectType.LUCK,         BUFF_AMP, false)
        ));

        // Blaze Rod → Fire Resistance + Speed (blazing fast)
        HAT_EFFECTS.put(Material.BLAZE_ROD, Arrays.asList(
                new EffectEntry(PotionEffectType.FIRE_RESISTANCE, BUFF_AMP, false),
                new EffectEntry(PotionEffectType.SPEED,           BUFF_AMP, false)
        ));

        // Ghast Tear → Regeneration I
        HAT_EFFECTS.put(Material.GHAST_TEAR, Collections.singletonList(
                new EffectEntry(PotionEffectType.REGENERATION, BUFF_AMP, false)
        ));

        // Slimeball → Jump Boost + Slowness (bouncy but sluggish)
        HAT_EFFECTS.put(Material.SLIME_BALL, Arrays.asList(
                new EffectEntry(PotionEffectType.JUMP_BOOST, BUFF_AMP,   false),
                new EffectEntry(PotionEffectType.SLOWNESS,   DEBUFF_AMP, true)
        ));

        // Spider Eye → Poison + Night Vision (arachnid vision)
        HAT_EFFECTS.put(Material.SPIDER_EYE, Arrays.asList(
                new EffectEntry(PotionEffectType.NIGHT_VISION, BUFF_AMP,   false),
                new EffectEntry(PotionEffectType.POISON,       DEBUFF_AMP, true)
        ));

        // Fermented Spider Eye → Blindness + Weakness (corrupted)
        HAT_EFFECTS.put(Material.FERMENTED_SPIDER_EYE, Arrays.asList(
                new EffectEntry(PotionEffectType.BLINDNESS, DEBUFF_AMP, true),
                new EffectEntry(PotionEffectType.WEAKNESS,  DEBUFF_AMP, true)
        ));

        // Feather → Jump Boost + Slow Falling
        HAT_EFFECTS.put(Material.FEATHER, Arrays.asList(
                new EffectEntry(PotionEffectType.JUMP_BOOST,   BUFF_AMP, false),
                new EffectEntry(PotionEffectType.SLOW_FALLING, BUFF_AMP, false)
        ));

        // Sugar → Speed II + Nausea (sugar rush)
        HAT_EFFECTS.put(Material.SUGAR, Arrays.asList(
                new EffectEntry(PotionEffectType.SPEED,  1,          false), // Speed II
                new EffectEntry(PotionEffectType.NAUSEA, DEBUFF_AMP, true)
        ));

        // Bone → Strength I (skeleton power)
        HAT_EFFECTS.put(Material.BONE, Collections.singletonList(
                new EffectEntry(PotionEffectType.STRENGTH, BUFF_AMP, false)
        ));

        // Gunpowder → Weakness + Slowness (explosive but unstable)
        HAT_EFFECTS.put(Material.GUNPOWDER, Arrays.asList(
                new EffectEntry(PotionEffectType.WEAKNESS,  DEBUFF_AMP, true),
                new EffectEntry(PotionEffectType.SLOWNESS,  DEBUFF_AMP, true)
        ));

        // Glow Berries / Glowstone Dust → Night Vision
        HAT_EFFECTS.put(Material.GLOW_BERRIES, Collections.singletonList(
                new EffectEntry(PotionEffectType.NIGHT_VISION, BUFF_AMP, false)
        ));
        HAT_EFFECTS.put(Material.GLOWSTONE_DUST, Collections.singletonList(
                new EffectEntry(PotionEffectType.NIGHT_VISION, BUFF_AMP, false)
        ));

        // Shield → Resistance I (defensive stance)
        HAT_EFFECTS.put(Material.SHIELD, Collections.singletonList(
                new EffectEntry(PotionEffectType.RESISTANCE, BUFF_AMP, false)
        ));

        // Totem of Undying → Absorption + Regeneration (divine protection)
        HAT_EFFECTS.put(Material.TOTEM_OF_UNDYING, Arrays.asList(
                new EffectEntry(PotionEffectType.ABSORPTION,   1, false),
                new EffectEntry(PotionEffectType.REGENERATION, 1, false)
        ));

        // Elytra → Slow Falling + Jump Boost
        HAT_EFFECTS.put(Material.ELYTRA, Arrays.asList(
                new EffectEntry(PotionEffectType.SLOW_FALLING, BUFF_AMP, false),
                new EffectEntry(PotionEffectType.JUMP_BOOST,   BUFF_AMP, false)
        ));

        // Wet Sponge → Water Breathing + Slowness (waterlogged)
        HAT_EFFECTS.put(Material.WET_SPONGE, Arrays.asList(
                new EffectEntry(PotionEffectType.WATER_BREATHING, BUFF_AMP,   false),
                new EffectEntry(PotionEffectType.SLOWNESS,        DEBUFF_AMP, true)
        ));

        // Flower Pot / Flowers → Saturation (fragrant blooms)
        for (Material m : new Material[]{
                Material.DANDELION, Material.POPPY, Material.BLUE_ORCHID,
                Material.ALLIUM, Material.SUNFLOWER, Material.ROSE_BUSH}) {
            HAT_EFFECTS.put(m, Collections.singletonList(
                    new EffectEntry(PotionEffectType.SATURATION, BUFF_AMP, false)
            ));
        }
    }

    // -----------------------------------------------------------------------
    // Constructor & scheduler
    // -----------------------------------------------------------------------

    public HatEffectListener(EnderVault plugin) {
        this.plugin = plugin;
        startEffectScheduler();
    }

    /**
     * Repeating task that refreshes hat effects for every online player so
     * the potion effect never times out while the item is worn.
     */
    private void startEffectScheduler() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    applyHatEffects(player);
                }
            }
        }.runTaskTimer(plugin, 0L, TICK_INTERVAL);
    }

    // -----------------------------------------------------------------------
    // Core logic
    // -----------------------------------------------------------------------

    /**
     * Applies effects for the player's current helmet item, or removes old
     * hat effects if the helmet is empty / an unrecognised material.
     */
    private void applyHatEffects(Player player) {
        ItemStack helmet = player.getInventory().getHelmet();
        Material mat = (helmet == null) ? Material.AIR : helmet.getType();

        List<EffectEntry> entries = HAT_EFFECTS.getOrDefault(mat, Collections.emptyList());

        // Collect which PotionEffectTypes are currently supposed to be active
        Set<PotionEffectType> activeTypes = new HashSet<>();
        for (EffectEntry entry : entries) {
            activeTypes.add(entry.type);
        }

        // Remove hat-sourced effects that no longer apply (helmet changed)
        for (EffectEntry anyEntry : allKnownEffectTypes()) {
            if (!activeTypes.contains(anyEntry.type) && player.hasPotionEffect(anyEntry.type)) {
                // Only remove it if it was one we could have applied —
                // we don't want to strip effects granted by potions/beacons.
                // We track this by storing which types we manage; here we use
                // the simple heuristic: remove only if the type appears in our map.
                if (isManagedType(anyEntry.type)) {
                    player.removePotionEffect(anyEntry.type);
                }
            }
        }

        // Apply/refresh currently active effects
        for (EffectEntry entry : entries) {
            player.addPotionEffect(
                    new PotionEffect(entry.type, EFFECT_DURATION, entry.amplifier, true, true, true),
                    true // overwrite if already present
            );
        }
    }

    /** Removes all managed hat effects from the player immediately. */
    private void removeAllHatEffects(Player player) {
        for (PotionEffectType type : getAllManagedTypes()) {
            if (player.hasPotionEffect(type)) {
                player.removePotionEffect(type);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Events
    // -----------------------------------------------------------------------

    /** Re-evaluate effects when a player joins (in case they had a hat before). */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        applyHatEffects(event.getPlayer());
    }

    /** Remove effects when a player leaves. */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        removeAllHatEffects(event.getPlayer());
    }

    /**
     * Detect helmet slot changes via inventory clicks so effects update
     * immediately rather than waiting for the next scheduler tick.
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        boolean affectsHelmet = false;

        // Armour slot 39 = helmet in player inventory
        if (event.getSlotType() == InventoryType.SlotType.ARMOR && event.getSlot() == 39) {
            affectsHelmet = true;
        }
        // Shift-click into armour from any slot can place in helmet slot
        if (event.isShiftClick()) {
            affectsHelmet = true;
        }

        if (affectsHelmet) {
            // Delay by 1 tick so the inventory update is committed first
            new BukkitRunnable() {
                @Override
                public void run() {
                    removeAllHatEffects(player);
                    applyHatEffects(player);
                }
            }.runTaskLater(plugin, 1L);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** All PotionEffectTypes that this listener can ever apply. */
    private static Set<PotionEffectType> getAllManagedTypes() {
        Set<PotionEffectType> types = new HashSet<>();
        for (List<EffectEntry> entries : HAT_EFFECTS.values()) {
            for (EffectEntry e : entries) {
                types.add(e.type);
            }
        }
        return types;
    }

    private static boolean isManagedType(PotionEffectType type) {
        return getAllManagedTypes().contains(type);
    }

    /** Returns a flat set of EffectEntry objects covering every managed type. */
    private static Set<EffectEntry> allKnownEffectTypes() {
        Set<EffectEntry> all = new HashSet<>();
        for (List<EffectEntry> entries : HAT_EFFECTS.values()) {
            all.addAll(entries);
        }
        return all;
    }

    // -----------------------------------------------------------------------
    // Inner record
    // -----------------------------------------------------------------------

    private static class EffectEntry {
        final PotionEffectType type;
        final int amplifier;
        final boolean isDebuff; // informational only

        EffectEntry(PotionEffectType type, int amplifier, boolean isDebuff) {
            this.type = type;
            this.amplifier = amplifier;
            this.isDebuff = isDebuff;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof EffectEntry other)) return false;
            return type.equals(other.type);
        }

        @Override
        public int hashCode() { return type.hashCode(); }
    }
}
