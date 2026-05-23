# -*- coding: utf-8 -*-
"""UI 面板组件（搜索、设备、服务器信息等）"""
import os
import json
import urllib.request
import urllib.parse
import tkinter as tk
from tkinter import ttk

from . import api_get, SERVER_PORT, get_local_ips, BASE_DIR



def build_actions(parent, card_bg, text_color, accent_color, warning_color, success_color,
                  on_qr, on_restart, on_backup, on_export, on_data_dir):
    """快捷操作面板"""
    actions = [
        ('[QR] 显示二维码', on_qr, accent_color),
        ('[RST] 重启服务器', on_restart, warning_color),
        ('[BAK] 备份数据库', on_backup, success_color),
        ('[EXP] 打开导出目录', on_export, text_color),
        ('[DAT] 打开数据目录', on_data_dir, text_color),
    ]
    for text, cmd, color in actions:
        tk.Button(parent, text=text, command=cmd,
                  font=('Microsoft YaHei', 9),
                  bg=card_bg, fg=color,
                  activebackground='#2A2F4A', activeforeground=color,
                  bd=1, relief='solid', highlightbackground='#2A2F4A',
                  cursor='hand2', padx=8, pady=4
                  ).pack(fill='x', pady=2)


def build_search(parent, card_bg, text_color, accent_color, on_search):
    """搜索面板"""
    row = tk.Frame(parent, bg=card_bg)
    row.pack(fill='x', pady=2)

    search_var = tk.StringVar()
    search_entry = tk.Entry(row, textvariable=search_var,
                            font=('Microsoft YaHei', 9),
                            bg='#0D1130', fg=text_color,
                            insertbackground=accent_color,
                            bd=1, relief='solid', highlightbackground='#2A2F4A')
    search_entry.pack(side='left', fill='x', expand=True, padx=(0, 5))
    search_entry.bind('<Return>', lambda e: on_search(search_var.get()))

    tk.Button(row, text='搜索',
              command=lambda: on_search(search_var.get()),
              font=('Microsoft YaHei', 9),
              bg=accent_color, fg='#0A0E27',
              bd=0, padx=10, cursor='hand2').pack(side='right')

    # 状态筛选
    filter_row = tk.Frame(parent, bg=card_bg)
    filter_row.pack(fill='x', pady=2)

    status_filter = tk.StringVar(value='全部')
    for text in ['全部', '入库', '分拣', '出库', '签收', '异常']:
        tk.Radiobutton(filter_row, text=text, variable=status_filter,
                       value=text,
                       command=lambda: on_search(search_var.get(), status_filter.get()),
                       font=('Microsoft YaHei', 8),
                       fg=text_color, bg=card_bg,
                       selectcolor=card_bg,
                       activebackground=card_bg,
                       activeforeground=accent_color
                       ).pack(side='left', padx=2)

    return search_var, status_filter


def build_online_devices(parent, card_bg, text_color, success_color, accent_color,
                         on_refresh):
    """在线设备面板"""
    device_count_label = tk.Label(parent, text='在线: 0 台',
        font=('Microsoft YaHei', 9, 'bold'), fg=success_color, bg=card_bg)
    device_count_label.pack(anchor='w', pady=(0, 4))

    device_list_frame = tk.Frame(parent, bg=card_bg)
    device_list_frame.pack(fill='x')

    tk.Button(parent, text='[RST] 刷新', command=on_refresh,
              font=('Microsoft YaHei', 8),
              bg=card_bg, fg=accent_color,
              activebackground='#2A2F4A', activeforeground=accent_color,
              bd=1, relief='solid', highlightbackground='#2A2F4A',
              cursor='hand2', padx=8, pady=2).pack(pady=(4, 0))

    return device_count_label, device_list_frame


def build_device_management(parent, card_bg, text_color, accent_color,
                            on_show_devices, on_show_logs):
    """设备管理面板"""
    device_mgr_frame = tk.Frame(parent, bg=card_bg)
    device_mgr_frame.pack(fill='x')

    offline_count_label = tk.Label(parent, text='离线: --',
        font=('Microsoft YaHei', 8), fg=text_color, bg=card_bg)
    offline_count_label.pack(anchor='w', pady=(0, 2))

    tk.Button(parent, text='[DEV] 查看所有设备', command=on_show_devices,
              font=('Microsoft YaHei', 8),
              bg=card_bg, fg=accent_color,
              activebackground='#2A2F4A', activeforeground=accent_color,
              bd=1, relief='solid', highlightbackground='#2A2F4A',
              cursor='hand2', padx=8, pady=2).pack(pady=(2, 0))

    tk.Button(parent, text='[LOG] 查看连接记录', command=on_show_logs,
              font=('Microsoft YaHei', 8),
              bg=card_bg, fg=accent_color,
              activebackground='#2A2F4A', activeforeground=accent_color,
              bd=1, relief='solid', highlightbackground='#2A2F4A',
              cursor='hand2', padx=8, pady=2).pack(pady=(2, 0))

    return offline_count_label


def build_server_info(parent, card_bg, text_color, accent_color, port):
    """服务器信息面板"""
    info_labels = {}
    items = [
        ('status_info', '状态', '[ON] 运行中'),
        ('records', '总记录', '0'),
        ('today_info', '今日', '0'),
        ('db_size_info', '数据库', '计算中...'),
        ('port_info', '端口', str(port)),
    ]
    for key, label, default in items:
        row = tk.Frame(parent, bg=card_bg)
        row.pack(fill='x', pady=1)
        tk.Label(row, text=label, font=('Microsoft YaHei', 9),
                 fg=text_color, bg=card_bg,
                 width=8, anchor='w').pack(side='left')
        lbl = tk.Label(row, text=default, font=('Microsoft YaHei', 9, 'bold'),
                       fg=accent_color, bg=card_bg, anchor='w')
        lbl.pack(side='left', fill='x', expand=True)
        info_labels[key] = lbl
    return info_labels


def build_stats_cards(parent, card_bg, text_color, accent_color, success_color):
    """统计卡片行"""
    stat_cards = {}
    stat_items = [
        ('total', '[PKG] 总计', '0', accent_color),
        ('today', '[DAY] 今日', '0', success_color),
        ('in', '[IN] 入库', '0', '#2196F3'),
        ('sort', '[SORT] 分拣', '0', '#FF9800'),
        ('ship', '[SHIP] 出库', '0', '#9C27B0'),
        ('sign', '[SIGN] 签收', '0', '#4CAF50'),
    ]
    for key, label, default, color in stat_items:
        card = tk.Frame(parent, bg=card_bg, bd=0, highlightthickness=1,
                        highlightbackground='#2A2F4A')
        card.pack(side='left', fill='x', expand=True, padx=3)
        tk.Label(card, text=label, font=('Microsoft YaHei', 9),
                 fg=text_color, bg=card_bg).pack(pady=(8, 0))
        val_lbl = tk.Label(card, text=default, font=('Microsoft YaHei', 16, 'bold'),
                           fg=color, bg=card_bg)
        val_lbl.pack(pady=(0, 8))
        stat_cards[key] = val_lbl
    return stat_cards


def build_record_tree(parent, card_bg, text_color, accent_color):
    """记录列表 Treeview"""
    record_header = tk.Frame(parent, bg=card_bg)
    record_header.pack(fill='x', pady=(0, 5))

    tk.Label(record_header, text='[REC] 实时扫码记录', font=('Microsoft YaHei', 12, 'bold'),
             fg=accent_color, bg=card_bg).pack(side='left', padx=12, pady=8)

    record_count_label = tk.Label(record_header, text='共 0 条',
        font=('Microsoft YaHei', 9), fg=text_color, bg=card_bg)
    record_count_label.pack(side='right', padx=12, pady=8)

    columns = ('time', 'barcode', 'status', 'user', 'address', 'signer')
    tree = ttk.Treeview(parent, columns=columns, show='headings', height=14)
    tree.heading('time', text='时间')
    tree.heading('barcode', text='条码')
    tree.heading('status', text='状态')
    tree.heading('user', text='操作人')
    tree.heading('address', text='目的地')
    tree.heading('signer', text='签收人')

    tree.column('time', width=140, minwidth=120)
    tree.column('barcode', width=180, minwidth=150)
    tree.column('status', width=60, minwidth=50)
    tree.column('user', width=80, minwidth=60)
    tree.column('address', width=150, minwidth=100)
    tree.column('signer', width=80, minwidth=60)

    style = ttk.Style()
    style.theme_use('clam')
    style.configure('Treeview', background='#0D1130', foreground='#E0E0E0',
                    fieldbackground='#0D1130', borderwidth=0, font=('Microsoft YaHei', 9))
    style.configure('Treeview.Heading', background='#1A1F3A', foreground='#00D4FF',
                    borderwidth=1, font=('Microsoft YaHei', 9, 'bold'))
    style.map('Treeview', background=[('selected', '#2A2F4A')])

    vsb = ttk.Scrollbar(parent, orient='vertical', command=tree.yview)
    tree.configure(yscrollcommand=vsb.set)
    vsb.pack(side='right', fill='y')
    tree.pack(fill='both', expand=True)

    return tree, record_count_label


def refresh_device_list(device_list_frame, card_bg, success_color):
    """刷新在线设备列表"""
    data, err = api_get('/api/devices/online?timeout=30', timeout=3)
    if err or not data:
        return 0
    devices = data.get('devices', [])
    online_count = data.get('online_count', len(devices))

    for w in device_list_frame.winfo_children():
        w.destroy()

    for dev in devices:
        dev_name = dev.get('device_name', dev.get('device_id', '未知'))
        user = dev.get('user_name', '')
        ip = dev.get('ip_address', '')
        label_text = f'[ON] {dev_name}'
        if user:
            label_text += f' ({user})'
        if ip:
            label_text += f' [{ip}]'
        tk.Label(device_list_frame, text=label_text,
            font=('Microsoft YaHei', 8), fg=success_color,
            bg=card_bg, anchor='w').pack(fill='x', pady=1)

    return online_count



def update_db_size(info_labels, base_dir):
    """更新数据库大小显示"""
    db_path = os.path.join(base_dir, 'database.sqlite')
    if os.path.exists(db_path):
        size = os.path.getsize(db_path)
        if size < 1024:
            size_str = f'{size} B'
        elif size < 1024 * 1024:
            size_str = f'{size / 1024:.1f} KB'
        else:
            size_str = f'{size / 1024 / 1024:.1f} MB'
        if 'db_size_info' in info_labels:
            info_labels['db_size_info'].config(text=size_str)
