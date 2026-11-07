DUSTLINE v11 - SMOOTH PATCH (lag / hang fix). Nothing removed from the game: same map, guns, bots, vehicles, HUD, graphics options.
Search the file for "PERF v3". 9 small tuning edits only:

1 Internal render-resolution budget (biggest fix): supersampling x pixel-ratio could make the GPU draw up to ~12x the screen pixels.
  Now capped (desktop ~5 MP, phone ~2.2 MP). Ultra look is kept (still supersampled, same bloom / AO / grading / PBR).
2 MSAA 4x -> 2x only while supersampling is already >= 1.3x (the extra 2x samples added no visible quality, only cost).
3 Auto-resolution governor reacts 2x faster (45 frames instead of 90), steps down bigger, waits before stepping up (no flicker / resize hitches).
4 Ultra shadow cascades: 4096 maps 2 -> 1 (13 m cascade 2048 is still ~1.3 cm per texel), PCSS taps 16 -> 12, 50 m cascade refreshes every 2nd frame.
  Shadows, soft edges and all shadow distances stay the same.
5 Game loop: max physics sub-steps 5 -> 3 (stops the "slow frame -> more work -> even slower" hang spiral). Movement / shooting speed unchanged.
6 Pixel-ratio governor reacts faster (1.2 s window).

Live tweak in console (F12): DLPB.mul=1.3  (more sharpness, lower FPS)   DLPB.mul=0.7  (more FPS)
Original untouched file: Dustline_landscape_v10_FULLSCREEN.html in your own zip.
