import math
from PIL import Image, ImageDraw, ImageFont

# Dimensions
size = (512, 512)
img = Image.new("RGBA", size, (0, 0, 0, 0))
draw = ImageDraw.Draw(img)

# Colors
primary_blue = (30, 64, 175, 255)     # #1E40AF
sky_blue = (14, 165, 233, 255)        # #0EA5E9
light_sky = (56, 189, 248, 255)       # #38BDF8
white = (255, 255, 255, 255)
dark_text = (15, 23, 42, 255)         # #0F172A
slate_bg = (248, 250, 252, 255)       # #F8FAFC

# Draw rounded base shield / squircle
# Outer rounded box
box = [24, 24, 488, 488]
radius = 110

# Create mask for smooth squircle
mask = Image.new("L", size, 0)
mask_draw = ImageDraw.Draw(mask)
mask_draw.rounded_rectangle(box, radius=radius, fill=255)

# Gradient background from Deep Tech Blue #1E40AF to Dark Navy
gradient = Image.new("RGBA", size, (0, 0, 0, 0))
g_draw = ImageDraw.Draw(gradient)
for y in range(512):
    ratio = y / 512.0
    # Blend #1E40AF (30, 64, 175) to (15, 30, 90)
    r = int(30 * (1 - ratio * 0.4))
    g = int(64 * (1 - ratio * 0.4))
    b = int(175 * (1 - ratio * 0.35))
    g_draw.line([(0, y), (512, y)], fill=(r, g, b, 255))

# Paste gradient using mask
img.paste(gradient, (0, 0), mask)

# Draw subtle inner border glow
draw.rounded_rectangle([28, 28, 484, 484], radius=106, outline=(14, 165, 233, 90), width=4)

# Draw QR Scanner Corner Brackets in Sky Blue (#0EA5E9)
corner_len = 54
c_width = 10
c_pad = 96
c_right = 512 - c_pad
c_bottom = 512 - c_pad

# Top-Left Bracket
draw.line([(c_pad, c_pad + corner_len), (c_pad, c_pad)], fill=sky_blue, width=c_width)
draw.line([(c_pad, c_pad), (c_pad + corner_len, c_pad)], fill=sky_blue, width=c_width)

# Top-Right Bracket
draw.line([(c_right - corner_len, c_pad), (c_right, c_pad)], fill=sky_blue, width=c_width)
draw.line([(c_right, c_pad), (c_right, c_pad + corner_len)], fill=sky_blue, width=c_width)

# Bottom-Left Bracket
draw.line([(c_pad, c_bottom - corner_len), (c_pad, c_bottom)], fill=sky_blue, width=c_width)
draw.line([(c_pad, c_bottom), (c_pad + corner_len, c_bottom)], fill=sky_blue, width=c_width)

# Bottom-Right Bracket
draw.line([(c_right - corner_len, c_bottom), (c_right, c_bottom)], fill=sky_blue, width=c_width)
draw.line([(c_right, c_bottom), (c_right, c_bottom - corner_len)], fill=sky_blue, width=c_width)

# Center Icon: Stylized IT Hardware Stack / Server Units with Asset Tag Badge
# Server Rack 1 (Top unit)
draw.rounded_rectangle([140, 150, 372, 204], radius=14, fill=(255, 255, 255, 240), outline=sky_blue, width=3)
# Indicator LED dots on Top Server
draw.ellipse([162, 172, 176, 186], fill=(34, 197, 94, 255)) # Green LED
draw.ellipse([186, 172, 200, 186], fill=sky_blue)           # Sky Blue LED
# Server slots
draw.line([(220, 177), (350, 177)], fill=(203, 213, 225, 255), width=6)

# Server Rack 2 (Middle unit)
draw.rounded_rectangle([140, 220, 372, 274], radius=14, fill=(255, 255, 255, 240), outline=sky_blue, width=3)
draw.ellipse([162, 242, 176, 256], fill=(34, 197, 94, 255)) # Green LED
draw.ellipse([186, 242, 200, 256], fill=(234, 179, 8, 255)) # Amber LED
draw.line([(220, 247), (350, 247)], fill=(203, 213, 225, 255), width=6)

# Modern Asset Tag / Barcode Scanner Beam across the middle
draw.line([(100, 305), (412, 305)], fill=light_sky, width=4)
# Glow above and below laser
laser_glow = Image.new("RGBA", size, (0, 0, 0, 0))
lg_draw = ImageDraw.Draw(laser_glow)
lg_draw.line([(100, 305), (412, 305)], fill=(14, 165, 233, 110), width=12)
img = Image.alpha_composite(img, laser_glow)
draw = ImageDraw.Draw(img)

# Server Rack 3 / Laptop Base (Bottom unit)
draw.rounded_rectangle([140, 335, 372, 389], radius=14, fill=(255, 255, 255, 240), outline=sky_blue, width=3)
draw.ellipse([162, 357, 176, 371], fill=(34, 197, 94, 255)) # Green LED
draw.ellipse([186, 357, 200, 371], fill=sky_blue)           # Sky Blue LED
draw.line([(220, 362), (350, 362)], fill=(203, 213, 225, 255), width=6)

# Overlay Stylized QR / Inventory Tag icon in center-bottom right badge
badge_box = [316, 270, 412, 366]
draw.rounded_rectangle(badge_box, radius=18, fill=(14, 165, 233, 255), outline=white, width=4)
# Mini QR cells inside badge
q_x, q_y = 332, 286
draw.rectangle([q_x, q_y, q_x+18, q_y+18], fill=white)
draw.rectangle([q_x+4, q_y+4, q_x+14, q_y+14], fill=(14, 165, 233, 255))
draw.rectangle([q_x+7, q_y+7, q_x+11, q_y+11], fill=white)

draw.rectangle([q_x+46, q_y, q_x+64, q_y+18], fill=white)
draw.rectangle([q_x+50, q_y+4, q_x+60, q_y+14], fill=(14, 165, 233, 255))
draw.rectangle([q_x+53, q_y+7, q_x+57, q_y+11], fill=white)

draw.rectangle([q_x, q_y+46, q_x+18, q_y+64], fill=white)
draw.rectangle([q_x+4, q_y+50, q_x+14, q_y+60], fill=(14, 165, 233, 255))
draw.rectangle([q_x+7, q_y+53, q_x+11, q_y+57], fill=white)

draw.rectangle([q_x+30, q_y+26, q_x+40, q_y+36], fill=white)
draw.rectangle([q_x+48, q_y+36, q_x+58, q_y+46], fill=white)
draw.rectangle([q_x+24, q_y+48, q_x+34, q_y+58], fill=white)
draw.rectangle([q_x+44, q_y+52, q_x+60, q_y+62], fill=white)

# Save images
img.save("logo.png", "PNG")
print("logo.png generated successfully.")
