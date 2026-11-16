<div align="center">

# ⚓ Currents of Trade

**A vanilla-friendly maritime trading, navigation, shipbuilding & harbor logistics mod for Minecraft.**

[![Minecraft 26.1.2](https://img.shields.io/badge/Minecraft-26.1.2%20(NeoForge)-blueviolet.svg)](https://neoforged.net/)
[![Minecraft 1.21.1](https://img.shields.io/badge/Minecraft-1.21.1%20(NeoForge)-brightgreen.svg)](https://neoforged.net/)
[![Minecraft 1.20.1](https://img.shields.io/badge/Minecraft-1.20.1%20(Forge)-orange.svg)](https://files.minecraftforge.net/)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

<p align="center">
  <em>Transform Minecraft's vast oceans and rivers into thriving commercial sea lanes. Establish specialized harbors, command customizable sailing sloops, fulfill Guild Trade Charters, plot custom sea routes in the Nautical Atlas, and automate transoceanic cargo & trade fleets.</em>
</p>

</div>

---

## 🌊 Vision & Purpose

In vanilla Minecraft, oceans and rivers often become passive backdrops once you acquire a pair of Elytra or build a Nether portal. **Currents of Trade** reimagines the seas as a living, physical economic network—where coastal villages, player-built ports, and oceanic waterways form the backbone of global logistics and commerce:

- **Physical Maritime Logistics:** No magic teleportation boxes. Cargo ships and trade vessels physically sail across oceans and rivers, dynamically loading chunks along their path, following plotted sea waypoints or river centerlines, and docking at destination piers.
- **Tiered Harbor Progression & Specializations:** Upgrade docks from humble **Fishing Wharves (Lv. 1)** into grand **Royal Dockyards (Lv. 5)**, and specialize Level 3+ ports into **Free Ports**, **Royal Drydocks**, or **Guild Depots**.
- **Customizable Sailing Vessels & Naval Defense:** Build and personalize your own **Sloop** and **Cargo Ship** with 18 sail colors (including *Royal Tyrian Purple* and *Dragon's Blood Red*), 3D heraldic stern banners, night-piercing bow lanterns, copper-sheathed hulls, and automated **Brass Swivel Harpoon** defense turrets.
- **Regional Trade Basins, Arbitrage & Guild Charters:** Discover biome-driven **Maritime Silk Road** routes where exporting regional specialties to high-demand ports pays up to **+100% bonus Doubloons**, claim **Daily Urgent Bounties (2× payout)**, and fulfill **Sealed Guild Trade Charters** for treasure-filled **Guild Strongboxes**.
- **Coastal Exploration & 6 Maritime Trade Leagues:** Erect **Lighthouse Lanterns** for coastal tailwinds, scout distant ships and ports through the **Captain's Brass Spyglass**, summon vessels with the **Bosun's Whistle**, dredge sunken relics, and trade with **Harbormasters** and visiting **Wandering Sailors** representing six distinct **Maritime Trade Leagues**.

---

## 🧭 Core Systems

### ⚓ 1. Anchor Point, Harbor Progression (Levels 1–5) & Specializations
The **Anchor Point** is the heart of every port.
- **Smart Open-Water Validation:** Uses flood-fill verification requiring at least **40 contiguous open-sky water blocks** with a minimum 2-block depth—preventing single-bucket landlocked exploits.
- **Persistent Harbor Data:** Breaking an upgraded, specialized, renamed, or custom-ledger Anchor Point preserves all harbor data (`SavedHarborData`) on the item stack so you can relocate your port without losing progress.
- **5-Tier Dockyard Upgrades:** Invest **Doubloons** and construction materials directly in the Harbor GUI:
  | Tier | Title | Cargo & Trade Hold | Market Offers | Fee Discount | Cooldown |
  | :---: | :--- | :---: | :---: | :---: | :---: |
  | **Lv. 1** | *Fishing Wharf* | 18 Slots (2 Rows) | Max 6 Offers | 0% | 60s |
  | **Lv. 2** | *Trading Post* | 27 Slots (3 Rows) | Max 8 Offers | -5% | 50s |
  | **Lv. 3** | *Harbor* | 36 Slots (4 Rows) | Max 10 Offers | -10% | 40s |
  | **Lv. 4** | *Grand Port* | 45 Slots (5 Rows) | Max 12 Offers | -15% | 30s |
  | **Lv. 5** | *Royal Dockyard* | 54 Slots (6 Rows) | Max 14 Offers | -25% | 20s |
- **Harbor Specializations (Unlocked at Lv. 3+):** Choose a specialized doctrine for each harbor (first choice is free; switching later costs **8 Doubloons**):
  - ⚖️ **Free Port:** **+25% Doubloon payouts** across Urgent Bounties, Trade Charters & Harbor Trades, and **-25% shipping fees**.
  - ⚒️ **Royal Drydock:** **+20% outbound Trade Ship speed**, reinforced hulls, **-50% port travel cooldowns**, and passive hull repair for nearby docked ships.
  - 📦 **Guild Depot:** Unlocks a **2nd Daily Urgent Bounty**, expands automated warehouse container scanning to **`8×4×8`**, and grants **+9 bonus cargo slots** on outbound vessels.
- **Harbor Depot & Redstone Automation:**
  - Returning trade ships and arriving cargo vessels automatically unload into adjacent chests/barrels (or an `8×4×8` warehouse radius for **Guild Depots**).
  - **Redstone Integration:** Attach a **Redstone Comparator** to read harbor fullness/cooldown state, and pulse the Anchor Point with Redstone to automatically restock and dispatch your saved **Trade Preset** using items from nearby depot chests!

---

### ⛵ 2. Vessels, Shipyard Customization & Naval Defense
Every vessel features a custom 2.4× scale 3D model with billowing sails, rotating steering wheels, natural ocean roll, and multi-part deck hitboxes you can walk across.

#### **The Sloop & Drivable Cargo Ship**
- **Quarterdeck Helm & Crew Seating:** The **Sloop** carries up to 3 riders with an **18-slot Captain's Hold**; the heavy **Cargo Ship** features a **54-slot Cargo Hold** (`Sneak + Right-Click` to open).
- **Chart Waypoint Autopilot:** Right-click while aboard with a bound **Nautical Chart** or **Route Journal** to engage autopilot along your plotted sea route!
- **Modular Shipyard Upgrades** *(preserved when the ship is broken/picked up)*:
  - 🎨 **18 Dyed Sail Colors:** Right-click with any of the 16 vanilla **Dyes**, **Royal Tyrian Purple**, or **Dragon's Blood Resin** (wash back to natural canvas with a **Water Bucket** or **Sponge**).
  - 🚩 **3D Stern Banner Heraldry:** Right-click with any **Banner** to mount your custom heraldic flag on the stern transom (remove with **Shears**).
  - 🏮 **Bow Lantern & Soul Lantern:** Mount a **Lantern** or **Soul Lantern** on the bow—granting **Night Vision** to riders at night or during storms (Soul Lanterns also grant a nocturnal speed boost).
  - 🛡️ **Copper-Sheathed Hull:** Reinforce the hull with a **Copper Block** or **Waxed Copper Block** for **+15% cruising speed** and **-50% hull damage taken**.
  - 🔱 **Brass Swivel Harpoon:** Mount onto any **Sloop**, **Cargo Ship**, or **Trade Ship** (detach with **Sneak + Shears**). Automatically fires harpoon bolts every 2.5s (`6.0` damage + knockback) at hostile aquatic threats (*Drowned, Guardians, Elder Guardians*) within 14 blocks.

---

### 📦 3. Autonomous Fleet Operations & Nautical Atlas

- **Nautical Atlas (`Route Journal`):**
  - Stores up to **12 Nautical Charts** in an interactive parchment radar screen.
  - View **live moving ship markers**, inspect remote harbor inventories/bounties, filter ports by commodity, and plot **custom multi-waypoint sea routes** on any **Nautical Chart**.
- **Voyage (Fast-Travel Charter):** Charter a high-speed voyage (~36 blocks/sec) between linked harbors with obstacle avoidance, live distance HUD countdown, and an arrival bell.
- **Send Items (Unmanned Cargo Fleet):** Load up to 54 slots (up to 63 with **Guild Depot**), insert a destination chart, and dispatch an autonomous **Cargo Ship** with dynamic **3×3 chunk loading** and smart island/river navigation.
- **Request Trade (Remote Commerce & Order Presets):**
  - Stage bulk orders (`Confirm` / `Confirm All`) from distant harbors, repeat your last manifest with **`↻`**, or automate recurring voyages via Redstone.
  - Load a **Sealed Trade Charter** alongside its required cargo onto an outbound **Trade Ship** or **Cargo Ship** to fulfill guild contracts remotely!

---

### 🌍 4. Maritime Trade Regions, Urgent Bounties & Guild Charters
Harbors automatically classify into five **Maritime Trade Regions**, each with signature exports and high-demand imports:
- 🏛️ **Temperate Maritime Hub** *(Plains, Forest, Cherry Grove, Beach)* — Exports: *Fine Porcelain, Tyrian Purple, Tea Brick, Luminous Pearl*
- 🏜️ **Arid & Levant Coast** *(Desert, Badlands, Savanna)* — Exports: *Saffron Bundle, Frankincense Tears, Damascus Steel, Salt Pouch*
- 🌴 **Tropical Spice Archipelago** *(Jungle, Mangrove, Warm Ocean, Bamboo)* — Exports: *Sandalwood, Vanilla Bundle, Cacao Elixir, Dragon's Blood Resin*
- ❄️ **Boreal & Amber Sea** *(Snowy, Taiga, Frozen Ocean, Ice Spikes)* — Exports: *Amber Vial, Sea Ivory, Ammonite Fossil, Salt Pouch*
- ⛰️ **Oriental & Highland Ports** *(Meadow, Windswept Hills, Stony Peaks, Dark Forest)* — Exports: *Silk Bale, Jade Figurine, Fine Porcelain, Tea Brick*

> **✦ High-Demand Arbitrage & Daily Urgent Bounties:**
> - Delivering a region's **High-Demand Import** directly to an Anchor Point (or via Trade Ship) pays **+60% to +100% bonus Doubloons** and halves shipping fees!
> - Every harbor posts a rotating **Daily Urgent Bounty** paying **2× Doubloons** (up to 3 claims/day; **Guild Depots** post a 2nd daily bounty!).
> - Complete **Sealed Guild Trade Charters** at matching regional harbors to earn Doubloons, XP, and **Guild Strongboxes** packed with rare maritime treasures.

---

### 🏮 5. Naval Captain's Kit, Coastal Docks & Trade Leagues
- **Captain's Brass Spyglass:** Zoom in (`2.8×` FOV) with a custom brass reticle and live **Maritime Recon HUD** showing target ship cargo/hull/harpoon status, Anchor Point level/specialization/bounty, or local regional exports & high-demand imports.
- **Bosun's Whistle:** Blow near water to glide your nearest uncrewed **Sloop** or **Cargo Ship** (within 48 blocks) safely to the shoreline, or `Sneak + Right-Click` to recall an outbound autonomous **Trade Ship** back to its home port.
- **Lighthouse Lantern:** Emits a rotating navigational beam visible from afar and grants a **+25% Tailwind Speed Boost** to all vessels within **128 blocks**.
- **Coastal Village Docks & Harbormasters:** Generates waterfront piers in coastal and riverside villages. Villagers working at an Anchor Point become **Harbormasters** with 5 tiers of maritime trades.
- **Wandering Sailors & 6 Maritime Trade Leagues:** Sea merchants who moor their faction-flagged Sloops (*Golden Levant Company, Boreal Amber Hanse, Emerald Archipelago Guild, Imperial Jade Flotilla, Crimson Privateer Syndicate, Azure Thalassocracy*) at coastal piers to trade signature regional goods, charters, and naval gear.

---

## 🧰 Complete Item & Equipment Reference

| Item | Category | Function |
| :--- | :--- | :--- |
| **Doubloon** | Currency | Minted gold sea coin used for voyage fares, cargo shipping, and harbor upgrades/specializations. |
| **Nautical Chart** | Navigation | Binds to an Anchor Point to store its coordinates, Trade Region, custom sea waypoints, and market snapshot. |
| **Route Journal** | Navigation | Interactive **Nautical Atlas** storing up to **12 Nautical Charts** with live fleet radar and trade filtering. |
| **Trade Ledger** | Commerce | Seals trade offers from villagers/Harbormasters and registers them to an Anchor Point. |
| **Sealed Trade Charter** | Contract | Regional merchant contract fulfilled at matching harbors or via ships for Doubloons, XP & a **Guild Strongbox**. |
| **Guild Strongbox** | Treasure | Ornate lockbox containing Doubloons, rare regional commodities, Nautical Charts, and sea relics. |
| **Captain's Brass Spyglass** | Naval Kit | `2.8×` scope with live **Maritime Recon HUD** for ships, harbors, and trade regions. |
| **Bosun's Whistle** | Naval Kit | Summons nearby Sloops/Cargo Ships to shore or recalls outbound autonomous Trade Ships. |
| **Brass Swivel Harpoon** | Naval Kit | Bow-mounted ship turret that automatically fires harpoon bolts at nearby hostile aquatic mobs. |
| **Captain's Tricorne** | Equipment | Wearable naval hat granting **+20% sailing speed** and **-10% Doubloon fee discount**. |
| **Captain's Pipe** | Utility | Puff to emit smoke rings, cure **Seasickness**, and gain **Luck I** (60s). |
| **Storm Glass** | Instrument | Weather barometer whose crystal sprite reacts to clear skies, rain, or storms. |
| **Brass Astrolabe** | Treasure | Ornate brass navigational instrument prized by Harbormasters and Trade Leagues. |
| **Salvage Dredge** | Tool | Scrapes the seabed from boats or piers to haul up sunken cargo, relics, and navigator maps. |
| **Message in a Bottle** | Lore & Loot | Uncorks into 15 maritime tales plus buried treasure maps, bound Nautical Charts, or relics. |
| **Consumables & Relics** | Active Goods | *Spice Sack, Amber Vial, Salt Pouch, Saffron Bundle, Jade Figurine, Cacao Elixir, Damascus Steel, Frankincense Tears, Tyrian Purple, Dragon's Blood Resin*. |
| **Trade Commodities** | Trade Goods | *Silk Bale, Luminous Pearl, Tea Brick, Fine Porcelain, Sandalwood, Vanilla Bundle, Ammonite Fossil, Sea Ivory*. |

---

## 🛠️ Commands, Compatibility & Localization

- **Commands:**
  - `/cot locate_dock` — Locates the nearest generated coastal village dock.
  - `/cot spawn_sailor` — Spawns a visiting Wandering Sailor and League Merchant Ship at the nearest harbor/dock.
  - `/cot harbors` — Lists all active registered harbors in the world.
- **JEI Integration:** Custom **Harbor Trade**, **Harbor Upgrade**, and **Shipyard Customization** categories plus detailed item info pages.
- **Advancements & Recipe Book:** **15 custom Maritime Advancements** + **13 vanilla Recipe Book unlock advancements**.
- **4 Languages Included:** Full localization for **English (`en_us`)**, **Turkish (`tr_tr`)**, **German (`de_de`)**, and **French (`fr_fr`)**.

---

## 📥 Installation

1. Install your preferred Minecraft & mod loader version:
   - **Minecraft 26.1.2** with **[NeoForge](https://neoforged.net/)**
   - **Minecraft 1.21.1** with **[NeoForge](https://neoforged.net/)**
   - **Minecraft 1.20.1** with **[Minecraft Forge](https://files.minecraftforge.net/)**
2. Download the matching release JAR from **Modrinth**, **CurseForge**, or [GitHub Releases](../../releases):
   - `currents_of_trade-1.0.7+neoforge-26.1.2.jar`
   - `currents_of_trade-1.0.7+neoforge-1.21.1.jar`
   - `currents_of_trade-1.0.7+forge-1.20.1.jar`
3. Place the `.jar` file into your `.minecraft/mods` directory.

---

## 📜 License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.
