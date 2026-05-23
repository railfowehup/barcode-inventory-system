# -*- coding: utf-8 -*-
"""弹窗（二维码、记录详情、设备列表、连接记录）"""
import json
import urllib.request
import urllib.parse
import tkinter as tk
from tkinter import ttk, messagebox
from PIL import Image, ImageTk

from . import api_get, SERVER_PORT, get_local_ips


def show_qr_code(parent, bg_color, card_bg, text_color, accent_color):
    """显示二维码弹窗"""
    data, err = api_get('/api/qrcode', timeout=5)
    if err or not data:
        messagebox.showerror('错误', f'获取二维码失败: {err}')
        return

    qr_data_url = data.get('qrcode', '')
    server_url = data.get('url', '')
    if not qr_data_url:
        messagebox.showerror('错误', '二维码数据为空')
        return

    import base64
    from io import BytesIO
    img_data = qr_data_url.split(',')[1]
    img_bytes = base64.b64decode(img_data)
    img = Image.open(BytesIO(img_bytes))
    img = img.resize((300, 300), Image.LANCZOS)
    photo = ImageTk.PhotoImage(img)

    qr_win = tk.Toplevel(parent)
    qr_win.title('[QR] 手机扫码连接')
    qr_win.configure(bg=bg_color)
    qr_win.resizable(False, False)

    tk.Label(qr_win, image=photo, bg=bg_color).pack(padx=30, pady=(30, 10))
    tk.Label(qr_win, text='请用手机扫描二维码连接', font=('Microsoft YaHei', 12),
             fg=accent_color, bg=bg_color).pack(pady=(5, 5))
    tk.Label(qr_win, text=f'服务器地址: {server_url}', font=('Microsoft YaHei', 10),
             fg=text_color, bg=bg_color).pack(pady=(0, 15))

    tk.Button(qr_win, text='关闭', command=qr_win.destroy,
              font=('Microsoft YaHei', 10),
              bg=card_bg, fg=text_color,
              bd=1, relief='solid', padx=20, pady=5
              ).pack(pady=(0, 20))

    qr_win.update_idletasks()
    x = (qr_win.winfo_screenwidth() - qr_win.winfo_reqwidth()) // 2
    y = (qr_win.winfo_screenheight() - qr_win.winfo_reqheight()) // 2
    qr_win.geometry(f'+{x}+{y}')
    qr_win.photo = photo


def show_record_detail(parent, record, bg_color, text_color, accent_color, card_bg):
    """显示记录详情弹窗"""
    detail_win = tk.Toplevel(parent)
    detail_win.title(f'[PKG] 包裹详情 - {record.get("barcode", "")}')
    detail_win.configure(bg=bg_color)
    detail_win.geometry('450x400')
    detail_win.resizable(False, False)

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
        row = tk.Frame(detail_win, bg=bg_color)
        row.pack(fill='x', padx=20, pady=2)
        tk.Label(row, text=label, font=('Microsoft YaHei', 9),
                 fg=text_color, bg=bg_color,
                 width=10, anchor='w').pack(side='left')
        tk.Label(row, text=value, font=('Microsoft YaHei', 9, 'bold'),
                 fg=accent_color, bg=bg_color,
                 anchor='w').pack(side='left', fill='x', expand=True)

    tk.Button(detail_win, text='关闭', command=detail_win.destroy,
              font=('Microsoft YaHei', 10),
              bg=card_bg, fg=text_color,
              bd=1, relief='solid', padx=20, pady=5
              ).pack(pady=15)


def show_all_devices(parent, bg_color, text_color, accent_color, card_bg):
    """显示所有设备列表弹窗"""
    data, err = api_get('/api/devices/history?limit=100', timeout=3)
    if err or not data:
        messagebox.showerror('错误', f'获取设备列表失败: {err}')
        return
    devices = data.get('devices', [])

    win = tk.Toplevel(parent)
    win.title('[DEV] 所有设备')
    win.configure(bg=bg_color)
    win.geometry('600x400')

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


def show_connection_logs(parent, bg_color, text_color, accent_color, card_bg):
    """显示连接记录弹窗"""
    data, err = api_get('/api/sync-logs?limit=100', timeout=3)
    if err or not data:
        messagebox.showerror('错误', f'获取连接记录失败: {err}')
        return
    logs = data.get('sync_logs', [])

    win = tk.Toplevel(parent)
    win.title('[LOG] 同步/连接记录')
    win.configure(bg=bg_color)
    win.geometry('600x400')

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
