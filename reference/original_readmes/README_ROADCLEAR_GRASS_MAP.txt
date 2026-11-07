DUSTLINE v9 - ROAD CLEAR + NO GRASS + ROTATING MINIMAP
(search the HTML for "DUSTLINE ROAD CLEAR v1", "DLNOGRASS", "DLMAPROT")

1 Houses off the road
  - every house whose walls / porch / roof edge touched or came near the asphalt is lifted and
    moved back (72 houses; average ~19 m, nearest possible spot, flat ground preferred)
  - collision boxes, the low-poly baked house meshes and the generated enterable buildings all move together
  - nothing of any house is closer than ~4 m to the asphalt now (before: 2440 building corners lay ON the road)
  - houses are never dropped on other houses, on the viaduct, inside the landmark compounds, on cars or on walls
  - add ?noclear to the URL to load the original layout

2 No grass
  - grass blades and wildflowers are gone
  - the green "lush grass" ground paint is replaced by dry soil, so no green meadow is left
  - trees, bushes, palms and rocks are untouched

3 Minimap rotates with the player
  - the corner minimap turns so the direction you look is always UP, a red N marks north
  - in the plane (full island view) the map stays north-up
  - console: DLMAPROT=false turns the rotation off
