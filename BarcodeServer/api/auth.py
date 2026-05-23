# -*- coding: utf-8 -*-
"""用户认证相关 API"""
from flask import request, jsonify
from . import app, db
from db import users as db_users


@app.route('/api/login', methods=['POST'])
def api_login():
    data = request.get_json()
    if not data or not data.get('name'):
        return jsonify({'error': '请输入姓名'}), 400
    user = db_users.login_user(db, data['name'])
    return jsonify(user)


@app.route('/api/users', methods=['GET'])
def api_users():
    users = db_users.get_all_users(db)
    return jsonify(users)
