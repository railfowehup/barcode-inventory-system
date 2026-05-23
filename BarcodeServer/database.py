# -*- coding: utf-8 -*-
"""
📦 包裹管理系统 - 数据库层
SQLite 数据库操作，支持所有业务功能
"""

import sqlite3
import os
import json
from datetime import datetime

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
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
        """初始化数据库表"""
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
        """将 sqlite3.Row 列表转为 dict 列表"""
        return [dict(r) for r in rows]

    def get_next_color(self):
        count = self.conn.execute('SELECT COUNT(*) as cnt FROM users').fetchone()['cnt']
        return THEME_COLORS[count % len(THEME_COLORS)]

    # ==================== 用户 ====================

    def login_user(self, name):
        user = self.conn.execute('SELECT * FROM users WHERE name = ?', (name,)).fetchone()
        if not user:
            color = self.get_next_color()
            self.conn.execute(
                'INSERT INTO users (name, theme_color, role) VALUES (?, ?, ?)',
                (name, color, 'operator')
            )
            self.conn.commit()
            user = self.conn.execute('SELECT * FROM users WHERE name = ?', (name,)).fetchone()
        return dict(user) if user else {'error': '创建用户失败'}

    def get_user(self, user_id):
        row = self.conn.execute('SELECT * FROM users WHERE id = ?', (user_id,)).fetchone()
        return dict(row) if row else None

    def get_all_users(self):
        rows = self.conn.execute(
            'SELECT id, name, theme_color, role, created_at FROM users ORDER BY id'
        ).fetchall()
        return self._rows_to_list(rows)

    # ==================== 记录 ====================

    def find_record_by_barcode(self, barcode):
        row = self.conn.execute('SELECT * FROM records WHERE barcode = ?', (barcode,)).fetchone()
        return dict(row) if row else None

    def find_record_by_id(self, record_id):
        row = self.conn.execute('SELECT * FROM records WHERE id = ?', (record_id,)).fetchone()
        return dict(row) if row else None

    def add_record(self, barcode, user_id, address='', weight=0, note='', status='入库', created_at=None):
        self.conn.execute(
            '''INSERT INTO records (barcode, user_id, status, address, weight, note, created_at, updated_at, version)
               VALUES (?, ?, ?, ?, ?, ?, COALESCE(?, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, 1)''',
            (barcode, user_id, status, address, weight, note, created_at)
        )
        self.conn.commit()
        return self.find_record_by_barcode(barcode)

    def sort_record(self, barcode, device_id=''):
        now = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        self.conn.execute(
            'UPDATE records SET status=?, device_id=?, sort_at=?, updated_at=?, version=version+1 WHERE barcode=?',
            ('分拣', device_id, now, now, barcode)
        )
        self.conn.commit()
        return self.find_record_by_barcode(barcode)

    def ship_record(self, barcode, logistics_no='', recipient=''):
        now = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        self.conn.execute(
            'UPDATE records SET status=?, logistics_no=?, recipient=?, sort_at=?, updated_at=?, version=version+1 WHERE barcode=?',
            ('出库', logistics_no, recipient, now, now, barcode)
        )
        self.conn.commit()
        return self.find_record_by_barcode(barcode)

    def sign_record(self, barcode, signer='', exception_type=''):
        now = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        status = '异常' if exception_type else '签收'
        self.conn.execute(
            'UPDATE records SET status=?, signer=?, sign_time=?, exception_type=?, updated_at=?, version=version+1 WHERE barcode=?',
            (status, signer, now, exception_type, now, barcode)
        )
        self.conn.commit()
        return self.find_record_by_barcode(barcode)

    def change_address(self, record_id, address, user_name=''):
        record = self.find_record_by_id(record_id)
        if not record:
            return {'error': '记录不存在'}

        history = []
        try:
            history = json.loads(record.get('address_history', '[]'))
        except:
            pass

        history.append({
            'from': record.get('address', ''),
            'to': address,
            'changed_by': user_name,
            'time': datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        })

        now = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        self.conn.execute(
            'UPDATE records SET address=?, address_history=?, updated_at=?, version=version+1 WHERE id=?',
            (address, json.dumps(history, ensure_ascii=False), now, record_id)
        )
        self.conn.commit()
        return self.find_record_by_id(record_id)

    def update_record(self, record_id, address=None, weight=None, note=None):
        updates = []
        params = []
        if address is not None:
            updates.append('address=?')
            params.append(address)
        if weight is not None:
            updates.append('weight=?')
            params.append(weight)
        if note is not None:
            updates.append('note=?')
            params.append(note)
        if updates:
            now = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
            updates.append('updated_at=?')
            params.append(now)
            updates.append('version=version+1')
            params.append(record_id)
            self.conn.execute(
                f'UPDATE records SET {", ".join(updates)} WHERE id=?', params
            )
            self.conn.commit()
        return self.find_record_by_id(record_id)

    def delete_record(self, record_id):
        self.conn.execute('DELETE FROM records WHERE id=?', (record_id,))
        self.conn.commit()
        return True

    def get_records(self, user_id=None, page=1, page_size=50, status=None, date_from=None, date_to=None):
        where = []
        params = []

        if user_id:
            where.append('r.user_id=?')
            params.append(user_id)
        if status:
            where.append('r.status=?')
            params.append(status)
        if date_from:
            where.append('r.created_at>=?')
            params.append(date_from)
        if date_to:
            where.append('r.created_at<=?')
            params.append(date_to + ' 23:59:59')

        where_clause = 'WHERE ' + ' AND '.join(where) if where else ''
        offset = (page - 1) * page_size

        total = self.conn.execute(
            f'SELECT COUNT(*) as cnt FROM records r {where_clause}', params
        ).fetchone()['cnt']

        query_params = params + [page_size, offset]
        rows = self.conn.execute(f'''
            SELECT r.*, u.name as user_name, u.theme_color
            FROM records r
            JOIN users u ON r.user_id = u.id
            {where_clause}
            ORDER BY r.created_at DESC
            LIMIT ? OFFSET ?
        ''', query_params).fetchall()

        return {
            'total': total,
            'page': page,
            'page_size': page_size,
            'records': self._rows_to_list(rows)
        }

    def search_records(self, query=None, status=None, address=None, date_from=None, date_to=None, page=1, page_size=50):
        where = []
        params = []

        if query:
            where.append('(r.barcode LIKE ? OR r.note LIKE ?)')
            params.append(f'%{query}%')
            params.append(f'%{query}%')
        if status:
            where.append('r.status=?')
            params.append(status)
        if address:
            where.append('r.address=?')
            params.append(address)
        if date_from:
            where.append('r.created_at>=?')
            params.append(date_from)
        if date_to:
            where.append('r.created_at<=?')
            params.append(date_to + ' 23:59:59')

        where_clause = 'WHERE ' + ' AND '.join(where) if where else ''
        offset = (page - 1) * page_size

        total = self.conn.execute(
            f'SELECT COUNT(*) as cnt FROM records r {where_clause}', params
        ).fetchone()['cnt']

        query_params = params + [page_size, offset]
        rows = self.conn.execute(f'''
            SELECT r.*, u.name as user_name, u.theme_color
            FROM records r
            JOIN users u ON r.user_id = u.id
            {where_clause}
            ORDER BY r.created_at DESC
            LIMIT ? OFFSET ?
        ''', query_params).fetchall()

        return {
            'total': total,
            'page': page,
            'page_size': page_size,
            'records': self._rows_to_list(rows)
        }

    def get_stats(self, user_id=None):
        where = 'WHERE user_id=?' if user_id else ''
        params = [user_id] if user_id else []

        def count(sql, extra=''):
            full_sql = f'SELECT COUNT(*) as cnt FROM records {where}'
            if extra:
                if where:
                    full_sql += f' {extra}'
                else:
                    full_sql += f' WHERE {extra.lstrip("AND ").lstrip("AND")}'
            return self.conn.execute(full_sql, params).fetchone()['cnt']

        total = count('')
        today = count('', "AND date(created_at)=date('now','localtime')")
        this_week = count('', "AND created_at>=datetime('now','localtime','weekday 0','-7 days')")
        this_month = count('', "AND strftime('%Y-%m',created_at)=strftime('%Y-%m','now','localtime')")

        in_count = count('', "AND status='入库'")
        sort_count = count('', "AND status='分拣'")
        ship_count = count('', "AND status='出库'")
        sign_count = count('', "AND status='签收'")
        duplicate_count = count('', "AND is_duplicate=1")

        # 每日统计
        daily_where = f'WHERE user_id=?' if user_id else ''
        daily_params = [user_id] if user_id else []
        daily_rows = self.conn.execute(f'''
            SELECT date(created_at) as date, COUNT(*) as count
            FROM records {daily_where}
            WHERE created_at>=datetime('now','localtime','-7 days')
            GROUP BY date(created_at) ORDER BY date DESC
        ''', daily_params).fetchall()

        # 地址分布
        address_where = f'WHERE user_id=?' if user_id else ''
        address_params = [user_id] if user_id else []
        address_rows = self.conn.execute(f'''
            SELECT address, COUNT(*) as count
            FROM records {address_where}
            AND address!=''
            GROUP BY address ORDER BY count DESC
        ''', address_params).fetchall()

        # 每人工作量
        user_where = f'WHERE r.user_id=?' if user_id else ''
        user_params = [user_id] if user_id else []
        user_rows = self.conn.execute(f'''
            SELECT u.name, u.theme_color, COUNT(*) as count
            FROM records r JOIN users u ON r.user_id=u.id
            {user_where}
            GROUP BY r.user_id ORDER BY count DESC
        ''', user_params).fetchall()

        return {
            'total': total, 'today': today,
            'this_week': this_week, 'this_month': this_month,
            'in_count': in_count, 'sort_count': sort_count,
            'ship_count': ship_count, 'sign_count': sign_count,
            'duplicate_count': duplicate_count,
            'daily_stats': self._rows_to_list(daily_rows),
            'address_stats': self._rows_to_list(address_rows),
            'user_stats': self._rows_to_list(user_rows),
        }

    # ==================== 设备心跳 ====================

    def record_heartbeat(self, device_id, device_name='', ip_address='', user_name=''):
        """记录设备心跳"""
        existing = self.conn.execute(
            'SELECT * FROM device_heartbeats WHERE device_id=?', (device_id,)
        ).fetchone()
        now = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        if existing:
            self.conn.execute(
                '''UPDATE device_heartbeats SET device_name=?, ip_address=?, user_name=?, last_heartbeat=?
                   WHERE device_id=?''',
                (device_name, ip_address, user_name, now, device_id)
            )
        else:
            self.conn.execute(
                '''INSERT INTO device_heartbeats (device_id, device_name, ip_address, user_name, last_heartbeat)
                   VALUES (?, ?, ?, ?, ?)''',
                (device_id, device_name, ip_address, user_name, now)
            )
        self.conn.commit()

    def update_device_group(self, device_id, group):
        """更新设备分组"""
        self.conn.execute(
            'UPDATE device_heartbeats SET device_group=? WHERE device_id=?',
            (group, device_id)
        )
        self.conn.commit()

    def get_online_devices(self, timeout_seconds=30):
        """获取在线设备（timeout_seconds 内心跳过的）"""
        rows = self.conn.execute('''
            SELECT * FROM device_heartbeats
            WHERE last_heartbeat >= datetime('now', 'localtime', ?)
            ORDER BY last_heartbeat DESC
        ''', (f'-{timeout_seconds} seconds',)).fetchall()
        return self._rows_to_list(rows)

    def get_all_devices(self, limit=50):
        """获取所有设备记录"""
        rows = self.conn.execute('''
            SELECT * FROM device_heartbeats
            ORDER BY last_heartbeat DESC LIMIT ?
        ''', (limit,)).fetchall()
        return self._rows_to_list(rows)

    # ==================== 连接记录 ====================

    def log_connection(self, device_id, device_name='', user_name='', ip_address=''):
        """记录设备连接"""
        self.conn.execute(
            '''INSERT INTO connection_logs (device_id, device_name, user_name, ip_address)
               VALUES (?, ?, ?, ?)''',
            (device_id, device_name, user_name, ip_address)
        )
        self.conn.commit()

    def get_connection_logs(self, limit=50):
        """获取连接记录"""
        rows = self.conn.execute(
            'SELECT * FROM connection_logs ORDER BY connected_at DESC LIMIT ?', (limit,)
        ).fetchall()
        return self._rows_to_list(rows)

    def get_connection_count(self, device_id=None):
        """获取连接次数"""
        if device_id:
            row = self.conn.execute(
                'SELECT COUNT(*) as cnt FROM connection_logs WHERE device_id=?', (device_id,)
            ).fetchone()
        else:
            row = self.conn.execute('SELECT COUNT(*) as cnt FROM connection_logs').fetchone()
        return row['cnt'] if row else 0

    # ==================== 同步/合并 ====================

    def merge_data(self, users_data, records_data, device_id='', user_name=''):
        users_created = 0
        users_skipped = 0
        records_created = 0
        records_skipped = 0
        duplicates = 0

        if users_data:
            for u in users_data:
                existing = self.conn.execute(
                    'SELECT * FROM users WHERE name=?', (u.get('name', ''),)
                ).fetchone()
                if not existing:
                    color = self.get_next_color()
                    self.conn.execute(
                        'INSERT INTO users (name, theme_color, role) VALUES (?, ?, ?)',
                        (u.get('name', ''), color, u.get('role', 'operator'))
                    )
                    users_created += 1
                else:
                    users_skipped += 1

        for r in records_data:
            existing = self.conn.execute(
                'SELECT * FROM records WHERE barcode=?', (r.get('barcode', ''),)
            ).fetchone()
            if existing:
                self.conn.execute(
                    'UPDATE records SET is_duplicate=1 WHERE id=?', (existing['id'],)
                )
                self.conn.execute(
                    '''INSERT INTO records (barcode, user_id, status, address, weight, device_id, note, is_duplicate, created_at)
                       VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?)''',
                    (r.get('barcode', ''), r.get('user_id', 1), r.get('status', '入库'),
                     r.get('address', ''), r.get('weight', 0), r.get('device_id', ''),
                     r.get('note', ''), r.get('created_at'))
                )
                duplicates += 1
            else:
                self.conn.execute(
                    '''INSERT INTO records (barcode, user_id, status, address, weight, device_id, note, created_at)
                       VALUES (?, ?, ?, ?, ?, ?, ?, ?)''',
                    (r.get('barcode', ''), r.get('user_id', 1), r.get('status', '入库'),
                     r.get('address', ''), r.get('weight', 0), r.get('device_id', ''),
                     r.get('note', ''), r.get('created_at'))
                )
                records_created += 1

        self.conn.commit()

        self.conn.execute(
            'INSERT INTO sync_logs (device_id, user_name, added, skipped, duplicates) VALUES (?, ?, ?, ?, ?)',
            (device_id, user_name, records_created, records_skipped, duplicates)
        )
        self.conn.commit()

        return {
            'users_created': users_created, 'users_skipped': users_skipped,
            'records_created': records_created, 'records_skipped': records_skipped,
            'duplicates': duplicates,
        }

    def pull_data(self, since=None):
        users = self.get_all_users()
        if since:
            rows = self.conn.execute('''
                SELECT r.*, u.name as user_name
                FROM records r JOIN users u ON r.user_id=u.id
                WHERE r.updated_at>=? OR r.created_at>=?
                ORDER BY r.updated_at DESC
            ''', (since, since)).fetchall()
        else:
            rows = self.conn.execute('''
                SELECT r.*, u.name as user_name
                FROM records r JOIN users u ON r.user_id=u.id
                ORDER BY r.updated_at DESC
            ''').fetchall()
        return users, self._rows_to_list(rows)

    def get_sync_logs(self, limit=100):
        rows = self.conn.execute(
            'SELECT * FROM sync_logs ORDER BY sync_time DESC LIMIT ?', (limit,)
        ).fetchall()
        return self._rows_to_list(rows)

    # ==================== 备份/恢复 ====================

    def backup_data(self):
        users = self._rows_to_list(self.conn.execute('SELECT * FROM users').fetchall())
        records = self._rows_to_list(
            self.conn.execute('SELECT * FROM records ORDER BY created_at DESC').fetchall()
        )
        sync_logs = self._rows_to_list(
            self.conn.execute('SELECT * FROM sync_logs ORDER BY sync_time DESC').fetchall()
        )
        return {
            'version': '1.0',
            'backup_time': datetime.now().isoformat(),
            'users': users,
            'records': records,
            'sync_logs': sync_logs,
        }

    def restore_data(self, data):
        users_restored = 0
        records_restored = 0

        for u in data.get('users', []):
            existing = self.conn.execute(
                'SELECT * FROM users WHERE name=?', (u.get('name', ''),)
            ).fetchone()
            if not existing:
                self.conn.execute(
                    'INSERT INTO users (name, theme_color, role, created_at) VALUES (?, ?, ?, ?)',
                    (u.get('name', ''), u.get('theme_color', '#2196F3'),
                     u.get('role', 'operator'), u.get('created_at'))
                )
                users_restored += 1

        for r in data.get('records', []):
            existing = self.conn.execute(
                'SELECT * FROM records WHERE barcode=?', (r.get('barcode', ''),)
            ).fetchone()
            if not existing:
                self.conn.execute(
                    '''INSERT INTO records (barcode, user_id, status, address, weight, device_id, note,
                       recipient, logistics_no, signer, sign_time, exception_type, is_duplicate, created_at, sort_at)
                       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)''',
                    (r.get('barcode', ''), r.get('user_id', 1), r.get('status', '入库'),
                     r.get('address', ''), r.get('weight', 0), r.get('device_id', ''),
                     r.get('note', ''), r.get('recipient', ''), r.get('logistics_no', ''),
                     r.get('signer', ''), r.get('sign_time'), r.get('exception_type', ''),
                     r.get('is_duplicate', 0), r.get('created_at'), r.get('sort_at'))
                )
                records_restored += 1

        self.conn.commit()
        return users_restored, records_restored

    def close(self):
        self.conn.close()

    # ==================== 兼容方法（供 server_api.py 调用） ====================

    def login_or_register(self, name, role=None):
        """兼容旧版 login_or_register 方法名"""
        return self.login_user(name)

    def find_record_by_barcode_and_status(self, barcode, status):
        """按条码+状态查找记录"""
        row = self.conn.execute(
            'SELECT * FROM records WHERE barcode=? AND status=?', (barcode, status)
        ).fetchone()
        return dict(row) if row else None

    def update_record_status(self, record_id, status, **kwargs):
        """更新记录状态（兼容旧版）"""
        updates = ['status=?', 'updated_at=?', 'version=version+1']
        params = [status, datetime.now().strftime('%Y-%m-%d %H:%M:%S')]
        for key, val in kwargs.items():
            if val is not None:
                updates.append(f'{key}=?')
                params.append(val)
        params.append(record_id)
        self.conn.execute(
            f'UPDATE records SET {", ".join(updates)} WHERE id=?', params
        )
        self.conn.commit()
        return self.find_record_by_id(record_id)

    def update_record_address(self, record_id, address, changed_by=''):
        """更新地址（兼容旧版）"""
        result = self.change_address(record_id, address, changed_by)
        if 'error' in result:
            return None
        record = self.find_record_by_id(record_id)
        history = []
        try:
            history = json.loads(record.get('address_history', '[]'))
        except:
            pass
        return record, history

    def get_record(self, record_id):
        """获取单条记录（兼容旧版）"""
        return self.find_record_by_id(record_id)

    def get_all_records_for_export(self, user_id=None, status=None, date_from=None, date_to=None):
        """获取所有记录用于导出（兼容旧版）"""
        where = []
        params = []
        if user_id:
            where.append('r.user_id=?')
            params.append(user_id)
        if status:
            where.append('r.status=?')
            params.append(status)
        if date_from:
            where.append('r.created_at>=?')
            params.append(date_from)
        if date_to:
            where.append('r.created_at<=?')
            params.append(date_to + ' 23:59:59')

        where_clause = 'WHERE ' + ' AND '.join(where) if where else ''
        rows = self.conn.execute(f'''
            SELECT r.*, u.name as user_name
            FROM records r JOIN users u ON r.user_id=u.id
            {where_clause}
            ORDER BY r.created_at DESC
        ''', params).fetchall()
        return self._rows_to_list(rows)
