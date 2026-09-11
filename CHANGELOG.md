# Changelog

## Unreleased

- Send the exact final cargo position before delivery or ejection so buffered
  animations reach the tube outlet instead of disappearing at the last snapshot.
- Fixed false obstruction reports on filter-pipe side connections by sharing
  diagonal port geometry between placement and transport for every orientation.
- Smoothed mixed-route cargo with buffered server timestamps instead of restarting
  interpolation on each packet; curved motion now interpolates physical distance.
- Restored standalone-curve breaking effects, normal pickup delay and material
  refunds, Shift-wrench dismantling and middle-click tube selection.
- Matched white-dye reset, open straight-end transport and divider branch
  priorities between block-based and standalone routes.
- Render cargo on mixed straight/curved routes before transparent tube walls,
  so items remain visible inside straight sections.
- Cargo now follows extensions added to a curve after dispatch, retaining its
  upstream pump when the remaining route is recalculated, including after reload.
- Fixed missing collars where straight tubes connect to standalone curved sections.
- Curved tubes are now standalone sections with their own endpoints, geometry,
  collision, appearance and saved cargo; construction no longer places support blocks.
- Added interaction with the visible section, free-end extension, painting and
  glow ink, and block placement beside its surface.
- Connected standalone sections to pumps, connectors and routing nodes. Cargo
  travels by physical distance along a bend and persists independently of blocks.
- Existing curved block chains migrate when their chunks load, retaining their
  material cost, colors, glow and cargo.

## 1.0.0

- Added pneumatic tubes with curved routing sections.
- Added pneumatic extractors with filters and redstone control.
- Added kinetic item pumps with speed-dependent stress impact.
- Added Ponder scenes for tubes, extractors and pumps.
- Added Engineer's Goggles transport-speed readout.
