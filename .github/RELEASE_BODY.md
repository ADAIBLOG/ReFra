## What's new in 5.2.0

ReFra 5.2.0 rebuilds People on accurate batch face clustering, turns cloud backup into real two-way sync with downloads and deletion propagation, and adds NFS shares, server-side smart search, custom upload folders, capture-time ordering, and verified cloud-to-cloud transfers.

### New Features

- **Rebuilt People section** — Deterministic batch clustering replaces order-dependent grouping, with a merge-review sheet for borderline pairs, possible-duplicates and scan-status cards, hidden people, and a detected-faces strip. Remove wrongly assigned faces from a person — from the person grid or straight from the media viewer — without deleting photos; corrections are remembered across rescans (#1161)
- **Two-way cloud sync** — Download remote-only media to the device per account, propagate remote deletions, and keep the cached index honest with Immich delta-sync plus a periodic full reconcile. A "Download all remote media" action lives in Sync Status (#1176)
- **NFS support** — Mount NFS shares as LAN cloud providers alongside SMB, with thumbnails, posters, upload and deletion support
- **Server-side smart search** — Text and visual search delegate to providers that support it (Immich smart search), synced server tags join metadata search, and a new Cloud libraries settings screen controls server search and per-provider on-device indexing (#1216)
- **Custom upload folders** — Set per-account upload and video base paths plus per-album overrides for WebDAV, ownCloud, Nextcloud, SMB and NFS backups (#1171)
- **Capture-time ordering** — A deferred capture-time index prefers embedded photo and video dates, so copied or moved files stop jumping in the timeline. Sort by capture or modified time and fix a photo's capture date from the viewer (#1206)
- **Copy and Move to cloud albums** — Verified transfers between the device and cloud albums — including across providers — with destination grouping, progress previews and safe move semantics (#1189)
- **Fluid viewer transitions** — The media viewer opens and closes with a shared-item animation, and dragging to dismiss peeks at the previous screen behind the card; story cards follow the same gesture (#1219)

### Improvements

- **Instant feedback** — Trash, delete, restore and favorite apply to the grid immediately, and the viewer stays open on the next item after a delete
- **Faster startup and navigation** — Cached Library and startup snapshots draw content on the first frame, and album state plus scroll position survive deep viewer opens (#1213)
- **Lighter cloud backup** — Fewer foreground database writes and throttled progress updates keep the app responsive while backups run (#1212)
- **Selection action bar** — Long action lists scroll instead of squashing labels, and a new Fill action bar option packs every action into the pill

### Bug Fixes

- **Hidden cloud albums actually hide** — Ignored cloud albums now remove their media from the unified timeline and the albums grid (#1174)
- **Restored blurred video chrome** — Overlay-mode and story videos blur the captured frame again during viewer dismissal (#1219)
- **Stable Story Cards** — The viewer keeps a stable snapshot, playback progress and predictable dismissal (#1217)
- **Hardened cloud thumbnails and deletion** — Fetches await account initialization, NetFS videos get poster frames, WebDAV deletes are idempotent and NFS removal is verified (#1215)
- **Matching splash icon** — The splash screen shows the legacy Gallery logo when that launcher icon is picked (#1158)
- **Repaired location names** — Saved but unresolved location names fix themselves at startup (#1211)
- **Square map tiles** — Static map previews keep geographic alignment on wide cards (#1203)
