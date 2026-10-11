# Results and limits

- Autocomplete suggests visible online names without requesting restriction data. IGN lookups resolve a verified Minecraft UUID through the website; offline-mode server UUIDs are not substituted for that identity.
- Active records appear before past records. Details contain community, literal reason, categories, UTC dates, verification status, and available source links. Missing expiry dates display **Not recorded**, not **Permanent**; explicit permanent wording remains in the reason.
- Each new check fetches a fresh snapshot. Detail pages use that snapshot and make no further requests. A new check or disconnect invalidates old detail buttons.
- The public API returns up to 100 active and 50 past records. Counts include additional records, and the result tells you to use the website for the complete list.
- One request can run per caller, with a five-second cooldown. The service may impose stricter limits shared by a server's public IP. `Retry-After` blocks further requests for the indicated duration; there are no automatic retries.
- Errors distinguish bad input, nonexistent players, rate limiting, and service failure. A service failure never means the player has no restrictions.


The `common` module is included in this repository so it builds independently. Keep shared lookup fixes consistent with the other RestrictTiers Minecraft repository.
