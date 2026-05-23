# -*- coding: utf-8 -*-
"""
SysMonitor - 桌面悬浮系统监控面板
显示 CPU、内存、磁盘、网络使用情况
"""

import sys
import time
import os
import ctypes

try:
    import winreg
except ImportError:
    winreg = None

import psutil
import tkinter as tk

# ============================================================
# 常量配置
# ============================================================
WINDOW_WIDTH = 300
WINDOW_HEIGHT = 200
REFRESH_INTERVAL = 1500  # 毫秒

COLOR_BG = "#1a1a2e"
COLOR_FG = "#e0e0e0"
COLOR_ACCENT_CPU = "#00d2ff"
COLOR_ACCENT_MEM = "#a855f7"
COLOR_ACCENT_DSK = "#f97316"
COLOR_ACCENT_NET = "#22c55e"
COLOR_BAR_BG = "#2a2a4a"
COLOR_BORDER = "#3a3a5a"
COLOR_BTN_HOVER = "#3a3a5a"

FONT_FAMILY = "Microsoft YaHei UI"
AUTOSTART_NAME = "SysMonitor"
FONT_SIZE = 10
FONT_SIZE_SMALL = 9

ALPHA_NORMAL = 0.75
ALPHA_HOVER = 0.90

# ============================================================
# 系统监控数据获取
# ============================================================
class SystemMonitor:
    def __init__(self):
        self._net_prev = psutil.net_io_counters()
        self._net_time = time.time()

    def get_cpu(self):
        return psutil.cpu_percent(interval=None)

    def get_memory(self):
        mem = psutil.virtual_memory()
        return mem.percent, mem.used, mem.total

    def get_disk(self):
        disk = psutil.disk_usage('/')
        return disk.percent, disk.used, disk.total

    def get_network(self):
        now = time.time()
        net = psutil.net_io_counters()
        elapsed = now - self._net_time
        if elapsed > 0:
            up_speed = (net.bytes_sent - self._net_prev.bytes_sent) / elapsed
            down_speed = (net.bytes_recv - self._net_prev.bytes_recv) / elapsed
        else:
            up_speed = 0
            down_speed = 0
        self._net_prev = net
        self._net_time = now
        return up_speed, down_speed


def format_speed(bps):
    """格式化速度"""
    if bps < 1024:
        return f"{bps:.0f}B/s"
    elif bps < 1024 * 1024:
        return f"{bps / 1024:.1f}K/s"
    else:
        return f"{bps / 1024 / 1024:.1f}M/s"


def set_autostart(enabled=True):
    """在 Windows 注册表中设置静默自启动"""
    if os.name != "nt" or winreg is None:
        return False

    run_key = r"Software\Microsoft\Windows\CurrentVersion\Run"
    app_path = os.path.abspath(__file__)
    exe_cmd = f'"{sys.executable}" "{app_path}"'

    try:
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, run_key, 0, winreg.KEY_SET_VALUE) as key:
            if enabled:
                winreg.SetValueEx(key, AUTOSTART_NAME, 0, winreg.REG_SZ, exe_cmd)
            else:
                try:
                    winreg.DeleteValue(key, AUTOSTART_NAME)
                except FileNotFoundError:
                    pass
        return True
    except OSError:
        return False


def is_autostart_enabled():
    if os.name != "nt" or winreg is None:
        return False
    run_key = r"Software\Microsoft\Windows\CurrentVersion\Run"
    try:
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, run_key, 0, winreg.KEY_READ) as key:
            value, _ = winreg.QueryValueEx(key, AUTOSTART_NAME)
            return bool(value)
    except OSError:
        return False


# ============================================================
# 自定义进度条组件
# ============================================================
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
        # 左上
        points.extend([x1 + r, y1, x2 - r, y1])
        # 右上弧
        points.extend([x2, y1, x2, y1 + r, x2, y2 - r])
        # 右下
        points.extend([x2, y2, x1 + r, y2])
        # 左下弧
        points.extend([x1, y2, x1, y2 - r, x1, y1 + r])
        # 闭合
        points.extend([x1, y1, x1 + r, y1])

        self.create_polygon(points, smooth=True, **kwargs)


# ============================================================
# 主悬浮窗口
# ============================================================
class SysMonitorApp:
    def __init__(self):
        self.root = tk.Tk()
        self.root.title("SysMonitor")
        self.monitor = SystemMonitor()

        # 窗口设置
        self.root.overrideredirect(True)  # 无边框
        self.root.attributes("-topmost", True)
        self.root.attributes("-alpha", ALPHA_NORMAL)
        self.root.configure(bg=COLOR_BG)

        # 置顶状态
        self._topmost = True
        self._collapsed = False
        self._normal_height = WINDOW_HEIGHT

        # 鼠标穿透状态
        self._click_through = False

        # 开机自启状态
        self._autostart_enabled = is_autostart_enabled()

        # 窗口位置（右下角）
        screen_w = self.root.winfo_screenwidth()
        screen_h = self.root.winfo_screenheight()
        self._x = screen_w - WINDOW_WIDTH - 20
        self._y = screen_h - WINDOW_HEIGHT - 60
        self.root.geometry(f"{WINDOW_WIDTH}x{WINDOW_HEIGHT}+{self._x}+{self._y}")

        # 设置窗口为工具窗口（不在任务栏显示）
        self.root.wm_attributes("-toolwindow", True)

        # 设置圆角窗口（Windows）
        self._set_round_corners()

        # 构建UI
        self._build_ui()

        # 绑定事件
        self._bind_events()

        # 启动刷新
        self._update_data()

        # 鼠标穿透检测线程
        self._check_click_through()

    def _set_round_corners(self):
        """Windows 10/11 圆角窗口"""
        try:
            hwnd = ctypes.windll.user32.GetParent(self.root.winfo_id())
            # DWMWA_WINDOW_CORNER_PREFERENCE = 33
            # DWMWCP_ROUND = 2
            ctypes.windll.dwmapi.DwmSetWindowAttribute(
                hwnd, 33,
                ctypes.byref(ctypes.c_int(2)),
                ctypes.sizeof(ctypes.c_int)
            )
        except Exception:
            pass

    def _build_ui(self):
        # 主框架
        self.main_frame = tk.Frame(self.root, bg=COLOR_BG)
        self.main_frame.pack(fill=tk.BOTH, expand=True)

        # 标题栏
        self._build_title_bar()

        # 内容区域
        self.content = tk.Frame(self.main_frame, bg=COLOR_BG)
        self.content.pack(fill=tk.BOTH, expand=True, padx=12, pady=(0, 8))

        # CPU
        self._build_row(self.content, 0, "CPU", COLOR_ACCENT_CPU)
        # 内存
        self._build_row(self.content, 1, "MEM", COLOR_ACCENT_MEM)
        # 磁盘
        self._build_row(self.content, 2, "DSK", COLOR_ACCENT_DSK)
        # 网络
        self._build_net_row()

    def _build_title_bar(self):
        title_bar = tk.Frame(self.main_frame, bg=COLOR_BG, height=28)
        title_bar.pack(fill=tk.X, padx=8, pady=(4, 0))
        title_bar.pack_propagate(False)
        self.title_bar = title_bar

        # 标题
        self.title_label = tk.Label(
            title_bar, text="SysMonitor", bg=COLOR_BG,
            fg="#8888aa", font=(FONT_FAMILY, FONT_SIZE_SMALL)
        )
        self.title_label.pack(side=tk.LEFT)

        # 按钮容器
        btn_frame = tk.Frame(title_bar, bg=COLOR_BG)
        btn_frame.pack(side=tk.RIGHT)

        # 置顶按钮
        self.btn_pin = tk.Label(
            btn_frame, text="[置顶]", bg=COLOR_BG, fg=COLOR_ACCENT_CPU,
            font=(FONT_FAMILY, FONT_SIZE_SMALL), cursor="hand2"
        )
        self.btn_pin.pack(side=tk.LEFT, padx=2)
        self.btn_pin.bind("<Button-1>", self._toggle_pin)
        self.btn_pin.bind("<Enter>", lambda e: self.btn_pin.configure(bg=COLOR_BTN_HOVER))
        self.btn_pin.bind("<Leave>", lambda e: self.btn_pin.configure(bg=COLOR_BG))

        # 折叠按钮
        self.btn_collapse = tk.Label(
            btn_frame, text="[-]", bg=COLOR_BG, fg="#8888aa",
            font=(FONT_FAMILY, FONT_SIZE_SMALL), cursor="hand2"
        )
        self.btn_collapse.pack(side=tk.LEFT, padx=2)
        self.btn_collapse.bind("<Button-1>", self._toggle_collapse)
        self.btn_collapse.bind("<Enter>", lambda e: self.btn_collapse.configure(bg=COLOR_BTN_HOVER))
        self.btn_collapse.bind("<Leave>", lambda e: self.btn_collapse.configure(bg=COLOR_BG))

        # 关闭按钮
        self.btn_close = tk.Label(
            btn_frame, text="X", bg=COLOR_BG, fg="#ff5555",
            font=(FONT_FAMILY, FONT_SIZE_SMALL), cursor="hand2"
        )
        self.btn_close.pack(side=tk.LEFT, padx=2)
        self.btn_close.bind("<Button-1>", lambda e: self._quit())
        self.btn_close.bind("<Enter>", lambda e: self.btn_close.configure(bg=COLOR_BTN_HOVER))
        self.btn_close.bind("<Leave>", lambda e: self.btn_close.configure(bg=COLOR_BG))

    def _build_row(self, parent, row, label, color):
        frame = tk.Frame(parent, bg=COLOR_BG)
        frame.pack(fill=tk.X, pady=2)

        # 标签
        lbl = tk.Label(
            frame, text=label, bg=COLOR_BG, fg=color,
            font=(FONT_FAMILY, FONT_SIZE, "bold"), width=4, anchor=tk.W
        )
        lbl.pack(side=tk.LEFT)

        # 进度条
        bar = ProgressBar(frame, width=160, height=8, color=color)
        bar.pack(side=tk.LEFT, padx=(6, 6))

        # 百分比文字
        pct = tk.Label(
            frame, text="0%", bg=COLOR_BG, fg=COLOR_FG,
            font=(FONT_FAMILY, FONT_SIZE), width=6, anchor=tk.E
        )
        pct.pack(side=tk.RIGHT)

        # 存储引用
        setattr(self, f"_bar_{row}", bar)
        setattr(self, f"_pct_{row}", pct)

    def _build_net_row(self):
        frame = tk.Frame(self.content, bg=COLOR_BG)
        frame.pack(fill=tk.X, pady=2)

        lbl = tk.Label(
            frame, text="NET", bg=COLOR_BG, fg=COLOR_ACCENT_NET,
            font=(FONT_FAMILY, FONT_SIZE, "bold"), width=4, anchor=tk.W
        )
        lbl.pack(side=tk.LEFT)

        self._net_label = tk.Label(
            frame, text="UP 0  DOWN 0", bg=COLOR_BG, fg=COLOR_FG,
            font=(FONT_FAMILY, FONT_SIZE_SMALL), anchor=tk.W
        )
        self._net_label.pack(side=tk.LEFT, padx=(6, 0), fill=tk.X, expand=True)

    def _bind_events(self):
        # 窗口拖动
        self.main_frame.bind("<ButtonPress-1>", self._start_move)
        self.main_frame.bind("<B1-Motion>", self._on_move)
        self.title_bar.bind("<ButtonPress-1>", self._start_move)
        self.title_bar.bind("<B1-Motion>", self._on_move)
        self.title_label.bind("<ButtonPress-1>", self._start_move)
        self.title_label.bind("<B1-Motion>", self._on_move)

        # 悬停透明度变化
        self.root.bind("<Enter>", lambda e: self.root.attributes("-alpha", ALPHA_HOVER))
        self.root.bind("<Leave>", lambda e: self.root.attributes("-alpha", ALPHA_NORMAL))

        # 右键菜单
        self.root.bind("<Button-3>", self._show_context_menu)

    def _start_move(self, event):
        self._drag_x = event.x_root - self._x
        self._drag_y = event.y_root - self._y

    def _on_move(self, event):
        self._x = event.x_root - self._drag_x
        self._y = event.y_root - self._drag_y
        self.root.geometry(f"+{self._x}+{self._y}")

    def _update_pin_label(self):
        self.btn_pin.configure(
            text="[置顶]" if self._topmost else "[取消置顶]",
            fg=COLOR_ACCENT_CPU if self._topmost else "#666688"
        )

    def _toggle_pin(self, event=None):
        self._topmost = not self._topmost
        self.root.attributes("-topmost", self._topmost)
        self._update_pin_label()

    def _toggle_collapse(self, event=None):
        self._collapsed = not self._collapsed
        if self._collapsed:
            self._normal_height = self.root.winfo_height()
            self.content.pack_forget()
            self.root.geometry(f"{WINDOW_WIDTH}x{32}")
            self.btn_collapse.configure(text="[+]")
        else:
            self.content.pack(fill=tk.BOTH, expand=True, padx=12, pady=(0, 8))
            self.root.geometry(f"{WINDOW_WIDTH}x{self._normal_height}")
            self.btn_collapse.configure(text="[-]")

    def _show_context_menu(self, event):
        menu = tk.Menu(self.root, tearoff=0, bg="#2a2a4a", fg=COLOR_FG,
                       activebackground="#3a3a5a", activeforeground=COLOR_FG,
                       font=(FONT_FAMILY, FONT_SIZE_SMALL))
        menu.add_command(
            label="[穿透] 鼠标穿透" if not self._click_through else "[正常] 取消穿透",
            command=self._toggle_click_through
        )
        menu.add_command(
            label="[自启] 开机自启" if not self._autostart_enabled else "[取消] 取消自启",
            command=self._toggle_autostart
        )
        menu.add_separator()
        menu.add_command(label="[X] 退出", command=self._quit)
        try:
            menu.tk_popup(event.x_root, event.y_root)
        finally:
            menu.grab_release()

    def _toggle_click_through(self):
        self._click_through = not self._click_through
        if self._click_through:
            # 设置窗口可穿透
            hwnd = ctypes.windll.user32.GetParent(self.root.winfo_id())
            # WS_EX_TRANSPARENT | WS_EX_LAYERED
            style = ctypes.windll.user32.GetWindowLongW(hwnd, -20)
            ctypes.windll.user32.SetWindowLongW(hwnd, -20, style | 0x80020)
            self.root.attributes("-alpha", 0.35)
        else:
            hwnd = ctypes.windll.user32.GetParent(self.root.winfo_id())
            style = ctypes.windll.user32.GetWindowLongW(hwnd, -20)
            ctypes.windll.user32.SetWindowLongW(hwnd, -20, style & ~0x80020)
            self.root.attributes("-alpha", ALPHA_NORMAL)

    def _toggle_autostart(self):
        self._autostart_enabled = not self._autostart_enabled
        if set_autostart(self._autostart_enabled):
            status = "已启用" if self._autostart_enabled else "已取消"
            print(f"{status}开机自启。")
        else:
            self._autostart_enabled = not self._autostart_enabled
            print("设置开机自启失败。请检查当前系统是否为 Windows。")

    def _check_click_through(self):
        """双击切换穿透模式"""
        self.root.bind("<Double-Button-1>", lambda e: self._toggle_click_through())

    def _update_data(self):
        """定时刷新数据"""
        try:
            # CPU
            cpu = self.monitor.get_cpu()
            self._bar_0.draw(cpu)
            self._pct_0.configure(text=f"{cpu:.0f}%")

            # 内存
            mem_pct, mem_used, mem_total = self.monitor.get_memory()
            self._bar_1.draw(mem_pct)
            self._pct_1.configure(text=f"{mem_pct:.0f}%")

            # 磁盘
            disk_pct, disk_used, disk_total = self.monitor.get_disk()
            self._bar_2.draw(disk_pct)
            self._pct_2.configure(text=f"{disk_pct:.0f}%")

            # 网络
            up, down = self.monitor.get_network()
            self._net_label.configure(
                text=f"UP {format_speed(up)}  DOWN {format_speed(down)}"
            )
        except Exception:
            pass

        self.root.after(REFRESH_INTERVAL, self._update_data)

    def _quit(self):
        self.root.destroy()
        sys.exit(0)

    def run(self):
        self.root.mainloop()


# ============================================================
# 入口
# ============================================================
if __name__ == "__main__":
    if "--install-autostart" in sys.argv:
        if set_autostart(True):
            print("SysMonitor 已设置为静默自启动。")
            sys.exit(0)
        else:
            print("当前系统不支持静默自启动，或注册表写入失败。")
            sys.exit(1)
    if "--remove-autostart" in sys.argv:
        if set_autostart(False):
            print("SysMonitor 已取消自启动。")
            sys.exit(0)
        else:
            print("取消自启动失败。")
            sys.exit(1)

    app = SysMonitorApp()
    app.run()
