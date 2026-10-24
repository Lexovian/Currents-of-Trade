<div align="center">

# ⚓ Currents of Trade

**A vanilla-friendly maritime trading, navigation, shipbuilding & harbor logistics mod for Minecraft.**

[![Minecraft 1.21.1](https://img.shields.io/badge/Minecraft-1.21.1%20(NeoForge)-brightgreen.svg)](https://neoforged.net/)
[![Minecraft 1.20.1](https://img.shields.io/badge/Minecraft-1.20.1%20(Forge)-orange.svg)](https://files.minecraftforge.net/)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

<p align="center">
  <em>Transform Minecraft's vast oceans into thriving commercial sea lanes. Establish tiered harbors, command customizable sailing sloops, chart inter-regional arbitrage routes, and automate transoceanic cargo & trade fleets.</em>
</p>

</div>

---

## 🌊 Vision & Purpose

In vanilla Minecraft, oceans and rivers often become passive backdrops once you acquire a pair of Elytra or build a Nether portal. **Currents of Trade** reimagines the seas as a living, physical economic network—where coastal villages, player-built ports, and oceanic waterways form the backbone of global logistics and commerce:

- **Physical Maritime Logistics:** No magic teleportation boxes. Cargo ships and trade vessels physically sail across the ocean, dynamically loading chunks along their path, steering around islands, and docking at destination piers.
- **Tiered Harbor Progression:** Upgrade docks from humble **Coastal Piers (Lv. 1)** into grand **Royal Dockyards (Lv. 5)** to unlock larger ship holds, expanded market registries, and shipping fee discounts.
- **Customizable Sailing Vessels:** Build and personalize your own **Sloop** with 16-color dyed sails, 3D heraldic banners, night-piercing bow lanterns, copper-sheathed hulls, and an onboard **Captain's Hold**.
- **Regional Trade Basins & Arbitrage:** Discover biome-driven **Maritime Silk Road** routes where exporting regional specialties to high-demand ports slashes shipping costs in half.
- **Coastal Exploration & Nautical Lore:** Erect **Lighthouse Lanterns** to grant coastal tailwinds, dredge sunken cargo from the seabed, uncork **Messages in a Bottle**, and trade with **Harbormasters** and **Wandering Sailors**.

---

## 🧭 Core Systems

### ⚓ 1. Anchor Point & Harbor Progression (Levels 1–5)
The **Anchor Point** is the heart of every port.
- **Smart Open-Water Validation:** Uses flood-fill verification requiring at least **40 contiguous open-sky water blocks** with a minimum 2-block depth—preventing single-bucket landlocked exploits.
- **5-Tier Dockyard Upgrades:** Invest **Doubloons** and construction materials directly in the Harbor GUI to upgrade your port:
  | Tier | Title | Cargo & Trade Hold | Market Offers | Fee Discount |
  | :---: | :--- | :---: | :---: | :---: |
  | **Lv. 1** | *Coastal Pier* | 18 Slots (2 Rows) | Max 6 Offers | 0% |
  | **Lv. 2** | *Merchant Wharf* | 27 Slots (3 Rows) | Max 8 Offers | -5% |
  | **Lv. 3** | *Trade Harbor* | 36 Slots (4 Rows) | Max 10 Offers | -10% |
  | **Lv. 4** | *Grand Port* | 45 Slots (5 Rows) | Max 12 Offers | -15% |
  | **Lv. 5** | *Royal Dockyard* | 54 Slots (6 Rows) | Max 14 Offers | -25% |
- **Harbor Depot Automation:** Place chests, barrels, or hoppers directly adjacent to an **Anchor Point**. Returning trade ships and arriving cargo vessels automatically unload their merchandise into your harbor depot, or you can transfer ship cargo with a single click via the **`⚓ Unload`** button inside any docked ship's hold.

---

### ⛵ 2. Vessels & Shipyard Customization
Every vessel features a custom 2.4× scale 3D model with billowing sails, rotating steering wheels, natural ocean roll, and multi-part deck hitboxes you can walk across.

#### **The Sloop (Player Sailing Ship)**
- **Quarterdeck Helm & Crew Seating:** Carries up to 3 riders (Captain at the elevated quarterdeck helm + 2 crew/passenger benches).
- **18-Slot Captain's Hold:** Press **Sneak + Right-Click** (or your inventory key while sailing) to open the onboard cargo hold.
- **Modular Shipyard Upgrades** *(preserved when the ship is picked up)*:
  - 🎨 **16-Color Dyed Sails:** Right-click with any **Dye** to tint your main sail and topsail (wash back to natural canvas with a **Water Bucket** or **Sponge**).
  - 🚩 **3D Banner Heraldry:** Right-click with any **Banner** to mount your custom emblem proudly on the front of the main sail (remove with **Shears**).
  - 🏮 **Bow Lantern & Soul Lantern:** Right-click with a **Lantern** or **Soul Lantern** to mount a glowing beacon on the bow—granting **Night Vision** to riders at night or during storms (Soul Lanterns also add a nocturnal speed boost).
  - 🛡️ **Copper-Sheathed Hull:** Right-click with a **Copper Block** or **Waxed Copper Block** to reinforce the hull, granting **+15% cruising speed** and **-50% hull damage taken**.

#### **Cargo Ship & Trade Ship**
- Heavy 54-slot freighters and adorned merchant vessels equipped with deck cranes, awnings, figureheads, and crow's nests—available both as autonomous harbor fleets and placeable water vessels.

---

### 📦 3. Autonomous Fleet Operations

- **Voyage (Fast-Travel Charter):** Select a destination **Nautical Chart** or **Route Journal** to charter a high-speed voyage (~36 blocks/sec) with 50-block obstacle avoidance, live distance HUD countdown, and an arrival bell upon docking.
- **Send Items (Unmanned Cargo Fleet):** Load the level-scaled hold (up to 54 slots), insert a destination chart, and dispatch an autonomous **Cargo Ship**:
  - **Dynamic 3×3 Chunk Loading:** Ships force-load chunks along their trajectory so deliveries complete reliably across thousands of blocks.
  - **Safe Route Recovery:** If a ship is obstructed for more than 25 seconds, the voyage safely aborts and refunds all cargo back to the origin dock.
- **Request Trade (Remote Commerce & Order Presets):**
  - Seal villager offers into a **Trade Ledger** and register them to your harbor's market.
  - Stage bulk orders (`Confirm` / `Confirm All`) up to your harbor's hold capacity (18–54 slots).
  - Use the **`↻` Repeat Last Order** button to automatically re-stage your previous trade manifest from your inventory in one click.
  - Your **Trade Ship** sails to the target port, executes the trades, and returns home with your goods.

---

### 🌍 4. Regional Trade Basins & Maritime Arbitrage
Harbors automatically detect their climate and geography, classifying into five **Trade Basins**:
- 🏜️ **Arid Basin** *(Desert, Badlands, Savanna)* — Exports: *Spice Sack, Salt Pouch*
- ❄️ **Frozen Basin** *(Snowy, Frozen Ocean, Ice Spikes)* — Exports: *Amber Vial, Ammonite Fossil*
- 🌴 **Tropical Basin** *(Jungle, Mangrove, Warm Ocean, Bamboo)* — Exports: *Sandalwood, Vanilla Bundle, Luminous Pearl*
- 🌲 **Temperate Basin** *(Plains, Forest, Taiga, Cherry Grove)* — Exports: *Tea Brick, Fine Porcelain, Silk Bale*
- 🌊 **Deep Pelagic Basin** *(Open & Deep Oceans)* — Exports: *Luminous Pearl, Salt Pouch, Ammonite Fossil*

> **✦ Maritime Silk Road Bonus:** Ordering a basin's signature export from a harbor in a different climate activates an **Arbitrage Route**, cutting Doubloon shipping fees by **-50%**!

---

### 🏮 5. Coastal Infrastructure, Professions & Exploration
- **Lighthouse Lantern:** Emits a rotating navigational beam visible from afar and grants a **+25% Tailwind Speed Boost** to all vessels within **128 blocks**.
- **Coastal Village Docks & Harbormasters:** Naturally generates waterfront piers in coastal and riverside villages (validated by `CoastalWaterCheckProcessor`). Villagers working at an Anchor Point become **Harbormasters** with 5 tiers of maritime trades.
- **Wandering Sailors:** Traveling sea merchants who arrive by Sloop at coastal harbors with rare oceanic wares and treasure maps.
- **Seabed Salvaging & Messages in a Bottle:** Cast the **Salvage Dredge** into open water to haul up sunken cargo, relics, and **Sunken Navigator's Maps**, or uncork **Messages in a Bottle** for sea lore, buried treasure charts, and pre-bound Nautical Charts.

---

## 🧰 Items, Equipment & Commodities

| Item | Category | Function |
| :--- | :--- | :--- |
| **Doubloon** | Currency | Minted gold sea coin used for voyage fares, cargo shipping, and harbor upgrades. |
| **Nautical Chart** | Navigation | Binds to an Anchor Point to store its coordinates, Trade Basin, and market snapshot. |
| **Route Journal** | Navigation | Leather-bound atlas storing up to **12 Nautical Charts** (`Sneak + Scroll` or GUI to switch active route). |
| **Trade Ledger** | Commerce | Copies trade offers from villagers/Harbormasters and registers them to an Anchor Point. |
| **Captain's Tricorne** | Equipment | Wearable naval hat granting **+20% Sloop sailing speed** and **-10% Doubloon fee discount**. |
| **Captain's Pipe** | Utility | Puff to emit aromatic smoke rings, cure **Seasickness**, and gain **Luck I** (60s). |
| **Storm Glass** | Instrument | Barometer whose crystal sprite reacts to clear skies, rain, or storms; right-click to forecast weather. |
| **Salvage Dredge** | Tool | Scrapes the seabed from boats or piers to recover sunken loot and navigator maps. |
| **Message in a Bottle** | Lore & Loot | Uncorks into 15 maritime tales plus a chance for buried treasure maps, charts, or relics. |
| **Spice Sack** | Consumable | Grants **Speed II** (30s) and **Haste I** (20s). |
| **Amber Vial** | Consumable | Grants **Regeneration II** (12s) with brief *Seasickness*. |
| **Salt Pouch** | Utility | Preserves and upgrades cooked food held in your off-hand into **Salted** rations (+4❤ heal). |
| **Exotic Commodities** | Trade Goods | *Silk Bale, Luminous Pearl, Tea Brick, Fine Porcelain, Sandalwood, Vanilla Bundle, Ammonite Fossil*. |

---

## 🛠️ Commands & Configuration

- **`/cot dock locate`** — Scans for and displays the coordinates of the nearest generated village dock.
- **Server/Common Config (`currents_of_trade-common.toml`)** — Customize water validation thresholds, Doubloon distance costs, and autonomous ship cruising speeds.
- **Localization** — Full English (`en_us`) and Turkish (`tr_tr`) language support, plus **13 custom Maritime Advancements**.

---

## 📥 Installation

1. Install **Minecraft 1.21.1** with **[NeoForge](https://neoforged.net/)**, or **Minecraft 1.20.1** with **[Minecraft Forge](https://files.minecraftforge.net/)**.
2. Download the matching release file from [Releases](../../releases):
   - `currents_of_trade-x.x.x+neoforge-1.21.1.jar` for **NeoForge 1.21.1**
   - `currents_of_trade-x.x.x+forge-1.20.1.jar` for **Forge 1.20.1**
3. Place the `.jar` file into your `.minecraft/mods` directory.

---

## 📜 License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.
