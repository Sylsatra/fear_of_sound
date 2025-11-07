# Fear of Sound - Changelog

## 2.0.8

- Advanced Fear Overrides: new config list `Advanced Fear Overrides.fearofsoundadvance` to conditionally gate fear by entity, NBT, and sound.
- Predicate support: `health<=X`, `health>=X`, `health<X`, `health>X`, `health_pct<=p`.
- SA and non-SA integration: gating enforced in both paths before reacting to sounds.
- Auto attach FearGoal: attach on spawn or via periodic scan when an entity is covered by advanced rules (even if not in `affectedEntities`).
- Config auto-updater: adds the new key with example entries.
- Fix: resolved lambda capture issue in comparator handling.

Examples:
- `low_health_any;*;health_pct<=0.30;*`
- `zombie_low_hp_tacz;minecraft:zombie;health_pct<0.30;tacz:gun`
