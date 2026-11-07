DUSTLINE v9 - PUBG OPTIMIZED  (v8 PUBG Ultra + Performance Pack v2)
Open Dustline_landscape_v9_PUBG_Optimized.html exactly like v8 (internet once for three.js from the CDN).
Search the file for "DUSTLINE PERFORMANCE PACK v2".

1 Distance-based optimization
  - bots further than 70 m stop casting shadows (shadow pass is the most expensive part of 35 skinned bots)
  - skeletons update every 2nd frame beyond 90 m, every 3rd beyond 210 m (position stays smooth)
  - bots hidden beyond 720 m, loot beyond 170 m, vehicles beyond 650 m
  - everything scales with scope zoom: when you zoom in, far things get full detail again
2 LOD
  - characters: 3 mesh levels built at runtime in idle time (full -> ~24% triangles at 48 m -> ~9% at 115 m), skinning + UVs kept
  - vegetation: full -> low-poly -> hidden, distances follow the fog; bushes/rocks/palms get their own cut-off distance
3 Frustum culling
  - skinned characters, loot items and vehicles are now culled against the camera frustum (they never were before)
  - anything within 26 m is always kept so shadows / flanking enemies never pop
4 Texture compression
  - texture memory budget: textures larger than the tier cap are re-sampled (box-filtered) and the oversize GPU copy is freed
  - cap: ultra desktop 4096 (unchanged), ultra phone 2048 (1024 on <=4 GB), high 2048/1024, mid 1024, low 512
  - note: GPU block formats (KTX2/Basis) need textures converted offline; this layer works on the decoded images
5 Instanced rendering
  - brass / shotgun shells / magazines: up to 36 draw calls -> 3 InstancedMesh draw calls
  - vegetation (trees, bushes, rocks, grass, flowers) was already instanced in v8 and is kept

Console (F12), live, no reload:
  DLOPT.enabled=false      switches the whole pack off (everything is restored)
  DLOPT.cull=false  DLOPT.charLod=false  DLOPT.texSweep=false
  DLOPT.shadowDist=70  DLOPT.lod1=48  DLOPT.lod2=115  DLOPT.botFar=720  DLOPT.lootFar=170  DLOPT.vehFar=650
  DLOPT.vegScale=1.3       vegetation LOD distances x1.3 (more detail, lower FPS)
  DLOPT.texCap=1024        force a texture cap
  DLOPT.stats              live counters
