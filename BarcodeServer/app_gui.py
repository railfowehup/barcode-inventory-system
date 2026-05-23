# -*- coding: utf-8 -*-
"""
📦 包裹管理系统 - tkinter 桌面管理界面
提供实时扫码监控、统计、记录查询、设备管理等功能
全部 tkinter 原生实现，不依赖浏览器
"""

import os
import sys
import json
import threading
import time
import socket
from datetime import datetime

import tkinter as tk
from tkinter import ttk, messagebox, scrolledtext
from PIL import Image, ImageDraw, ImageTk

# 全局变量
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
EXPORT_DIR = os.path.join(BASE_DIR, 'exports')
SERVER_PORT = 3000


def get_local_ips():
    """获取本机局域网 IPv4 地址"""
    ips = []
    try:
        import psutil
        for iface, addrs in psutil.net_if_addrs().items():
            for addr in addrs:
                if addr.family == socket.AF_INET and not addr.address.startswith('127.'):
                    ips.append(addr.address)
    except:
        pass
    if not ips:
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.connect(('8.8.8.8', 80))
            ips.append(s.getsockname()[0])
            s.close()
        except:
            ips.append('127.0.0.1')
    return ips


def play_sound():
    """播放提示音"""
    try:
        import winsound
        winsound.MessageBeep(winsound.MB_OK)
    except:
        pass


def show_notification(title, message, timeout=3):
    """显示系统通知"""
    try:
        from plyer import notification
        notification.notify(
            title=title,
            message=message,
            timeout=timeout,
        )
    except:
        pass


class ServerManager:
    """管理 Flask 服务器进程"""
    def __init__(self):
        self.server_thread = None
        self.running = False
        self._flask_app = None

    def start(self):
        if self.running:
            return True
        try:
            from server_api import app, start_server
            self._flask_app = app
            self.running = True
            self.server_thread = threading.Thread(
                target=start_server,
                daemon=True,
                kwargs={'port': SERVER_PORT}
            )
            self.server_thread.start()
            for _ in range(30):
                if self._check_health():
                    return True
                time.sleep(0.5)
            return False
        except Exception as e:
            print(f"启动服务器失败: {e}")
            return False

    def stop(self):
        self.running = False

    def restart(self):
        self.stop()
        time.sleep(1)
        return self.start()

    def _check_health(self):
        try:
            import urllib.request
            resp = urllib.request.urlopen(
                f'http://127.0.0.1:{SERVER_PORT}/api/health', timeout=1
            )
            return resp.status == 200
        except:
            return False

    def is_running(self):
        return self.running and self._check_health()

    def get_status_text(self):
        if self.is_running():
            ips = get_local_ips()
            ip = ips[0] if ips else 'localhost'
            return f"🟢 运行中 | http://{ip}:{SERVER_PORT}"
        return "🔴 已停止"


class SystemTrayApp:
    """系统托盘应用"""
    def __init__(self, root, server_mgr, status_callback=None):
        self.root = root
        self.server = server_mgr
        self.status_callback = status_callback
        self.tray_icon = None
        self._init_tray()

    def _init_tray(self):
        try:
            import pystray
        except ImportError:
            return

        icon_size = 64
        image = Image.new('RGBA', (icon_size, icon_size), (0, 0, 0, 0))
        draw = ImageDraw.Draw(image)
        draw.rectangle([8, 20, 56, 50], fill=(0, 212, 255, 200), outline=(0, 150, 200, 255), width=2)
        draw.rectangle([12, 10, 52, 22], fill=(0, 180, 220, 180), outline=(0, 150, 200, 255), width=1)
        draw.text((20, 28), "📦", font=None, fill=(255, 255, 255, 255))

        def on_open(icon, item):
            self.root.after(0, self._show_window)

        def on_restart(icon, item):
            self.root.after(0, self._restart_server)

        def on_open_export(icon, item):
            if not os.path.exists(EXPORT_DIR):
                os.makedirs(EXPORT_DIR)
            os.startfile(EXPORT_DIR)

        def on_open_data(icon, item):
            os.startfile(BASE_DIR)

        def on_quit(icon, item):
            icon.stop()
            self.root.after(0, self._quit)

        def on_double_click(icon):
            self.root.after(0, self._show_window)

        menu = pystray.Menu(
            pystray.MenuItem('📊 显示主界面', on_open, default=True),
            pystray.Menu.SEPARATOR,
            pystray.MenuItem(lambda item: self.server.get_status_text(), None, enabled=False),
            pystray.MenuItem('🔄 重启服务器', on_restart),
            pystray.Menu.SEPARATOR,
            pystray.MenuItem('📁 打开导出目录', on_open_export),
            pystray.MenuItem('📂 打开数据目录', on_open_data),
            pystray.Menu.SEPARATOR,
            pystray.MenuItem('❌ 退出', on_quit),
        )

        self.tray_icon = pystray.Icon(
            'barcode-server',
            image,
            '📦 包裹管理系统',
            menu,
        )
        self.tray_icon.on_double_click = on_double_click

    def _show_window(self):
        self.root.deiconify()
        self.root.lift()
        self.root.focus_force()

    def _restart_server(self):
        result = self.server.restart()
        if self.status_callback:
            self.status_callback()
        try:
            from plyer import notification
            notification.notify(
                title='📦 包裹管理系统',
                message='✅ 服务器重启成功' if result else '❌ 服务器重启失败',
                timeout=3,
            )
        except:
            pass

    def _quit(self):
        self.server.stop()
        self.root.quit()
        os._exit(0)

    def run(self):
        if self.tray_icon:
            threading.Thread(target=self.tray_icon.run, daemon=True).start()


class MainWindow:
    """主窗口 - 管理控制台"""
    def __init__(self, root, server_mgr):
        self.root = root
        self.server = server_mgr
        self.root.title('📦 包裹管理系统 - 管理控制台')
        self.root.geometry('1200x780')
        self.root.minsize(1000, 650)

        # 暗色主题颜色
        self.bg_color = '#0A0E27'
        self.card_bg = '#1A1F3A'
        self.accent_color = '#00D4FF'
        self.text_color = '#E0E0E0'
        self.success_color = '#4CAF50'
        self.warning_color = '#FF9800'
        self.error_color = '#F44336'
        self.highlight_color = '#FFD700'

        self.root.configure(bg=self.bg_color)

        # 记录上次扫码数量，用于检测新记录
        self._last_record_count = 0
        self._last_scan_time = ''
        self._last_offline_count = 0
        self._server_update_listener_registered = False

        # 设置窗口关闭行为（隐藏到托盘）
        self.root.protocol('WM_DELETE_WINDOW', self._hide_window)

        self._build_ui()
        self._start_monitor()

    def _hide_window(self):
        self.root.withdraw()

    def _build_ui(self):
        """构建界面"""
        # ===== 标题栏 =====
        title_frame = tk.Frame(self.root, bg=self.bg_color, height=50)
        title_frame.pack(fill='x', padx=20, pady=(12, 5))

        tk.Label(
            title_frame, text='📦 包裹管理系统',
            font=('Microsoft YaHei', 18, 'bold'),
            fg=self.accent_color, bg=self.bg_color
        ).pack(side='left')

        # 状态指示器
        self.status_label = tk.Label(
            title_frame, text='🟢 运行中',
            font=('Microsoft YaHei', 11),
            fg=self.success_color, bg=self.bg_color
        )
        self.status_label.pack(side='right', padx=10)

        # 服务器地址
        self.url_label = tk.Label(
            title_frame, text='',
            font=('Microsoft YaHei', 10),
            fg=self.text_color, bg=self.bg_color
        )
        self.url_label.pack(side='right', padx=10)

        # ===== 分隔线 =====
        separator = tk.Frame(self.root, bg=self.accent_color, height=2)
        separator.pack(fill='x', padx=20)

        # ===== 主内容区域 =====
        main_frame = tk.Frame(self.root, bg=self.bg_color)
        main_frame.pack(fill='both', expand=True, padx=15, pady=10)

        # ===== 顶部统计卡片行 =====
        stats_frame = tk.Frame(main_frame, bg=self.bg_color)
        stats_frame.pack(fill='x', pady=(0, 10))

        self.stat_cards = {}
        stat_items = [
            ('total', '📦 总计', '0', self.accent_color),
            ('today', '📅 今日', '0', self.success_color),
            ('in', '📥 入库', '0', '#2196F3'),
            ('sort', '📤 分拣', '0', '#FF9800'),
            ('ship', '🚚 出库', '0', '#9C27B0'),
            ('sign', '✅ 签收', '0', '#4CAF50'),
        ]

        for i, (key, label, default, color) in enumerate(stat_items):
            card = tk.Frame(stats_frame, bg=self.card_bg, bd=0, highlightthickness=1,
                            highlightbackground='#2A2F4A')
            card.pack(side='left', fill='x', expand=True, padx=3)

            tk.Label(card, text=label, font=('Microsoft YaHei', 9),
                     fg=self.text_color, bg=self.card_bg).pack(pady=(8, 0))
            val_lbl = tk.Label(card, text=default, font=('Microsoft YaHei', 16, 'bold'),
                               fg=color, bg=self.card_bg)
            val_lbl.pack(pady=(0, 8))
            self.stat_cards[key] = val_lbl

        # ===== 中间区域：左侧实时记录 + 右侧操作面板 =====
        middle_frame = tk.Frame(main_frame, bg=self.bg_color)
        middle_frame.pack(fill='both', expand=True)

        # ===== 左侧：实时扫码记录 =====
        left_frame = tk.Frame(middle_frame, bg=self.bg_color)
        left_frame.pack(side='left', fill='both', expand=True, padx=(0, 8))

        # 记录列表标题
        record_header = tk.Frame(left_frame, bg=self.card_bg)
        record_header.pack(fill='x', pady=(0, 5))

        tk.Label(record_header, text='📋 实时扫码记录', font=('Microsoft YaHei', 12, 'bold'),
                 fg=self.accent_color, bg=self.card_bg).pack(side='left', padx=12, pady=8)

        self.record_count_label = tk.Label(record_header, text='共 0 条',
                                           font=('Microsoft YaHei', 9),
                                           fg=self.text_color, bg=self.card_bg)
        self.record_count_label.pack(side='right', padx=12, pady=8)

        # 记录列表（Treeview）
        columns = ('time', 'barcode', 'status', 'user', 'address')
        self.record_tree = ttk.Treeview(left_frame, columns=columns, show='headings',
                                        height=14)
        self.record_tree.heading('time', text='时间')
        self.record_tree.heading('barcode', text='条码')
        self.record_tree.heading('status', text='状态')
        self.record_tree.heading('user', text='操作人')
        self.record_tree.heading('address', text='目的地')

        self.record_tree.column('time', width=140, minwidth=120)
        self.record_tree.column('barcode', width=180, minwidth=150)
        self.record_tree.column('status', width=60, minwidth=50)
        self.record_tree.column('user', width=80, minwidth=60)
        self.record_tree.column('address', width=150, minwidth=100)

        # 样式
        style = ttk.Style()
        style.theme_use('clam')
        style.configure('Treeview',
                        background='#0D1130',
                        foreground='#E0E0E0',
                        fieldbackground='#0D1130',
                        borderwidth=0,
                        font=('Microsoft YaHei', 9))
        style.configure('Treeview.Heading',
                        background='#1A1F3A',
                        foreground='#00D4FF',
                        borderwidth=1,
                        font=('Microsoft YaHei', 9, 'bold'))
        style.map('Treeview', background=[('selected', '#2A2F4A')])

        vsb = ttk.Scrollbar(left_frame, orient='vertical', command=self.record_tree.yview)
        self.record_tree.configure(yscrollcommand=vsb.set)
        vsb.pack(side='right', fill='y')
        self.record_tree.pack(fill='both', expand=True)

        # 双击查看详情
        self.record_tree.bind('<Double-1>', self._on_record_double_click)

        # ===== 右侧：操作面板 =====
        right_frame = tk.Frame(middle_frame, bg=self.bg_color)
        right_frame.pack(side='right', fill='y', padx=(8, 0))

        # 快捷操作
        self._create_section(right_frame, '⚡ 快捷操作', self._build_actions)
        # 搜索
        self._create_section(right_frame, '🔍 搜索记录', self._build_search)
        # 在线设备
        self._create_section(right_frame, '📱 在线设备', self._build_online_devices)
        # 设备管理
        self._create_section(right_frame, '⚙️ 设备管理', self._build_device_management)
        # 服务器信息
        self._create_section(right_frame, '💻 服务器信息', self._build_server_info)

        # ===== 底部日志 =====
        log_frame = tk.Frame(main_frame, bg=self.bg_color)
        log_frame.pack(fill='x', pady=(8, 0))

        tk.Label(log_frame, text='📋 运行日志', font=('Microsoft YaHei', 10, 'bold'),
                 fg=self.accent_color, bg=self.bg_color).pack(anchor='w')

        self.log_text = scrolledtext.ScrolledText(
            log_frame,
            font=('Consolas', 9),
            bg='#0D1130', fg='#00D4FF',
            insertbackground=self.accent_color,
            bd=0, highlightthickness=0,
            height=5
        )
        self.log_text.pack(fill='x', pady=(3, 0))
        self.log_text.insert('end', '📦 包裹管理系统已启动\n')
        self.log_text.see('end')

    def _create_section(self, parent, title, build_func):
        """创建分区"""
        frame = tk.Frame(parent, bg=self.card_bg, bd=0, highlightthickness=1,
                         highlightbackground='#2A2F4A')
        frame.pack(fill='x', pady=4)

        tk.Label(frame, text=title, font=('Microsoft YaHei', 10, 'bold'),
                 fg=self.accent_color, bg=self.card_bg).pack(anchor='w', padx=12, pady=(8, 2))

        content = tk.Frame(frame, bg=self.card_bg)
        content.pack(fill='x', padx=12, pady=(0, 10))

        build_func(content)
        return frame

    def _build_actions(self, parent):
        """快捷操作"""
        actions = [
            ('📋 显示二维码', self._show_qr_code, self.accent_color),
            ('🔄 重启服务器', self._restart_server, self.warning_color),
            ('💾 备份数据库', self._backup_db, self.success_color),
            ('📁 打开导出目录', self._open_export, self.text_color),
            ('📂 打开数据目录', self._open_data_dir, self.text_color),
        ]

        for text, cmd, color in actions:
            btn = tk.Button(
                parent, text=text, command=cmd,
                font=('Microsoft YaHei', 9),
                bg=self.card_bg, fg=color,
                activebackground='#2A2F4A', activeforeground=color,
                bd=1, relief='solid', highlightbackground='#2A2F4A',
                cursor='hand2', padx=8, pady=4
            )
            btn.pack(fill='x', pady=2)

    def _build_search(self, parent):
        """搜索记录"""
        row = tk.Frame(parent, bg=self.card_bg)
        row.pack(fill='x', pady=2)

        self.search_var = tk.StringVar()
        search_entry = tk.Entry(row, textvariable=self.search_var,
                                font=('Microsoft YaHei', 9),
                                bg='#0D1130', fg=self.text_color,
                                insertbackground=self.accent_color,
                                bd=1, relief='solid', highlightbackground='#2A2F4A')
        search_entry.pack(side='left', fill='x', expand=True, padx=(0, 5))
        search_entry.bind('<Return>', lambda e: self._do_search())

        tk.Button(row, text='搜索', command=self._do_search,
                  font=('Microsoft YaHei', 9),
                  bg=self.accent_color, fg='#0A0E27',
                  bd=0, padx=10, cursor='hand2'
                  ).pack(side='right')

        # 状态筛选
        filter_row = tk.Frame(parent, bg=self.card_bg)
        filter_row.pack(fill='x', pady=2)

        self.status_filter = tk.StringVar(value='全部')
        for text in ['全部', '入库', '分拣', '出库', '签收', '异常']:
            tk.Radiobutton(filter_row, text=text, variable=self.status_filter,
                           value=text, command=self._do_search,
                           font=('Microsoft YaHei', 8),
                           fg=self.text_color, bg=self.card_bg,
                           selectcolor=self.card_bg,
                           activebackground=self.card_bg,
                           activeforeground=self.accent_color
                           ).pack(side='left', padx=2)

    def _build_online_devices(self, parent):
        """在线设备面板"""
        self.device_count_label = tk.Label(
            parent, text='在线: 0 台', font=('Microsoft YaHei', 9, 'bold'),
            fg=self.success_color, bg=self.card_bg
        )
        self.device_count_label.pack(anchor='w', pady=(0, 4))

        # 设备列表容器
        self.device_list_frame = tk.Frame(parent, bg=self.card_bg)
        self.device_list_frame.pack(fill='x')

        # 刷新按钮
        tk.Button(parent, text='🔄 刷新', command=self._refresh_devices,
                  font=('Microsoft YaHei', 8),
                  bg=self.card_bg, fg=self.accent_color,
                  activebackground='#2A2F4A', activeforeground=self.accent_color,
                  bd=1, relief='solid', highlightbackground='#2A2F4A',
                  cursor='hand2', padx=8, pady=2
                  ).pack(pady=(4, 0))

        # 启动定时刷新
        self._refresh_devices()

    def _build_device_management(self, parent):
        """设备管理面板"""
        self.device_mgr_frame = tk.Frame(parent, bg=self.card_bg)
        self.device_mgr_frame.pack(fill='x')

        # 离线设备计数
        self.offline_count_label = tk.Label(
            parent, text='离线: --', font=('Microsoft YaHei', 8),
            fg=self.text_color, bg=self.card_bg
        )
        self.offline_count_label.pack(anchor='w', pady=(0, 2))

        # 查看所有设备按钮
        tk.Button(parent, text='📋 查看所有设备', command=self._show_all_devices,
                  font=('Microsoft YaHei', 8),
                  bg=self.card_bg, fg=self.accent_color,
                  activebackground='#2A2F4A', activeforeground=self.accent_color,
                  bd=1, relief='solid', highlightbackground='#2A2F4A',
                  cursor='hand2', padx=8, pady=2
                  ).pack(pady=(2, 0))

        # 查看连接记录按钮
        tk.Button(parent, text='🔗 查看连接记录', command=self._show_connection_logs,
                  font=('Microsoft YaHei', 8),
                  bg=self.card_bg, fg=self.accent_color,
                  activebackground='#2A2F4A', activeforeground=self.accent_color,
                  bd=1, relief='solid', highlightbackground='#2A2F4A',
                  cursor='hand2', padx=8, pady=2
                  ).pack(pady=(2, 0))

    def _build_server_info(self, parent):
        """服务器信息"""
        self.info_labels = {}
        items = [
            ('status_info', '状态', '🟢 运行中'),
            ('records', '总记录', '0'),
            ('today_info', '今日', '0'),
            ('db_size_info', '数据库', '计算中...'),
            ('port_info', '端口', str(SERVER_PORT)),
        ]

        for key, label, default in items:
            row = tk.Frame(parent, bg=self.card_bg)
            row.pack(fill='x', pady=1)
            tk.Label(row, text=label, font=('Microsoft YaHei', 9),
                     fg=self.text_color, bg=self.card_bg,
                     width=8, anchor='w').pack(side='left')
            lbl = tk.Label(row, text=default, font=('Microsoft YaHei', 9, 'bold'),
                           fg=self.accent_color, bg=self.card_bg, anchor='w')
            lbl.pack(side='left', fill='x', expand=True)
            self.info_labels[key] = lbl

    def _do_search(self):
        """执行搜索"""
        keyword = self.search_var.get().strip()
        status = self.status_filter.get()
        if status == '全部':
            status = None

        try:
            import urllib.request
            params = []
            if keyword:
                params.append(f'q={urllib.parse.quote(keyword)}')
            if status:
                params.append(f'status={urllib.parse.quote(status)}')
            params.append('page_size=100')

            url = f'http://127.0.0.1:{SERVER_PORT}/api/records/search?{"&".join(params)}'
            resp = urllib.request.urlopen(url, timeout=3)
            data = json.loads(resp.read().decode())

            # 清空并填充搜索结果
            for item in self.record_tree.get_children():
                self.record_tree.delete(item)

            for r in data.get('records', []):
                self.record_tree.insert('', 'end', values=(
                    r.get('created_at', '')[:19],
                    r.get('barcode', ''),
                    r.get('status', ''),
                    r.get('user_name', ''),
                    r.get('address', '')[:20],
                ))

            self.record_count_label.config(text=f'搜索到 {data.get("total", 0)} 条')
            self.log(f'🔍 搜索到 {data.get("total", 0)} 条记录')

        except Exception as e:
            self.log(f'❌ 搜索失败: {str(e)}')

    def _on_record_double_click(self, event):
        """双击记录查看详情"""
        selection = self.record_tree.selection()
        if not selection:
            return

        values = self.record_tree.item(selection[0], 'values')
        if not values:
            return

        barcode = values[1]
        # 查询详情
        try:
            import urllib.request
            resp = urllib.request.urlopen(
                f'http://127.0.0.1:{SERVER_PORT}/api/records/search?q={urllib.parse.quote(barcode)}&page_size=1',
                timeout=3
            )
            data = json.loads(resp.read().decode())
            records = data.get('records', [])
            if records:
                self._show_record_detail(records[0])
        except Exception as e:
            self.log(f'❌ 查询详情失败: {str(e)}')

    def _show_record_detail(self, record):
        """显示记录详情弹窗"""
        detail_win = tk.Toplevel(self.root)
        detail_win.title(f'📦 包裹详情 - {record.get("barcode", "")}')
        detail_win.configure(bg=self.bg_color)
        detail_win.geometry('450x400')
        detail_win.resizable(False, False)

        # 居中
        detail_win.update_idletasks()
        x = (detail_win.winfo_screenwidth() - 450) // 2
        y = (detail_win.winfo_screenheight() - 400) // 2
        detail_win.geometry(f'+{x}+{y}')

        fields = [
            ('条码', record.get('barcode', '')),
            ('状态', record.get('status', '')),
            ('操作人', record.get('user_name', '')),
            ('目的地', record.get('address', '')),
            ('重量(kg)', str(record.get('weight', 0))),
            ('收件人', record.get('recipient', '') or ''),
            ('物流单号', record.get('logistics_no', '') or ''),
            ('签收人', record.get('signer', '') or ''),
            ('异常类型', record.get('exception_type', '') or ''),
            ('备注', record.get('note', '') or ''),
            ('入库时间', record.get('created_at', '')[:19]),
            ('分拣时间', (record.get('sort_at', '') or '')[:19]),
            ('签收时间', (record.get('sign_time', '') or '')[:19]),
        ]

        for label, value in fields:
            row = tk.Frame(detail_win, bg=self.bg_color)
            row.pack(fill='x', padx=20, pady=2)
            tk.Label(row, text=label, font=('Microsoft YaHei', 9),
                     fg=self.text_color, bg=self.bg_color,
                     width=10, anchor='w').pack(side='left')
            tk.Label(row, text=value, font=('Microsoft YaHei', 9, 'bold'),
                     fg=self.accent_color, bg=self.bg_color,
                     anchor='w').pack(side='left', fill='x', expand=True)

        tk.Button(detail_win, text='关闭', command=detail_win.destroy,
                  font=('Microsoft YaHei', 10),
                  bg=self.card_bg, fg=self.text_color,
                  bd=1, relief='solid', padx=20, pady=5
                  ).pack(pady=15)

    def _show_qr_code(self):
        """显示二维码弹窗（居中显示）"""
        import urllib.request
        try:
            resp = urllib.request.urlopen(
                f'http://127.0.0.1:{SERVER_PORT}/api/qrcode', timeout=5
            )
            data = json.loads(resp.read().decode())
            if not data.get('success'):
                messagebox.showerror('错误', '获取二维码失败')
                return

            qr_data_url = data['qrcode']
            server_url = data['url']

            qr_win = tk.Toplevel(self.root)
            qr_win.title('📱 手机扫码连接')
            qr_win.configure(bg=self.bg_color)
            qr_win.resizable(False, False)

            import base64
            from io import BytesIO
            img_data = qr_data_url.split(',')[1]
            img_bytes = base64.b64decode(img_data)
            img = Image.open(BytesIO(img_bytes))
            img = img.resize((300, 300), Image.LANCZOS)
            photo = ImageTk.PhotoImage(img)

            tk.Label(qr_win, image=photo, bg=self.bg_color).pack(padx=30, pady=(30, 10))
            tk.Label(qr_win, text='📱 请用手机扫描二维码连接', font=('Microsoft YaHei', 12),
                     fg=self.accent_color, bg=self.bg_color).pack(pady=(5, 5))
            tk.Label(qr_win, text=f'服务器地址: {server_url}', font=('Microsoft YaHei', 10),
                     fg=self.text_color, bg=self.bg_color).pack(pady=(0, 15))

            tk.Button(qr_win, text='关闭', command=qr_win.destroy,
                      font=('Microsoft YaHei', 10),
                      bg=self.card_bg, fg=self.text_color,
                      bd=1, relief='solid', padx=20, pady=5
                      ).pack(pady=(0, 20))

            # 居中
            qr_win.update_idletasks()
            x = (qr_win.winfo_screenwidth() - qr_win.winfo_reqwidth()) // 2
            y = (qr_win.winfo_screenheight() - qr_win.winfo_reqheight()) // 2
            qr_win.geometry(f'+{x}+{y}')

            qr_win.photo = photo

        except Exception as e:
            messagebox.showerror('错误', f'获取二维码失败: {str(e)}')

    def _show_all_devices(self):
        """显示所有设备列表弹窗"""
        try:
            import urllib.request
            resp = urllib.request.urlopen(
                f'http://127.0.0.1:{SERVER_PORT}/api/devices/history?limit=100', timeout=3
            )
            data = json.loads(resp.read().decode())
            devices = data.get('devices', [])

            win = tk.Toplevel(self.root)
            win.title('📱 所有设备')
            win.configure(bg=self.bg_color)
            win.geometry('600x400')

            # 居中
            win.update_idletasks()
            x = (win.winfo_screenwidth() - 600) // 2
            y = (win.winfo_screenheight() - 400) // 2
            win.geometry(f'+{x}+{y}')

            columns = ('device', 'name', 'user', 'ip', 'group', 'last_seen')
            tree = ttk.Treeview(win, columns=columns, show='headings')
            tree.heading('device', text='设备ID')
            tree.heading('name', text='设备名')
            tree.heading('user', text='操作人')
            tree.heading('ip', text='IP')
            tree.heading('group', text='分组')
            tree.heading('last_seen', text='最后在线')

            tree.column('device', width=120)
            tree.column('name', width=100)
            tree.column('user', width=80)
            tree.column('ip', width=120)
            tree.column('group', width=80)
            tree.column('last_seen', width=150)

            vsb = ttk.Scrollbar(win, orient='vertical', command=tree.yview)
            tree.configure(yscrollcommand=vsb.set)
            vsb.pack(side='right', fill='y')
            tree.pack(fill='both', expand=True, padx=10, pady=10)

            for dev in devices:
                tree.insert('', 'end', values=(
                    dev.get('device_id', ''),
                    dev.get('device_name', ''),
                    dev.get('user_name', ''),
                    dev.get('ip_address', ''),
                    dev.get('device_group', ''),
                    (dev.get('last_heartbeat', '') or '')[:19],
                ))

        except Exception as e:
            messagebox.showerror('错误', f'获取设备列表失败: {str(e)}')

    def _show_connection_logs(self):
        """显示连接记录弹窗"""
        try:
            import urllib.request
            resp = urllib.request.urlopen(
                f'http://127.0.0.1:{SERVER_PORT}/api/sync-logs?limit=100', timeout=3
            )
            logs = json.loads(resp.read().decode())

            win = tk.Toplevel(self.root)
            win.title('🔗 同步/连接记录')
            win.configure(bg=self.bg_color)
            win.geometry('600x400')

            # 居中
            win.update_idletasks()
            x = (win.winfo_screenwidth() - 600) // 2
            y = (win.winfo_screenheight() - 400) // 2
            win.geometry(f'+{x}+{y}')

            columns = ('time', 'device', 'user', 'added', 'skipped', 'duplicates')
            tree = ttk.Treeview(win, columns=columns, show='headings')
            tree.heading('time', text='时间')
            tree.heading('device', text='设备')
            tree.heading('user', text='操作人')
            tree.heading('added', text='新增')
            tree.heading('skipped', text='跳过')
            tree.heading('duplicates', text='重复')

            tree.column('time', width=160)
            tree.column('device', width=120)
            tree.column('user', width=80)
            tree.column('added', width=60)
            tree.column('skipped', width=60)
            tree.column('duplicates', width=60)

            vsb = ttk.Scrollbar(win, orient='vertical', command=tree.yview)
            tree.configure(yscrollcommand=vsb.set)
            vsb.pack(side='right', fill='y')
            tree.pack(fill='both', expand=True, padx=10, pady=10)

            for log in logs:
                tree.insert('', 'end', values=(
                    log.get('sync_time', '')[:19],
                    log.get('device_id', ''),
                    log.get('user_name', ''),
                    log.get('added', 0),
                    log.get('skipped', 0),
                    log.get('duplicates', 0),
                ))

        except Exception as e:
            messagebox.showerror('错误', f'获取连接记录失败: {str(e)}')

    def _restart_server(self):
        self.log('🔄 正在重启服务器...')
        result = self.server.restart()
        if result:
            self.log('✅ 服务器重启成功')
        else:
            self.log('❌ 服务器重启失败')

    def _open_export(self):
        if not os.path.exists(EXPORT_DIR):
            os.makedirs(EXPORT_DIR)
        os.startfile(EXPORT_DIR)

    def _open_data_dir(self):
        os.startfile(BASE_DIR)

    def _backup_db(self):
        import urllib.request
        try:
            resp = urllib.request.urlopen(
                f'http://127.0.0.1:{SERVER_PORT}/api/backup', timeout=10
            )
            self.log('✅ 数据库备份成功')
            messagebox.showinfo('成功', '数据库备份成功！\n备份文件保存在 exports 目录')
        except Exception as e:
            messagebox.showerror('错误', f'备份失败: {str(e)}')

    def log(self, message):
        """添加日志"""
        try:
            timestamp = datetime.now().strftime('%H:%M:%S')
            self.log_text.insert('end', f'[{timestamp}] {message}\n')
            self.log_text.see('end')
        except:
            pass

    def _start_monitor(self):
        """启动监控定时器 - 每3秒刷新"""
        def update():
            try:
                import psutil
                import urllib.request

                # 检查服务器状态
                is_running = self.server.is_running()
                if is_running:
                    self.status_label.config(text='🟢 运行中', fg=self.success_color)
                    ips = get_local_ips()
                    ip = ips[0] if ips else 'localhost'
                    self.url_label.config(text=f'http://{ip}:{SERVER_PORT}')

                    # 获取统计数据
                    try:
                        resp = urllib.request.urlopen(
                            f'http://127.0.0.1:{SERVER_PORT}/api/stats', timeout=2
                        )
                        stats = json.loads(resp.read().decode())

                        # 更新统计卡片
                        self.stat_cards['total'].config(text=str(stats.get('total', 0)))
                        self.stat_cards['today'].config(text=str(stats.get('today', 0)))
                        self.stat_cards['in'].config(text=str(stats.get('in_count', 0)))
                        self.stat_cards['sort'].config(text=str(stats.get('sort_count', 0)))
                        self.stat_cards['ship'].config(text=str(stats.get('ship_count', 0)))
                        self.stat_cards['sign'].config(text=str(stats.get('sign_count', 0)))

                        # 更新服务器信息
                        if 'status_info' in self.info_labels:
                            self.info_labels['status_info'].config(
                                text=f'🟢 运行中 | 总计 {stats.get("total", 0)} 件'
                            )
                        if 'records' in self.info_labels:
                            self.info_labels['records'].config(text=str(stats.get('total', 0)))
                        if 'today_info' in self.info_labels:
                            self.info_labels['today_info'].config(text=str(stats.get('today', 0)))

                        # 检测新记录（扫码反馈）
                        current_total = stats.get('total', 0)
                        if current_total > self._last_record_count and self._last_record_count > 0:
                            new_count = current_total - self._last_record_count
                            self.log(f'📥 检测到 {new_count} 条新入库记录！')
                            play_sound()
                            show_notification('📦 包裹管理系统', f'检测到 {new_count} 条新记录')
                            self._refresh_records()
                        self._last_record_count = current_total

                        # 立即注册服务器事件监听器（只注册一次）
                        if not self._server_update_listener_registered:
                            self._register_server_update_listener()
                            self._server_update_listener_registered = True

                    except Exception as e:
                        pass

                    # 更新数据库大小
                    db_path = os.path.join(BASE_DIR, 'database.sqlite')
                    if os.path.exists(db_path):
                        size = os.path.getsize(db_path)
                        if size < 1024:
                            size_str = f'{size} B'
                        elif size < 1024**2:
                            size_str = f'{size/1024:.1f} KB'
                        else:
                            size_str = f'{size/1024**2:.1f} MB'
                        if 'db_size_info' in self.info_labels:
                            self.info_labels['db_size_info'].config(text=size_str)

                    # 刷新记录列表（每3秒）
                    self._refresh_records()

                    # 检查离线设备
                    self._check_offline_devices()

                else:
                    self.status_label.config(text='🔴 已停止', fg=self.error_color)
                    self.url_label.config(text='')
                    if 'status_info' in self.info_labels:
                        self.info_labels['status_info'].config(text='🔴 已停止')

            except Exception as e:
                pass

            self.root.after(3000, update)

        update()

    def _check_offline_devices(self):
        """检查离线设备"""
        try:
            import urllib.request
            resp = urllib.request.urlopen(
                f'http://127.0.0.1:{SERVER_PORT}/api/devices/check-offline?timeout=60', timeout=2
            )
            data = json.loads(resp.read().decode())
            offline_count = data.get('offline_count', 0)
            offline_devices = data.get('offline_devices', [])

            # 更新离线计数
            if 'offline_count_label' in dir(self):
                if offline_count > 0:
                    self.offline_count_label.config(
                        text=f'离线: {offline_count} 台',
                        fg=self.error_color
                    )
                else:
                    self.offline_count_label.config(
                        text='离线: 0 台',
                        fg=self.success_color
                    )

            # 检测新离线设备
            if offline_count > self._last_offline_count and self._last_offline_count > 0:
                new_offline = offline_count - self._last_offline_count
                for dev in offline_devices:
                    name = dev.get('device_name', dev.get('device_id', '未知设备'))
                    self.log(f'🔴 设备离线: {name}')
                    show_notification('⚠️ 设备离线', f'设备 {name} 已离线')
                    play_sound()

            self._last_offline_count = offline_count

        except:
            pass

    def _refresh_records(self):
        """刷新实时记录列表"""
        try:
            import urllib.request
            resp = urllib.request.urlopen(
                f'http://127.0.0.1:{SERVER_PORT}/api/records?page_size=50', timeout=2
            )
            data = json.loads(resp.read().decode())
            records = data.get('records', [])

            # 检查是否有新记录
            if records:
                latest_time = records[0].get('created_at', '')
                if latest_time != self._last_scan_time and self._last_scan_time:
                    barcode = records[0].get('barcode', '')
                    user = records[0].get('user_name', '')
                    status = records[0].get('status', '')
                    self.log(f'📱 {user} {status}: {barcode}')
                    play_sound()
                    show_notification(f'📱 {user} {status}', f'条码: {barcode}')
                self._last_scan_time = latest_time

            # 清空并填充
            for item in self.record_tree.get_children():
                self.record_tree.delete(item)

            for r in records:
                self.record_tree.insert('', 'end', values=(
                    r.get('created_at', '')[:19],
                    r.get('barcode', ''),
                    r.get('status', ''),
                    r.get('user_name', ''),
                    r.get('address', '')[:20],
                ))

            self.record_count_label.config(text=f'共 {data.get("total", 0)} 条')

        except:
            pass

    def _register_server_update_listener(self):
        try:
            from server_api import register_update_listener
            register_update_listener(self._handle_server_update)
        except Exception:
            pass

    def _handle_server_update(self, event_type, data):
        try:
            self.root.after(0, lambda: self._on_server_update(event_type, data))
        except Exception:
            pass

    def _on_server_update(self, event_type, data):
        if event_type in ('scan', 'sort', 'ship', 'sign', 'address_change', 'record_update', 'scan_batch', 'sync_push'):
            if event_type == 'sort':
                self.log(f'📱 手机已分拣: {data.get("barcode", "")}')
            elif event_type == 'scan':
                self.log(f'📱 手机已入库: {data.get("barcode", "")}')
            elif event_type == 'ship':
                self.log(f'📱 手机已出库: {data.get("barcode", "")}')
            elif event_type == 'sign':
                self.log(f'📱 手机已签收: {data.get("barcode", "")}')
            elif event_type == 'address_change':
                self.log(f'📱 手机已改地址: {data.get("barcode", "")}')
            elif event_type == 'record_update':
                self.log(f'📱 手机已更新记录: {data.get("barcode", "")}')
            elif event_type == 'scan_batch':
                self.log(f'📱 手机批量入库: {data.get("inserted", 0)} 条')
            elif event_type == 'sync_push':
                self.log(f'📱 手机同步: 新增{data.get("inserted", 0)} 更新{data.get("updated", 0)}')

            play_sound()
            show_notification('📦 包裹管理系统', f'{event_type}: {data.get("barcode", "")}')
            self._refresh_stats()
            self._refresh_records()

    def _refresh_stats(self):
        try:
            import urllib.request
            resp = urllib.request.urlopen(
                f'http://127.0.0.1:{SERVER_PORT}/api/stats', timeout=2
            )
            stats = json.loads(resp.read().decode())
            self.stat_cards['total'].config(text=str(stats.get('total', 0)))
            self.stat_cards['today'].config(text=str(stats.get('today', 0)))
            self.stat_cards['in'].config(text=str(stats.get('in_count', 0)))
            self.stat_cards['sort'].config(text=str(stats.get('sort_count', 0)))
            self.stat_cards['ship'].config(text=str(stats.get('ship_count', 0)))
            self.stat_cards['sign'].config(text=str(stats.get('sign_count', 0)))
            if 'status_info' in self.info_labels:
                self.info_labels['status_info'].config(
                    text=f'🟢 运行中 | 总计 {stats.get("total", 0)} 件'
                )
            if 'records' in self.info_labels:
                self.info_labels['records'].config(text=str(stats.get('total', 0)))
            if 'today_info' in self.info_labels:
                self.info_labels['today_info'].config(text=str(stats.get('today', 0)))
        except Exception:
            pass

    def run(self):
        self.root.mainloop()


def run_gui():
    """启动 GUI"""
    root = tk.Tk()
    root.withdraw()

    server_mgr = ServerManager()

    print("正在启动服务器...")
    if server_mgr.start():
        print("✅ 服务器启动成功")
        ips = get_local_ips()
        ip = ips[0] if ips else 'localhost'
        print(f"🌐 API: http://{ip}:{SERVER_PORT}")
    else:
        print("❌ 服务器启动失败")

    main_win = MainWindow(root, server_mgr)

    tray = SystemTrayApp(root, server_mgr, status_callback=main_win._restart_server)
    tray.run()

    root.deiconify()

    main_win.run()


if __name__ == '__main__':
    run_gui()
