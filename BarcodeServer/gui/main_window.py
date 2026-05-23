# -*- coding: utf-8 -*-
"""主窗口 - 管理控制台"""
import os
import json
import urllib.request
import urllib.parse
from datetime import datetime

import tkinter as tk
from tkinter import ttk, messagebox, scrolledtext

from . import api_get, SERVER_PORT, get_local_ips, play_sound, show_notification, BASE_DIR, EXPORT_DIR
from . import dialogs
from . import panels



class MainWindow:
    def __init__(self, root, server_mgr):
        self.root = root
        self.server = server_mgr
        self.root.title('包裹管理系统 - 管理控制台')
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

        tk.Label(title_frame, text='[包裹管理系统]',
                 font=('Microsoft YaHei', 18, 'bold'),
                 fg=self.accent_color, bg=self.bg_color).pack(side='left')

        self.status_label = tk.Label(title_frame, text='[运行中]',
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
        self.stat_cards = panels.build_stats_cards(
            stats_frame, self.card_bg, self.text_color,
            self.accent_color, self.success_color
        )

        # ===== 中间区域 =====
        middle_frame = tk.Frame(main_frame, bg=self.bg_color)
        middle_frame.pack(fill='both', expand=True)

        # ===== 左侧：实时扫码记录 =====
        left_frame = tk.Frame(middle_frame, bg=self.bg_color)
        left_frame.pack(side='left', fill='both', expand=True, padx=(0, 8))

        self.record_tree, self.record_count_label = panels.build_record_tree(
            left_frame, self.card_bg, self.text_color, self.accent_color
        )
        self.record_tree.bind('<Double-1>', self._on_record_double_click)

        # ===== 右侧：操作面板 =====
        right_frame = tk.Frame(middle_frame, bg=self.bg_color)
        right_frame.pack(side='right', fill='y', padx=(8, 0))

        self._create_section(right_frame, '[快捷操作]', self._build_actions)
        self._create_section(right_frame, '[搜索记录]', self._build_search)
        self._create_section(right_frame, '[在线设备]', self._build_online_devices)
        self._create_section(right_frame, '[设备管理]', self._build_device_management)
        self._create_section(right_frame, '[服务器信息]', self._build_server_info)

        # ===== 底部日志 =====
        log_frame = tk.Frame(main_frame, bg=self.bg_color)
        log_frame.pack(fill='x', pady=(8, 0))

        tk.Label(log_frame, text='[运行日志]', font=('Microsoft YaHei', 10, 'bold'),
                 fg=self.accent_color, bg=self.bg_color).pack(anchor='w')

        self.log_text = scrolledtext.ScrolledText(
            log_frame, font=('Consolas', 9),
            bg='#0D1130', fg='#00D4FF',
            insertbackground=self.accent_color,
            bd=0, highlightthickness=0, height=5
        )
        self.log_text.pack(fill='x', pady=(3, 0))
        self.log_text.insert('end', '包裹管理系统已启动\n')
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
        panels.build_actions(parent, self.card_bg, self.text_color,
            self.accent_color, self.warning_color, self.success_color,
            on_qr=lambda: dialogs.show_qr_code(self.root, self.bg_color, self.card_bg,
                                                self.text_color, self.accent_color),
            on_restart=self._restart_server,
            on_backup=self._backup_db,
            on_export=self._open_export,
            on_data_dir=self._open_data_dir)

    def _build_search(self, parent):
        self.search_var, self.status_filter = panels.build_search(
            parent, self.card_bg, self.text_color, self.accent_color,
            on_search=self._do_search
        )

    def _build_online_devices(self, parent):
        self.device_count_label, self.device_list_frame = panels.build_online_devices(
            parent, self.card_bg, self.text_color, self.success_color, self.accent_color,
            on_refresh=self._refresh_devices
        )
        self._refresh_devices()

    def _build_device_management(self, parent):
        self.offline_count_label = panels.build_device_management(
            parent, self.card_bg, self.text_color, self.accent_color,
            on_show_devices=lambda: dialogs.show_all_devices(self.root, self.bg_color,
                                                              self.text_color, self.accent_color,
                                                              self.card_bg),
            on_show_logs=lambda: dialogs.show_connection_logs(self.root, self.bg_color,
                                                               self.text_color, self.accent_color,
                                                               self.card_bg)
        )

    def _build_server_info(self, parent):
        self.info_labels = panels.build_server_info(
            parent, self.card_bg, self.text_color, self.accent_color, SERVER_PORT
        )

    def _do_search(self, keyword=None, status=None):
        if keyword is None:
            keyword = self.search_var.get().strip()
        if status is None:
            status = self.status_filter.get()
        if status == '全部':
            status = None

        params = []
        if keyword:
            params.append(f'q={urllib.parse.quote(keyword)}')
        if status:
            params.append(f'status={urllib.parse.quote(status)}')
        params.append('page_size=100')

        path = f'/api/records/search?{"&".join(params)}'
        data, err = api_get(path, timeout=3)
        if err or not data:
            return

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


    def _on_record_double_click(self, event):
        selection = self.record_tree.selection()
        if not selection:
            return
        item = self.record_tree.item(selection[0])
        values = item['values']
        if len(values) < 2:
            return
        barcode = values[1]
        data, err = api_get(
            f'/api/records/search?q={urllib.parse.quote(barcode)}&page_size=1', timeout=3
        )
        if err or not data:
            return
        records = data.get('records', [])
        if records:
            dialogs.show_record_detail(self.root, records[0],
                self.bg_color, self.text_color, self.accent_color, self.card_bg)


    def _restart_server(self):
        self.log('正在重启服务器...')
        result = self.server.restart()
        self.log('服务器重启成功' if result else '服务器重启失败')

    def _backup_db(self):
        """备份数据库 - 下载备份文件到导出目录"""
        try:
            import urllib.request
            from . import SERVER_PORT
            url = f'http://127.0.0.1:{SERVER_PORT}/api/backup'
            resp = urllib.request.urlopen(url, timeout=10)
            # 获取文件名
            content_disposition = resp.headers.get('Content-Disposition', '')
            file_name = 'backup.json'
            if 'filename=' in content_disposition:
                file_name = content_disposition.split('filename=')[-1].strip('"\'')
            file_path = os.path.join(EXPORT_DIR, file_name)
            os.makedirs(EXPORT_DIR, exist_ok=True)
            with open(file_path, 'wb') as f:
                f.write(resp.read())
            self.log(f'数据库备份完成: {file_name}')
            messagebox.showinfo('备份成功', f'备份文件已保存到:\n{file_path}')
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
            online_count = panels.refresh_device_list(
                self.device_list_frame, self.card_bg, self.success_color
            )
            self.device_count_label.config(text=f'在线: {online_count} 台')

            # 检查离线设备
            offline_data, err = api_get('/api/devices/check-offline?timeout=60', timeout=3)
            if not err and offline_data:
                offline_count = offline_data.get('offline_count', 0)
                self.offline_count_label.config(text=f'离线: {offline_count} 台')
                self._last_offline_count = offline_count
        except Exception:
            pass


    def _start_monitor(self):
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
        # 如果用户正在搜索/筛选，使用搜索参数刷新，保持筛选状态
        keyword = self.search_var.get().strip() if hasattr(self, 'search_var') else ''
        status = self.status_filter.get() if hasattr(self, 'status_filter') else '全部'
        if status == '全部':
            status = None

        if keyword or status:
            params = []
            if keyword:
                params.append(f'q={urllib.parse.quote(keyword)}')
            if status:
                params.append(f'status={urllib.parse.quote(status)}')
            params.append('page_size=100')
            path = f'/api/records/search?{"&".join(params)}'
        else:
            path = '/api/records?page_size=100'

        data, err = api_get(path, timeout=3)
        if err or not data:
            return
        records = data.get('records', [])

        if records:
            latest_time = records[0].get('created_at', '')
            if latest_time != self._last_scan_time and self._last_scan_time:
                barcode = records[0].get('barcode', '')
                user = records[0].get('user_name', '')
                status = records[0].get('status', '')
                self.log(f'{user} {status}: {barcode}')
                play_sound()
                show_notification(f'{user} {status}', f'条码: {barcode}')
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

    def _refresh_stats(self):
        stats, err = api_get('/api/stats', timeout=2)
        if err or not stats:
            return
        self.stat_cards['total'].config(text=str(stats.get('total', 0)))
        self.stat_cards['today'].config(text=str(stats.get('today', 0)))
        self.stat_cards['in'].config(text=str(stats.get('in_count', 0)))
        self.stat_cards['sort'].config(text=str(stats.get('sort_count', 0)))
        self.stat_cards['ship'].config(text=str(stats.get('ship_count', 0)))
        self.stat_cards['sign'].config(text=str(stats.get('sign_count', 0)))
        if 'status_info' in self.info_labels:
            self.info_labels['status_info'].config(
                text=f'运行中 | 总计 {stats.get("total", 0)} 件'
            )
        if 'records' in self.info_labels:
            self.info_labels['records'].config(text=str(stats.get('total', 0)))
        if 'today_info' in self.info_labels:
            self.info_labels['today_info'].config(text=str(stats.get('today', 0)))


    def _update_server_info(self):
        try:
            panels.update_db_size(self.info_labels, BASE_DIR)
            ips = get_local_ips()
            ip = ips[0] if ips else 'localhost'
            self.url_label.config(text=f'http://{ip}:{SERVER_PORT}')
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

    from .server import ServerManager
    from .tray import SystemTrayApp

    server_mgr = ServerManager()

    print("正在启动服务器...")
    if server_mgr.start():
        print("服务器启动成功")
        ips = get_local_ips()
        ip = ips[0] if ips else 'localhost'
        print(f"API: http://{ip}:{SERVER_PORT}")
    else:
        print("服务器启动失败")

    main_win = MainWindow(root, server_mgr)

    tray = SystemTrayApp(root, server_mgr)

    tray.run()

    root.deiconify()
    main_win.run()


if __name__ == '__main__':
    run_gui()
