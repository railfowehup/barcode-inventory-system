# -*- coding: utf-8 -*-
"""自定义Tkinter组件"""

import tkinter as tk

COLOR_BAR_BG = "#2a2a4a"


class ProgressBar(tk.Canvas):
    def __init__(self, master, width=160, height=8, color="#00d2ff", **kwargs):
        super().__init__(master, width=width, height=height,
                         highlightthickness=0, bg=COLOR_BAR_BG, **kwargs)
        self._width = width
        self._height = height
        self._color = color
        self._percent = 0
        self._radius = height // 2
        self.draw(0)

    def draw(self, percent):
        self.delete("all")
        self._percent = percent
        w = self._width
        h = self._height
        r = self._radius

        # 背景圆角矩形
        self.create_rounded_rect(0, 0, w, h, r, fill=COLOR_BAR_BG, outline="")

        # 前景进度条
        fw = max(w * percent / 100, h) if percent > 0 else 0
        if fw > 0:
            self.create_rounded_rect(0, 0, fw, h, r, fill=self._color, outline="")

    def create_rounded_rect(self, x1, y1, x2, y2, r, **kwargs):
        points = []
        points.extend([x1 + r, y1, x2 - r, y1])
        points.extend([x2, y1, x2, y1 + r, x2, y2 - r])
        points.extend([x2, y2, x1 + r, y2])
        points.extend([x1, y2, x1, y2 - r, x1, y1 + r])
        points.extend([x1, y1, x1 + r, y1])
        self.create_polygon(points, smooth=True, **kwargs)
