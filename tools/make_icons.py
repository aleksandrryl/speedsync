"""Иконки лаунчера для SpeedSync Overlay (без Android SDK)."""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "main" / "res"

SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

FONTS = [
    r"C:\Windows\Fonts\arialbd.ttf",
    r"C:\Windows\Fonts\segoeuib.ttf",
]

ACCENT = (255, 181, 69)
MUTED = (139, 160, 178)
TEXT = (234, 242, 248)


def font(size: int) -> ImageFont.FreeTypeFont:
    for path in FONTS:
        if Path(path).exists():
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def render(size: int = 512) -> Image.Image:
    img = Image.new("RGB", (size, size), (10, 16, 23))
    draw = ImageDraw.Draw(img, "RGBA")

    # фон: мягкий градиент
    gradient = Image.new("RGB", (1, size))
    px = gradient.load()
    for y in range(size):
        t = y / (size - 1)
        px[0, y] = (round(18 + (7 - 18) * t), round(28 + (11 - 28) * t), round(38 + (15 - 38) * t))
    img = gradient.resize((size, size))
    draw = ImageDraw.Draw(img, "RGBA")

    box = (size * 0.18, size * 0.18, size * 0.82, size * 0.82)
    draw.arc(box, start=0, end=360, fill=(34, 48, 61), width=int(size * 0.045))
    draw.arc(box, start=135, end=405, fill=ACCENT, width=int(size * 0.045))

    number = "90"
    f = font(int(size * 0.29))
    bbox = draw.textbbox((0, 0), number, font=f)
    draw.text(
        ((size - (bbox[2] - bbox[0])) / 2 - bbox[0], size * 0.30 - bbox[1]),
        number,
        font=f,
        fill=TEXT,
    )

    unit = "км/ч"
    uf = font(int(size * 0.075))
    ubox = draw.textbbox((0, 0), unit, font=uf)
    draw.text(
        ((size - (ubox[2] - ubox[0])) / 2 - ubox[0], size * 0.63 - ubox[1]),
        unit,
        font=uf,
        fill=MUTED,
    )

    return img


def main() -> None:
    master = render(512)
    for folder, px in SIZES.items():
        target = RES / folder / "ic_launcher.png"
        target.parent.mkdir(parents=True, exist_ok=True)
        master.resize((px, px), Image.LANCZOS).save(target)
        print("wrote", target)


if __name__ == "__main__":
    main()
