# -*- coding: utf-8 -*-
"""用户相关数据库操作"""
from . import Database


def login_user(db: Database, name: str):
    """登录或自动注册用户"""
    user = db.conn.execute('SELECT * FROM users WHERE name = ?', (name,)).fetchone()
    if not user:
        color = db.get_next_color()
        db.conn.execute(
            'INSERT INTO users (name, theme_color, role) VALUES (?, ?, ?)',
            (name, color, 'operator')
        )
        db.conn.commit()
        user = db.conn.execute('SELECT * FROM users WHERE name = ?', (name,)).fetchone()
    return dict(user) if user else {'error': '创建用户失败'}


def get_user(db: Database, user_id):
    row = db.conn.execute('SELECT * FROM users WHERE id = ?', (user_id,)).fetchone()
    return dict(row) if row else None


def get_all_users(db: Database):
    rows = db.conn.execute(
        'SELECT id, name, theme_color, role, created_at FROM users ORDER BY id'
    ).fetchall()
    return db._rows_to_list(rows)
