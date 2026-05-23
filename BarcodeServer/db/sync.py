# -*- coding: utf-8 -*-
"""同步/合并相关数据库操作"""
from . import Database
from . import users as db_users
from . import records as db_records


def merge_data(db: Database, users_data, records_data, device_id='', user_name=''):
    users_created = 0
    users_skipped = 0
    records_created = 0
    records_skipped = 0
    duplicates = 0

    if users_data:
        for u in users_data:
            existing = db.conn.execute(
                'SELECT * FROM users WHERE name=?', (u.get('name', ''),)
            ).fetchone()
            if not existing:
                color = db.get_next_color()
                db.conn.execute(
                    'INSERT INTO users (name, theme_color, role) VALUES (?, ?, ?)',
                    (u.get('name', ''), color, u.get('role', 'operator'))
                )
                users_created += 1
            else:
                users_skipped += 1

    for r in records_data:
        existing = db.conn.execute(
            'SELECT * FROM records WHERE barcode=?', (r.get('barcode', ''),)
        ).fetchone()
        if existing:
            db.conn.execute(
                'UPDATE records SET is_duplicate=1 WHERE id=?', (existing['id'],)
            )
            db.conn.execute(
                '''INSERT INTO records (barcode, user_id, status, address, weight, device_id, note, is_duplicate, created_at)
                   VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?)''',
                (r.get('barcode', ''), r.get('user_id', 1), r.get('status', '入库'),
                 r.get('address', ''), r.get('weight', 0), r.get('device_id', ''),
                 r.get('note', ''), r.get('created_at'))
            )
            duplicates += 1
        else:
            db.conn.execute(
                '''INSERT INTO records (barcode, user_id, status, address, weight, device_id, note, created_at)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?)''',
                (r.get('barcode', ''), r.get('user_id', 1), r.get('status', '入库'),
                 r.get('address', ''), r.get('weight', 0), r.get('device_id', ''),
                 r.get('note', ''), r.get('created_at'))
            )
            records_created += 1

    db.conn.commit()

    db.conn.execute(
        'INSERT INTO sync_logs (device_id, user_name, added, skipped, duplicates) VALUES (?, ?, ?, ?, ?)',
        (device_id, user_name, records_created, records_skipped, duplicates)
    )
    db.conn.commit()

    return {
        'users_created': users_created, 'users_skipped': users_skipped,
        'records_created': records_created, 'records_skipped': records_skipped,
        'duplicates': duplicates,
    }


def pull_data(db: Database, since=None):
    users = db_users.get_all_users(db)
    if since:
        rows = db.conn.execute('''
            SELECT r.*, u.name as user_name
            FROM records r JOIN users u ON r.user_id=u.id
            WHERE r.updated_at>=? OR r.created_at>=?
            ORDER BY r.updated_at DESC
        ''', (since, since)).fetchall()
    else:
        rows = db.conn.execute('''
            SELECT r.*, u.name as user_name
            FROM records r JOIN users u ON r.user_id=u.id
            ORDER BY r.updated_at DESC
        ''').fetchall()
    return users, db._rows_to_list(rows)


def get_sync_logs(db: Database, limit=100):
    rows = db.conn.execute(
        'SELECT * FROM sync_logs ORDER BY sync_time DESC LIMIT ?', (limit,)
    ).fetchall()
    return db._rows_to_list(rows)
