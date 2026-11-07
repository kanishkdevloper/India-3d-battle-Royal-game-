DUSTLINE v13 - ROAD-FREE + SMOOTH (v12 par). Kuch hataya nahi gaya; sirf road se trees/bushes/rocks aur ghar door kiye.
HTML me search karo: "v13"

1 Trees road par kyun the: purana road-mask sirf road ke dono kinaaron ki patti jaanta tha, beech ka asli asphalt nahi
  (asli asphalt ke ~28% points ko "road nahi" maana jaata tha). Ab asli asphalt ki doori (metre me) use hoti hai.
  Pine >= 4.5 m, oak >= 4 m, palm >= 3 m, bush >= 1.8 m, rocks >= 2.2 m road se. Baaki jungle same.
2 Ghar: road se minimum doori 4.5 m -> 7 m (porch/chhat samet). 221 me se 80 ghar khiskaye, 0 fail (headless verify).
3 Lag: phone par auto-quality 19.5 ms par hi resolution ghatata hai (pehle 23 ms), resolution steps 0.1 (kam realloc hitch).
4 v12 ke fixes saath me: texture-sweep hitch, env-scan hitch, kam floor resolution.
?noclear URL me jodne par original layout aata hai. Console: DLPB.mul=0.7 (aur FPS)

--- PERF v5 (is update me) ---
5 Bug fix: phone par resolution wapas badhne ki shart 15.8 ms thi (60 Hz par kabhi nahi milti). Ab 17.4 ms.
6 Emergency level (DLEM): sirf tab jab sabse kam resolution par bhi frame slow ho (>26 ms):
  level1 MSAA max 2x, level2 + shadow quality 'medium' tak, level3 + MSAA off. ~20 sec smooth rehne par apne aap wapas.
  Console: DLEM.on=false (band), DLEM.e (abhi ka level)
7 Memory: map/landmark/grass ka ~30 MB base64 text decode ke baad DOM se hata diya jata hai (kam memory = kam GC atak).
