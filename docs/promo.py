#!/usr/bin/env python3
"""Deterministic promo art for PortalStash (Pillow, no generative model).

Surfaces follow the module's real styling: black translucent panels, white
text, 18/24dp corners, entry 170x56dp, panel 310dp wide, docked window 28dp.
Canvas 1600x900 keeps 15px body text legible at README width.
"""
import os
from PIL import Image, ImageDraw, ImageFilter, ImageFont

SANS = "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"
BOLD = "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc"
SC = 2
W, H = 1600, 900


def font(size, bold=False):
    return ImageFont.truetype(BOLD if bold else SANS, size, index=SC)


def measure(value, size, bold=False):
    return font(size, bold).getlength(value)


def wrap(value, size, max_px, bold=False):
    f = font(size, bold)
    lines, line = [], ""
    for ch in value:
        trial = line + ch
        if f.getlength(trial) <= max_px or not line:
            line = trial
        else:
            lines.append(line)
            line = ch
    if line:
        lines.append(line)
    return lines


def canvas(tint=(9, 10, 14)):
    im = Image.new("RGB", (W, H), tint).convert("RGBA")
    blooms = [
        (0.12, 0.06, 0.46, (34, 88, 210), 0.30),
        (0.95, 0.98, 0.40, (146, 58, 188), 0.22),
        (0.66, -0.04, 0.26, (16, 148, 148), 0.14),
    ]
    for cx, cy, r, color, alpha in blooms:
        layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        rr = int(W * r)
        ImageDraw.Draw(layer).ellipse(
            [int(W * cx) - rr, int(H * cy) - rr, int(W * cx) + rr, int(H * cy) + rr],
            fill=color + (int(255 * alpha),))
        im = Image.alpha_composite(im, layer.filter(ImageFilter.GaussianBlur(rr // 2)))
    veil = Image.new("L", (W, H), 0)
    ImageDraw.Draw(veil).rectangle([0, int(H * 0.42), W, H], fill=96)
    veil = veil.filter(ImageFilter.GaussianBlur(130))
    return Image.composite(Image.new("RGB", (W, H), (4, 5, 7)).convert("RGBA"),
                           im, veil).convert("RGBA")


def shape(im, box, radius, fill=(255, 255, 255, 18), outline=None, width=1, shadow=0):
    if shadow:
        layer = Image.new("RGBA", im.size, (0, 0, 0, 0))
        ImageDraw.Draw(layer).rounded_rectangle(box, radius, fill=(0, 0, 0, 150))
        im.alpha_composite(layer.filter(ImageFilter.GaussianBlur(shadow)))
    layer = Image.new("RGBA", im.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).rounded_rectangle(box, radius, fill=fill, outline=outline, width=width)
    im.alpha_composite(layer)


def disc(im, box, fill=(255, 255, 255, 24)):
    layer = Image.new("RGBA", im.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).ellipse(box, fill=fill)
    im.alpha_composite(layer)


def save_icon(im, cx, cy, r, color=(255, 255, 255, 240)):
    layer = Image.new("RGBA", im.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    w = max(2, r // 5)
    d.line([(cx, cy - r), (cx, cy + r // 4)], fill=color, width=w)
    d.line([(cx - r // 2, cy), (cx, cy + r // 2)], fill=color, width=w)
    d.line([(cx + r // 2, cy), (cx, cy + r // 2)], fill=color, width=w)
    d.line([(cx - r, cy + r // 3), (cx - r, cy + r)], fill=color, width=w)
    d.line([(cx + r, cy + r // 3), (cx + r, cy + r)], fill=color, width=w)
    d.line([(cx - r, cy + r), (cx + r, cy + r)], fill=color, width=w)
    im.alpha_composite(layer)


def label(im, xy, value, size, color=(255, 255, 255, 235), bold=False, anchor="la"):
    f = font(size, bold)
    layer = Image.new("RGBA", im.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).text(xy, value, font=f, fill=color, anchor=anchor)
    im.alpha_composite(layer)


def entry(im, x, y, s=1.0, text="暂存"):
    w, h = int(170 * s), int(56 * s)
    shape(im, [x, y, x + w, y + h], int(18 * s), (0, 0, 0, 172), (255, 255, 255, 34), shadow=int(10 * s))
    pad = int(8 * s)
    shape(im, [x + pad, y + pad, x + pad + int(40 * s), y + pad + int(40 * s)], int(10 * s), (255, 255, 255, 34))
    save_icon(im, x + pad + int(20 * s), y + pad + int(20 * s), int(10 * s))
    label(im, (x + pad + int(50 * s), y + h / 2), text, int(15 * s), (255, 255, 255, 244), True, "lm")
    return w, h


def tile(im, x, y, w, h, kind, s=1.0, caption="", actions=("分享", "复制", "移除"), lines=None):
    shape(im, [x, y, x + w, y + h], int(18 * s), (0, 0, 0, 118), (255, 255, 255, 28))
    mx0, my0 = int(x + 8 * s), int(y + 8 * s)
    mx1, my1 = int(x + w - 8 * s), int(y + 8 * s + 78 * s)
    if kind == "image":
        gw, gh = mx1 - mx0, my1 - my0
        grad = Image.new("RGBA", (gw, gh))
        gd = ImageDraw.Draw(grad)
        for i in range(gh):
            t = i / max(1, gh - 1)
            gd.line([(0, i), (gw, i)], fill=(int(56 + 62 * t), int(98 + 44 * t), int(152 + 58 * t), 255))
        mask = Image.new("L", (gw, gh), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, gw - 1, gh - 1], int(10 * s), fill=255)
        im.paste(grad, (mx0, my0), mask)
    else:
        shape(im, [mx0, my0, mx1, my1], int(10 * s), (255, 255, 255, 246))
        for i, line in enumerate(lines or []):
            label(im, (mx0 + int(9 * s), my0 + int(12 * s) + i * int(20 * s)), line, int(11 * s), (28, 29, 34, 255))
    cy = my1 + int(8 * s)
    if caption:
        label(im, (x + int(11 * s), cy), caption, int(10 * s), (255, 255, 255, 228), True)
        cy += int(18 * s)
    for i, act in enumerate(actions):
        label(im, (x + int(11 * s) + i * int(44 * s), cy), act, int(10 * s), (255, 255, 255, 172))


def stash(im, x, y, s=1.0, count=2):
    w = int(310 * s)
    h = int(226 * s)
    shape(im, [x, y, x + w, y + h], int(24 * s), (0, 0, 0, 172), (255, 255, 255, 32), shadow=int(14 * s))
    label(im, (x + int(18 * s), y + int(16 * s)), "暂存栏  %d" % count, int(15 * s), (255, 255, 255, 246), True)
    bw = int(50 * s)
    bx1 = x + w - int(14 * s)
    shape(im, [bx1 - bw, y + int(11 * s), bx1, y + int(41 * s)], int(11 * s), (255, 255, 255, 26))
    label(im, (bx1 - bw / 2, y + int(26 * s)), "收起", int(11 * s), (255, 255, 255, 234), anchor="mm")
    label(im, (x + int(18 * s), y + int(48 * s)), "长按拖出 · 上滑移除", int(10 * s), (255, 255, 255, 158))
    ty = y + int(68 * s)
    tile(im, x + int(12 * s), ty, int(136 * s), int(146 * s), "text", s, "网页里的一段",
         lines=["选中文字后", "收进暂存栏", "稍后再拖出去"])
    tile(im, x + int(156 * s), ty, int(136 * s), int(146 * s), "image", s, "识别到的图片", ("分享", "移除"))
    return w, h


def dock_icon(im, cx, cy, s=1.0):
    size = int(56 * s)
    shape(im, [cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2], int(18 * s),
          (0, 0, 0, 196), (255, 255, 255, 40), shadow=int(8 * s))
    save_icon(im, cx + size * 0.18, cy, int(10 * s))


def chip(im, x, y, value, s=1.0):
    fs = int(13 * s)
    w = measure(value, fs) + int(24 * s)
    h = int(30 * s)
    shape(im, [x, y, x + w, y + h], h // 2, (255, 255, 255, 22), (255, 255, 255, 44))
    label(im, (x + w / 2, y + h / 2), value, fs, (255, 255, 255, 216), anchor="mm")
    return w


def phone(im, x, y, w, h):
    shape(im, [x, y, x + w, y + h], 46, (17, 18, 22, 255), (255, 255, 255, 44), 2, shadow=22)
    inner = [x + 11, y + 11, x + w - 11, y + h - 11]
    shape(im, inner, 36, (30, 32, 38, 255))
    label(im, (x + 30, y + 28), "22:30", 12, (255, 255, 255, 200), True)
    label(im, (x + w - 30, y + 28), "5G   78%", 11, (255, 255, 255, 158), anchor="ra")
    return inner


def chrome(im, name, sub=None):
    label(im, (56, 40), name, 38, (255, 255, 255, 252), True)
    if sub:
        label(im, (58, 90), sub, 17, (255, 255, 255, 168))


def footer(im):
    label(im, (56, H - 40), "PortalStash · 0.4.3", 15, (255, 255, 255, 120))
    label(im, (W - 56, H - 40), "LSPosed · 仅支持传送门 5.1.5", 15, (255, 255, 255, 120), anchor="ra")


def hero(path):
    im = canvas()
    bx, by = 56, 40
    pw = measure("LSPosed 模块 · 澎湃 OS", 16) + 30
    shape(im, [bx, by, bx + pw, by + 34], 17, (255, 255, 255, 20), (255, 255, 255, 48))
    label(im, (bx + pw / 2, by + 17), "LSPosed 模块 · 澎湃 OS", 16, (255, 255, 255, 226), anchor="mm")

    label(im, (54, 96), "传送门暂存", 86, (255, 255, 255, 255), True)
    label(im, (56, 206), "长按识别，随手存下", 33, (255, 255, 255, 238))
    label(im, (56, 250), "不用切应用，不用再截图找", 33, (255, 255, 255, 238))
    body = ("为澎湃 OS 传送门增量添加暂存栏：识别到的文字与图片留在原界面，"
            "随时长按拖进目标应用。原版 APK 不修改、不重签。")
    yy = 322
    for line in wrap(body, 20, 700):
        label(im, (56, yy), line, 20, (255, 255, 255, 158))
        yy += 32
    cx = 56
    for value in ["原生卡片样式", "完整圆角", "边缘平移", "半图标藏边", "本地不联网"]:
        cx += chip(im, cx, 470, value) + 10

    label(im, (56, 566), "0.4.3 这一版", 15, (255, 255, 255, 130), True)
    notes = ["背景改成纯黑半透明，只降不透明度，不改成灰色",
             "拖动标题可移动整栏，靠边松手只留半个图标",
             "原生材质能开能关，失败自动退回普通背景"]
    ny = 598
    for note in notes:
        disc(im, [57, ny + 7, 63, ny + 13], (255, 255, 255, 110))
        label(im, (78, ny), note, 16, (255, 255, 255, 176))
        ny += 30

    inner = phone(im, 980, 96, 470, 664)
    shape(im, [inner[0] + 16, inner[1] + 26, inner[2] - 16, inner[1] + 186], 22, (255, 255, 255, 16))
    label(im, (inner[0] + 36, inner[1] + 44), "识别结果", 18, (255, 255, 255, 210), True)
    for i, line in enumerate(["这是一段被传送门识别出的文字，", "可以整段收进暂存栏，再拖到", "聊天框或笔记里。"]):
        label(im, (inner[0] + 36, inner[1] + 78 + i * 30), line, 16, (255, 255, 255, 158))

    entry(im, inner[0] + 22, inner[1] + 210, 1.22)
    dock_icon(im, inner[2] - 30, inner[1] + 245, 1.15)
    stash(im, inner[0] + 22, inner[1] + 300, 1.3)

    label(im, (980 + 470 / 2, 792), "界面示意图 · 按模块真实样式绘制，非实机截图", 15,
          (255, 255, 255, 118), anchor="ma")
    footer(im)
    im.convert("RGB").save(path)


def flow(path):
    im = canvas()
    chrome(im, "用起来只有四步", "全程停在传送门里，不打断当前操作")
    steps = [
        ("长按识别", "双指按压取词，传送门照常弹出"),
        ("点一下暂存", "入口加在识别卡片下方，原功能不受影响"),
        ("打开暂存栏", "文字与图片横向排列，可左右翻"),
        ("拖出去或上滑删除", "长按拖进聊天框、笔记；上滑丢掉"),
    ]
    cw, ch, top = 350, 486, 168
    gap = (W - 112 - cw * 4) / 3
    for i, (title, desc) in enumerate(steps):
        x = 56 + i * (cw + gap)
        shape(im, [x, top, x + cw, top + ch], 26, (255, 255, 255, 13), (255, 255, 255, 32), shadow=14)
        disc(im, [x + 24, top + 24, x + 62, top + 62], (255, 255, 255, 26))
        label(im, (x + 43, top + 43), str(i + 1), 19, (255, 255, 255, 240), True, "mm")
        label(im, (x + 24, top + 82), title, 21, (255, 255, 255, 248), True)
        yy = top + 116
        for line in wrap(desc, 14, cw - 48):
            label(im, (x + 24, yy), line, 14, (255, 255, 255, 152))
            yy += 24

        my = top + 210
        if i == 0:
            shape(im, [x + 22, my, x + cw - 22, my + 150], 18, (255, 255, 255, 14))
            for j, line in enumerate(["识别到的文字……", "覆盖在内容上方", "松手后出现入口"]):
                label(im, (x + 40, my + 24 + j * 34), line, 15, (255, 255, 255, 158))
        elif i == 1:
            entry(im, x + 22, my + 18, 0.98)
            label(im, (x + 22, my + 100), "↑ 新增的入口", 14, (255, 255, 255, 128))
        elif i == 2:
            stash(im, x + 24, my - 6, 0.97, 2)
        else:
            shape(im, [x + 22, my, x + 190, my + 96], 16, (0, 0, 0, 130), (255, 255, 255, 40))
            label(im, (x + 106, my + 48), "拖到聊天框", 15, (255, 255, 255, 214), anchor="mm")
            layer = Image.new("RGBA", im.size, (0, 0, 0, 0))
            ImageDraw.Draw(layer).line([(x + 198, my + 48), (x + 268, my + 48)], fill=(255, 255, 255, 176), width=3)
            ImageDraw.Draw(layer).polygon([(x + 268, my + 48), (x + 256, my + 40), (x + 256, my + 56)],
                                          fill=(255, 255, 255, 176))
            im.alpha_composite(layer)
            shape(im, [x + 22, my + 112, x + 280, my + 148], 12, (255, 255, 255, 18))
            label(im, (x + 151, my + 130), "↑ 上滑移除", 14, (255, 255, 255, 166), anchor="mm")

    shape(im, [56, 700, 1544, 782], 18, (255, 255, 255, 12), (255, 255, 255, 26))
    label(im, (86, 716), "不打断当前操作", 17, (255, 255, 255, 226), True)
    label(im, (86, 744), "暂存栏是独立浮层：拖动标题可移动整栏，靠边松手只留半个图标；点它或向里拖即可展开。",
          15, (255, 255, 255, 158))
    footer(im)
    im.convert("RGB").save(path)


def features(path):
    im = canvas()
    chrome(im, "为什么它看起来就是原生的", "复用传送门自己的组件与动效曲线，而不是另绘一套界面")
    items = [
        ("原生卡片", "直接实例化原 APK 的 TaplusRecognitionShrinkCard，字体、图标、背景都来自原应用"),
        ("完整圆角", "外框、内容卡片、图片三层各自裁切，可选 18 / 24 / 30dp"),
        ("边缘平移", "入场 350ms、收起 200ms，曲线取自原版 FloatViewAnimUtil，不回弹"),
        ("半图标藏边", "拖到屏幕边缘只留半个图标，点一下或向里拖出来"),
        ("只存本地", "不联网、不读通知与聊天记录、不自动发送，内容在模块私有目录"),
        ("可选柔光", "原生材质接口可用才启用，失败自动退回普通背景"),
    ]
    cw, ch = 700, 132
    for i, (title, desc) in enumerate(items):
        col, row = i % 2, i // 2
        x = 56 + col * (cw + 40)
        y = 168 + row * (ch + 26)
        shape(im, [x, y, x + cw, y + ch], 24, (255, 255, 255, 13), (255, 255, 255, 30), shadow=12)
        disc(im, [x + 26, y + 24, x + 60, y + 58], (255, 255, 255, 24))
        layer = Image.new("RGBA", im.size, (0, 0, 0, 0))
        ImageDraw.Draw(layer).rounded_rectangle([x + 34, y + 32, x + 52, y + 50], 5,
                                                fill=(255, 255, 255, 225))
        im.alpha_composite(layer)
        label(im, (x + 74, y + 26), title, 21, (255, 255, 255, 248), True)
        yy = y + 66
        for line in wrap(desc, 15, cw - 100):
            label(im, (x + 74, yy), line, 15, (255, 255, 255, 152))
            yy += 26
    label(im, (56, 690), "不做第二套界面，只把暂存塞进原界面。", 34, (255, 255, 255, 240), True)
    label(im, (58, 740), "设置页仍用原生 Views；传送门内的一切尺寸都取自原 APK 的资源表。", 17,
          (255, 255, 255, 150))
    footer(im)
    im.convert("RGB").save(path)


def install(path):
    im = canvas()
    chrome(im, "装上就能用", "安装、勾选作用域、重新加载，三步完成")
    steps = [
        ("安装模块", "从 Releases 下载 APK。与旧版同一签名，可直接覆盖升级。"),
        ("勾选作用域", "基础功能只勾选“传送门”。系统组件材质为可选功能，需额外作用域。"),
        ("重新加载", "让传送门进程重新读取模块，之后长按取词即可看到“暂存”。"),
    ]
    y = 176
    for i, (title, desc) in enumerate(steps):
        disc(im, [56, y, 100, y + 44], (255, 255, 255, 24))
        label(im, (78, y + 22), str(i + 1), 18, (255, 255, 255, 238), True, "mm")
        label(im, (116, y + 6), title, 21, (255, 255, 255, 246), True)
        yy = y + 40
        for line in wrap(desc, 14, 620):
            label(im, (116, yy), line, 14, (255, 255, 255, 152))
            yy += 24
        y = yy + 26

    shape(im, [840, 168, 1544, 648], 26, (255, 255, 255, 12), (255, 255, 255, 30), shadow=14)
    label(im, (876, 200), "兼容性", 22, (255, 255, 255, 246), True)
    rows = [
        ("传送门", "com.miui.contentextension 5.1.5（50015）"),
        ("系统", "澎湃 OS 4 · Android 14 及以上"),
        ("框架", "LSPosed，支持传统 Xposed API"),
        ("系统组件材质", "miui.systemui.plugin 18.3.2.22.0（可选）"),
    ]
    ry = 254
    for name, value in rows:
        label(im, (876, ry), name, 14, (255, 255, 255, 150))
        yy = ry + 24
        for line in wrap(value, 15, 600):
            label(im, (876, yy), line, 15, (255, 255, 255, 220))
            yy += 24
        ry = yy + 26
    shape(im, [876, 560, 1508, 616], 14, (255, 255, 255, 16))
    label(im, (892, 574), "其他版本不会装载 Hook，避免误改未知界面", 14, (255, 255, 255, 170))
    shape(im, [56, 470, 760, 700], 24, (255, 255, 255, 12), (255, 255, 255, 30), shadow=12)
    label(im, (88, 496), "作用域怎么勾", 19, (255, 255, 255, 246), True)
    disc(im, [88, 540, 112, 564], (255, 255, 255, 190))
    layer = Image.new("RGBA", im.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).line([(94, 552), (100, 558), (107, 546)], fill=(24, 26, 32, 255), width=3)
    im.alpha_composite(layer)
    label(im, (126, 534), "传送门", 15, (255, 255, 255, 232), True)
    label(im, (126, 554), "com.miui.contentextension　必选", 13, (255, 255, 255, 150))
    disc(im, [88, 600, 112, 624], (255, 255, 255, 18))
    label(im, (112, 612), "○", 12, (255, 255, 255, 120), anchor="mm")
    label(im, (126, 594), "系统界面 / 系统界面组件", 15, (255, 255, 255, 210), True)
    label(im, (126, 614), "仅在启用系统组件材质时需要", 13, (255, 255, 255, 150))
    footer(im)
    im.convert("RGB").save(path)


if __name__ == "__main__":
    out = "/root/portal-hook-work/design/promo"
    os.makedirs(out, exist_ok=True)
    hero(os.path.join(out, "promo-1-hero.png"))
    flow(os.path.join(out, "promo-2-flow.png"))
    features(os.path.join(out, "promo-3-features.png"))
    install(os.path.join(out, "promo-4-install.png"))
    for name in sorted(os.listdir(out)):
        print(name, os.path.getsize(os.path.join(out, name)))
