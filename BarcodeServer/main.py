# -*- coding: utf-8 -*-
"""
📦 包裹管理系统 - 主入口
支持两种启动模式：
  1. python main.py          → 启动桌面 GUI（管理控制台）
  2. python main.py --server → 仅启动 API 服务器（无 GUI）
"""
import sys
import os

# 确保项目根目录在 sys.path 中
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
if BASE_DIR not in sys.path:
    sys.path.insert(0, BASE_DIR)


def run_server_only():
    """仅启动 API 服务器"""
    from api import start_server
    print("=" * 50)
    print("  📦 包裹管理系统 - API 服务器")
    print("=" * 50)
    start_server()


def run_gui():
    """启动桌面 GUI"""
    from gui.main_window import run_gui
    run_gui()


if __name__ == '__main__':
    if '--server' in sys.argv:
        run_server_only()
    else:
        run_gui()
