from pathlib import Path
from PIL import Image, ImageDraw

root = Path(__file__).resolve().parent.parent
image = Image.open(root / 'assets/netmax-logo.png').convert('RGBA')
resources = root / 'composeApp/src/androidMain/res'
for density, scale in [('mdpi', 1), ('hdpi', 1.5), ('xhdpi', 2), ('xxhdpi', 3), ('xxxhdpi', 4)]:
    target = resources / f'mipmap-{density}'
    target.mkdir(parents=True, exist_ok=True)
    for name in ['ic_launcher', 'ic_launcher_round']:
        image.resize((int(48 * scale),) * 2, Image.Resampling.LANCZOS).save(target / f'{name}.webp', lossless=True)
    size, artwork = int(108 * scale), int(72 * scale)
    foreground = Image.new('RGBA', (size, size))
    foreground.alpha_composite(image.resize((artwork, artwork), Image.Resampling.LANCZOS), ((size - artwork) // 2,) * 2)
    foreground.save(target / 'ic_launcher_foreground.webp', lossless=True)
    monochrome = Image.new('RGBA', (size, size))
    draw = ImageDraw.Draw(monochrome)
    def points(vertices):
        return [(round(x * scale), round(y * scale)) for x, y in vertices]
    for vertices in [[(35,30),(47,30),(73,75),(61,75)], [(35,30),(45,48),(45,77),(35,73)], [(61,30),(73,34),(73,75),(61,55)]]:
        draw.polygon(points(vertices), fill='white')
    draw.polygon(points([(64,35),(64,44),(70,40)]), fill=(0,0,0,0))
    monochrome.save(target / 'ic_launcher_monochrome.webp', lossless=True)
image.convert('RGB').save(root / 'composeApp/src/commonMain/composeResources/drawable/app_icon_original.png')
print('NETMAX icons generated at all five Android densities, including adaptive and themed icons.')
