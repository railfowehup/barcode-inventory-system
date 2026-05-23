# -*- coding: utf-8 -*-
"""系统监控数据获取与自启动管理"""

import os
import time
import psutil

try:
    import winreg
except ImportError:
    winreg = None

AUTOSTART_NAME = "SysMonitor"


class SystemMonitor:
    def __init__(self):
        self._net_prev = psutil.net_io_counters()
        self._net_time = time.time()

    def get_cpu(self):
        return psutil.cpu_percent(interval=0.1)


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
    import sys
    run_key = r"Software\Microsoft\Windows\CurrentVersion\Run"
    app_path = os.path.abspath(sys.argv[0])
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
