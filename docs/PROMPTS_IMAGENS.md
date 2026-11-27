# Prompts de imagens para os temas do Plurali

Este documento tem tudo o que é preciso pedir a uma IA de imagens (Midjourney, DALL·E, Ideogram, Leonardo, Flux…)
para deixar os temas mais bonitos: papéis de parede, faixas decorativas, enfeites de canto, molduras de perfil,
formatos de foto e ilustrações de tela vazia.

**Como usar**
1. Cole primeiro o **Guia de estilo** (seção 1) — ele garante que todas as imagens combinem entre si.
2. Depois cole o prompt de cada imagem (seções 3 a 5). Os prompts estão em inglês porque as IAs seguem melhor.
3. Salve cada imagem com o **nome de arquivo** indicado, na pasta indicada. Assim eu encaixo no app sem adivinhar.
4. Comece pelo que está marcado **[ESSENCIAL]**. O resto é **[EXTRA]**.

> Se a IA não fizer fundo transparente, peça "on a plain pure white background" e depois remova o fundo
> (remove.bg, Canva, ou me mande que eu removo). Imagens com transparência precisam ser **PNG**.

---

## 1. Guia de estilo (cole isto antes de qualquer prompt)

```
You are creating UI art assets for a gentle, cozy Android app used by plural systems (people with DID/OSDD
who share one body). The art must feel safe, warm and calm — never scary, gory, sexual or aggressive.

Global rules for EVERY image:
- No text, no letters, no numbers, no logos, no watermarks, no signatures, no UI elements, no phone frames.
- No realistic human faces. Characters, if any, are cute stylized animals, silhouettes or masked figures seen from far.
- Style: polished 2D digital illustration, soft shading, clean edges, slightly textured (paper / gouache feel),
  harmonious limited palette (use the hex colors given), consistent line weight, gentle lighting.
- Readability first: the app shows white or dark text and cards on top of these images, so keep detail and
  contrast LOW in the center and put the interesting elements near the edges/corners.
- Mobile-friendly: shapes must stay recognizable when shown small.
- For transparent assets: isolated object, fully transparent background (PNG with alpha), no drop shadow on the
  background, no ground plane, centered with ~5% empty margin.
```

---

## 2. Tipos de imagem, tamanhos e nomes de arquivo

| Tipo | Para que serve | Tamanho (px) | Fundo | Nome do arquivo |
|---|---|---|---|---|
| Papel de parede escuro | Fundo das telas no modo escuro | 1080 × 2400 (retrato) | Opaco | `wallpaper_dark.png` |
| Papel de parede claro | Fundo das telas no modo claro | 1080 × 2400 (retrato) | Opaco | `wallpaper_light.png` |
| Faixa do topo | Enfeite embaixo da barra de título; precisa **repetir sem emenda** na horizontal | 1440 × 240 | Transparente | `band.png` |
| Enfeite de canto | Fica no canto dos cartões | 256 × 256 | Transparente | `corner.png` |
| Moldura de perfil do tema | Vai em volta da foto (o meio fica vazio) | 1024 × 1024 | Transparente, **círculo vazio no centro** com 70% do tamanho | `frame.png` |
| Tela vazia | Aparece quando não há nada numa lista | 800 × 800 | Transparente | `empty.png` |
| Miniatura do tema | Botão na tela de escolher tema | 512 × 512 | Opaco | `thumb.png` |

**Pastas:** `assets/themes/<id-do-tema>/` — por exemplo `assets/themes/spider/wallpaper_dark.png`.
Os ids estão no título de cada tema abaixo (entre parênteses).

**Regra da moldura (`frame.png`):** o centro da imagem precisa ficar **totalmente transparente** num círculo
de 716 px de diâmetro (70% de 1024), centralizado. A decoração fica só no anel em volta. Peça assim:
`"...arranged as a ring around a perfectly empty transparent circular hole in the center (70% of the width)..."`

**Regra da faixa (`band.png`):** a borda esquerda tem que continuar na direita (repetição horizontal).
Os elementos ficam pendurados/encostados na **parte de cima**, e a parte de baixo vai sumindo.
Peça: `"seamless horizontally tileable border strip, elements hanging from the top edge, fading out toward the bottom"`.

---

## 3. Prompts por tema

Cada tema tem: papel de parede escuro e claro, faixa, enfeite de canto, moldura e tela vazia.
**[ESSENCIAL]** = papel de parede escuro + claro, faixa e moldura. **[EXTRA]** = canto, tela vazia, miniatura.

### 3.1 Clássico (`classic`)
Cores: dourado `#D6AC5E`, grafite `#1D2228`, cinza-azulado `#384048`, off-white `#F1F2F5`.
Clima: elegante, discreto, atemporal.

- **wallpaper_dark** [ESSENCIAL]
  `Elegant minimal dark background, deep graphite #1D2228 with very subtle diagonal art-deco gold #D6AC5E line work only along the top and bottom edges, faint grain texture, calm empty center, portrait 1080x2400.`
- **wallpaper_light** [ESSENCIAL]
  `Same composition as an elegant minimal light background, soft off-white #F1F2F5 with faint gold #D6AC5E art-deco line work near the top and bottom edges, paper texture, calm empty center, portrait 1080x2400.`
- **band** [ESSENCIAL]
  `Seamless horizontally tileable thin art-deco border, gold #D6AC5E fan and line motifs hanging from the top edge, fading out toward the bottom, transparent background, 1440x240.`
- **frame** [ESSENCIAL]
  `Thin elegant gold #D6AC5E art-deco ring with small geometric fan details at the top and bottom, arranged as a ring around a perfectly empty transparent circular hole in the center (70% of the width), transparent background, 1024x1024.`
- **corner** [EXTRA] `Small gold art-deco corner ornament, quarter fan with fine lines, transparent background, 256x256.`
- **empty** [EXTRA] `A cozy empty armchair with a soft blanket and a warm lamp, minimal gold and graphite palette, transparent background, 800x800.`

### 3.2 Gatos (`cat`)
Cores: lilás acinzentado `#8C86A0`, amarelo-lua `#E8C547`, branco-lilás `#EDEAF2`, fundo noturno `#1B1826`.
Clima: noite tranquila, lua, gatinhos pretos fofos, patinhas.

- **wallpaper_dark** [ESSENCIAL]
  `Calm night sky in deep indigo #1B1826 with a big soft crescent moon #E8C547 in the top corner, tiny stars, a row of rooftops at the bottom edge with a cute black cat silhouette sitting on one roof looking at the moon, faint paw prints, empty calm center, portrait 1080x2400.`
- **wallpaper_light** [ESSENCIAL]
  `Soft lavender #EDEAF2 dusk sky, pale yellow moon #E8C547 in a corner, a sleepy cute black cat curled on a windowsill at the bottom edge, scattered small paw prints in #8C86A0 at low opacity, empty calm center, portrait 1080x2400.`
- **band** [ESSENCIAL]
  `Seamless horizontally tileable border of small black cats peeking over the top edge with only their ears, eyes and paws visible, little yellow #E8C547 stars between them, transparent background, 1440x240.`
- **frame** [ESSENCIAL]
  `Cute black cat frame: two cat ears on top, two little paws hugging the bottom, a curling tail on the right side, soft lilac #8C86A0 outline, arranged as a ring around a perfectly empty transparent circular hole in the center (70% of the width), transparent background, 1024x1024.`
- **corner** [EXTRA] `A tiny black kitten peeking from a corner with big yellow eyes, transparent background, 256x256.`
- **empty** [EXTRA] `A cute black cat sleeping curled up on a cushion under a crescent moon, transparent background, 800x800.`

### 3.3 Jardim (`garden`)
Cores: verde-folha `#6BA35E`, rosa `#E58FB0`, amarelo `#F2C94C`, lilás `#9C7BC4`, fundo claro `#F3F7EE`.
Clima: jardim de primavera, folhas, flores pequenas, borboletas ao longe.

- **wallpaper_dark** [ESSENCIAL]
  `Garden at twilight, deep green #17261A background, lush leaves and small pink #E58FB0, yellow #F2C94C and lilac #9C7BC4 flowers framing the top and bottom edges, fireflies glowing softly, empty calm center, portrait 1080x2400.`
- **wallpaper_light** [ESSENCIAL]
  `Fresh spring garden, soft cream-green #F3F7EE background, watercolor leaves #6BA35E and small flowers in pink, yellow and lilac growing from the bottom edge and hanging from the top, a tiny butterfly, empty calm center, portrait 1080x2400.`
- **band** [ESSENCIAL]
  `Seamless horizontally tileable garland of leafy vines with small pink, yellow and lilac flowers hanging from the top edge, watercolor style, transparent background, 1440x240.`
- **frame** [ESSENCIAL]
  `Flower crown wreath of green #6BA35E leaves with pink, yellow and lilac small flowers and a tiny ladybug, arranged as a ring around a perfectly empty transparent circular hole in the center (70% of the width), transparent background, 1024x1024.`
- **corner** [EXTRA] `Small corner cluster of leaves and two tiny pink flowers, watercolor, transparent background, 256x256.`
- **empty** [EXTRA] `A small watering can next to a sprouting seedling in a pot, watercolor, transparent background, 800x800.`

### 3.4 Homem-Aranha (`spider`)
Cores: vermelho `#E23636`, azul-marinho `#05081A`, azul `#1F4FB8`, branco-azulado `#C8D0F0`.
Clima: cidade à noite, teias, ação mas sem violência.

> **Sobre o personagem:** muitas IAs recusam gerar personagens com direitos autorais pelo nome
> ("Spider-Man"). Os prompts abaixo descrevem o **estilo** (herói mascarado de vermelho e azul, teias,
> emblema de aranha, cidade) sem citar o nome — funciona na maioria das IAs. Se a sua aceitar o nome, pode
> acrescentar "in the style of Spider-Man". Use só para uso pessoal (o repositório é privado).

- **wallpaper_dark** [ESSENCIAL]
  `Night city skyline of tall buildings in deep navy #05081A with glowing windows, a big full moon, a masked hero in a red #E23636 and blue #1F4FB8 suit with black web pattern swinging on a web line seen as a small silhouette against the moon in the top area, spider webs in the top corners, empty calm center, portrait 1080x2400.`
- **wallpaper_light** [ESSENCIAL]
  `Daytime city rooftops in pale blue #C8D0F0 with soft clouds, delicate white spider webs in the corners, a small red and blue masked hero crouching on a rooftop antenna at the bottom edge seen from far, comic halftone dots at low opacity, empty calm center, portrait 1080x2400.`
- **band** [ESSENCIAL]
  `Seamless horizontally tileable border of spider web strands hanging from the top edge with small dangling spiders, red #E23636 and navy accents, comic book style, transparent background, 1440x240.`
- **frame** [ESSENCIAL]
  `Spider web frame: a round spider web in white and red #E23636 with a small black spider emblem at the top, comic halftone accents, arranged as a ring around a perfectly empty transparent circular hole in the center (70% of the width), transparent background, 1024x1024.`
- **corner** [EXTRA] `Corner spider web with a cute little spider hanging by a thread, transparent background, 256x256.`
- **empty** [EXTRA] `A red and blue masked hero's mask resting on a rooftop ledge next to a coffee cup at night, cozy, transparent background, 800x800.`
- **extra: emblema** [EXTRA] `Stylized spider emblem, bold black spider with long legs, simple, transparent background, 512x512.` → `emblem.png`

### 3.5 Coelhos (`bunny`)
Cores: rosa-pó `#D9B8C4`, laranja-cenoura `#F5A05A`, verde `#7CC08A`, rosa `#EC7FA9`, fundo `#FFF6F8`.
Clima: pastel, macio, fofo.

- **wallpaper_dark** [ESSENCIAL]
  `Soft dark plum #2A1F2A night meadow, cute sleeping bunnies curled in the grass at the bottom edge, pastel pink #EC7FA9 hearts and tiny carrots #F5A05A floating near the top, dreamy, empty calm center, portrait 1080x2400.`
- **wallpaper_light** [ESSENCIAL]
  `Pastel pink #FFF6F8 background with a soft meadow at the bottom, two fluffy white bunnies nibbling carrots, little hearts and clovers near the edges, cotton-soft texture, empty calm center, portrait 1080x2400.`
- **band** [ESSENCIAL]
  `Seamless horizontally tileable border of bunny ears peeking from the top edge alternating with small carrots and pink hearts, pastel, transparent background, 1440x240.`
- **frame** [ESSENCIAL]
  `Fluffy pastel frame with long bunny ears on top, a small carrot and a pink bow on the side, dotted pink #EC7FA9 outline, arranged as a ring around a perfectly empty transparent circular hole in the center (70% of the width), transparent background, 1024x1024.`
- **corner** [EXTRA] `A small fluffy bunny tail and a carrot in a corner, pastel, transparent background, 256x256.`
- **empty** [EXTRA] `A cute bunny hugging a big carrot, pastel, transparent background, 800x800.`

### 3.6 Inferno (`inferno`)
Cores: laranja `#FF5A1F`, âmbar `#FFA726`, amarelo `#FFD54F`, vinho `#B71C1C`, fundo `#160806`.
Clima: brasas e fogo, intenso mas aconchegante (como lareira), **nada assustador**.

- **wallpaper_dark** [ESSENCIAL]
  `Dark ember background #160806 with glowing embers floating upward, warm flames rising softly from the bottom edge in #FF5A1F, #FFA726 and #FFD54F, smoky glow, cozy fireplace mood not scary, empty calm center, portrait 1080x2400.`
- **wallpaper_light** [ESSENCIAL]
  `Warm sunset-orange #FFE7D6 background with soft stylized flames at the bottom edge and small sparks drifting up, gentle gradient, cozy, empty calm center, portrait 1080x2400.`
- **band** [ESSENCIAL]
  `Seamless horizontally tileable border of stylized flames hanging from the top edge (upside-down fire licks) with sparks, orange and amber, transparent background, 1440x240.`
- **frame** [ESSENCIAL]
  `Ring of stylized fire with small horns-shaped flame tips at the top and embers floating around, orange #FF5A1F to yellow #FFD54F glow, arranged as a ring around a perfectly empty transparent circular hole in the center (70% of the width), transparent background, 1024x1024.`
- **corner** [EXTRA] `Small corner flame with sparks, transparent background, 256x256.`
- **empty** [EXTRA] `A little cute fire spirit sitting by a campfire, warm and friendly, transparent background, 800x800.`

### 3.7 Fadas (`fairy`)
Cores: lilás `#B98CE6`, rosa `#F7A8D0`, turquesa `#7FCFCF`, dourado `#F2C94C`, fundo `#1E1630`.
Clima: floresta mágica, brilhos, cogumelos, asas transparentes.

- **wallpaper_dark** [ESSENCIAL]
  `Enchanted forest at night, deep violet #1E1630, glowing mushrooms and fireflies at the bottom edge, sparkling fairy dust in lilac, pink and turquoise near the top, a tiny fairy silhouette with glowing wings far away, empty calm center, portrait 1080x2400.`
- **wallpaper_light** [ESSENCIAL]
  `Dreamy pastel fairy glade, soft lilac-pink gradient, flowers and small mushrooms at the bottom, floating sparkles and bubbles, iridescent light, empty calm center, portrait 1080x2400.`
- **band** [ESSENCIAL]
  `Seamless horizontally tileable string of tiny fairy lights and hanging flowers with sparkles from the top edge, pastel lilac, pink and turquoise, transparent background, 1440x240.`
- **frame** [ESSENCIAL]
  `Delicate iridescent fairy wings on both sides, a small flower crown on top and sparkles around, pastel lilac #B98CE6 and pink #F7A8D0, arranged as a ring around a perfectly empty transparent circular hole in the center (70% of the width), transparent background, 1024x1024.`
- **corner** [EXTRA] `A small glowing mushroom with sparkles in a corner, transparent background, 256x256.`
- **empty** [EXTRA] `A tiny fairy sleeping inside a flower, glowing softly, transparent background, 800x800.`

### 3.8 Exploração (`geo`)
Cores: marrom-couro `#8B6B3E`, azul-oceano `#1F6E8C`, terracota `#B5523B`, papel `#F2E8D5`.
Clima: mapas antigos, bússola, carimbos de viagem.

- **wallpaper_dark** [ESSENCIAL]
  `Old nautical map at night, dark navy-brown #1B1A17 with faint gold contour lines and a compass rose in the top corner, dotted travel routes and a small paper boat near the bottom edge, empty calm center, portrait 1080x2400.`
- **wallpaper_light** [ESSENCIAL]
  `Vintage parchment map #F2E8D5 with faded coastlines, contour lines, a compass rose in a corner, dashed route lines and small mountains along the bottom edge, empty calm center, portrait 1080x2400.`
- **band** [ESSENCIAL]
  `Seamless horizontally tileable vintage map border with a measuring scale bar, little flags and dashed route, brown and ocean blue, transparent background, 1440x240.`
- **frame** [ESSENCIAL]
  `Vintage compass rose ring frame with cardinal points N E S W marked by small arrows (no letters), brass and leather tones #8B6B3E, arranged as a ring around a perfectly empty transparent circular hole in the center (70% of the width), transparent background, 1024x1024.`
- **corner** [EXTRA] `Small vintage postage stamp with a mountain drawing, wavy edge, transparent background, 256x256.`
- **empty** [EXTRA] `An open travel journal with a compass and a small backpack, transparent background, 800x800.`

### 3.9 Insetos (`insects`)
Cores: vermelho-joaninha `#D2452E`, preto `#2B2B2B`, mel `#F2B632`, verde `#7FA650`, azul-céu `#8FB8DE`.
Clima: joaninhas, abelhas, borboletas, favos de mel — tudo fofo (**nada nojento**).

- **wallpaper_dark** [ESSENCIAL]
  `Night meadow in dark green #14201A with glowing fireflies, a cute ladybug on a leaf at the bottom edge, honeycomb pattern faintly in the top corners, empty calm center, portrait 1080x2400.`
- **wallpaper_light** [ESSENCIAL]
  `Sunny meadow, pale sky blue #8FB8DE to cream gradient, cute bees and a butterfly near the top, grass and clover with a ladybug at the bottom, faint honeycomb hexagons, empty calm center, portrait 1080x2400.`
- **band** [ESSENCIAL]
  `Seamless horizontally tileable border of honeycomb cells dripping a bit of honey from the top edge with tiny cute bees flying, transparent background, 1440x240.`
- **frame** [ESSENCIAL]
  `Honeycomb hexagon ring frame with a cute bee and a ladybug resting on it, honey #F2B632 and red #D2452E accents, arranged as a ring around a perfectly empty transparent circular hole in the center (70% of the width), transparent background, 1024x1024.`
- **corner** [EXTRA] `A tiny cute ladybug on a leaf in a corner, transparent background, 256x256.`
- **empty** [EXTRA] `A cute bee sleeping on a flower, transparent background, 800x800.`

### 3.10 Desenho (`artist`)
Cores: azul-caderno `#9DB5D9`, vermelho `#E57373`, grafite `#555555`, amarelo `#F2B632`, ciano `#4FC3F7`, roxo `#BA68C8`, verde `#81C784`.
Clima: folha de caderno, rabiscos, lápis de cor, manchas de tinta. **Manter o visual feito à mão.**

- **wallpaper_dark** [ESSENCIAL]
  `Dark chalkboard-like sketchbook page #22252B with faint grid lines, hand-drawn doodles in colored pencil (stars, spirals, little hearts, arrows) only near the edges, a few watercolor paint splashes in the corners, empty calm center, portrait 1080x2400.`
- **wallpaper_light** [ESSENCIAL]
  `Lined notebook paper with blue lines #9DB5D9 and a red margin line, colorful hand-drawn doodles and crayon scribbles near the edges, washi tape pieces in the corners, a pencil lying at the bottom edge, empty calm center, portrait 1080x2400.`
- **band** [ESSENCIAL]
  `Seamless horizontally tileable strip of colorful washi tape and hand-drawn doodle bunting hanging from the top edge, sketchy pencil lines, transparent background, 1440x240.`
- **frame** [ESSENCIAL]
  `Hand-drawn sketchy pencil circle drawn several times, colored pencil scribbles, a small paint splash and a pencil tucked on the side, arranged as a ring around a perfectly empty transparent circular hole in the center (70% of the width), transparent background, 1024x1024.`
- **corner** [EXTRA] `A piece of colorful washi tape at a corner, slightly torn edges, transparent background, 256x256.`
- **empty** [EXTRA] `An open sketchbook with colored pencils and a small paint palette, doodle style, transparent background, 800x800.`

### 3.11 Pássaros (`birds`)
Cores: azul-ardósia `#34495E`, laranja `#E67E22`, azul-céu `#5DADE2`, amarelo `#F4D03F`, ferrugem `#D35400`.
Clima: céu aberto, bandos voando, passarinhos no fio, penas.

- **wallpaper_dark** [ESSENCIAL]
  `Evening sky in deep slate blue #1A2430 with a flock of small birds flying in the distance near the top, a power line at the bottom edge with three cute round birds sitting on it, a few floating feathers, empty calm center, portrait 1080x2400.`
- **wallpaper_light** [ESSENCIAL]
  `Bright morning sky #D6ECF8 with soft clouds, flock of birds near the top, cute round birds on a wire at the bottom, warm orange #E67E22 and yellow #F4D03F accents, empty calm center, portrait 1080x2400.`
- **band** [ESSENCIAL]
  `Seamless horizontally tileable telephone wire along the top with small cute round birds sitting on it at different spacing, transparent background, 1440x240.`
- **frame** [ESSENCIAL]
  `Nest-like ring of twigs and soft feathers with a small cute bird perched on top, warm orange and sky blue feathers, arranged as a ring around a perfectly empty transparent circular hole in the center (70% of the width), transparent background, 1024x1024.`
- **corner** [EXTRA] `A single soft feather in a corner, orange to blue, transparent background, 256x256.`
- **empty** [EXTRA] `A cute round bird sitting in an empty nest, transparent background, 800x800.`

### 3.12 Ladybug (`ladybug`)
Cores: vermelho `#E53935`, preto `#000000`, lilás-noite `#8E7AA8`, amarelo-luz `#FFE082`.
Clima: Paris à noite, Torre Eiffel, bolinhas pretas no vermelho, ioiô.

> Mesma observação do Homem-Aranha: os prompts descrevem o estilo (heroína mascarada com estampa de joaninha,
> Paris, ioiô) sem citar o nome do desenho. Uso pessoal.

- **wallpaper_dark** [ESSENCIAL]
  `Paris rooftops at night in purple-blue #2A2140 with the Eiffel Tower glowing #FFE082 in the distance, a small masked heroine silhouette in a red #E53935 suit with black polka dots swinging on a yo-yo string near the top, soft stars, empty calm center, portrait 1080x2400.`
- **wallpaper_light** [ESSENCIAL]
  `Soft pink-lilac dusk sky over Paris rooftops, Eiffel Tower in the distance, red and black polka dots scattered faintly in the corners, a red yo-yo with black dots at the bottom edge, empty calm center, portrait 1080x2400.`
- **band** [ESSENCIAL]
  `Seamless horizontally tileable border of red #E53935 bunting flags with black polka dots hanging from the top edge, with small Eiffel Tower silhouettes between them, transparent background, 1440x240.`
- **frame** [ESSENCIAL]
  `Red ring with black polka dots like a ladybug shell, a small red yo-yo with black spots hanging at the bottom by a string, arranged as a ring around a perfectly empty transparent circular hole in the center (70% of the width), transparent background, 1024x1024.`
- **corner** [EXTRA] `A small red yo-yo with black spots in a corner, transparent background, 256x256.`
- **empty** [EXTRA] `A cute ladybug resting on a croissant next to a tiny Eiffel Tower figurine, transparent background, 800x800.`

### 3.13 Vento (`wind`)
Cores: verde-sálvia `#6FA882`, verde `#7CB342`, laranja-suave `#F2A65A`, fundo `#EEF5F0`.
Clima: leve, arejado, folhas voando, linhas de vento, vidro.

- **wallpaper_dark** [ESSENCIAL]
  `Deep teal-green #12201B background with flowing translucent wind swirl lines and a few leaves drifting across the top and bottom edges, soft glassy light, empty calm center, portrait 1080x2400.`
- **wallpaper_light** [ESSENCIAL]
  `Airy pale mint #EEF5F0 background with elegant curling wind lines, green #7CB342 and soft orange #F2A65A leaves blowing across the edges, a dandelion releasing seeds at the bottom, empty calm center, portrait 1080x2400.`
- **band** [ESSENCIAL]
  `Seamless horizontally tileable border of curling wind swirls and small leaves blowing along the top edge, sage green, transparent background, 1440x240.`
- **frame** [ESSENCIAL]
  `Ring of swirling wind lines with green and orange leaves caught in the breeze and a few dandelion seeds, arranged as a ring around a perfectly empty transparent circular hole in the center (70% of the width), transparent background, 1024x1024.`
- **corner** [EXTRA] `A small dandelion with seeds blowing away in a corner, transparent background, 256x256.`
- **empty** [EXTRA] `A kite flying in the breeze with leaves around it, transparent background, 800x800.`

---

## 4. Molduras de perfil extras (para todos os temas)

Hoje existem 12 molduras desenhadas pelo app (Neon, Vinhas, Eletrônica, Arco-íris, Estrelas, Fogo, Gelo, Flores,
Corações, Coroa, Gatinho, Pixel). Estas aqui são **molduras em imagem**, no estilo das decorações do Discord.
Todas: **1024 × 1024, PNG transparente, buraco circular vazio no centro com 70% da largura.**
Pasta: `assets/frames/` — nome do arquivo entre parênteses.

Comece cada prompt com:
`Avatar decoration overlay for a profile picture, arranged as a ring around a perfectly empty transparent circular hole in the center (70% of the width), elements may extend slightly beyond the circle, transparent background, 1024x1024, cute polished 2D style,`

1. **Asas de anjo** (`angel.png`) — `soft white feathered angel wings on both sides and a glowing golden halo floating on top.`
2. **Chifrinhos** (`devil.png`) — `two small cute red devil horns on top and a little pointed tail curling at the bottom right, playful not scary.`
3. **Fones de ouvido** (`headphones.png`) — `big cute pastel headphones going over the top of the circle with small music notes floating.`
4. **Coroa de flores** (`flowercrown.png`) — `lush flower crown of roses, daisies and baby's breath covering the top third.`
5. **Galáxia** (`galaxy.png`) — `ring of swirling galaxy nebula in purple and blue with tiny stars and a small planet with rings.`
6. **Cristais** (`crystals.png`) — `cluster of glowing amethyst and quartz crystals growing from the bottom and sides.`
7. **Sakura** (`sakura.png`) — `cherry blossom branch arching over the top with petals falling around.`
8. **Nuvens** (`clouds.png`) — `fluffy pastel clouds around the bottom half and a tiny rainbow on top.`
9. **Bolhas de sabão** (`bubbles.png`) — `iridescent soap bubbles of different sizes floating around the ring.`
10. **Outono** (`autumn.png`) — `wreath of autumn maple leaves in orange, red and gold with a small acorn.`
11. **Chuva** (`rain.png`) — `a small cute cloud on top with raindrops falling around the sides and a tiny umbrella.`
12. **Raios** (`lightning.png`) — `crackling electric lightning bolts in cyan and white around the ring, glowing.`
13. **Estrelas cadentes** (`shooting.png`) — `two shooting stars circling the ring leaving sparkling trails.`
14. **Patinhas** (`paws.png`) — `trail of cute animal paw prints walking around the ring in pastel colors.`
15. **Doces** (`candy.png`) — `ring made of candies, lollipops and sprinkles, pastel colors.`
16. **Lua e estrelas** (`moon.png`) — `a crescent moon hugging the left side with tiny stars and a sleepy cloud.`
17. **Polvo fofo** (`octopus.png`) — `a cute pastel octopus on top with its tentacles gently wrapping around the ring.`
18. **Chamas azuis** (`bluefire.png`) — `ring of magical blue and white flames, soft glow.`
19. **Pixel coração** (`pixelheart.png`) — `8-bit pixel art ring of tiny pixel hearts and pixel stars, retro game style.`
20. **Arco-íris pastel** (`pastelrainbow.png`) — `soft pastel rainbow arching over the top with two little clouds at its ends.`

---

## 5. Formatos novos de foto (máscaras)

Os formatos de foto de hoje: arredondado, círculo, quadrado, hexágono, octógono, orelhas de gato, orelhas de coelho,
flor, escudo, selo, folha, rabisco e ovo. Para novos formatos, a IA deve fazer **só a silhueta**: forma **preta sólida** em fundo
**transparente**, centralizada, ocupando quase tudo, **sem detalhes internos**, bordas lisas.
**512 × 512, PNG transparente.** Pasta: `assets/shapes/`.

Comece cada prompt com:
`Solid flat black silhouette shape, no inner details, smooth clean edges, centered, filling 90% of the canvas, transparent background, 512x512, vector-like,`

1. **Estrela** (`star.png`) — `a five-pointed star with softly rounded tips.`
2. **Coração** (`heart.png`) — `a plump symmetrical heart.`
3. **Nuvem** (`cloud.png`) — `a fluffy cloud made of round puffs.`
4. **Gota** (`drop.png`) — `a water drop.`
5. **Lua** (`moon.png`) — `a fat crescent moon.`
6. **Patinha** (`paw.png`) — `a big cat paw print with four toe beans merged into one shape.`
7. **Diamante** (`diamond.png`) — `a gem diamond shape with rounded corners.`
8. **Borboleta** (`butterfly.png`) — `a butterfly with wings open, simple.`
9. **Bolha orgânica** (`blob.png`) — `a soft organic blob shape like a pebble.`
10. **Biscoito** (`cookie.png`) — `a scalloped cookie / flower-like round shape with 12 soft bumps.`
11. **Casinha** (`house.png`) — `a simple house shape with a pointed roof.`
12. **Urso** (`bear.png`) — `a circle with two round bear ears on top.`

---

## 6. Ideias de personalização que essas imagens permitem

Com as imagens prontas, dá para acrescentar no app (eu faço quando você mandar as imagens):
- **Papel de parede do tema** como opção ao lado do fundo desenhado e da foto própria (claro e escuro automáticos).
- **Faixa do topo em imagem** no lugar do enfeite desenhado.
- **Enfeite de canto** opcional nos cartões (adesivo).
- **Molduras em imagem** junto com as 12 desenhadas, cada uma combinável com qualquer tema.
- **Formatos de foto novos** a partir das máscaras (estrela, coração, nuvem, patinha…).
- **Tela vazia ilustrada** por tema.
- **Miniatura bonita** de cada tema na tela de escolher tema.

Checklist rápido do que gerar primeiro (mínimo para um tema ficar bem mais bonito):
`wallpaper_dark.png`, `wallpaper_light.png`, `band.png`, `frame.png` — para cada um dos 13 temas (52 imagens).
