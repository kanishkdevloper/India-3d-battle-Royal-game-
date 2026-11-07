DUSTLINE v12 - NO-LAG PATCH (v11 SMOOTH par). Kuch bhi hataya nahi gaya: map, guns, bots, vehicles, HUD, ultra graphics sab same.
HTML me search karo: "PERF v4"  (4 chhote edits)

1 Texture sweep: har 1.5 sec me poora scene scan hota tha (12 ms tak) = periodic atakna. Ab budget 4 ms, aur kaam khatam hone par 8 sec me ek baar.
2 dlEnv material scan: shuruaati warm-up same, uske baad har 90 ki jagah har 300 frame.
3 Auto-resolution floor: 0.85 -> 0.65 (aur bahut slow hone par bada step). Kamzor GPU/phone par FPS jaldi wapas aata hai; strong device par ultra look wahi rehta hai.
4 Hard clamp 0.75 -> 0.6.

Console (F12): DLPB.mul=0.7 (aur FPS), DLPB.mul=1.3 (aur sharp). Original file: Dustline_landscape_v11_SMOOTH.html
