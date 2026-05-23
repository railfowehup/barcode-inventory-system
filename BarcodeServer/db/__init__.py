# -*- coding: utf-8 -*-
"""
📦 包裹管理系统 - 数据库层
SQLite 数据库操作，按功能拆分到各子模块
"""
import sqlite3
import os

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DB_PATH = os.path.join(BASE_DIR, 'database.sqlite')
EXPORT_DIR = os.path.join(BASE_DIR, 'exports')

THEME_COLORS = [
    '#2196F3', '#4CAF50', '#F44336', '#FF9800',
    '#9C27B0', '#00BCD4', '#795548', '#607D8B',
    '#E91E63', '#3F51B5',
]


class Database:
    def __init__(self, db_path=None):
        self.db_path = db_path or DB_PATH
        self.conn = sqlite3.connect(self.db_path, check_same_thread=False)
        self.conn.row_factory = sqlite3.Row
        self.conn.execute('PRAGMA journal_mode=WAL')
        self.conn.execute('PRAGMA foreign_keys=ON')
        self._init_tables()

    def _init_tables(self):
        self.conn.executescript('''
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                theme_color TEXT NOT NULL DEFAULT '#2196F3',
                role TEXT NOT NULL DEFAULT 'operator',
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            );
            CREATE TABLE IF NOT EXISTS records (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                barcode TEXT NOT NULL,
                user_id INTEGER NOT NULL,
                status TEXT NOT NULL DEFAULT '入库',
                address TEXT DEFAULT '',
                weight REAL DEFAULT 0,
                device_id TEXT DEFAULT '',
                note TEXT DEFAULT '',
                recipient TEXT DEFAULT '',
                logistics_no TEXT DEFAULT '',
                signer TEXT DEFAULT '',
                sign_time DATETIME,
                exception_type TEXT DEFAULT '',
                is_duplicate INTEGER DEFAULT 0,
                is_merged INTEGER DEFAULT 0,
                address_history TEXT DEFAULT '[]',
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                sort_at DATETIME,
                updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                version INTEGER DEFAULT 1,
                sync_status TEXT DEFAULT 'synced',
                FOREIGN KEY (user_id) REFERENCES users(id)
            );
            CREATE TABLE IF NOT EXISTS sync_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                device_id TEXT NOT NULL,
                user_name TEXT DEFAULT '',
                sync_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                added INTEGER DEFAULT 0,
                skipped INTEGER DEFAULT 0,
                duplicates INTEGER DEFAULT 0
            );
            CREATE TABLE IF NOT EXISTS device_heartbeats (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                device_id TEXT NOT NULL,
                device_name TEXT DEFAULT '',
                ip_address TEXT DEFAULT '',
                user_name TEXT DEFAULT '',
                device_group TEXT DEFAULT '',
                last_heartbeat DATETIME DEFAULT CURRENT_TIMESTAMP
            );
            CREATE TABLE IF NOT EXISTS connection_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                device_id TEXT NOT NULL,
                device_name TEXT DEFAULT '',
                user_name TEXT DEFAULT '',
                ip_address TEXT DEFAULT '',
                connected_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                operation_count INTEGER DEFAULT 0
            );
            CREATE INDEX IF NOT EXISTS idx_records_user ON records(user_id);
            CREATE INDEX IF NOT EXISTS idx_records_date ON records(created_at);
            CREATE INDEX IF NOT EXISTS idx_records_status ON records(status);
            CREATE INDEX IF NOT EXISTS idx_records_barcode ON records(barcode);
            CREATE INDEX IF NOT EXISTS idx_records_duplicate ON records(is_duplicate);
            CREATE INDEX IF NOT EXISTS idx_heartbeat_device ON device_heartbeats(device_id);
            CREATE INDEX IF NOT EXISTS idx_heartbeat_time ON device_heartbeats(last_heartbeat);
            CREATE INDEX IF NOT EXISTS idx_connection_device ON connection_logs(device_id);
            CREATE INDEX IF NOT EXISTS idx_records_updated ON records(updated_at);
        ''')
        self.conn.commit()

    def _rows_to_list(self, rows):
        return [dict(r) for r in rows]

    def get_next_color(self):
        count = self.conn.execute('SELECT COUNT(*) as cnt FROM users').fetchone()['cnt']
        return THEME_COLORS[count % len(THEME_COLORS)]

    def close(self):
        self.conn.close()
