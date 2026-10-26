import os
from PIL import Image

src_logo = "logo.png"
if not os.path.exists(src_logo):
    raise FileNotFoundError("logo.png not found")

base_img = Image.open(src_logo).convert("RGBA")

# Ensure directories
os.makedirs("web/assets", exist_ok=True)
os.makedirs("app/src/main/res/drawable", exist_ok=True)

# Save web logo and app drawable
base_img.save("web/assets/logo.png", "PNG")
base_img.save("app/src/main/res/drawable/logo.png", "PNG")

# Generate favicon.ico for web
favicon_sizes = [(16, 16), (32, 32), (48, 48), (64, 64)]
base_img.save("web/favicon.ico", format="ICO", sizes=favicon_sizes)
base_img.save("web/assets/favicon.ico", format="ICO", sizes=favicon_sizes)

# Android mipmap densities
densities = {
    "app/src/main/res/mipmap-mdpi": (48, 48),
    "app/src/main/res/mipmap-hdpi": (72, 72),
    "app/src/main/res/mipmap-xhdpi": (96, 96),
    "app/src/main/res/mipmap-xxhdpi": (144, 144),
    "app/src/main/res/mipmap-xxxhdpi": (192, 192),
}

for folder, (w, h) in densities.items():
    os.makedirs(folder, exist_ok=True)
    resized = base_img.resize((w, h), Image.Resampling.LANCZOS)
    resized.save(os.path.join(folder, "ic_launcher.png"), "PNG")
    resized.save(os.path.join(folder, "ic_launcher_round.png"), "PNG")

print("All unified logo assets and mipmaps deployed successfully.")
