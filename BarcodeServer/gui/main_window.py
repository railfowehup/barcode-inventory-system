# -*- coding: utf-8 -*-
"""主窗口 - 管理控制台"""
import os
import json
import time
import urllib.request
import urllib.parse
from datetime import datetime

import tkinter as tk
from tkinter import ttk, messagebox, scrolledtext

from . import SERVER_PORT, get_local_ips, play_sound, show_notification, BASE_DIR, EXPORT_DIR
from . import dialogs


class MainWindow:
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

        self.root.configure(bg=self.bg_color)

        self._last_record_count = 0
        self._last_scan_time = ''
        self._last_offline_count = 0
        self._server_update_listener_registered = False

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

        tk.Label(title_frame, text='📦 包裹管理系统',
                 font=('Microsoft YaHei', 18, 'bold'),
                 fg=self.accent_color, bg=self.bg_color).pack(side='left')

        self.status_label = tk.Label(title_frame, text='🟢 运行中',
            font=('Microsoft YaHei', 11), fg=self.success_color, bg=self.bg_color)
        self.status_label.pack(side='right', padx=10)

        self.url_label = tk.Label(title_frame, text='',
            font=('Microsoft YaHei', 10), fg=self.text_color, bg=self.bg_color)
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

        for key, label, default, color in stat_items:
            card = tk.Frame(stats_frame, bg=self.card_bg, bd=0, highlightthickness=1,
                            highlightbackground='#2A2F4A')
            card.pack(side='left', fill='x', expand=True, padx=3)
            tk.Label(card, text=label, font=('Microsoft YaHei', 9),
                     fg=self.text_color, bg=self.card_bg).pack(pady=(8, 0))
            val_lbl = tk.Label(card, text=default, font=('Microsoft YaHei', 16, 'bold'),
                               fg=color, bg=self.card_bg)
            val_lbl.pack(pady=(0, 8))
            self.stat_cards[key] = val_lbl

        # ===== 中间区域 =====
        middle_frame = tk.Frame(main_frame, bg=self.bg_color)
        middle_frame.pack(fill='both', expand=True)

        # ===== 左侧：实时扫码记录 =====
        left_frame = tk.Frame(middle_frame, bg=self.bg_color)
        left_frame.pack(side='left', fill='both', expand=True, padx=(0, 8))

        record_header = tk.Frame(left_frame, bg=self.card_bg)
        record_header.pack(fill='x', pady=(0, 5))

        tk.Label(record_header, text='📋 实时扫码记录', font=('Microsoft YaHei', 12, 'bold'),
                 fg=self.accent_color, bg=self.card_bg).pack(side='left', padx=12, pady=8)

        self.record_count_label = tk.Label(record_header, text='共 0 条',
            font=('Microsoft YaHei', 9), fg=self.text_color, bg=self.card_bg)
        self.record_count_label.pack(side='right', padx=12, pady=8)

        columns = ('time', 'barcode', 'status', 'user', 'address', 'signer')
        self.record_tree = ttk.Treeview(left_frame, columns=columns, show='headings', height=14)
        self.record_tree.heading('time', text='时间')
        self.record_tree.heading('barcode', text='条码')
        self.record_tree.heading('status', text='状态')
        self.record_tree.heading('user', text='操作人')
        self.record_tree.heading('address', text='目的地')
        self.record_tree.heading('signer', text='签收人')

        self.record_tree.column('time', width=140, minwidth=120)
        self.record_tree.column('barcode', width=180, minwidth=150)
        self.record_tree.column('status', width=60, minwidth=50)
        self.record_tree.column('user', width=80, minwidth=60)
        self.record_tree.column('address', width=150, minwidth=100)
        self.record_tree.column('signer', width=80, minwidth=60)

        style = ttk.Style()
        style.theme_use('clam')
        style.configure('Treeview', background='#0D1130', foreground='#E0E0E0',
                        fieldbackground='#0D1130', borderwidth=0, font=('Microsoft YaHei', 9))
        style.configure('Treeview.Heading', background='#1A1F3A', foreground='#00D4FF',
                        borderwidth=1, font=('Microsoft YaHei', 9, 'bold'))
        style.map('Treeview', background=[('selected', '#2A2F4A')])

        vsb = ttk.Scrollbar(left_frame, orient='vertical', command=self.record_tree.yview)
        self.record_tree.configure(yscrollcommand=vsb.set)
        vsb.pack(side='right', fill='y')
        self.record_tree.pack(fill='both', expand=True)

        self.record_tree.bind('<Double-1>', self._on_record_double_click)

        # ===== 右侧：操作面板 =====
        right_frame = tk.Frame(middle_frame, bg=self.bg_color)
        right_frame.pack(side='right', fill='y', padx=(8, 0))

        self._create_section(right_frame, '⚡ 快捷操作', self._build_actions)
        self._create_section(right_frame, '🔍 搜索记录', self._build_search)
        self._create_section(right_frame, '📱 在线设备', self._build_online_devices)
        self._create_section(right_frame, '⚙️ 设备管理', self._build_device_management)
        self._create_section(right_frame, '💻 服务器信息', self._build_server_info)

        # ===== 底部日志 =====
        log_frame = tk.Frame(main_frame, bg=self.bg_color)
        log_frame.pack(fill='x', pady=(8, 0))

        tk.Label(log_frame, text='📋 运行日志', font=('Microsoft YaHei', 10, 'bold'),
                 fg=self.accent_color, bg=self.bg_color).pack(anchor='w')

        self.log_text = scrolledtext.ScrolledText(
            log_frame, font=('Consolas', 9),
            bg='#0D1130', fg='#00D4FF',
            insertbackground=self.accent_color,
            bd=0, highlightthickness=0, height=5
        )
        self.log_text.pack(fill='x', pady=(3, 0))
        self.log_text.insert('end', '📦 包裹管理系统已启动\n')
        self.log_text.see('end')

    def _create_section(self, parent, title, build_func):
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
        actions = [
            ('📋 显示二维码', lambda: dialogs.show_qr_code(self.root, self.bg_color, self.card_bg, self.text_color, self.accent_color), self.accent_color),
            ('🔄 重启服务器', self._restart_server, self.warning_color),
            ('💾 备份数据库', self._backup_db, self.success_color),
            ('📁 打开导出目录', self._open_export, self.text_color),
            ('📂 打开数据目录', self._open_data_dir, self.text_color),
        ]
        for text, cmd, color in actions:
            tk.Button(parent, text=text, command=cmd,
                      font=('Microsoft YaHei', 9),
                      bg=self.card_bg, fg=color,
                      activebackground='#2A2F4A', activeforeground=color,
                      bd=1, relief='solid', highlightbackground='#2A2F4A',
                      cursor='hand2', padx=8, pady=4
                      ).pack(fill='x', pady=2)

    def _build_search(self, parent):
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
                  bd=0, padx=10, cursor='hand2').pack(side='right')

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
        self.device_count_label = tk.Label(parent, text='在线: 0 台',
            font=('Microsoft YaHei', 9, 'bold'), fg=self.success_color, bg=self.card_bg)
        self.device_count_label.pack(anchor='w', pady=(0, 4))

        self.device_list_frame = tk.Frame(parent, bg=self.card_bg)
        self.device_list_frame.pack(fill='x')

        tk.Button(parent, text='🔄 刷新', command=self._refresh_devices,
                  font=('Microsoft YaHei', 8),
                  bg=self.card_bg, fg=self.accent_color,
                  activebackground='#2A2F4A', activeforeground=self.accent_color,
                  bd=1, relief='solid', highlightbackground='#2A2F4A',
                  cursor='hand2', padx=8, pady=2).pack(pady=(4, 0))

        self._refresh_devices()

    def _build_device_management(self, parent):
        self.device_mgr_frame = tk.Frame(parent, bg=self.card_bg)
        self.device_mgr_frame.pack(fill='x')

        self.offline_count_label = tk.Label(parent, text='离线: --',
            font=('Microsoft YaHei', 8), fg=self.text_color, bg=self.card_bg)
        self.offline_count_label.pack(anchor='w', pady=(0, 2))

        tk.Button(parent, text='📋 查看所有设备',
                  command=lambda: dialogs.show_all_devices(self.root, self.bg_color, self.text_color, self.accent_color, self.card_bg),
                  font=('Microsoft YaHei', 8),
                  bg=self.card_bg, fg=self.accent_color,
                  activebackground='#2A2F4A', activeforeground=self.accent_color,
                  bd=1, relief='solid', highlightbackground='#2A2F4A',
                  cursor='hand2', padx=8, pady=2).pack(pady=(2, 0))

        tk.Button(parent, text='🔗 查看连接记录',
                  command=lambda: dialogs.show_connection_logs(self.root, self.bg_color, self.text_color, self.accent_color, self.card_bg),
                  font=('Microsoft YaHei', 8),
                  bg=self.card_bg, fg=self.accent_color,
                  activebackground='#2A2F4A', activeforeground=self.accent_color,
                  bd=1, relief='solid', highlightbackground='#2A2F4A',
                  cursor='hand2', padx=8, pady=2).pack(pady=(2, 0))

    def _build_server_info(self, parent):
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
        keyword = self.search_var.get().strip()
        status = self.status_filter.get()
        if status == '全部':
            status = None

        try:
            params = []
            if keyword:
                params.append(f'q={urllib.parse.quote(keyword)}')
            if status:
                params.append(f'status={urllib.parse.quote(status)}')
            params.append('page_size=100')

            url = f'http://127.0.0.1:{SERVER_PORT}/api/records/search?{"&".join(params)}'
            resp = urllib.request.urlopen(url, timeout=3)
            data = json.loads(resp.read().decode())

            for item in self.record_tree.get_children():
                self.record_tree.delete(item)

            for r in data.get('records', []):
                self.record_tree.insert('', 'end', values=(
                    r.get('created_at', '')[:19],
                    r.get('barcode', ''),
                    r.get('status', ''),
                    r.get('user_name', ''),
                    r.get('address', '')[:20],
                    r.get('signer', '') or '',
                ))
        except Exception:
            pass

    def _on_record_double_click(self, event):
        selection = self.record_tree.selection()
        if not selection:
            return
        item = self.record_tree.item(selection[0])
        values = item['values']
        if len(values) < 2:
            return
        barcode = values[1]
        try:
            resp = urllib.request.urlopen(
                f'http://127.0.0.1:{SERVER_PORT}/api/records/search?q={urllib.parse.quote(barcode)}&page_size=1',
                timeout=3
            )
            data = json.loads(resp.read().decode())
            records = data.get('records', [])
            if records:
                dialogs.show_record_detail(self.root, records[0],
                    self.bg_color, self.text_color, self.accent_color, self.card_bg)
        except Exception:
            pass

    def _restart_server(self):
        self.log('🔄 正在重启服务器...')
        result = self.server.restart()
        if result:
            self.log('✅ 服务器重启成功')
        else:
            self.log('❌ 服务器重启失败')

    def _backup_db(self):
        try:
            resp = urllib.request.urlopen(
                f'http://127.0.0.1:{SERVER_PORT}/api/backup', timeout=10
            )
            self.log(f'💾 数据库备份完成')
            messagebox.showinfo('备份成功', '数据库备份已完成')
        except Exception as e:
            messagebox.showerror('备份失败', str(e))

    def _open_export(self):
        if not os.path.exists(EXPORT_DIR):
            os.makedirs(EXPORT_DIR)
        os.startfile(EXPORT_DIR)

    def _open_data_dir(self):
        os.startfile(BASE_DIR)

    def _refresh_devices(self):
        try:
            resp = urllib.request.urlopen(
                f'http://127.0.0.1:{SERVER_PORT}/api/devices/online?timeout=30', timeout=3
            )
            data = json.loads(resp.read().decode())
            devices = data.get('devices', [])
            online_count = data.get('online_count', len(devices))
            self.device_count_label.config(text=f'在线: {online_count} 台')

            for w in self.device_list_frame.winfo_children():
                w.destroy()

            for dev in devices:
                dev_name = dev.get('device_name', dev.get('device_id', '未知'))
                user = dev.get('user_name', '')
                ip = dev.get('ip_address', '')
                label_text = f'🟢 {dev_name}'
                if user:
                    label_text += f' ({user})'
                if ip:
                    label_text += f' [{ip}]'
                tk.Label(self.device_list_frame, text=label_text,
                    font=('Microsoft YaHei', 8), fg=self.success_color,
                    bg=self.card_bg, anchor='w').pack(fill='x', pady=1)

            # 检查离线设备
            try:
                resp2 = urllib.request.urlopen(
                    f'http://127.0.0.1:{SERVER_PORT}/api/devices/check-offline?timeout=60', timeout=3
                )
                offline_data = json.loads(resp2.read().decode())
                offline_count = offline_data.get('offline_count', 0)
                self.offline_count_label.config(text=f'离线: {offline_count} 台')
                self._last_offline_count = offline_count
            except Exception:
                pass

        except Exception:
            pass

    def _start_monitor(self):
        """启动定时刷新"""
        self._refresh_all()
        self._schedule_monitor()

    def _schedule_monitor(self):
        try:
            self.root.after(3000, self._monitor_tick)
        except Exception:
            pass

    def _monitor_tick(self):
        try:
            self._refresh_all()
        except Exception:
            pass
        self._schedule_monitor()

    def _refresh_all(self):
        self._refresh_records()
        self._refresh_stats()
        self._refresh_devices()
        self._update_server_info()

    def _refresh_records(self):
        try:
            resp = urllib.request.urlopen(
                f'http://127.0.0.1:{SERVER_PORT}/api/records?page_size=100', timeout=3
            )
            data = json.loads(resp.read().decode())
            records = data.get('records', [])

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

            for item in self.record_tree.get_children():
                self.record_tree.delete(item)

            for r in records:
                self.record_tree.insert('', 'end', values=(
                    r.get('created_at', '')[:19],
                    r.get('barcode', ''),
                    r.get('status', ''),
                    r.get('user_name', ''),
                    r.get('address', '')[:20],
                    r.get('signer', '') or '',
                ))

            self.record_count_label.config(text=f'共 {data.get("total", 0)} 条')

        except Exception:
            pass

    def _refresh_stats(self):
        try:
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

    def _update_server_info(self):
        try:
            db_path = os.path.join(BASE_DIR, 'database.sqlite')
            if os.path.exists(db_path):
                size = os.path.getsize(db_path)
                if size < 1024:
                    size_str = f'{size} B'
                elif size < 1024 * 1024:
                    size_str = f'{size / 1024:.1f} KB'
                else:
                    size_str = f'{size / 1024 / 1024:.1f} MB'
                if 'db_size_info' in self.info_labels:
                    self.info_labels['db_size_info'].config(text=size_str)

            ips = get_local_ips()
            ip = ips[0] if ips else 'localhost'
            self.url_label.config(text=f'🌐 http://{ip}:{SERVER_PORT}')
        except Exception:
            pass

    def log(self, message):
        try:
            timestamp = datetime.now().strftime('%H:%M:%S')
            self.log_text.insert('end', f'[{timestamp}] {message}\n')
            self.log_text.see('end')
        except Exception:
            pass

    def run(self):
        self.root.mainloop()


def run_gui():
    """启动 GUI"""
    root = tk.Tk()
    root.withdraw()

    # 延迟导入以避免循环导入问题并让 Pylance 能解析符号
    from .server import ServerManager
    from .tray import SystemTrayApp

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


