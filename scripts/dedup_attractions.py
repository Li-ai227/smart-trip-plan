#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
knowledge-base.json 景点去重 + 替换脚本

规则：
1. 完全相同名称 → 重复
2. 城市前缀：X == 城市名+X → 重复
3. 通用后缀：X == X+后缀 → 重复
4. 括号限定：X == X(任意) → 重复
5. 描述性前缀：X == 前缀+X → 重复
6. 双端去缀归一：两个名称去掉各自通用后缀后相同 → 重复
"""
import json
import re
import sys
import os

GENERIC_SUFFIXES = [
    '风景名胜区', '风景区', '旅游度假区', '旅游区', '景区', '公园',
    '风光带', '风景带', '遗址公园', '遗址', '步行街', '历史文化街区',
    '历史街区', '博物院', '博物馆', '新馆', '电视塔', '头',
]

DESCRIPTIVE_PREFIXES = [
    '道教圣地', '侵华日军', '万岁山', '肥西', '栾川',
    '句容', '兰溪', '海盐', '顺德',
]


def are_semantically_same(name1, name2, city):
    if name1 == name2:
        return True
    if len(name1) > len(name2):
        name1, name2 = name2, name1

    if name2 == city + name1:
        return True
    if name2.startswith(city) and name2[len(city):] == name1:
        return True

    n1 = name1[len(city):] if name1.startswith(city) else name1
    n2 = name2[len(city):] if name2.startswith(city) else name2
    if n1 == n2:
        return True
    if len(n1) > len(n2):
        n1, n2 = n2, n1

    for suffix in GENERIC_SUFFIXES:
        if n2 == n1 + suffix:
            return True

    paren_match = re.match(r'^(.+?)[（(].+[）)]$', n2)
    if paren_match and paren_match.group(1) == n1:
        return True

    for prefix in DESCRIPTIVE_PREFIXES:
        if n2 == prefix + n1:
            return True

    def strip_suffix(name):
        for s in sorted(GENERIC_SUFFIXES, key=len, reverse=True):
            if name.endswith(s) and len(name) > len(s):
                return name[:-len(s)]
        return name

    if strip_suffix(n1) == strip_suffix(n2):
        return True

    return False


# ═══════════════════════════════════════════════════════
# 替换景点数据池（按城市组织，每个城市提供足够数量的替换景点）
# ═══════════════════════════════════════════════════════
REPLACEMENTS = {
    "北京": [
        {"name": "恭王府", "location": {"longitude": 116.387, "latitude": 39.935, "address": "北京市西城区柳荫街甲14号", "city": "北京"}, "rating": 4.6, "durationMin": 120, "ticketPrice": 40.0, "tags": ["历史文化", "古建筑"]},
        {"name": "陶然亭公园", "location": {"longitude": 116.381, "latitude": 39.872, "address": "北京市西城区太平街19号", "city": "北京"}, "rating": 4.3, "durationMin": 90, "ticketPrice": 2.0, "tags": ["园林", "免费"]},
    ],
    "上海": [
        {"name": "中华艺术宫", "location": {"longitude": 121.491, "latitude": 31.186, "address": "上海市浦东新区上南路205号", "city": "上海"}, "rating": 4.5, "durationMin": 120, "ticketPrice": 0.0, "tags": ["艺术", "免费"]},
        {"name": "滴水湖", "location": {"longitude": 121.941, "latitude": 30.893, "address": "上海市浦东新区临港新城", "city": "上海"}, "rating": 4.3, "durationMin": 120, "ticketPrice": 0.0, "tags": ["湖泊", "免费", "休闲"]},
        {"name": "崇明东滩湿地", "location": {"longitude": 121.963, "latitude": 31.517, "address": "上海市崇明区东滩大道", "city": "上海"}, "rating": 4.4, "durationMin": 180, "ticketPrice": 50.0, "tags": ["湿地", "自然", "观鸟"]},
        {"name": "上海大世界", "location": {"longitude": 121.481, "latitude": 31.226, "address": "上海市黄浦区西藏南路1号", "city": "上海"}, "rating": 4.2, "durationMin": 120, "ticketPrice": 60.0, "tags": ["文化", "娱乐"]},
        {"name": "嘉定孔庙", "location": {"longitude": 121.237, "latitude": 31.385, "address": "上海市嘉定区南大街183号", "city": "上海"}, "rating": 4.3, "durationMin": 90, "ticketPrice": 0.0, "tags": ["历史文化", "古建筑", "免费"]},
    ],
    "广州": [
        {"name": "广州图书馆", "location": {"longitude": 113.318, "latitude": 23.119, "address": "广州市天河区珠江新城珠江东路4号", "city": "广州"}, "rating": 4.5, "durationMin": 120, "ticketPrice": 0.0, "tags": ["文化", "免费"]},
        {"name": "荔枝湾涌", "location": {"longitude": 113.242, "latitude": 23.123, "address": "广州市荔湾区荔湾路", "city": "广州"}, "rating": 4.3, "durationMin": 60, "ticketPrice": 0.0, "tags": ["历史文化", "免费", "水乡"]},
        {"name": "红砖厂创意园", "location": {"longitude": 113.354, "latitude": 23.137, "address": "广州市天河区员村四横路128号", "city": "广州"}, "rating": 4.2, "durationMin": 90, "ticketPrice": 0.0, "tags": ["创意", "免费", "文艺"]},
        {"name": "广州雕塑公园", "location": {"longitude": 113.275, "latitude": 23.163, "address": "广州市白云区飞鹅岭下塘西路", "city": "广州"}, "rating": 4.1, "durationMin": 90, "ticketPrice": 0.0, "tags": ["艺术", "公园", "免费"]},
    ],
    "深圳": [
        {"name": "甘坑客家小镇", "location": {"longitude": 114.135, "latitude": 22.651, "address": "深圳市龙岗区吉华街道甘坑社区", "city": "深圳"}, "rating": 4.3, "durationMin": 120, "ticketPrice": 0.0, "tags": ["古镇", "客家文化", "免费"]},
        {"name": "深圳人才公园", "location": {"longitude": 113.957, "latitude": 22.519, "address": "深圳市南山区沙河西路与东滨路交汇处", "city": "深圳"}, "rating": 4.4, "durationMin": 90, "ticketPrice": 0.0, "tags": ["公园", "休闲", "免费"]},
        {"name": "梧桐山", "location": {"longitude": 114.157, "latitude": 22.577, "address": "深圳市罗湖区梧桐山村", "city": "深圳"}, "rating": 4.5, "durationMin": 300, "ticketPrice": 0.0, "tags": ["山岳", "户外", "免费"]},
        {"name": "深圳海上世界", "location": {"longitude": 113.916, "latitude": 22.486, "address": "深圳市南山区蛇口望海路", "city": "深圳"}, "rating": 4.3, "durationMin": 120, "ticketPrice": 0.0, "tags": ["休闲", "购物", "免费"]},
    ],
    "成都": [
        {"name": "四川博物院", "location": {"longitude": 104.040, "latitude": 30.660, "address": "成都市青羊区浣花南路251号", "city": "成都"}, "rating": 4.5, "durationMin": 150, "ticketPrice": 0.0, "tags": ["博物馆", "历史文化", "免费"]},
        {"name": "铁像寺水街", "location": {"longitude": 104.058, "latitude": 30.570, "address": "成都市武侯区铁像寺路88号", "city": "成都"}, "rating": 4.2, "durationMin": 90, "ticketPrice": 0.0, "tags": ["休闲", "美食", "免费"]},
        {"name": "天府广场", "location": {"longitude": 104.065, "latitude": 30.657, "address": "成都市青羊区天府广场", "city": "成都"}, "rating": 4.3, "durationMin": 60, "ticketPrice": 0.0, "tags": ["地标", "免费"]},
        {"name": "大慈寺", "location": {"longitude": 104.080, "latitude": 30.656, "address": "成都市锦江区大慈寺路23号", "city": "成都"}, "rating": 4.3, "durationMin": 60, "ticketPrice": 5.0, "tags": ["寺庙", "佛教"]},
        {"name": "白鹭湾生态湿地", "location": {"longitude": 104.128, "latitude": 30.593, "address": "成都市锦江区白鹭湾湿地", "city": "成都"}, "rating": 4.4, "durationMin": 120, "ticketPrice": 0.0, "tags": ["湿地", "自然", "免费"]},
        {"name": "建设路美食街", "location": {"longitude": 104.102, "latitude": 30.673, "address": "成都市成华区建设路", "city": "成都"}, "rating": 4.3, "durationMin": 90, "ticketPrice": 0.0, "tags": ["美食", "夜生活", "免费"]},
        {"name": "西岭雪山", "location": {"longitude": 103.164, "latitude": 30.578, "address": "成都市大邑县西岭镇", "city": "成都"}, "rating": 4.6, "durationMin": 360, "ticketPrice": 120.0, "tags": ["山岳", "滑雪", "自然"]},
        {"name": "黄龙溪古镇", "location": {"longitude": 103.975, "latitude": 30.390, "address": "成都市双流区黄龙溪镇", "city": "成都"}, "rating": 4.3, "durationMin": 180, "ticketPrice": 0.0, "tags": ["古镇", "免费"]},
        {"name": "三星堆博物馆", "location": {"longitude": 104.198, "latitude": 31.010, "address": "成都市广汉市三星堆镇", "city": "成都"}, "rating": 4.8, "durationMin": 240, "ticketPrice": 72.0, "tags": ["博物馆", "历史文化", "必去"]},
        {"name": "龙泉山城市森林公园", "location": {"longitude": 104.283, "latitude": 30.563, "address": "成都市龙泉驿区", "city": "成都"}, "rating": 4.3, "durationMin": 180, "ticketPrice": 0.0, "tags": ["森林", "户外", "免费"]},
        {"name": "东郊记忆", "location": {"longitude": 104.117, "latitude": 30.670, "address": "成都市成华区建设南支路4号", "city": "成都"}, "rating": 4.2, "durationMin": 120, "ticketPrice": 0.0, "tags": ["创意", "文艺", "免费"]},
        {"name": "望江楼公园", "location": {"longitude": 104.090, "latitude": 30.637, "address": "成都市武侯区望江路30号", "city": "成都"}, "rating": 4.3, "durationMin": 90, "ticketPrice": 20.0, "tags": ["园林", "历史文化"]},
        {"name": "彭镇老茶馆", "location": {"longitude": 103.922, "latitude": 30.560, "address": "成都市双流区彭镇杨柳河畔", "city": "成都"}, "rating": 4.4, "durationMin": 60, "ticketPrice": 0.0, "tags": ["文化体验", "市井生活", "免费"]},
    ],
    "杭州": [
        {"name": "良渚古城遗址", "location": {"longitude": 119.982, "latitude": 30.393, "address": "杭州市余杭区凤都路", "city": "杭州"}, "rating": 4.6, "durationMin": 240, "ticketPrice": 60.0, "tags": ["世界遗产", "历史文化"]},
        {"name": "九溪烟树", "location": {"longitude": 120.103, "latitude": 30.197, "address": "杭州市西湖区龙井村南侧", "city": "杭州"}, "rating": 4.5, "durationMin": 120, "ticketPrice": 0.0, "tags": ["自然", "溪流", "免费"]},
        {"name": "中国茶叶博物馆", "location": {"longitude": 120.111, "latitude": 30.218, "address": "杭州市西湖区龙井路88号", "city": "杭州"}, "rating": 4.4, "durationMin": 90, "ticketPrice": 0.0, "tags": ["博物馆", "茶文化", "免费"]},
        {"name": "大运河滨水公园", "location": {"longitude": 120.141, "latitude": 30.325, "address": "杭州市拱墅区运河广场", "city": "杭州"}, "rating": 4.3, "durationMin": 90, "ticketPrice": 0.0, "tags": ["世界遗产", "运河", "免费"]},
    ],
    "南京": [
        {"name": "牛首山", "location": {"longitude": 118.780, "latitude": 31.942, "address": "南京市江宁区宁丹大道18号", "city": "南京"}, "rating": 4.6, "durationMin": 240, "ticketPrice": 98.0, "tags": ["佛教", "山岳", "建筑"]},
        {"name": "甘熙宅第", "location": {"longitude": 118.778, "latitude": 32.038, "address": "南京市秦淮区中山南路南捕厅", "city": "南京"}, "rating": 4.3, "durationMin": 90, "ticketPrice": 20.0, "tags": ["历史文化", "古建筑"]},
        {"name": "南京眼步行桥", "location": {"longitude": 118.726, "latitude": 31.998, "address": "南京市建邺区河西新城", "city": "南京"}, "rating": 4.4, "durationMin": 60, "ticketPrice": 0.0, "tags": ["地标", "建筑", "免费"]},
    ],
    "西安": [
        {"name": "白鹿原影视城", "location": {"longitude": 109.132, "latitude": 34.186, "address": "西安市蓝田县前卫镇", "city": "西安"}, "rating": 4.2, "durationMin": 240, "ticketPrice": 0.0, "tags": ["影视", "民俗", "免费"]},
        {"name": "大明宫国家遗址公园", "location": {"longitude": 108.961, "latitude": 34.282, "address": "西安市新城区自强东路585号", "city": "西安"}, "rating": 4.5, "durationMin": 180, "ticketPrice": 60.0, "tags": ["历史文化", "世界遗产"]},
        {"name": "永兴坊", "location": {"longitude": 108.956, "latitude": 34.268, "address": "西安市新城区东新街小东门内", "city": "西安"}, "rating": 4.3, "durationMin": 90, "ticketPrice": 0.0, "tags": ["美食", "历史文化", "免费"]},
    ],
    "重庆": [
        {"name": "重庆中国三峡博物馆", "location": {"longitude": 106.549, "latitude": 29.554, "address": "重庆市渝中区人民路236号", "city": "重庆"}, "rating": 4.5, "durationMin": 150, "ticketPrice": 0.0, "tags": ["博物馆", "历史文化", "免费"]},
        {"name": "山城步道", "location": {"longitude": 106.569, "latitude": 29.553, "address": "重庆市渝中区中兴路", "city": "重庆"}, "rating": 4.4, "durationMin": 90, "ticketPrice": 0.0, "tags": ["步道", "城市风光", "免费"]},
        {"name": "弹子石老街", "location": {"longitude": 106.591, "latitude": 29.556, "address": "重庆市南岸区泰昌路68号", "city": "重庆"}, "rating": 4.2, "durationMin": 90, "ticketPrice": 0.0, "tags": ["历史文化", "美食", "免费"]},
        {"name": "北仓文创街区", "location": {"longitude": 106.558, "latitude": 29.578, "address": "重庆市江北区北仓路", "city": "重庆"}, "rating": 4.1, "durationMin": 60, "ticketPrice": 0.0, "tags": ["创意", "文艺", "免费"]},
        {"name": "四面山", "location": {"longitude": 106.393, "latitude": 28.667, "address": "重庆市江津区四面山镇", "city": "重庆"}, "rating": 4.5, "durationMin": 360, "ticketPrice": 110.0, "tags": ["山岳", "瀑布", "自然"]},
    ],
    "武汉": [
        {"name": "武汉博物馆", "location": {"longitude": 114.262, "latitude": 30.553, "address": "武汉市汉口青年路373号", "city": "武汉"}, "rating": 4.4, "durationMin": 120, "ticketPrice": 0.0, "tags": ["博物馆", "免费"]},
        {"name": "古琴台", "location": {"longitude": 114.253, "latitude": 30.545, "address": "武汉市汉阳区琴台大道10号", "city": "武汉"}, "rating": 4.2, "durationMin": 60, "ticketPrice": 15.0, "tags": ["历史文化", "古迹"]},
        {"name": "晴川阁", "location": {"longitude": 114.277, "latitude": 30.553, "address": "武汉市汉阳区洗马长街86号", "city": "武汉"}, "rating": 4.3, "durationMin": 60, "ticketPrice": 0.0, "tags": ["历史文化", "古建筑", "免费"]},
        {"name": "江汉关博物馆", "location": {"longitude": 114.291, "latitude": 30.583, "address": "武汉市江汉区沿江大道129号", "city": "武汉"}, "rating": 4.4, "durationMin": 90, "ticketPrice": 0.0, "tags": ["历史文化", "博物馆", "免费"]},
        {"name": "花博汇", "location": {"longitude": 114.151, "latitude": 30.411, "address": "武汉市蔡甸区天星村", "city": "武汉"}, "rating": 4.2, "durationMin": 180, "ticketPrice": 60.0, "tags": ["花卉", "休闲", "田园"]},
    ],
    "长沙": [
        {"name": "湖南烈士公园", "location": {"longitude": 112.995, "latitude": 28.212, "address": "长沙市开福区东风路1号", "city": "长沙"}, "rating": 4.2, "durationMin": 90, "ticketPrice": 0.0, "tags": ["公园", "免费", "爱国主义"]},
        {"name": "贾谊故居", "location": {"longitude": 112.972, "latitude": 28.191, "address": "长沙市天心区太平街28号", "city": "长沙"}, "rating": 4.3, "durationMin": 60, "ticketPrice": 0.0, "tags": ["历史文化", "古建筑", "免费"]},
        {"name": "靖港古镇", "location": {"longitude": 112.868, "latitude": 28.316, "address": "长沙市望城区靖港镇", "city": "长沙"}, "rating": 4.2, "durationMin": 180, "ticketPrice": 78.0, "tags": ["古镇", "历史文化"]},
        {"name": "梅溪湖", "location": {"longitude": 112.889, "latitude": 28.190, "address": "长沙市岳麓区梅溪湖路", "city": "长沙"}, "rating": 4.3, "durationMin": 90, "ticketPrice": 0.0, "tags": ["湖泊", "休闲", "免费"]},
    ],
    "昆明": [
        {"name": "翠湖公园", "location": {"longitude": 102.690, "latitude": 25.047, "address": "昆明市五华区翠湖南路67号", "city": "昆明"}, "rating": 4.4, "durationMin": 90, "ticketPrice": 0.0, "tags": ["公园", "免费", "观鸟"]},
        {"name": "官渡古镇", "location": {"longitude": 102.747, "latitude": 24.982, "address": "昆明市官渡区广福路", "city": "昆明"}, "rating": 4.2, "durationMin": 120, "ticketPrice": 0.0, "tags": ["古镇", "免费", "历史文化"]},
        {"name": "云南大学", "location": {"longitude": 102.703, "latitude": 25.058, "address": "昆明市五华区翠湖北路2号", "city": "昆明"}, "rating": 4.3, "durationMin": 90, "ticketPrice": 0.0, "tags": ["校园", "建筑", "免费"]},
        {"name": "海埂大坝", "location": {"longitude": 102.667, "latitude": 24.958, "address": "昆明市西山区滇池路1318号", "city": "昆明"}, "rating": 4.3, "durationMin": 60, "ticketPrice": 0.0, "tags": ["滇池", "观鸟", "免费"]},
        {"name": "斗南花卉市场", "location": {"longitude": 102.745, "latitude": 24.922, "address": "昆明市呈贡区斗南街道", "city": "昆明"}, "rating": 4.4, "durationMin": 90, "ticketPrice": 0.0, "tags": ["花卉", "市场", "免费"]},
    ],
    "丽江": [
        {"name": "黑龙潭公园", "location": {"longitude": 100.227, "latitude": 26.890, "address": "丽江市古城区民主路1号", "city": "丽江"}, "rating": 4.3, "durationMin": 90, "ticketPrice": 0.0, "tags": ["公园", "免费", "湖泊"]},
        {"name": "白沙古镇", "location": {"longitude": 100.175, "latitude": 26.933, "address": "丽江市玉龙县白沙镇", "city": "丽江"}, "rating": 4.4, "durationMin": 120, "ticketPrice": 0.0, "tags": ["古镇", "壁画", "免费"]},
        {"name": "拉市海湿地公园", "location": {"longitude": 100.119, "latitude": 26.865, "address": "丽江市玉龙县拉市镇", "city": "丽江"}, "rating": 4.2, "durationMin": 180, "ticketPrice": 30.0, "tags": ["湿地", "骑马", "自然"]},
        {"name": "玉湖村", "location": {"longitude": 100.195, "latitude": 27.038, "address": "丽江市玉龙县白沙镇玉湖村", "city": "丽江"}, "rating": 4.3, "durationMin": 120, "ticketPrice": 0.0, "tags": ["纳西族", "乡村", "免费"]},
        {"name": "文笔山", "location": {"longitude": 100.193, "latitude": 26.823, "address": "丽江市古城区文笔路", "city": "丽江"}, "rating": 4.1, "durationMin": 180, "ticketPrice": 25.0, "tags": ["山岳", "寺庙"]},
        {"name": "三文笔水利工程", "location": {"longitude": 100.235, "latitude": 26.855, "address": "丽江市古城区清溪水库旁", "city": "丽江"}, "rating": 4.0, "durationMin": 60, "ticketPrice": 0.0, "tags": ["水利", "历史", "免费"]},
        {"name": "东巴万神园", "location": {"longitude": 100.206, "latitude": 27.020, "address": "丽江市玉龙县玉峰寺旁", "city": "丽江"}, "rating": 4.1, "durationMin": 90, "ticketPrice": 15.0, "tags": ["纳西族", "宗教", "文化"]},
    ],
    "大理": [
        {"name": "喜洲古镇", "location": {"longitude": 100.091, "latitude": 25.794, "address": "大理市喜洲镇", "city": "大理"}, "rating": 4.4, "durationMin": 180, "ticketPrice": 0.0, "tags": ["古镇", "白族", "免费"]},
        {"name": "才村码头", "location": {"longitude": 100.137, "latitude": 25.698, "address": "大理市才村", "city": "大理"}, "rating": 4.2, "durationMin": 60, "ticketPrice": 0.0, "tags": ["洱海", "日落", "免费"]},
        {"name": "鸡足山", "location": {"longitude": 100.381, "latitude": 25.965, "address": "大理州宾川县鸡足山镇", "city": "大理"}, "rating": 4.5, "durationMin": 360, "ticketPrice": 80.0, "tags": ["佛教", "山岳", "朝圣"]},
        {"name": "沙溪古镇", "location": {"longitude": 99.932, "latitude": 26.290, "address": "大理州剑川县沙溪镇", "city": "大理"}, "rating": 4.6, "durationMin": 240, "ticketPrice": 0.0, "tags": ["古镇", "茶马古道", "免费"]},
        {"name": "无量山樱花谷", "location": {"longitude": 100.449, "latitude": 24.795, "address": "大理州南涧县无量山镇", "city": "大理"}, "rating": 4.5, "durationMin": 180, "ticketPrice": 0.0, "tags": ["花卉", "自然", "免费"]},
        {"name": "巍山古城", "location": {"longitude": 100.305, "latitude": 25.229, "address": "大理州巍山县", "city": "大理"}, "rating": 4.3, "durationMin": 180, "ticketPrice": 0.0, "tags": ["古城", "南诏文化", "免费"]},
    ],
    "苏州": [
        {"name": "平江历史街区", "location": {"longitude": 120.639, "latitude": 31.313, "address": "苏州市姑苏区平江路", "city": "苏州"}, "rating": 4.5, "durationMin": 120, "ticketPrice": 0.0, "tags": ["历史文化", "水乡", "免费"]},
        {"name": "木渎古镇", "location": {"longitude": 120.528, "latitude": 31.252, "address": "苏州市吴中区木渎镇", "city": "苏州"}, "rating": 4.3, "durationMin": 180, "ticketPrice": 78.0, "tags": ["古镇", "园林"]},
        {"name": "苏州博物馆西馆", "location": {"longitude": 120.517, "latitude": 31.305, "address": "苏州市高新区长江路399号", "city": "苏州"}, "rating": 4.6, "durationMin": 150, "ticketPrice": 0.0, "tags": ["博物馆", "建筑", "免费"]},
        {"name": "网师园", "location": {"longitude": 120.634, "latitude": 31.295, "address": "苏州市姑苏区带城桥路阔家头巷11号", "city": "苏州"}, "rating": 4.5, "durationMin": 90, "ticketPrice": 40.0, "tags": ["园林", "世界遗产"]},
        {"name": "太湖国家湿地公园", "location": {"longitude": 120.472, "latitude": 31.212, "address": "苏州市吴中区太湖大堤", "city": "苏州"}, "rating": 4.3, "durationMin": 180, "ticketPrice": 60.0, "tags": ["湿地", "自然"]},
    ],
    "厦门": [
        {"name": "集美学村", "location": {"longitude": 118.098, "latitude": 24.565, "address": "厦门市集美区嘉庚路", "city": "厦门"}, "rating": 4.4, "durationMin": 120, "ticketPrice": 0.0, "tags": ["历史文化", "建筑", "免费"]},
        {"name": "中山路步行街", "location": {"longitude": 118.087, "latitude": 24.451, "address": "厦门市思明区中山路", "city": "厦门"}, "rating": 4.3, "durationMin": 90, "ticketPrice": 0.0, "tags": ["购物", "美食", "免费"]},
        {"name": "万石植物园", "location": {"longitude": 118.104, "latitude": 24.458, "address": "厦门市思明区虎园路25号", "city": "厦门"}, "rating": 4.5, "durationMin": 180, "ticketPrice": 40.0, "tags": ["植物园", "自然"]},
        {"name": "白城沙滩", "location": {"longitude": 118.088, "latitude": 24.435, "address": "厦门市思明区大学路附近", "city": "厦门"}, "rating": 4.3, "durationMin": 90, "ticketPrice": 0.0, "tags": ["沙滩", "海景", "免费"]},
        {"name": "沙坡尾", "location": {"longitude": 118.080, "latitude": 24.440, "address": "厦门市思明区大学路", "city": "厦门"}, "rating": 4.3, "durationMin": 90, "ticketPrice": 0.0, "tags": ["文艺", "美食", "免费"]},
    ],
    "青岛": [
        {"name": "信号山公园", "location": {"longitude": 120.320, "latitude": 36.068, "address": "青岛市市南区齐东路17号", "city": "青岛"}, "rating": 4.4, "durationMin": 60, "ticketPrice": 5.0, "tags": ["公园", "观景"]},
        {"name": "石老人海水浴场", "location": {"longitude": 120.461, "latitude": 36.075, "address": "青岛市崂山区海尔路", "city": "青岛"}, "rating": 4.3, "durationMin": 120, "ticketPrice": 0.0, "tags": ["沙滩", "免费"]},
        {"name": "海军博物馆", "location": {"longitude": 120.320, "latitude": 36.054, "address": "青岛市市南区莱阳路8号", "city": "青岛"}, "rating": 4.4, "durationMin": 120, "ticketPrice": 30.0, "tags": ["博物馆", "军事"]},
        {"name": "小鱼山公园", "location": {"longitude": 120.338, "latitude": 36.059, "address": "青岛市市南区鱼山路", "city": "青岛"}, "rating": 4.3, "durationMin": 60, "ticketPrice": 15.0, "tags": ["公园", "观景"]},
        {"name": "劈柴院", "location": {"longitude": 120.316, "latitude": 36.067, "address": "青岛市市南区中山路北端", "city": "青岛"}, "rating": 4.1, "durationMin": 60, "ticketPrice": 0.0, "tags": ["美食", "老街", "免费"]},
    ],
    "三亚": [
        {"name": "三亚千古情景区", "location": {"longitude": 109.428, "latitude": 18.278, "address": "三亚市吉阳区迎宾路", "city": "三亚"}, "rating": 4.5, "durationMin": 180, "ticketPrice": 280.0, "tags": ["演出", "文化"]},
        {"name": "后海村", "location": {"longitude": 109.687, "latitude": 18.223, "address": "三亚市海棠区后海村", "city": "三亚"}, "rating": 4.3, "durationMin": 180, "ticketPrice": 0.0, "tags": ["冲浪", "渔村", "免费"]},
        {"name": "半山半岛帆船港", "location": {"longitude": 109.451, "latitude": 18.222, "address": "三亚市鹿回头半山半岛", "city": "三亚"}, "rating": 4.2, "durationMin": 120, "ticketPrice": 0.0, "tags": ["帆船", "海景", "免费"]},
        {"name": "凤凰岭公园", "location": {"longitude": 109.416, "latitude": 18.253, "address": "三亚市天涯区凤凰路", "city": "三亚"}, "rating": 4.3, "durationMin": 120, "ticketPrice": 98.0, "tags": ["山岳", "观景"]},
        {"name": "大东海旅游区", "location": {"longitude": 109.511, "latitude": 18.219, "address": "三亚市吉阳区榆亚路", "city": "三亚"}, "rating": 4.3, "durationMin": 180, "ticketPrice": 0.0, "tags": ["沙滩", "海景", "免费"]},
        {"name": "槟榔谷黎苗文化旅游区", "location": {"longitude": 109.574, "latitude": 18.412, "address": "三亚市保亭县甘什岭", "city": "三亚"}, "rating": 4.4, "durationMin": 240, "ticketPrice": 96.0, "tags": ["民族文化", "雨林"]},
        {"name": "西岛", "location": {"longitude": 109.348, "latitude": 18.233, "address": "三亚市天涯区西岛", "city": "三亚"}, "rating": 4.3, "durationMin": 300, "ticketPrice": 95.0, "tags": ["海岛", "潜水"]},
    ],
    "哈尔滨": [
        {"name": "哈尔滨大剧院", "location": {"longitude": 126.560, "latitude": 45.808, "address": "哈尔滨市松北区文化中心岛", "city": "哈尔滨"}, "rating": 4.6, "durationMin": 90, "ticketPrice": 30.0, "tags": ["建筑", "艺术"]},
        {"name": "老道外巴洛克建筑群", "location": {"longitude": 126.651, "latitude": 45.788, "address": "哈尔滨市道外区南二道街", "city": "哈尔滨"}, "rating": 4.3, "durationMin": 90, "ticketPrice": 0.0, "tags": ["历史建筑", "免费"]},
        {"name": "伏尔加庄园", "location": {"longitude": 126.915, "latitude": 45.655, "address": "哈尔滨市香坊区哈成路", "city": "哈尔滨"}, "rating": 4.4, "durationMin": 240, "ticketPrice": 100.0, "tags": ["俄罗斯风情", "庄园"]},
        {"name": "松花江斯大林公园", "location": {"longitude": 126.622, "latitude": 45.791, "address": "哈尔滨市道里区近江路", "city": "哈尔滨"}, "rating": 4.3, "durationMin": 60, "ticketPrice": 0.0, "tags": ["公园", "江景", "免费"]},
    ],
    "桂林": [
        {"name": "日月双塔", "location": {"longitude": 110.296, "latitude": 25.271, "address": "桂林市象山区文明路", "city": "桂林"}, "rating": 4.4, "durationMin": 60, "ticketPrice": 35.0, "tags": ["地标", "夜景"]},
        {"name": "独秀峰王城景区", "location": {"longitude": 110.295, "latitude": 25.280, "address": "桂林市秀峰区王城1号", "city": "桂林"}, "rating": 4.5, "durationMin": 120, "ticketPrice": 100.0, "tags": ["历史文化", "山岳"]},
        {"name": "十里画廊", "location": {"longitude": 110.441, "latitude": 24.747, "address": "桂林市阳朔县抗战路", "city": "桂林"}, "rating": 4.5, "durationMin": 180, "ticketPrice": 0.0, "tags": ["自然", "骑行", "免费"]},
        {"name": "遇龙河漂流", "location": {"longitude": 110.410, "latitude": 24.743, "address": "桂林市阳朔县白沙镇", "city": "桂林"}, "rating": 4.6, "durationMin": 180, "ticketPrice": 200.0, "tags": ["漂流", "自然"]},
        {"name": "叠彩山", "location": {"longitude": 110.298, "latitude": 25.293, "address": "桂林市叠彩区叠彩路", "city": "桂林"}, "rating": 4.3, "durationMin": 90, "ticketPrice": 35.0, "tags": ["山岳", "观景"]},
    ],
    "贵阳": [
        {"name": "花溪公园", "location": {"longitude": 106.670, "latitude": 26.408, "address": "贵阳市花溪区花溪大道", "city": "贵阳"}, "rating": 4.3, "durationMin": 120, "ticketPrice": 6.0, "tags": ["公园", "溪流"]},
        {"name": "贵州省博物馆", "location": {"longitude": 106.702, "latitude": 26.596, "address": "贵阳市观山湖区林城东路", "city": "贵阳"}, "rating": 4.4, "durationMin": 120, "ticketPrice": 0.0, "tags": ["博物馆", "免费"]},
        {"name": "贵阳森林公园", "location": {"longitude": 106.752, "latitude": 26.608, "address": "贵阳市南明区枣山路187号", "city": "贵阳"}, "rating": 4.2, "durationMin": 120, "ticketPrice": 0.0, "tags": ["森林", "免费"]},
        {"name": "天河潭", "location": {"longitude": 106.592, "latitude": 26.453, "address": "贵阳市花溪区石板镇", "city": "贵阳"}, "rating": 4.4, "durationMin": 240, "ticketPrice": 80.0, "tags": ["溶洞", "瀑布", "自然"]},
        {"name": "多彩贵州城", "location": {"longitude": 106.817, "latitude": 26.646, "address": "贵阳市双龙航空港经济区", "city": "贵阳"}, "rating": 4.1, "durationMin": 120, "ticketPrice": 0.0, "tags": ["民族文化", "免费"]},
    ],
    "珠海": [
        {"name": "外伶仃岛", "location": {"longitude": 113.572, "latitude": 22.007, "address": "珠海市香洲区担杆镇", "city": "珠海"}, "rating": 4.5, "durationMin": 480, "ticketPrice": 0.0, "tags": ["海岛", "户外", "免费"]},
        {"name": "东澳岛", "location": {"longitude": 113.728, "latitude": 22.098, "address": "珠海市万山海洋开发区", "city": "珠海"}, "rating": 4.5, "durationMin": 480, "ticketPrice": 0.0, "tags": ["海岛", "沙滩", "免费"]},
        {"name": "珠海渔女雕像", "location": {"longitude": 113.578, "latitude": 22.237, "address": "珠海市香洲区情侣中路", "city": "珠海"}, "rating": 4.2, "durationMin": 30, "ticketPrice": 0.0, "tags": ["地标", "免费"]},
    ],
    "南宁": [
        {"name": "扬美古镇", "location": {"longitude": 108.156, "latitude": 22.681, "address": "南宁市江南区扬美古镇", "city": "南宁"}, "rating": 4.2, "durationMin": 180, "ticketPrice": 0.0, "tags": ["古镇", "免费"]},
        {"name": "广西科技馆", "location": {"longitude": 108.371, "latitude": 22.835, "address": "南宁市兴宁区民族大道20号", "city": "南宁"}, "rating": 4.3, "durationMin": 120, "ticketPrice": 0.0, "tags": ["科技", "免费"]},
        {"name": "南宁人民公园", "location": {"longitude": 108.352, "latitude": 22.820, "address": "南宁市兴宁区人民东路1号", "city": "南宁"}, "rating": 4.1, "durationMin": 90, "ticketPrice": 0.0, "tags": ["公园", "免费"]},
        {"name": "花花大世界", "location": {"longitude": 108.444, "latitude": 22.972, "address": "南宁市武鸣区双桥镇", "city": "南宁"}, "rating": 4.2, "durationMin": 180, "ticketPrice": 50.0, "tags": ["花卉", "游乐"]},
    ],
    "海口": [
        {"name": "火山口世界地质公园", "location": {"longitude": 110.196, "latitude": 19.962, "address": "海口市秀英区石山镇", "city": "海口"}, "rating": 4.4, "durationMin": 120, "ticketPrice": 60.0, "tags": ["地质", "火山", "自然"]},
        {"name": "海口钟楼", "location": {"longitude": 110.351, "latitude": 20.044, "address": "海口市龙华区长堤路20号", "city": "海口"}, "rating": 4.1, "durationMin": 30, "ticketPrice": 0.0, "tags": ["地标", "免费"]},
        {"name": "冯小刚电影公社", "location": {"longitude": 110.218, "latitude": 19.910, "address": "海口市龙华区观澜湖", "city": "海口"}, "rating": 4.2, "durationMin": 180, "ticketPrice": 128.0, "tags": ["影视", "民国风"]},
    ],
    "大连": [
        {"name": "圣亚海洋世界", "location": {"longitude": 121.574, "latitude": 38.868, "address": "大连市沙河口区中山路608号", "city": "大连"}, "rating": 4.4, "durationMin": 180, "ticketPrice": 190.0, "tags": ["海洋", "亲子"]},
        {"name": "旅顺军港", "location": {"longitude": 121.251, "latitude": 38.813, "address": "大连市旅顺口区白玉山", "city": "大连"}, "rating": 4.3, "durationMin": 120, "ticketPrice": 0.0, "tags": ["军事", "历史", "免费"]},
        {"name": "东港音乐喷泉广场", "location": {"longitude": 121.667, "latitude": 38.919, "address": "大连市中山区东港商务区", "city": "大连"}, "rating": 4.2, "durationMin": 60, "ticketPrice": 0.0, "tags": ["夜景", "免费"]},
        {"name": "渔人码头", "location": {"longitude": 121.680, "latitude": 38.918, "address": "大连市中山区虎滩渔港", "city": "大连"}, "rating": 4.2, "durationMin": 90, "ticketPrice": 0.0, "tags": ["海景", "免费"]},
        {"name": "付家庄海水浴场", "location": {"longitude": 121.602, "latitude": 38.857, "address": "大连市西岗区滨海西路", "city": "大连"}, "rating": 4.3, "durationMin": 120, "ticketPrice": 0.0, "tags": ["沙滩", "海水浴", "免费"]},
        {"name": "大连自然博物馆", "location": {"longitude": 121.560, "latitude": 38.872, "address": "大连市沙河口区黑石礁西村街40号", "city": "大连"}, "rating": 4.4, "durationMin": 120, "ticketPrice": 0.0, "tags": ["博物馆", "免费"]},
    ],
    "洛阳": [
        {"name": "丽景门", "location": {"longitude": 112.429, "latitude": 34.663, "address": "洛阳市老城区西大街", "city": "洛阳"}, "rating": 4.3, "durationMin": 90, "ticketPrice": 30.0, "tags": ["历史文化", "美食"]},
        {"name": "天堂明堂景区", "location": {"longitude": 112.451, "latitude": 34.671, "address": "洛阳市老城区中州中路", "city": "洛阳"}, "rating": 4.4, "durationMin": 120, "ticketPrice": 120.0, "tags": ["历史文化", "古建筑"]},
        {"name": "洛邑古城", "location": {"longitude": 112.458, "latitude": 34.656, "address": "洛阳市老城区柳林街", "city": "洛阳"}, "rating": 4.2, "durationMin": 120, "ticketPrice": 0.0, "tags": ["古城", "免费"]},
        {"name": "中国国花园", "location": {"longitude": 112.462, "latitude": 34.628, "address": "洛阳市洛龙区龙门大道", "city": "洛阳"}, "rating": 4.3, "durationMin": 90, "ticketPrice": 0.0, "tags": ["花卉", "牡丹", "免费"]},
    ],
    "敦煌": [
        {"name": "敦煌古城", "location": {"longitude": 94.590, "latitude": 40.106, "address": "敦煌市大漠路", "city": "敦煌"}, "rating": 4.1, "durationMin": 60, "ticketPrice": 40.0, "tags": ["影视", "古城"]},
        {"name": "三危山", "location": {"longitude": 94.850, "latitude": 40.064, "address": "敦煌市莫高窟东南", "city": "敦煌"}, "rating": 4.2, "durationMin": 180, "ticketPrice": 0.0, "tags": ["山岳", "佛教", "免费"]},
        {"name": "敦煌夜市", "location": {"longitude": 94.662, "latitude": 40.141, "address": "敦煌市阳关东路", "city": "敦煌"}, "rating": 4.3, "durationMin": 90, "ticketPrice": 0.0, "tags": ["美食", "夜市", "免费"]},
    ],
    "张家界": [
        {"name": "宝峰湖", "location": {"longitude": 110.416, "latitude": 29.305, "address": "张家界市武陵源区索溪峪镇", "city": "张家界"}, "rating": 4.4, "durationMin": 120, "ticketPrice": 96.0, "tags": ["湖泊", "山水"]},
        {"name": "百龙天梯", "location": {"longitude": 110.434, "latitude": 29.362, "address": "张家界市武陵源区", "city": "张家界"}, "rating": 4.5, "durationMin": 30, "ticketPrice": 72.0, "tags": ["电梯", "观景"]},
        {"name": "芙蓉镇", "location": {"longitude": 109.973, "latitude": 28.947, "address": "张家界市永顺县芙蓉镇", "city": "张家界"}, "rating": 4.5, "durationMin": 180, "ticketPrice": 100.0, "tags": ["古镇", "瀑布"]},
    ],
    "黄山": [
        {"name": "翡翠谷", "location": {"longitude": 118.227, "latitude": 30.104, "address": "黄山市黄山区汤口镇", "city": "黄山"}, "rating": 4.3, "durationMin": 120, "ticketPrice": 75.0, "tags": ["溪流", "自然"]},
        {"name": "花山谜窟", "location": {"longitude": 118.352, "latitude": 29.726, "address": "黄山市屯溪区花山路", "city": "黄山"}, "rating": 4.2, "durationMin": 120, "ticketPrice": 67.0, "tags": ["溶洞", "历史"]},
        {"name": "齐云山", "location": {"longitude": 118.048, "latitude": 29.802, "address": "黄山市休宁县齐云山镇", "city": "黄山"}, "rating": 4.4, "durationMin": 240, "ticketPrice": 68.0, "tags": ["道教", "山岳"]},
        {"name": "新安江山水画廊", "location": {"longitude": 118.441, "latitude": 29.898, "address": "黄山市歙县深渡镇", "city": "黄山"}, "rating": 4.5, "durationMin": 180, "ticketPrice": 128.0, "tags": ["山水", "游船"]},
        {"name": "南屏村", "location": {"longitude": 117.927, "latitude": 30.007, "address": "黄山市黟县碧阳镇", "city": "黄山"}, "rating": 4.3, "durationMin": 90, "ticketPrice": 43.0, "tags": ["古村落", "徽派建筑"]},
        {"name": "卢村木雕楼", "location": {"longitude": 117.947, "latitude": 30.025, "address": "黄山市黟县宏村镇卢村", "city": "黄山"}, "rating": 4.2, "durationMin": 60, "ticketPrice": 50.0, "tags": ["古村落", "木雕"]},
    ],
    "九寨沟": [
        {"name": "甘海子", "location": {"longitude": 103.888, "latitude": 33.230, "address": "九寨沟县漳扎镇甘海子", "city": "九寨沟"}, "rating": 4.2, "durationMin": 60, "ticketPrice": 0.0, "tags": ["草甸", "自然", "免费"]},
        {"name": "神仙池", "location": {"longitude": 103.773, "latitude": 33.387, "address": "九寨沟县大录乡", "city": "九寨沟"}, "rating": 4.3, "durationMin": 180, "ticketPrice": 100.0, "tags": ["钙华", "池塘", "自然"]},
        {"name": "扎如沟", "location": {"longitude": 103.916, "latitude": 33.251, "address": "九寨沟风景区内扎如沟", "city": "九寨沟"}, "rating": 4.1, "durationMin": 120, "ticketPrice": 0.0, "tags": ["原始森林", "藏族文化", "免费"]},
    ],
    "乌鲁木齐": [
        {"name": "新疆维吾尔自治区博物馆", "location": {"longitude": 87.581, "latitude": 43.810, "address": "乌鲁木齐市沙依巴克区西北路132号", "city": "乌鲁木齐"}, "rating": 4.5, "durationMin": 150, "ticketPrice": 0.0, "tags": ["博物馆", "免费"]},
        {"name": "南山牧场", "location": {"longitude": 87.432, "latitude": 43.340, "address": "乌鲁木齐市乌鲁木齐县水西沟镇", "city": "乌鲁木齐"}, "rating": 4.3, "durationMin": 240, "ticketPrice": 30.0, "tags": ["草原", "自然"]},
    ],
    "拉萨": [
        {"name": "罗布林卡", "location": {"longitude": 91.093, "latitude": 29.648, "address": "拉萨市城关区罗布林卡路21号", "city": "拉萨"}, "rating": 4.5, "durationMin": 120, "ticketPrice": 60.0, "tags": ["园林", "世界遗产"]},
        {"name": "西藏博物馆", "location": {"longitude": 91.093, "latitude": 29.643, "address": "拉萨市城关区罗布林卡路19号", "city": "拉萨"}, "rating": 4.4, "durationMin": 120, "ticketPrice": 0.0, "tags": ["博物馆", "免费"]},
        {"name": "药王山摩崖石刻", "location": {"longitude": 91.113, "latitude": 29.653, "address": "拉萨市城关区北京中路", "city": "拉萨"}, "rating": 4.3, "durationMin": 60, "ticketPrice": 0.0, "tags": ["石刻", "佛教", "免费"]},
        {"name": "仓姑寺", "location": {"longitude": 91.133, "latitude": 29.650, "address": "拉萨市城关区林廓南路", "city": "拉萨"}, "rating": 4.2, "durationMin": 60, "ticketPrice": 0.0, "tags": ["寺庙", "免费"]},
        {"name": "拉萨河畔", "location": {"longitude": 91.130, "latitude": 29.640, "address": "拉萨市城关区拉萨河南岸", "city": "拉萨"}, "rating": 4.1, "durationMin": 60, "ticketPrice": 0.0, "tags": ["河流", "休闲", "免费"]},
    ],
    "天津": [
        {"name": "天津博物馆", "location": {"longitude": 117.235, "latitude": 39.083, "address": "天津市河西区平江道62号", "city": "天津"}, "rating": 4.4, "durationMin": 120, "ticketPrice": 0.0, "tags": ["博物馆", "免费"]},
        {"name": "杨柳青古镇", "location": {"longitude": 116.990, "latitude": 39.127, "address": "天津市西青区杨柳青镇", "city": "天津"}, "rating": 4.3, "durationMin": 180, "ticketPrice": 0.0, "tags": ["古镇", "年画", "免费"]},
        {"name": "海河游船", "location": {"longitude": 117.207, "latitude": 39.132, "address": "天津市河北区天津站码头", "city": "天津"}, "rating": 4.4, "durationMin": 60, "ticketPrice": 80.0, "tags": ["游船", "夜景"]},
        {"name": "民园广场", "location": {"longitude": 117.206, "latitude": 39.107, "address": "天津市和平区重庆道83号", "city": "天津"}, "rating": 4.3, "durationMin": 60, "ticketPrice": 0.0, "tags": ["建筑", "休闲", "免费"]},
        {"name": "水上公园", "location": {"longitude": 117.176, "latitude": 39.082, "address": "天津市南开区水上公园路", "city": "天津"}, "rating": 4.2, "durationMin": 120, "ticketPrice": 0.0, "tags": ["公园", "免费"]},
    ],
    "扬州": [
        {"name": "文昌阁", "location": {"longitude": 119.428, "latitude": 32.392, "address": "扬州市广陵区文昌中路", "city": "扬州"}, "rating": 4.1, "durationMin": 30, "ticketPrice": 0.0, "tags": ["地标", "免费"]},
        {"name": "朱自清故居", "location": {"longitude": 119.441, "latitude": 32.396, "address": "扬州市广陵区安乐巷27号", "city": "扬州"}, "rating": 4.2, "durationMin": 60, "ticketPrice": 0.0, "tags": ["名人故居", "免费"]},
        {"name": "茱萸湾风景区", "location": {"longitude": 119.533, "latitude": 32.398, "address": "扬州市邗江区湾头镇", "city": "扬州"}, "rating": 4.3, "durationMin": 180, "ticketPrice": 30.0, "tags": ["自然", "动物"]},
        {"name": "扬州汉陵苑", "location": {"longitude": 119.408, "latitude": 32.368, "address": "扬州市邗江区平山堂东路", "city": "扬州"}, "rating": 4.3, "durationMin": 90, "ticketPrice": 30.0, "tags": ["历史文化", "古墓"]},
        {"name": "凤凰岛生态旅游区", "location": {"longitude": 119.489, "latitude": 32.441, "address": "扬州市邗江区泰安镇", "city": "扬州"}, "rating": 4.2, "durationMin": 180, "ticketPrice": 45.0, "tags": ["湿地", "自然"]},
    ],
    "绍兴": [
        {"name": "仓桥直街", "location": {"longitude": 120.577, "latitude": 29.987, "address": "绍兴市越城区仓桥直街", "city": "绍兴"}, "rating": 4.3, "durationMin": 90, "ticketPrice": 0.0, "tags": ["古街", "免费"]},
        {"name": "大禹陵", "location": {"longitude": 120.603, "latitude": 29.950, "address": "绍兴市越城区稽山路", "city": "绍兴"}, "rating": 4.3, "durationMin": 90, "ticketPrice": 50.0, "tags": ["历史文化", "祭祀"]},
        {"name": "安昌古镇", "location": {"longitude": 120.479, "latitude": 30.038, "address": "绍兴市柯桥区安昌镇", "city": "绍兴"}, "rating": 4.4, "durationMin": 180, "ticketPrice": 50.0, "tags": ["古镇", "水乡"]},
        {"name": "秋瑾故居", "location": {"longitude": 120.578, "latitude": 29.993, "address": "绍兴市越城区和畅堂", "city": "绍兴"}, "rating": 4.2, "durationMin": 60, "ticketPrice": 0.0, "tags": ["名人故居", "免费"]},
        {"name": "会稽山", "location": {"longitude": 120.612, "latitude": 29.936, "address": "绍兴市越城区稽山路", "city": "绍兴"}, "rating": 4.3, "durationMin": 180, "ticketPrice": 0.0, "tags": ["山岳", "历史", "免费"]},
    ],
    "西宁": [
        {"name": "北禅寺", "location": {"longitude": 101.761, "latitude": 36.651, "address": "西宁市城北区祁连路", "city": "西宁"}, "rating": 4.2, "durationMin": 90, "ticketPrice": 5.0, "tags": ["寺庙", "悬空寺"]},
        {"name": "南山公园", "location": {"longitude": 101.776, "latitude": 36.607, "address": "西宁市城中区南山路", "city": "西宁"}, "rating": 4.1, "durationMin": 90, "ticketPrice": 0.0, "tags": ["公园", "观景", "免费"]},
        {"name": "日月山", "location": {"longitude": 101.103, "latitude": 36.720, "address": "西宁市湟源县日月乡", "city": "西宁"}, "rating": 4.3, "durationMin": 90, "ticketPrice": 40.0, "tags": ["山岳", "历史"]},
        {"name": "丹噶尔古城", "location": {"longitude": 101.254, "latitude": 36.691, "address": "西宁市湟源县城关镇", "city": "西宁"}, "rating": 4.2, "durationMin": 120, "ticketPrice": 40.0, "tags": ["古城", "茶马古道"]},
    ],
    "太原": [
        {"name": "太原古县城", "location": {"longitude": 112.515, "latitude": 37.753, "address": "太原市晋源区古城营村", "city": "太原"}, "rating": 4.3, "durationMin": 180, "ticketPrice": 0.0, "tags": ["古城", "免费"]},
        {"name": "汾河公园", "location": {"longitude": 112.549, "latitude": 37.868, "address": "太原市万柏林区滨河西路", "city": "太原"}, "rating": 4.2, "durationMin": 90, "ticketPrice": 0.0, "tags": ["公园", "河流", "免费"]},
        {"name": "太原食品街", "location": {"longitude": 112.563, "latitude": 37.870, "address": "太原市杏花岭区鼓楼街", "city": "太原"}, "rating": 4.1, "durationMin": 90, "ticketPrice": 0.0, "tags": ["美食", "免费"]},
        {"name": "中国煤炭博物馆", "location": {"longitude": 112.546, "latitude": 37.864, "address": "太原市万柏林区迎泽西大街2号", "city": "太原"}, "rating": 4.3, "durationMin": 120, "ticketPrice": 60.0, "tags": ["博物馆", "工业"]},
    ],
    "长春": [
        {"name": "南湖公园", "location": {"longitude": 125.342, "latitude": 43.841, "address": "长春市朝阳区工农大路51号", "city": "长春"}, "rating": 4.2, "durationMin": 90, "ticketPrice": 0.0, "tags": ["公园", "免费"]},
        {"name": "东北虎园", "location": {"longitude": 125.503, "latitude": 43.774, "address": "长春市南关区净月大街10500号", "city": "长春"}, "rating": 4.4, "durationMin": 120, "ticketPrice": 105.0, "tags": ["动物", "亲子"]},
        {"name": "长春文庙", "location": {"longitude": 125.341, "latitude": 43.874, "address": "长春市南关区东天街", "city": "长春"}, "rating": 4.1, "durationMin": 60, "ticketPrice": 0.0, "tags": ["历史文化", "免费"]},
        {"name": "般若寺", "location": {"longitude": 125.348, "latitude": 43.865, "address": "长春市南关区长春大街", "city": "长春"}, "rating": 4.2, "durationMin": 60, "ticketPrice": 0.0, "tags": ["寺庙", "免费"]},
    ],
    "泉州": [
        {"name": "西街", "location": {"longitude": 118.590, "latitude": 24.908, "address": "泉州市鲤城区西街", "city": "泉州"}, "rating": 4.4, "durationMin": 90, "ticketPrice": 0.0, "tags": ["古街", "美食", "免费"]},
        {"name": "泉州海外交通史博物馆", "location": {"longitude": 118.607, "latitude": 24.908, "address": "泉州市丰泽区东湖街425号", "city": "泉州"}, "rating": 4.5, "durationMin": 120, "ticketPrice": 0.0, "tags": ["博物馆", "海丝", "免费"]},
        {"name": "承天寺", "location": {"longitude": 118.595, "latitude": 24.900, "address": "泉州市鲤城区承天巷", "city": "泉州"}, "rating": 4.3, "durationMin": 60, "ticketPrice": 0.0, "tags": ["寺庙", "免费"]},
        {"name": "关帝庙", "location": {"longitude": 118.593, "latitude": 24.899, "address": "泉州市鲤城区涂门街", "city": "泉州"}, "rating": 4.3, "durationMin": 30, "ticketPrice": 0.0, "tags": ["寺庙", "免费"]},
    ],
    "银川": [
        {"name": "中华回乡文化园", "location": {"longitude": 106.400, "latitude": 38.507, "address": "银川市永宁县纳家户村", "city": "银川"}, "rating": 4.3, "durationMin": 120, "ticketPrice": 60.0, "tags": ["民族文化", "建筑"]},
        {"name": "水洞沟遗址", "location": {"longitude": 106.629, "latitude": 38.371, "address": "银川市灵武市临河镇", "city": "银川"}, "rating": 4.4, "durationMin": 240, "ticketPrice": 60.0, "tags": ["遗址", "历史"]},
        {"name": "鸣翠湖国家湿地公园", "location": {"longitude": 106.333, "latitude": 38.448, "address": "银川市兴庆区掌政镇", "city": "银川"}, "rating": 4.2, "durationMin": 120, "ticketPrice": 30.0, "tags": ["湿地", "观鸟"]},
    ],
    "济南": [
        {"name": "泉城广场", "location": {"longitude": 117.022, "latitude": 36.649, "address": "济南市历下区泺源大街", "city": "济南"}, "rating": 4.2, "durationMin": 60, "ticketPrice": 0.0, "tags": ["地标", "免费"]},
        {"name": "曲水亭街", "location": {"longitude": 117.026, "latitude": 36.671, "address": "济南市历下区曲水亭街", "city": "济南"}, "rating": 4.4, "durationMin": 60, "ticketPrice": 0.0, "tags": ["古街", "泉水", "免费"]},
        {"name": "九如山瀑布群", "location": {"longitude": 117.385, "latitude": 36.463, "address": "济南市历城区西营镇", "city": "济南"}, "rating": 4.3, "durationMin": 240, "ticketPrice": 120.0, "tags": ["瀑布", "山岳", "自然"]},
    ],
    "合肥": [
        {"name": "安徽博物院", "location": {"longitude": 117.287, "latitude": 31.815, "address": "合肥市蜀山区怀宁路268号", "city": "合肥"}, "rating": 4.4, "durationMin": 120, "ticketPrice": 0.0, "tags": ["博物馆", "免费"]},
        {"name": "李鸿章故居", "location": {"longitude": 117.281, "latitude": 31.862, "address": "合肥市庐阳区淮河路步行街", "city": "合肥"}, "rating": 4.3, "durationMin": 60, "ticketPrice": 0.0, "tags": ["名人故居", "免费"]},
        {"name": "巢湖风景区", "location": {"longitude": 117.575, "latitude": 31.600, "address": "合肥市巢湖市", "city": "合肥"}, "rating": 4.3, "durationMin": 240, "ticketPrice": 0.0, "tags": ["湖泊", "自然", "免费"]},
    ],
    "兰州": [
        {"name": "水车博览园", "location": {"longitude": 103.826, "latitude": 36.057, "address": "兰州市城关区南滨河东路524号", "city": "兰州"}, "rating": 4.2, "durationMin": 60, "ticketPrice": 0.0, "tags": ["文化", "免费"]},
        {"name": "兴隆山", "location": {"longitude": 104.005, "latitude": 35.787, "address": "兰州市榆中县城西南", "city": "兰州"}, "rating": 4.3, "durationMin": 240, "ticketPrice": 40.0, "tags": ["山岳", "自然"]},
        {"name": "正宁路夜市", "location": {"longitude": 103.821, "latitude": 36.057, "address": "兰州市城关区正宁路", "city": "兰州"}, "rating": 4.4, "durationMin": 90, "ticketPrice": 0.0, "tags": ["美食", "夜市", "免费"]},
    ],
    "福州": [
        {"name": "西湖公园", "location": {"longitude": 119.278, "latitude": 26.098, "address": "福州市鼓楼区湖滨路70号", "city": "福州"}, "rating": 4.3, "durationMin": 90, "ticketPrice": 0.0, "tags": ["公园", "免费"]},
        {"name": "林则徐纪念馆", "location": {"longitude": 119.302, "latitude": 26.078, "address": "福州市鼓楼区澳门路16号", "city": "福州"}, "rating": 4.4, "durationMin": 60, "ticketPrice": 0.0, "tags": ["纪念馆", "免费"]},
        {"name": "烟台山公园", "location": {"longitude": 119.301, "latitude": 26.060, "address": "福州市仓山区烟台路", "city": "福州"}, "rating": 4.3, "durationMin": 90, "ticketPrice": 0.0, "tags": ["公园", "历史建筑", "免费"]},
    ],
    "宁波": [
        {"name": "鼓楼步行街", "location": {"longitude": 121.555, "latitude": 29.873, "address": "宁波市海曙区中山路", "city": "宁波"}, "rating": 4.2, "durationMin": 90, "ticketPrice": 0.0, "tags": ["购物", "美食", "免费"]},
        {"name": "宁波博物院", "location": {"longitude": 121.601, "latitude": 29.826, "address": "宁波市鄞州区首南中路1000号", "city": "宁波"}, "rating": 4.5, "durationMin": 120, "ticketPrice": 0.0, "tags": ["博物馆", "建筑", "免费"]},
        {"name": "月湖公园", "location": {"longitude": 121.548, "latitude": 29.865, "address": "宁波市海曙区月湖公园", "city": "宁波"}, "rating": 4.2, "durationMin": 60, "ticketPrice": 0.0, "tags": ["公园", "免费"]},
    ],
    "威海": [
        {"name": "那香海景区", "location": {"longitude": 122.194, "latitude": 37.200, "address": "威海市荣成市环海路", "city": "威海"}, "rating": 4.3, "durationMin": 180, "ticketPrice": 0.0, "tags": ["海景", "免费"]},
        {"name": "威海公园", "location": {"longitude": 122.113, "latitude": 37.502, "address": "威海市环翠区海滨北路", "city": "威海"}, "rating": 4.2, "durationMin": 90, "ticketPrice": 0.0, "tags": ["公园", "海景", "免费"]},
        {"name": "定远舰景区", "location": {"longitude": 122.119, "latitude": 37.501, "address": "威海市环翠区海滨北路", "city": "威海"}, "rating": 4.3, "durationMin": 60, "ticketPrice": 75.0, "tags": ["军事", "历史"]},
    ],
    "呼和浩特": [
        {"name": "昭君墓", "location": {"longitude": 111.638, "latitude": 40.738, "address": "呼和浩特市玉泉区昭君路", "city": "呼和浩特"}, "rating": 4.2, "durationMin": 90, "ticketPrice": 65.0, "tags": ["历史文化", "古迹"]},
        {"name": "五塔寺", "location": {"longitude": 111.684, "latitude": 40.790, "address": "呼和浩特市玉泉区五塔寺后街", "city": "呼和浩特"}, "rating": 4.3, "durationMin": 60, "ticketPrice": 35.0, "tags": ["寺庙", "建筑"]},
    ],
    "南昌": [
        {"name": "南昌之星摩天轮", "location": {"longitude": 115.811, "latitude": 28.639, "address": "南昌市红谷滩区赣江南大道", "city": "南昌"}, "rating": 4.3, "durationMin": 60, "ticketPrice": 50.0, "tags": ["地标", "夜景"]},
        {"name": "梅岭风景区", "location": {"longitude": 115.763, "latitude": 28.770, "address": "南昌市湾里区梅岭镇", "city": "南昌"}, "rating": 4.4, "durationMin": 240, "ticketPrice": 0.0, "tags": ["山岳", "自然", "免费"]},
    ],
    "沈阳": [
        {"name": "九一八历史博物馆", "location": {"longitude": 123.465, "latitude": 41.832, "address": "沈阳市大东区望花南街46号", "city": "沈阳"}, "rating": 4.5, "durationMin": 120, "ticketPrice": 0.0, "tags": ["博物馆", "爱国主义", "免费"]},
        {"name": "北陵公园", "location": {"longitude": 123.397, "latitude": 41.837, "address": "沈阳市皇姑区泰山路12号", "city": "沈阳"}, "rating": 4.4, "durationMin": 120, "ticketPrice": 6.0, "tags": ["历史文化", "皇陵", "公园"]},
    ],
    "无锡": [
        {"name": "南禅寺", "location": {"longitude": 120.311, "latitude": 31.563, "address": "无锡市梁溪区南长街", "city": "无锡"}, "rating": 4.2, "durationMin": 60, "ticketPrice": 0.0, "tags": ["寺庙", "美食", "免费"]},
        {"name": "荡口古镇", "location": {"longitude": 120.478, "latitude": 31.564, "address": "无锡市锡山区鹅湖镇", "city": "无锡"}, "rating": 4.3, "durationMin": 180, "ticketPrice": 0.0, "tags": ["古镇", "水乡", "免费"]},
    ],
    "开封": [
        {"name": "大相国寺", "location": {"longitude": 114.355, "latitude": 34.796, "address": "开封市鼓楼区自由路36号", "city": "开封"}, "rating": 4.3, "durationMin": 60, "ticketPrice": 40.0, "tags": ["寺庙", "历史文化"]},
        {"name": "包公祠", "location": {"longitude": 114.361, "latitude": 34.781, "address": "开封市鼓楼区向阳路1号", "city": "开封"}, "rating": 4.2, "durationMin": 60, "ticketPrice": 30.0, "tags": ["历史文化", "名人"]},
    ],
    "岳阳": [
        {"name": "张谷英村", "location": {"longitude": 113.320, "latitude": 28.967, "address": "岳阳市岳阳县张谷英镇", "city": "岳阳"}, "rating": 4.3, "durationMin": 180, "ticketPrice": 50.0, "tags": ["古村落", "建筑"]},
        {"name": "南湖广场", "location": {"longitude": 113.130, "latitude": 29.348, "address": "岳阳市岳阳楼区南湖大道", "city": "岳阳"}, "rating": 4.1, "durationMin": 60, "ticketPrice": 0.0, "tags": ["休闲", "免费"]},
    ],
    "镇江": [
        {"name": "句容茅山风景区", "location": {"longitude": 119.112, "latitude": 31.923, "address": "镇江市句容市茅山镇", "city": "镇江"}, "rating": 4.4, "durationMin": 240, "ticketPrice": 90.0, "tags": ["道教", "山岳"]},
    ],
    "十堰": [
        {"name": "太极湖", "location": {"longitude": 110.943, "latitude": 32.605, "address": "十堰市丹江口市太极湖旅游区", "city": "十堰"}, "rating": 4.3, "durationMin": 180, "ticketPrice": 120.0, "tags": ["湖泊", "山水"]},
    ],
    "金华": [
        {"name": "横店影视城梦幻谷", "location": {"longitude": 120.283, "latitude": 29.063, "address": "金华市东阳市横店镇", "city": "金华"}, "rating": 4.4, "durationMin": 240, "ticketPrice": 195.0, "tags": ["游乐", "影视"]},
    ],
    "嘉兴": [
        {"name": "梅花洲景区", "location": {"longitude": 120.838, "latitude": 30.659, "address": "嘉兴市南湖区凤桥镇", "city": "嘉兴"}, "rating": 4.3, "durationMin": 180, "ticketPrice": 45.0, "tags": ["古镇", "梅花"]},
    ],
    "玉溪": [
        {"name": "映月潭休闲文化中心", "location": {"longitude": 102.517, "latitude": 24.380, "address": "玉溪市红塔区", "city": "玉溪"}, "rating": 4.1, "durationMin": 120, "ticketPrice": 0.0, "tags": ["休闲", "温泉", "免费"]},
    ],
    "迪庆": [
        {"name": "纳帕海", "location": {"longitude": 99.633, "latitude": 27.855, "address": "迪庆州香格里拉市纳帕海", "city": "迪庆"}, "rating": 4.4, "durationMin": 120, "ticketPrice": 0.0, "tags": ["湿地", "草甸", "免费"]},
    ],
    "乐山": [
        {"name": "峨眉山", "location": {"longitude": 103.332, "latitude": 29.522, "address": "乐山市峨眉山市黄湾镇", "city": "乐山"}, "rating": 4.8, "durationMin": 480, "ticketPrice": 160.0, "tags": ["佛教", "山岳", "世界遗产"]},
    ],
}

EXTRA_REPLACEMENTS = {
    "成都": [
        {"name": "安仁古镇", "location": {"longitude": 103.836, "latitude": 30.520, "address": "成都市大邑县安仁镇", "city": "成都"}, "rating": 4.4, "durationMin": 180, "ticketPrice": 0.0, "tags": ["古镇", "博物馆", "免费"]},
        {"name": "鸳鸯楼", "location": {"longitude": 104.048, "latitude": 30.645, "address": "成都市武侯区红瓦寺街18号", "city": "成都"}, "rating": 4.0, "durationMin": 30, "ticketPrice": 0.0, "tags": ["网红打卡", "建筑", "免费"]},
        {"name": "浣花溪公园", "location": {"longitude": 104.035, "latitude": 30.652, "address": "成都市青羊区青华路9号", "city": "成都"}, "rating": 4.4, "durationMin": 90, "ticketPrice": 0.0, "tags": ["公园", "免费"]},
        {"name": "五凤溪古镇", "location": {"longitude": 104.384, "latitude": 30.568, "address": "成都市金堂县五凤镇", "city": "成都"}, "rating": 4.1, "durationMin": 120, "ticketPrice": 0.0, "tags": ["古镇", "免费"]},
        {"name": "平乐古镇", "location": {"longitude": 103.395, "latitude": 30.239, "address": "成都市邛崃市平乐镇", "city": "成都"}, "rating": 4.2, "durationMin": 180, "ticketPrice": 0.0, "tags": ["古镇", "免费"]},
        {"name": "文殊坊", "location": {"longitude": 104.067, "latitude": 30.679, "address": "成都市青羊区文殊坊街", "city": "成都"}, "rating": 4.1, "durationMin": 60, "ticketPrice": 0.0, "tags": ["美食", "文化", "免费"]},
        {"name": "成都欢乐谷", "location": {"longitude": 104.138, "latitude": 30.657, "address": "成都市金牛区西华大道16号", "city": "成都"}, "rating": 4.3, "durationMin": 360, "ticketPrice": 230.0, "tags": ["游乐", "亲子"]},
        {"name": "成都国际金融中心", "location": {"longitude": 104.081, "latitude": 30.650, "address": "成都市锦江区红星路三段1号", "city": "成都"}, "rating": 4.2, "durationMin": 120, "ticketPrice": 0.0, "tags": ["购物", "地标", "免费"]},
        {"name": "街子古镇", "location": {"longitude": 103.599, "latitude": 30.733, "address": "成都市崇州市街子镇", "city": "成都"}, "rating": 4.3, "durationMin": 180, "ticketPrice": 0.0, "tags": ["古镇", "免费"]},
        {"name": "新都桂湖", "location": {"longitude": 104.152, "latitude": 30.827, "address": "成都市新都区桂湖中路109号", "city": "成都"}, "rating": 4.1, "durationMin": 90, "ticketPrice": 20.0, "tags": ["园林", "荷花"]},
    ],
    "三亚": [
        {"name": "亚特兰蒂斯水世界", "location": {"longitude": 109.715, "latitude": 18.270, "address": "三亚市海棠区海棠北路", "city": "三亚"}, "rating": 4.5, "durationMin": 300, "ticketPrice": 298.0, "tags": ["水上乐园", "亲子"]},
        {"name": "海昌梦幻海洋不夜城", "location": {"longitude": 109.713, "latitude": 18.268, "address": "三亚市海棠区海棠北路", "city": "三亚"}, "rating": 4.2, "durationMin": 180, "ticketPrice": 0.0, "tags": ["海洋", "夜景", "免费"]},
        {"name": "太阳湾", "location": {"longitude": 109.672, "latitude": 18.207, "address": "三亚市吉阳区太阳湾路", "city": "三亚"}, "rating": 4.4, "durationMin": 120, "ticketPrice": 0.0, "tags": ["沙滩", "海景", "免费"]},
        {"name": "椰梦长廊", "location": {"longitude": 109.371, "latitude": 18.254, "address": "三亚市天涯区三亚湾路", "city": "三亚"}, "rating": 4.3, "durationMin": 60, "ticketPrice": 0.0, "tags": ["日落", "海景", "免费"]},
        {"name": "三亚湾", "location": {"longitude": 109.395, "latitude": 18.259, "address": "三亚市天涯区三亚湾海坡", "city": "三亚"}, "rating": 4.2, "durationMin": 120, "ticketPrice": 0.0, "tags": ["沙滩", "免费"]},
        {"name": "分界洲岛", "location": {"longitude": 109.873, "latitude": 18.381, "address": "三亚市陵水县光坡镇", "city": "三亚"}, "rating": 4.5, "durationMin": 300, "ticketPrice": 132.0, "tags": ["海岛", "潜水"]},
        {"name": "皇后湾", "location": {"longitude": 109.704, "latitude": 18.195, "address": "三亚市吉阳区海棠区", "city": "三亚"}, "rating": 4.3, "durationMin": 180, "ticketPrice": 0.0, "tags": ["沙滩", "浮潜", "免费"]},
        {"name": "三亚宋城浪浪浪水公园", "location": {"longitude": 109.430, "latitude": 18.277, "address": "三亚市吉阳区迎宾路", "city": "三亚"}, "rating": 4.2, "durationMin": 240, "ticketPrice": 198.0, "tags": ["水上乐园", "亲子"]},
    ],
    "桂林": [
        {"name": "芦笛岩", "location": {"longitude": 110.258, "latitude": 25.314, "address": "桂林市秀峰区芦笛路", "city": "桂林"}, "rating": 4.5, "durationMin": 90, "ticketPrice": 90.0, "tags": ["溶洞", "自然"]},
        {"name": "冠岩", "location": {"longitude": 110.373, "latitude": 25.172, "address": "桂林市雁山区草坪镇", "city": "桂林"}, "rating": 4.3, "durationMin": 120, "ticketPrice": 60.0, "tags": ["溶洞", "自然"]},
        {"name": "世外桃源", "location": {"longitude": 110.370, "latitude": 24.828, "address": "桂林市阳朔县白沙镇", "city": "桂林"}, "rating": 4.4, "durationMin": 120, "ticketPrice": 65.0, "tags": ["田园", "自然"]},
        {"name": "银子岩", "location": {"longitude": 110.427, "latitude": 24.710, "address": "桂林市荔浦市马岭镇", "city": "桂林"}, "rating": 4.4, "durationMin": 90, "ticketPrice": 65.0, "tags": ["溶洞", "自然"]},
        {"name": "七星公园", "location": {"longitude": 110.321, "latitude": 25.266, "address": "桂林市七星区自由路", "city": "桂林"}, "rating": 4.2, "durationMin": 120, "ticketPrice": 55.0, "tags": ["公园", "自然"]},
        {"name": "靖江王城", "location": {"longitude": 110.295, "latitude": 25.280, "address": "桂林市秀峰区王城路1号", "city": "桂林"}, "rating": 4.4, "durationMin": 90, "ticketPrice": 100.0, "tags": ["历史文化"]},
        {"name": "相公山", "location": {"longitude": 110.440, "latitude": 24.873, "address": "桂林市阳朔县兴坪镇", "city": "桂林"}, "rating": 4.5, "durationMin": 120, "ticketPrice": 60.0, "tags": ["山岳", "观景"]},
        {"name": "古东瀑布", "location": {"longitude": 110.416, "latitude": 25.186, "address": "桂林市灵川县大圩镇", "city": "桂林"}, "rating": 4.3, "durationMin": 180, "ticketPrice": 75.0, "tags": ["瀑布", "户外"]},
    ],
    "扬州": [
        {"name": "汉广陵王墓", "location": {"longitude": 119.447, "latitude": 32.380, "address": "扬州市邗江区平山堂路", "city": "扬州"}, "rating": 4.3, "durationMin": 60, "ticketPrice": 30.0, "tags": ["历史", "古墓"]},
        {"name": "史公祠", "location": {"longitude": 119.448, "latitude": 32.391, "address": "扬州市广陵区广储门外街24号", "city": "扬州"}, "rating": 4.2, "durationMin": 60, "ticketPrice": 0.0, "tags": ["纪念馆", "免费"]},
        {"name": "高邮湖", "location": {"longitude": 119.451, "latitude": 32.782, "address": "扬州市高邮市", "city": "扬州"}, "rating": 4.3, "durationMin": 120, "ticketPrice": 0.0, "tags": ["湖泊", "自然", "免费"]},
        {"name": "仙鹤寺", "location": {"longitude": 119.430, "latitude": 32.394, "address": "扬州市广陵区南门街", "city": "扬州"}, "rating": 4.1, "durationMin": 30, "ticketPrice": 0.0, "tags": ["寺庙", "伊斯兰", "免费"]},
        {"name": "扬州古运河", "location": {"longitude": 119.437, "latitude": 32.387, "address": "扬州市广陵区便益门", "city": "扬州"}, "rating": 4.3, "durationMin": 60, "ticketPrice": 0.0, "tags": ["运河", "夜景", "免费"]},
        {"name": "二分明月楼", "location": {"longitude": 119.432, "latitude": 32.398, "address": "扬州市广陵区广陵路", "city": "扬州"}, "rating": 4.0, "durationMin": 30, "ticketPrice": 0.0, "tags": ["古建筑", "免费"]},
        {"name": "宝应湖国家湿地", "location": {"longitude": 119.303, "latitude": 33.289, "address": "扬州市宝应县", "city": "扬州"}, "rating": 4.2, "durationMin": 180, "ticketPrice": 0.0, "tags": ["湿地", "自然", "免费"]},
        {"name": "四望亭", "location": {"longitude": 119.430, "latitude": 32.393, "address": "扬州市广陵区四望亭路", "city": "扬州"}, "rating": 4.0, "durationMin": 20, "ticketPrice": 0.0, "tags": ["古建筑", "免费"]},
    ],
    "深圳": [
        {"name": "深圳大学", "location": {"longitude": 113.933, "latitude": 22.534, "address": "深圳市南山区南海大道3688号", "city": "深圳"}, "rating": 4.2, "durationMin": 90, "ticketPrice": 0.0, "tags": ["校园", "免费"]},
        {"name": "南头古城", "location": {"longitude": 113.919, "latitude": 22.543, "address": "深圳市南山区南头天桥北", "city": "深圳"}, "rating": 4.3, "durationMin": 120, "ticketPrice": 0.0, "tags": ["古城", "免费"]},
        {"name": "深圳中心书城", "location": {"longitude": 114.055, "latitude": 22.544, "address": "深圳市福田区福中一路", "city": "深圳"}, "rating": 4.3, "durationMin": 120, "ticketPrice": 0.0, "tags": ["文化", "免费"]},
        {"name": "较场尾", "location": {"longitude": 114.494, "latitude": 22.538, "address": "深圳市大鹏新区大鹏镇", "city": "深圳"}, "rating": 4.2, "durationMin": 240, "ticketPrice": 0.0, "tags": ["海滨", "民宿", "免费"]},
        {"name": "杨梅坑", "location": {"longitude": 114.542, "latitude": 22.546, "address": "深圳市大鹏新区南澳街道", "city": "深圳"}, "rating": 4.4, "durationMin": 180, "ticketPrice": 0.0, "tags": ["骑行", "海景", "免费"]},
        {"name": "深圳湾体育中心春茧", "location": {"longitude": 113.957, "latitude": 22.516, "address": "深圳市南山区滨海大道", "city": "深圳"}, "rating": 4.2, "durationMin": 60, "ticketPrice": 0.0, "tags": ["建筑", "免费"]},
    ],
    "丽江": [
        {"name": "木府", "location": {"longitude": 100.231, "latitude": 26.872, "address": "丽江市古城区光义街官院巷49号", "city": "丽江"}, "rating": 4.5, "durationMin": 90, "ticketPrice": 40.0, "tags": ["历史文化", "古建筑"]},
        {"name": "石鼓镇", "location": {"longitude": 99.877, "latitude": 26.951, "address": "丽江市玉龙县石鼓镇", "city": "丽江"}, "rating": 4.2, "durationMin": 120, "ticketPrice": 0.0, "tags": ["古镇", "长江第一湾", "免费"]},
        {"name": "蓝月谷", "location": {"longitude": 100.190, "latitude": 27.097, "address": "丽江市玉龙县甘海子以北", "city": "丽江"}, "rating": 4.6, "durationMin": 120, "ticketPrice": 0.0, "tags": ["湖泊", "雪山", "免费"]},
        {"name": "大研花巷", "location": {"longitude": 100.232, "latitude": 26.876, "address": "丽江市古城区大研花巷", "city": "丽江"}, "rating": 4.1, "durationMin": 60, "ticketPrice": 0.0, "tags": ["商业街", "免费"]},
        {"name": "玉水寨", "location": {"longitude": 100.190, "latitude": 26.985, "address": "丽江市玉龙县白沙镇", "city": "丽江"}, "rating": 4.2, "durationMin": 90, "ticketPrice": 50.0, "tags": ["纳西族", "水源"]},
        {"name": "长江第一湾观景台", "location": {"longitude": 99.862, "latitude": 26.898, "address": "丽江市玉龙县石鼓镇", "city": "丽江"}, "rating": 4.3, "durationMin": 30, "ticketPrice": 0.0, "tags": ["观景", "自然", "免费"]},
    ],
    "大理": [
        {"name": "严家大院", "location": {"longitude": 100.092, "latitude": 25.795, "address": "大理市喜洲镇四方街", "city": "大理"}, "rating": 4.2, "durationMin": 60, "ticketPrice": 30.0, "tags": ["白族", "古建筑"]},
        {"name": "龙龛码头", "location": {"longitude": 100.142, "latitude": 25.670, "address": "大理市下关镇龙龛村", "city": "大理"}, "rating": 4.1, "durationMin": 60, "ticketPrice": 0.0, "tags": ["洱海", "日出", "免费"]},
        {"name": "诺邓古村", "location": {"longitude": 99.488, "latitude": 25.866, "address": "大理州云龙县诺邓镇", "city": "大理"}, "rating": 4.4, "durationMin": 240, "ticketPrice": 0.0, "tags": ["古村落", "白族", "免费"]},
        {"name": "周城扎染村", "location": {"longitude": 100.107, "latitude": 25.771, "address": "大理市喜洲镇周城村", "city": "大理"}, "rating": 4.2, "durationMin": 90, "ticketPrice": 0.0, "tags": ["手工艺", "扎染", "免费"]},
        {"name": "石宝山", "location": {"longitude": 99.875, "latitude": 26.275, "address": "大理州剑川县沙溪镇", "city": "大理"}, "rating": 4.3, "durationMin": 240, "ticketPrice": 50.0, "tags": ["石窟", "自然"]},
        {"name": "蝴蝶泉", "location": {"longitude": 100.090, "latitude": 25.836, "address": "大理市喜洲镇蝴蝶泉路", "city": "大理"}, "rating": 4.1, "durationMin": 60, "ticketPrice": 40.0, "tags": ["自然", "泉水"]},
    ],
    "苏州": [
        {"name": "甪直古镇", "location": {"longitude": 120.982, "latitude": 31.272, "address": "苏州市吴中区甪直镇", "city": "苏州"}, "rating": 4.3, "durationMin": 180, "ticketPrice": 78.0, "tags": ["古镇", "水乡"]},
        {"name": "留园", "location": {"longitude": 120.586, "latitude": 31.318, "address": "苏州市姑苏区留园路338号", "city": "苏州"}, "rating": 4.6, "durationMin": 90, "ticketPrice": 55.0, "tags": ["园林", "世界遗产"]},
        {"name": "山塘街", "location": {"longitude": 120.597, "latitude": 31.319, "address": "苏州市姑苏区山塘街", "city": "苏州"}, "rating": 4.4, "durationMin": 90, "ticketPrice": 0.0, "tags": ["古街", "免费"]},
        {"name": "沧浪亭", "location": {"longitude": 120.630, "latitude": 31.289, "address": "苏州市姑苏区沧浪亭街3号", "city": "苏州"}, "rating": 4.4, "durationMin": 60, "ticketPrice": 20.0, "tags": ["园林", "世界遗产"]},
        {"name": "盘门景区", "location": {"longitude": 120.614, "latitude": 31.283, "address": "苏州市姑苏区东大街49号", "city": "苏州"}, "rating": 4.3, "durationMin": 90, "ticketPrice": 40.0, "tags": ["历史文化", "古建筑"]},
        {"name": "同里古镇", "location": {"longitude": 120.737, "latitude": 31.128, "address": "苏州市吴江区同里镇", "city": "苏州"}, "rating": 4.4, "durationMin": 240, "ticketPrice": 100.0, "tags": ["古镇", "水乡"]},
    ],
    "珠海": [
        {"name": "海滨公园", "location": {"longitude": 113.568, "latitude": 22.247, "address": "珠海市香洲区海滨南路", "city": "珠海"}, "rating": 4.2, "durationMin": 60, "ticketPrice": 0.0, "tags": ["公园", "免费"]},
        {"name": "景山公园", "location": {"longitude": 113.566, "latitude": 22.257, "address": "珠海市香洲区景山路", "city": "珠海"}, "rating": 4.1, "durationMin": 90, "ticketPrice": 0.0, "tags": ["公园", "观景", "免费"]},
        {"name": "庙湾岛", "location": {"longitude": 114.230, "latitude": 22.197, "address": "珠海市万山区庙湾岛", "city": "珠海"}, "rating": 4.5, "durationMin": 480, "ticketPrice": 0.0, "tags": ["海岛", "潜水", "免费"]},
        {"name": "珠海博物馆", "location": {"longitude": 113.559, "latitude": 22.253, "address": "珠海市香洲区景乐路", "city": "珠海"}, "rating": 4.2, "durationMin": 90, "ticketPrice": 0.0, "tags": ["博物馆", "免费"]},
        {"name": "唐家湾古镇", "location": {"longitude": 113.620, "latitude": 22.367, "address": "珠海市香洲区唐家镇", "city": "珠海"}, "rating": 4.2, "durationMin": 120, "ticketPrice": 0.0, "tags": ["古镇", "免费"]},
        {"name": "横琴花海长廊", "location": {"longitude": 113.531, "latitude": 22.098, "address": "珠海市横琴新区", "city": "珠海"}, "rating": 4.1, "durationMin": 60, "ticketPrice": 0.0, "tags": ["花卉", "免费"]},
    ],
    "黄山": [
        {"name": "棠樾牌坊群", "location": {"longitude": 118.480, "latitude": 29.897, "address": "黄山市歙县棠樾村", "city": "黄山"}, "rating": 4.4, "durationMin": 90, "ticketPrice": 100.0, "tags": ["牌坊", "徽文化"]},
        {"name": "徽州古城", "location": {"longitude": 118.435, "latitude": 29.870, "address": "黄山市歙县徽城镇", "city": "黄山"}, "rating": 4.3, "durationMin": 180, "ticketPrice": 80.0, "tags": ["古城", "徽文化"]},
        {"name": "渔梁坝", "location": {"longitude": 118.445, "latitude": 29.852, "address": "黄山市歙县渔梁镇", "city": "黄山"}, "rating": 4.2, "durationMin": 60, "ticketPrice": 0.0, "tags": ["古建筑", "水利", "免费"]},
        {"name": "太平湖", "location": {"longitude": 118.166, "latitude": 30.342, "address": "黄山市黄山区太平湖", "city": "黄山"}, "rating": 4.3, "durationMin": 180, "ticketPrice": 68.0, "tags": ["湖泊", "游船"]},
        {"name": "唐模古村", "location": {"longitude": 118.317, "latitude": 29.829, "address": "黄山市徽州区唐模村", "city": "黄山"}, "rating": 4.2, "durationMin": 90, "ticketPrice": 80.0, "tags": ["古村落", "徽派"]},
        {"name": "潜口民宅", "location": {"longitude": 118.305, "latitude": 29.840, "address": "黄山市徽州区潜口镇", "city": "黄山"}, "rating": 4.3, "durationMin": 60, "ticketPrice": 0.0, "tags": ["古建筑", "免费"]},
    ],
    "西宁": [
        {"name": "西宁植物园", "location": {"longitude": 101.735, "latitude": 36.607, "address": "西宁市城西区文苑路", "city": "西宁"}, "rating": 4.1, "durationMin": 90, "ticketPrice": 0.0, "tags": ["植物园", "免费"]},
        {"name": "湟中卡约遗址", "location": {"longitude": 101.560, "latitude": 36.517, "address": "西宁市湟中区", "city": "西宁"}, "rating": 4.0, "durationMin": 60, "ticketPrice": 0.0, "tags": ["遗址", "历史", "免费"]},
        {"name": "大通老爷山", "location": {"longitude": 101.685, "latitude": 36.895, "address": "西宁市大通县桥头镇", "city": "西宁"}, "rating": 4.3, "durationMin": 180, "ticketPrice": 15.0, "tags": ["山岳", "道教"]},
        {"name": "互助土族故土园", "location": {"longitude": 101.956, "latitude": 36.838, "address": "西宁市互助县威远镇", "city": "西宁"}, "rating": 4.3, "durationMin": 180, "ticketPrice": 120.0, "tags": ["民族文化", "土族"]},
        {"name": "虎台遗址", "location": {"longitude": 101.759, "latitude": 36.622, "address": "西宁市城西区虎台", "city": "西宁"}, "rating": 4.0, "durationMin": 30, "ticketPrice": 0.0, "tags": ["遗址", "免费"]},
        {"name": "藏医药文化博物馆", "location": {"longitude": 101.795, "latitude": 36.643, "address": "西宁市城北区经二路36号", "city": "西宁"}, "rating": 4.4, "durationMin": 120, "ticketPrice": 60.0, "tags": ["博物馆", "藏文化"]},
    ],
    "合肥": [
        {"name": "逍遥津公园", "location": {"longitude": 117.286, "latitude": 31.874, "address": "合肥市庐阳区寿春路16号", "city": "合肥"}, "rating": 4.2, "durationMin": 90, "ticketPrice": 0.0, "tags": ["公园", "三国", "免费"]},
        {"name": "大蜀山森林公园", "location": {"longitude": 117.202, "latitude": 31.843, "address": "合肥市蜀山区望江西路", "city": "合肥"}, "rating": 4.3, "durationMin": 180, "ticketPrice": 0.0, "tags": ["森林", "登山", "免费"]},
        {"name": "罍街", "location": {"longitude": 117.298, "latitude": 31.829, "address": "合肥市包河区宁国南路", "city": "合肥"}, "rating": 4.1, "durationMin": 90, "ticketPrice": 0.0, "tags": ["美食", "夜生活", "免费"]},
        {"name": "瑶海公园", "location": {"longitude": 117.330, "latitude": 31.882, "address": "合肥市瑶海区长江东路", "city": "合肥"}, "rating": 4.0, "durationMin": 60, "ticketPrice": 0.0, "tags": ["公园", "免费"]},
        {"name": "滨湖湿地森林公园", "location": {"longitude": 117.298, "latitude": 31.714, "address": "合肥市包河区环湖北路", "city": "合肥"}, "rating": 4.3, "durationMin": 180, "ticketPrice": 0.0, "tags": ["湿地", "免费"]},
        {"name": "渡江战役纪念馆", "location": {"longitude": 117.298, "latitude": 31.720, "address": "合肥市包河区云谷路", "city": "合肥"}, "rating": 4.4, "durationMin": 90, "ticketPrice": 0.0, "tags": ["纪念馆", "免费"]},
    ],
    "绍兴": [
        {"name": "秋瑾碑", "location": {"longitude": 120.582, "latitude": 29.988, "address": "绍兴市越城区胜利西路", "city": "绍兴"}, "rating": 4.1, "durationMin": 30, "ticketPrice": 0.0, "tags": ["历史", "免费"]},
        {"name": "咸亨酒店", "location": {"longitude": 120.577, "latitude": 29.996, "address": "绍兴市越城区鲁迅中路44号", "city": "绍兴"}, "rating": 4.2, "durationMin": 60, "ticketPrice": 0.0, "tags": ["文学", "美食", "免费"]},
        {"name": "绍兴古纤道", "location": {"longitude": 120.537, "latitude": 30.017, "address": "绍兴市柯桥区柯岩街道", "city": "绍兴"}, "rating": 4.2, "durationMin": 60, "ticketPrice": 0.0, "tags": ["古道", "免费"]},
        {"name": "周恩来祖居", "location": {"longitude": 120.575, "latitude": 29.997, "address": "绍兴市越城区劳动路", "city": "绍兴"}, "rating": 4.2, "durationMin": 60, "ticketPrice": 0.0, "tags": ["名人故居", "免费"]},
        {"name": "吼山残石景区", "location": {"longitude": 120.627, "latitude": 30.047, "address": "绍兴市越城区皋埠镇", "city": "绍兴"}, "rating": 4.1, "durationMin": 120, "ticketPrice": 40.0, "tags": ["地质", "自然"]},
        {"name": "西施故里", "location": {"longitude": 120.240, "latitude": 29.718, "address": "绍兴市诸暨市城南", "city": "绍兴"}, "rating": 4.3, "durationMin": 120, "ticketPrice": 0.0, "tags": ["历史文化", "免费"]},
    ],
    "武汉": [
        {"name": "昙华林", "location": {"longitude": 114.307, "latitude": 30.543, "address": "武汉市武昌区昙华林路", "city": "武汉"}, "rating": 4.3, "durationMin": 90, "ticketPrice": 0.0, "tags": ["文艺", "老街", "免费"]},
        {"name": "武汉植物园", "location": {"longitude": 114.425, "latitude": 30.545, "address": "武汉市武昌区鲁磨路388号", "city": "武汉"}, "rating": 4.3, "durationMin": 180, "ticketPrice": 35.0, "tags": ["植物园", "自然"]},
        {"name": "楚河汉街", "location": {"longitude": 114.354, "latitude": 30.553, "address": "武汉市武昌区楚河汉街", "city": "武汉"}, "rating": 4.2, "durationMin": 90, "ticketPrice": 0.0, "tags": ["商业街", "免费"]},
        {"name": "辛亥革命博物馆", "location": {"longitude": 114.303, "latitude": 30.538, "address": "武汉市武昌区彭刘杨路", "city": "武汉"}, "rating": 4.4, "durationMin": 90, "ticketPrice": 0.0, "tags": ["博物馆", "免费"]},
    ],
    "昆明": [
        {"name": "金殿公园", "location": {"longitude": 102.748, "latitude": 25.064, "address": "昆明市盘龙区穿金路71号", "city": "昆明"}, "rating": 4.3, "durationMin": 120, "ticketPrice": 30.0, "tags": ["园林", "道教"]},
        {"name": "昆明老街", "location": {"longitude": 102.714, "latitude": 25.036, "address": "昆明市五华区文明街", "city": "昆明"}, "rating": 4.2, "durationMin": 90, "ticketPrice": 0.0, "tags": ["古街", "美食", "免费"]},
        {"name": "昆明世博园", "location": {"longitude": 102.749, "latitude": 25.065, "address": "昆明市盘龙区世博路10号", "city": "昆明"}, "rating": 4.1, "durationMin": 180, "ticketPrice": 70.0, "tags": ["花卉", "园林"]},
        {"name": "九乡溶洞", "location": {"longitude": 103.295, "latitude": 24.832, "address": "昆明市宜良县九乡镇", "city": "昆明"}, "rating": 4.4, "durationMin": 180, "ticketPrice": 90.0, "tags": ["溶洞", "自然"]},
    ],
    "厦门": [
        {"name": "铁路文化公园", "location": {"longitude": 118.100, "latitude": 24.448, "address": "厦门市思明区鸿山隧道", "city": "厦门"}, "rating": 4.2, "durationMin": 60, "ticketPrice": 0.0, "tags": ["公园", "铁路", "免费"]},
        {"name": "胡里山炮台", "location": {"longitude": 118.108, "latitude": 24.434, "address": "厦门市思明区曾厝垵路2号", "city": "厦门"}, "rating": 4.3, "durationMin": 60, "ticketPrice": 25.0, "tags": ["历史", "军事"]},
        {"name": "五缘湾湿地公园", "location": {"longitude": 118.161, "latitude": 24.504, "address": "厦门市湖里区五缘湾", "city": "厦门"}, "rating": 4.3, "durationMin": 90, "ticketPrice": 0.0, "tags": ["湿地", "免费"]},
        {"name": "同安影视城", "location": {"longitude": 118.151, "latitude": 24.727, "address": "厦门市同安区五显镇", "city": "厦门"}, "rating": 4.1, "durationMin": 180, "ticketPrice": 35.0, "tags": ["影视", "历史"]},
    ],
    "青岛": [
        {"name": "太平角公园", "location": {"longitude": 120.356, "latitude": 36.048, "address": "青岛市市南区太平角六路", "city": "青岛"}, "rating": 4.3, "durationMin": 60, "ticketPrice": 0.0, "tags": ["公园", "海景", "免费"]},
        {"name": "青岛奥帆中心", "location": {"longitude": 120.382, "latitude": 36.058, "address": "青岛市市南区新会路1号", "city": "青岛"}, "rating": 4.3, "durationMin": 90, "ticketPrice": 0.0, "tags": ["帆船", "海景", "免费"]},
        {"name": "花石楼", "location": {"longitude": 120.348, "latitude": 36.053, "address": "青岛市市南区黄海路18号", "city": "青岛"}, "rating": 4.2, "durationMin": 30, "ticketPrice": 8.5, "tags": ["建筑", "历史"]},
        {"name": "仰口海水浴场", "location": {"longitude": 120.630, "latitude": 36.200, "address": "青岛市崂山区仰口", "city": "青岛"}, "rating": 4.3, "durationMin": 180, "ticketPrice": 0.0, "tags": ["沙滩", "免费"]},
    ],
    "哈尔滨": [
        {"name": "圣索菲亚大教堂", "location": {"longitude": 126.626, "latitude": 45.770, "address": "哈尔滨市道里区透笼街88号", "city": "哈尔滨"}, "rating": 4.5, "durationMin": 60, "ticketPrice": 15.0, "tags": ["建筑", "教堂"]},
        {"name": "果戈里大街", "location": {"longitude": 126.651, "latitude": 45.751, "address": "哈尔滨市南岗区果戈里大街", "city": "哈尔滨"}, "rating": 4.2, "durationMin": 60, "ticketPrice": 0.0, "tags": ["商业街", "免费"]},
        {"name": "关东古巷", "location": {"longitude": 126.622, "latitude": 45.777, "address": "哈尔滨市道里区中央大街附近", "city": "哈尔滨"}, "rating": 4.1, "durationMin": 60, "ticketPrice": 0.0, "tags": ["美食", "免费"]},
        {"name": "波塞冬海洋世界", "location": {"longitude": 126.543, "latitude": 45.839, "address": "哈尔滨市松北区天元街", "city": "哈尔滨"}, "rating": 4.3, "durationMin": 240, "ticketPrice": 180.0, "tags": ["海洋", "亲子"]},
    ],
    "贵阳": [
        {"name": "黔灵山", "location": {"longitude": 106.696, "latitude": 26.610, "address": "贵阳市云岩区枣山路187号", "city": "贵阳"}, "rating": 4.4, "durationMin": 180, "ticketPrice": 5.0, "tags": ["山岳", "猕猴"]},
        {"name": "阳明祠", "location": {"longitude": 106.730, "latitude": 26.552, "address": "贵阳市南明区东山路", "city": "贵阳"}, "rating": 4.2, "durationMin": 60, "ticketPrice": 0.0, "tags": ["历史文化", "免费"]},
        {"name": "文昌阁", "location": {"longitude": 106.708, "latitude": 26.579, "address": "贵阳市云岩区文昌北路1号", "city": "贵阳"}, "rating": 4.1, "durationMin": 30, "ticketPrice": 0.0, "tags": ["古建筑", "免费"]},
        {"name": "青云路夜市", "location": {"longitude": 106.712, "latitude": 26.568, "address": "贵阳市南明区青云路", "city": "贵阳"}, "rating": 4.3, "durationMin": 90, "ticketPrice": 0.0, "tags": ["美食", "夜市", "免费"]},
    ],
    "洛阳": [
        {"name": "应天门", "location": {"longitude": 112.451, "latitude": 34.672, "address": "洛阳市老城区定鼎路", "city": "洛阳"}, "rating": 4.4, "durationMin": 60, "ticketPrice": 30.0, "tags": ["古建筑", "夜景"]},
        {"name": "王城公园", "location": {"longitude": 112.438, "latitude": 34.672, "address": "洛阳市西工区中州中路312号", "city": "洛阳"}, "rating": 4.2, "durationMin": 90, "ticketPrice": 0.0, "tags": ["公园", "牡丹", "免费"]},
        {"name": "洛阳古墓博物馆", "location": {"longitude": 112.395, "latitude": 34.688, "address": "洛阳市老城区机场路45号", "city": "洛阳"}, "rating": 4.3, "durationMin": 90, "ticketPrice": 0.0, "tags": ["博物馆", "古墓", "免费"]},
        {"name": "十字街夜市", "location": {"longitude": 112.455, "latitude": 34.664, "address": "洛阳市老城区十字街", "city": "洛阳"}, "rating": 4.2, "durationMin": 60, "ticketPrice": 0.0, "tags": ["美食", "夜市", "免费"]},
    ],
    "敦煌": [
        {"name": "雅丹国家地质公园", "location": {"longitude": 93.500, "latitude": 40.500, "address": "敦煌市西北约180公里", "city": "敦煌"}, "rating": 4.5, "durationMin": 180, "ticketPrice": 120.0, "tags": ["地质", "沙漠", "自然"]},
        {"name": "阳关遗址", "location": {"longitude": 94.070, "latitude": 39.780, "address": "敦煌市南湖镇阳关", "city": "敦煌"}, "rating": 4.3, "durationMin": 120, "ticketPrice": 50.0, "tags": ["历史", "边关"]},
        {"name": "敦煌影视城", "location": {"longitude": 94.590, "latitude": 40.105, "address": "敦煌市大漠路", "city": "敦煌"}, "rating": 4.1, "durationMin": 60, "ticketPrice": 40.0, "tags": ["影视"]},
        {"name": "沙州古城", "location": {"longitude": 94.661, "latitude": 40.142, "address": "敦煌市沙州南路", "city": "敦煌"}, "rating": 4.0, "durationMin": 60, "ticketPrice": 0.0, "tags": ["古城", "免费"]},
    ],
    "九寨沟": [
        {"name": "嫩恩桑措", "location": {"longitude": 103.924, "latitude": 33.260, "address": "九寨沟县漳扎镇", "city": "九寨沟"}, "rating": 4.2, "durationMin": 120, "ticketPrice": 0.0, "tags": ["湖泊", "原生态", "免费"]},
        {"name": "白河自然保护区", "location": {"longitude": 104.001, "latitude": 33.210, "address": "九寨沟县白河乡", "city": "九寨沟"}, "rating": 4.1, "durationMin": 180, "ticketPrice": 0.0, "tags": ["自然保护区", "金丝猴", "免费"]},
        {"name": "中查沟", "location": {"longitude": 103.943, "latitude": 33.280, "address": "九寨沟县中查村", "city": "九寨沟"}, "rating": 4.2, "durationMin": 120, "ticketPrice": 0.0, "tags": ["藏寨", "自然", "免费"]},
        {"name": "柴门关", "location": {"longitude": 103.877, "latitude": 33.168, "address": "九寨沟县柴门关", "city": "九寨沟"}, "rating": 4.0, "durationMin": 60, "ticketPrice": 0.0, "tags": ["古道", "历史", "免费"]},
    ],
    "乌鲁木齐": [
        {"name": "水磨沟公园", "location": {"longitude": 87.637, "latitude": 43.812, "address": "乌鲁木齐市水磨沟区水磨沟路", "city": "乌鲁木齐"}, "rating": 4.3, "durationMin": 120, "ticketPrice": 0.0, "tags": ["公园", "免费"]},
        {"name": "盐湖景区", "location": {"longitude": 87.555, "latitude": 43.567, "address": "乌鲁木齐市达坂城区盐湖", "city": "乌鲁木齐"}, "rating": 4.2, "durationMin": 120, "ticketPrice": 0.0, "tags": ["盐湖", "自然", "免费"]},
        {"name": "二道桥大巴扎", "location": {"longitude": 87.614, "latitude": 43.783, "address": "乌鲁木齐市天山区解放南路", "city": "乌鲁木齐"}, "rating": 4.3, "durationMin": 90, "ticketPrice": 0.0, "tags": ["市场", "民族文化", "免费"]},
        {"name": "乌鲁木齐植物园", "location": {"longitude": 87.579, "latitude": 43.837, "address": "乌鲁木齐市沙依巴克区", "city": "乌鲁木齐"}, "rating": 4.1, "durationMin": 90, "ticketPrice": 5.0, "tags": ["植物园", "自然"]},
    ],
    "拉萨": [
        {"name": "小昭寺", "location": {"longitude": 91.140, "latitude": 29.660, "address": "拉萨市城关区小昭寺路", "city": "拉萨"}, "rating": 4.3, "durationMin": 60, "ticketPrice": 20.0, "tags": ["寺庙", "佛教"]},
        {"name": "甘丹寺", "location": {"longitude": 91.483, "latitude": 29.668, "address": "拉萨市达孜区甘丹寺", "city": "拉萨"}, "rating": 4.5, "durationMin": 180, "ticketPrice": 40.0, "tags": ["寺庙", "佛教"]},
        {"name": "扎基寺", "location": {"longitude": 91.137, "latitude": 29.668, "address": "拉萨市城关区扎基路", "city": "拉萨"}, "rating": 4.2, "durationMin": 30, "ticketPrice": 0.0, "tags": ["寺庙", "免费"]},
        {"name": "宗角禄康公园", "location": {"longitude": 91.116, "latitude": 29.660, "address": "拉萨市城关区布达拉宫后方", "city": "拉萨"}, "rating": 4.3, "durationMin": 60, "ticketPrice": 0.0, "tags": ["公园", "免费"]},
    ],
    "呼和浩特": [
        {"name": "将军衙署", "location": {"longitude": 111.673, "latitude": 40.808, "address": "呼和浩特市新城区新华大街31号", "city": "呼和浩特"}, "rating": 4.2, "durationMin": 60, "ticketPrice": 0.0, "tags": ["历史文化", "免费"]},
        {"name": "蒙古风情园", "location": {"longitude": 111.627, "latitude": 40.846, "address": "呼和浩特市赛罕区", "city": "呼和浩特"}, "rating": 4.1, "durationMin": 120, "ticketPrice": 30.0, "tags": ["民族文化", "草原"]},
        {"name": "清真大寺", "location": {"longitude": 111.669, "latitude": 40.797, "address": "呼和浩特市回民区通道南街", "city": "呼和浩特"}, "rating": 4.2, "durationMin": 30, "ticketPrice": 0.0, "tags": ["寺庙", "伊斯兰", "免费"]},
        {"name": "哈素海", "location": {"longitude": 111.300, "latitude": 40.720, "address": "呼和浩特市土默特左旗", "city": "呼和浩特"}, "rating": 4.2, "durationMin": 180, "ticketPrice": 30.0, "tags": ["湖泊", "自然"]},
    ],
    "南昌": [
        {"name": "绳金塔", "location": {"longitude": 115.882, "latitude": 28.670, "address": "南昌市西湖区绳金塔街", "city": "南昌"}, "rating": 4.2, "durationMin": 60, "ticketPrice": 0.0, "tags": ["古建筑", "免费"]},
        {"name": "万寿宫历史文化街区", "location": {"longitude": 115.891, "latitude": 28.682, "address": "南昌市西湖区翠花街", "city": "南昌"}, "rating": 4.3, "durationMin": 90, "ticketPrice": 0.0, "tags": ["历史文化", "免费"]},
        {"name": "江西省博物馆", "location": {"longitude": 115.901, "latitude": 28.691, "address": "南昌市东湖区新馆路", "city": "南昌"}, "rating": 4.4, "durationMin": 120, "ticketPrice": 0.0, "tags": ["博物馆", "免费"]},
        {"name": "秋水广场", "location": {"longitude": 115.833, "latitude": 28.648, "address": "南昌市红谷滩区赣江南大道", "city": "南昌"}, "rating": 4.2, "durationMin": 60, "ticketPrice": 0.0, "tags": ["喷泉", "夜景", "免费"]},
    ],
    "福州": [
        {"name": "镇海楼", "location": {"longitude": 119.305, "latitude": 26.092, "address": "福州市鼓楼区屏山公园内", "city": "福州"}, "rating": 4.2, "durationMin": 60, "ticketPrice": 0.0, "tags": ["古建筑", "免费"]},
        {"name": "闽江夜游", "location": {"longitude": 119.289, "latitude": 26.055, "address": "福州市台江区中亭街码头", "city": "福州"}, "rating": 4.3, "durationMin": 60, "ticketPrice": 120.0, "tags": ["游船", "夜景"]},
        {"name": "金牛山福道", "location": {"longitude": 119.274, "latitude": 26.066, "address": "福州市鼓楼区杨桥西路", "city": "福州"}, "rating": 4.4, "durationMin": 120, "ticketPrice": 0.0, "tags": ["步道", "免费"]},
        {"name": "福建博物院", "location": {"longitude": 119.277, "latitude": 26.098, "address": "福州市鼓楼区湖头街96号", "city": "福州"}, "rating": 4.3, "durationMin": 120, "ticketPrice": 0.0, "tags": ["博物馆", "免费"]},
    ],
    "沈阳": [
        {"name": "辽宁省博物馆", "location": {"longitude": 123.456, "latitude": 41.724, "address": "沈阳市浑南区智慧三街157号", "city": "沈阳"}, "rating": 4.5, "durationMin": 150, "ticketPrice": 0.0, "tags": ["博物馆", "免费"]},
        {"name": "沈阳植物园", "location": {"longitude": 123.593, "latitude": 41.760, "address": "沈阳市浑南区双园路301号", "city": "沈阳"}, "rating": 4.3, "durationMin": 180, "ticketPrice": 50.0, "tags": ["植物园", "自然"]},
        {"name": "中街", "location": {"longitude": 123.460, "latitude": 41.803, "address": "沈阳市沈河区中街路", "city": "沈阳"}, "rating": 4.2, "durationMin": 90, "ticketPrice": 0.0, "tags": ["商业街", "免费"]},
        {"name": "清福陵", "location": {"longitude": 123.547, "latitude": 41.837, "address": "沈阳市浑南区东陵路", "city": "沈阳"}, "rating": 4.3, "durationMin": 120, "ticketPrice": 40.0, "tags": ["历史文化", "皇陵"]},
    ],
    "长春": [
        {"name": "长春动植物公园", "location": {"longitude": 125.363, "latitude": 43.870, "address": "长春市南关区自由大路2121号", "city": "长春"}, "rating": 4.2, "durationMin": 180, "ticketPrice": 30.0, "tags": ["动物园", "亲子"]},
        {"name": "新民大街历史文化街区", "location": {"longitude": 125.317, "latitude": 43.852, "address": "长春市朝阳区新民大街", "city": "长春"}, "rating": 4.3, "durationMin": 60, "ticketPrice": 0.0, "tags": ["历史建筑", "免费"]},
        {"name": "莲花山滑雪场", "location": {"longitude": 125.790, "latitude": 43.640, "address": "长春市二道区四家乡", "city": "长春"}, "rating": 4.2, "durationMin": 240, "ticketPrice": 120.0, "tags": ["滑雪", "户外"]},
        {"name": "长春欧亚卖场", "location": {"longitude": 125.255, "latitude": 43.850, "address": "长春市朝阳区开运街5178号", "city": "长春"}, "rating": 4.1, "durationMin": 180, "ticketPrice": 0.0, "tags": ["购物", "免费"]},
    ],
    "天津": [
        {"name": "南开大学", "location": {"longitude": 117.160, "latitude": 39.093, "address": "天津市南开区卫津路94号", "city": "天津"}, "rating": 4.3, "durationMin": 90, "ticketPrice": 0.0, "tags": ["校园", "免费"]},
        {"name": "天津大悦城", "location": {"longitude": 117.171, "latitude": 39.133, "address": "天津市南开区南门外大街2号", "city": "天津"}, "rating": 4.2, "durationMin": 120, "ticketPrice": 0.0, "tags": ["购物", "免费"]},
        {"name": "静园", "location": {"longitude": 117.199, "latitude": 39.115, "address": "天津市和平区鞍山道70号", "city": "天津"}, "rating": 4.3, "durationMin": 60, "ticketPrice": 20.0, "tags": ["历史文化", "末代皇帝"]},
        {"name": "天津自然博物馆", "location": {"longitude": 117.235, "latitude": 39.084, "address": "天津市河西区友谊路31号", "city": "天津"}, "rating": 4.4, "durationMin": 120, "ticketPrice": 0.0, "tags": ["博物馆", "免费"]},
    ],
    "无锡": [
        {"name": "蠡湖公园", "location": {"longitude": 120.263, "latitude": 31.525, "address": "无锡市滨湖区环湖路", "city": "无锡"}, "rating": 4.3, "durationMin": 90, "ticketPrice": 0.0, "tags": ["公园", "湖泊", "免费"]},
        {"name": "梅园横山风景区", "location": {"longitude": 120.238, "latitude": 31.531, "address": "无锡市滨湖区梁溪路", "city": "无锡"}, "rating": 4.4, "durationMin": 120, "ticketPrice": 60.0, "tags": ["梅花", "园林"]},
        {"name": "东林书院", "location": {"longitude": 120.310, "latitude": 31.581, "address": "无锡市梁溪区解放东路867号", "city": "无锡"}, "rating": 4.3, "durationMin": 60, "ticketPrice": 0.0, "tags": ["历史文化", "免费"]},
        {"name": "三国水浒城", "location": {"longitude": 120.197, "latitude": 31.514, "address": "无锡市滨湖区山水西路", "city": "无锡"}, "rating": 4.3, "durationMin": 240, "ticketPrice": 150.0, "tags": ["影视", "历史"]},
    ],
    "泉州": [
        {"name": "府文庙", "location": {"longitude": 118.590, "latitude": 24.903, "address": "泉州市鲤城区百源川池畔", "city": "泉州"}, "rating": 4.3, "durationMin": 60, "ticketPrice": 0.0, "tags": ["古建筑", "免费"]},
        {"name": "天后宫", "location": {"longitude": 118.590, "latitude": 24.895, "address": "泉州市鲤城区天后路1号", "city": "泉州"}, "rating": 4.3, "durationMin": 30, "ticketPrice": 0.0, "tags": ["寺庙", "免费"]},
        {"name": "安平桥", "location": {"longitude": 118.565, "latitude": 24.678, "address": "泉州市晋江市安海镇", "city": "泉州"}, "rating": 4.4, "durationMin": 60, "ticketPrice": 0.0, "tags": ["古桥", "免费"]},
        {"name": "草庵摩尼光佛", "location": {"longitude": 118.502, "latitude": 24.772, "address": "泉州市晋江市罗山镇", "city": "泉州"}, "rating": 4.3, "durationMin": 30, "ticketPrice": 0.0, "tags": ["摩尼教", "世界遗产", "免费"]},
    ],
    "开封": [
        {"name": "山陕甘会馆", "location": {"longitude": 114.352, "latitude": 34.796, "address": "开封市鼓楼区徐府街", "city": "开封"}, "rating": 4.4, "durationMin": 60, "ticketPrice": 25.0, "tags": ["古建筑", "砖雕"]},
        {"name": "延庆观", "location": {"longitude": 114.355, "latitude": 34.795, "address": "开封市鼓楼区观前街", "city": "开封"}, "rating": 4.2, "durationMin": 30, "ticketPrice": 15.0, "tags": ["道教", "古建筑"]},
        {"name": "刘少奇在开封陈列馆", "location": {"longitude": 114.356, "latitude": 34.793, "address": "开封市鼓楼区北土街", "city": "开封"}, "rating": 4.1, "durationMin": 60, "ticketPrice": 0.0, "tags": ["纪念馆", "免费"]},
        {"name": "开封城摞城博物馆", "location": {"longitude": 114.352, "latitude": 34.791, "address": "开封市龙亭区", "city": "开封"}, "rating": 4.3, "durationMin": 60, "ticketPrice": 30.0, "tags": ["博物馆", "考古"]},
    ],
    "岳阳": [
        {"name": "屈子文化园", "location": {"longitude": 113.095, "latitude": 28.832, "address": "岳阳市汨罗市屈子祠镇", "city": "岳阳"}, "rating": 4.3, "durationMin": 120, "ticketPrice": 50.0, "tags": ["历史文化", "屈原"]},
        {"name": "平江石牛寨", "location": {"longitude": 113.680, "latitude": 28.890, "address": "岳阳市平江县石牛寨镇", "city": "岳阳"}, "rating": 4.3, "durationMin": 240, "ticketPrice": 118.0, "tags": ["玻璃桥", "户外"]},
        {"name": "铁山水库", "location": {"longitude": 113.295, "latitude": 29.150, "address": "岳阳市岳阳县中洲乡", "city": "岳阳"}, "rating": 4.1, "durationMin": 120, "ticketPrice": 0.0, "tags": ["水库", "自然", "免费"]},
        {"name": "圣安寺", "location": {"longitude": 113.130, "latitude": 29.379, "address": "岳阳市岳阳楼区城南", "city": "岳阳"}, "rating": 4.2, "durationMin": 60, "ticketPrice": 0.0, "tags": ["寺庙", "免费"]},
    ],
    "杭州": [
        {"name": "浙江省博物馆", "location": {"longitude": 120.150, "latitude": 30.254, "address": "杭州市西湖区孤山路25号", "city": "杭州"}, "rating": 4.4, "durationMin": 120, "ticketPrice": 0.0, "tags": ["博物馆", "免费"]},
        {"name": "小河直街", "location": {"longitude": 120.130, "latitude": 30.305, "address": "杭州市拱墅区小河路", "city": "杭州"}, "rating": 4.2, "durationMin": 60, "ticketPrice": 0.0, "tags": ["古街", "免费"]},
    ],
    "西安": [
        {"name": "碑林博物馆", "location": {"longitude": 108.949, "latitude": 34.253, "address": "西安市碑林区三学街15号", "city": "西安"}, "rating": 4.6, "durationMin": 120, "ticketPrice": 65.0, "tags": ["博物馆", "历史文化"]},
        {"name": "青龙寺", "location": {"longitude": 108.993, "latitude": 34.228, "address": "西安市雁塔区西影路铁炉庙村", "city": "西安"}, "rating": 4.3, "durationMin": 60, "ticketPrice": 0.0, "tags": ["寺庙", "樱花", "免费"]},
    ],
    "南宁": [
        {"name": "南宁方特东盟神画", "location": {"longitude": 108.287, "latitude": 22.743, "address": "南宁市青秀区青环路", "city": "南宁"}, "rating": 4.3, "durationMin": 360, "ticketPrice": 299.0, "tags": ["游乐", "东盟文化"]},
        {"name": "南宁博物馆", "location": {"longitude": 108.346, "latitude": 22.788, "address": "南宁市良庆区龙堤路15号", "city": "南宁"}, "rating": 4.2, "durationMin": 90, "ticketPrice": 0.0, "tags": ["博物馆", "免费"]},
    ],
    "海口": [
        {"name": "海南省博物馆", "location": {"longitude": 110.380, "latitude": 19.990, "address": "海口市琼山区国兴大道68号", "city": "海口"}, "rating": 4.3, "durationMin": 120, "ticketPrice": 0.0, "tags": ["博物馆", "免费"]},
        {"name": "西秀海滩", "location": {"longitude": 110.277, "latitude": 20.009, "address": "海口市秀英区滨海西路", "city": "海口"}, "rating": 4.2, "durationMin": 120, "ticketPrice": 0.0, "tags": ["沙滩", "免费"]},
    ],
    "大连": [
        {"name": "旅顺日俄监狱旧址", "location": {"longitude": 121.261, "latitude": 38.811, "address": "大连市旅顺口区元宝房", "city": "大连"}, "rating": 4.3, "durationMin": 90, "ticketPrice": 25.0, "tags": ["历史", "博物馆"]},
        {"name": "滨海国家地质公园", "location": {"longitude": 121.809, "latitude": 39.065, "address": "大连市金州区金石滩", "city": "大连"}, "rating": 4.4, "durationMin": 120, "ticketPrice": 0.0, "tags": ["地质", "海景", "免费"]},
    ],
    "张家界": [
        {"name": "慈利五雷山", "location": {"longitude": 110.710, "latitude": 29.338, "address": "张家界市慈利县五雷山", "city": "张家界"}, "rating": 4.2, "durationMin": 180, "ticketPrice": 63.0, "tags": ["道教", "山岳"]},
        {"name": "普光禅寺", "location": {"longitude": 110.479, "latitude": 29.120, "address": "张家界市永定区普光路", "city": "张家界"}, "rating": 4.1, "durationMin": 60, "ticketPrice": 0.0, "tags": ["寺庙", "免费"]},
    ],
    "银川": [
        {"name": "宁夏博物馆", "location": {"longitude": 106.262, "latitude": 38.482, "address": "银川市金凤区人民广场东", "city": "银川"}, "rating": 4.3, "durationMin": 120, "ticketPrice": 0.0, "tags": ["博物馆", "免费"]},
        {"name": "黄沙古渡", "location": {"longitude": 106.498, "latitude": 38.583, "address": "银川市兴庆区月牙湖乡", "city": "银川"}, "rating": 4.2, "durationMin": 180, "ticketPrice": 88.0, "tags": ["沙漠", "黄河"]},
    ],
    "济南": [
        {"name": "山东省博物馆", "location": {"longitude": 117.069, "latitude": 36.654, "address": "济南市历下区经十一路11899号", "city": "济南"}, "rating": 4.4, "durationMin": 150, "ticketPrice": 0.0, "tags": ["博物馆", "免费"]},
        {"name": "黑虎泉", "location": {"longitude": 117.029, "latitude": 36.653, "address": "济南市历下区黑虎泉北路", "city": "济南"}, "rating": 4.3, "durationMin": 30, "ticketPrice": 0.0, "tags": ["泉水", "免费"]},
    ],
    "太原": [
        {"name": "柳巷", "location": {"longitude": 112.559, "latitude": 37.867, "address": "太原市迎泽区柳巷", "city": "太原"}, "rating": 4.1, "durationMin": 90, "ticketPrice": 0.0, "tags": ["商业街", "免费"]},
        {"name": "纯阳宫", "location": {"longitude": 112.560, "latitude": 37.863, "address": "太原市迎泽区起凤街1号", "city": "太原"}, "rating": 4.2, "durationMin": 60, "ticketPrice": 0.0, "tags": ["道教", "免费"]},
    ],
    "兰州": [
        {"name": "甘肃省图书馆", "location": {"longitude": 103.826, "latitude": 36.057, "address": "兰州市城关区南滨河东路", "city": "兰州"}, "rating": 4.1, "durationMin": 90, "ticketPrice": 0.0, "tags": ["文化", "免费"]},
        {"name": "黄河母亲雕塑", "location": {"longitude": 103.813, "latitude": 36.070, "address": "兰州市七里河区滨河中路", "city": "兰州"}, "rating": 4.2, "durationMin": 15, "ticketPrice": 0.0, "tags": ["地标", "免费"]},
    ],
    "宁波": [
        {"name": "宁波五龙潭", "location": {"longitude": 121.463, "latitude": 29.786, "address": "宁波市鄞州区龙观乡", "city": "宁波"}, "rating": 4.3, "durationMin": 180, "ticketPrice": 40.0, "tags": ["瀑布", "自然"]},
        {"name": "阿育王寺", "location": {"longitude": 121.692, "latitude": 29.842, "address": "宁波市鄞州区五乡镇", "city": "宁波"}, "rating": 4.4, "durationMin": 90, "ticketPrice": 0.0, "tags": ["寺庙", "免费"]},
    ],
    "镇江": [
        {"name": "西津渡古街", "location": {"longitude": 119.431, "latitude": 32.216, "address": "镇江市润州区西津渡街25号", "city": "镇江"}, "rating": 4.4, "durationMin": 120, "ticketPrice": 0.0, "tags": ["古街", "免费"]},
        {"name": "镇江醋文化博物馆", "location": {"longitude": 119.463, "latitude": 32.213, "address": "镇江市丹徒区", "city": "镇江"}, "rating": 4.2, "durationMin": 60, "ticketPrice": 0.0, "tags": ["博物馆", "免费"]},
    ],
    "嘉兴": [
        {"name": "西塘古镇", "location": {"longitude": 120.893, "latitude": 30.944, "address": "嘉兴市嘉善县西塘镇", "city": "嘉兴"}, "rating": 4.5, "durationMin": 240, "ticketPrice": 95.0, "tags": ["古镇", "水乡"]},
        {"name": "月河历史街区", "location": {"longitude": 120.756, "latitude": 30.760, "address": "嘉兴市南湖区月河街", "city": "嘉兴"}, "rating": 4.3, "durationMin": 90, "ticketPrice": 0.0, "tags": ["古街", "免费"]},
    ],
    "乐山": [
        {"name": "犍为嘉阳小火车", "location": {"longitude": 103.938, "latitude": 29.220, "address": "乐山市犍为县芭沟镇", "city": "乐山"}, "rating": 4.5, "durationMin": 240, "ticketPrice": 80.0, "tags": ["蒸汽火车", "怀旧"]},
        {"name": "罗城古镇", "location": {"longitude": 103.589, "latitude": 29.408, "address": "乐山市犍为县罗城镇", "city": "乐山"}, "rating": 4.2, "durationMin": 120, "ticketPrice": 0.0, "tags": ["古镇", "免费"]},
    ],
}

FINAL_REPLACEMENTS = {
    "杭州": [
        {"name": "杭州植物园", "location": {"longitude": 120.117, "latitude": 30.255, "address": "杭州市西湖区桃源岭1号", "city": "杭州"}, "rating": 4.3, "durationMin": 120, "ticketPrice": 10.0, "tags": ["植物园", "自然"]},
    ],
    "昆明": [
        {"name": "圆通寺", "location": {"longitude": 102.717, "latitude": 25.048, "address": "昆明市五华区圆通街30号", "city": "昆明"}, "rating": 4.2, "durationMin": 60, "ticketPrice": 6.0, "tags": ["寺庙", "佛教"]},
    ],
    "苏州": [
        {"name": "狮子林", "location": {"longitude": 120.633, "latitude": 31.316, "address": "苏州市姑苏区园林路23号", "city": "苏州"}, "rating": 4.5, "durationMin": 90, "ticketPrice": 40.0, "tags": ["园林", "世界遗产"]},
        {"name": "耦园", "location": {"longitude": 120.646, "latitude": 31.314, "address": "苏州市姑苏区仓街小新桥巷7号", "city": "苏州"}, "rating": 4.3, "durationMin": 60, "ticketPrice": 25.0, "tags": ["园林", "世界遗产"]},
    ],
    "厦门": [
        {"name": "日光岩", "location": {"longitude": 118.065, "latitude": 24.444, "address": "厦门市思明区鼓浪屿日光岩", "city": "厦门"}, "rating": 4.5, "durationMin": 60, "ticketPrice": 50.0, "tags": ["山岳", "观景"]},
    ],
    "桂林": [
        {"name": "伏波山", "location": {"longitude": 110.308, "latitude": 25.291, "address": "桂林市叠彩区伏波山公园", "city": "桂林"}, "rating": 4.2, "durationMin": 60, "ticketPrice": 25.0, "tags": ["山岳", "历史"]},
    ],
    "黄山": [
        {"name": "关麓古村", "location": {"longitude": 117.896, "latitude": 30.006, "address": "黄山市黟县碧阳镇关麓村", "city": "黄山"}, "rating": 4.2, "durationMin": 60, "ticketPrice": 35.0, "tags": ["古村落", "徽派"]},
        {"name": "打鼓岭", "location": {"longitude": 117.900, "latitude": 29.910, "address": "黄山市黟县美溪乡", "city": "黄山"}, "rating": 4.1, "durationMin": 120, "ticketPrice": 0.0, "tags": ["自然", "瀑布", "免费"]},
    ],
    "济南": [
        {"name": "灵岩寺", "location": {"longitude": 116.894, "latitude": 36.378, "address": "济南市长清区万德镇", "city": "济南"}, "rating": 4.5, "durationMin": 180, "ticketPrice": 40.0, "tags": ["寺庙", "佛教"]},
    ],
    "南昌": [
        {"name": "安义古村群", "location": {"longitude": 115.579, "latitude": 28.850, "address": "南昌市安义县石鼻镇", "city": "南昌"}, "rating": 4.2, "durationMin": 180, "ticketPrice": 30.0, "tags": ["古村落", "免费"]},
    ],
    "沈阳": [
        {"name": "东陵公园", "location": {"longitude": 123.547, "latitude": 41.838, "address": "沈阳市浑南区东陵路210号", "city": "沈阳"}, "rating": 4.3, "durationMin": 120, "ticketPrice": 30.0, "tags": ["历史", "陵墓"]},
    ],
    "泉州": [
        {"name": "德济门遗址", "location": {"longitude": 118.592, "latitude": 24.893, "address": "泉州市鲤城区天后路", "city": "泉州"}, "rating": 4.1, "durationMin": 30, "ticketPrice": 0.0, "tags": ["遗址", "世界遗产", "免费"]},
    ],
    "开封": [
        {"name": "天波杨府", "location": {"longitude": 114.351, "latitude": 34.808, "address": "开封市龙亭区龙亭北路14号", "city": "开封"}, "rating": 4.3, "durationMin": 90, "ticketPrice": 40.0, "tags": ["历史文化", "古建筑"]},
    ],
    "岳阳": [
        {"name": "任弼时纪念馆", "location": {"longitude": 113.395, "latitude": 28.675, "address": "岳阳市汨罗市弼时镇", "city": "岳阳"}, "rating": 4.2, "durationMin": 90, "ticketPrice": 0.0, "tags": ["纪念馆", "免费"]},
    ],
    "嘉兴": [
        {"name": "乌镇", "location": {"longitude": 120.487, "latitude": 30.745, "address": "嘉兴市桐乡市乌镇镇", "city": "嘉兴"}, "rating": 4.7, "durationMin": 360, "ticketPrice": 150.0, "tags": ["古镇", "水乡", "必去"]},
    ],
}

LAST_REPLACEMENTS = {
    "杭州": [
        {"name": "龙井茶园", "location": {"longitude": 120.106, "latitude": 30.216, "address": "杭州市西湖区龙井路", "city": "杭州"}, "rating": 4.3, "durationMin": 90, "ticketPrice": 0.0, "tags": ["茶文化", "免费"]},
    ],
    "昆明": [
        {"name": "东川红土地", "location": {"longitude": 103.180, "latitude": 26.075, "address": "昆明市东川区花石头村", "city": "昆明"}, "rating": 4.5, "durationMin": 240, "ticketPrice": 0.0, "tags": ["自然", "摄影", "免费"]},
    ],
    "苏州": [
        {"name": "东山景区", "location": {"longitude": 120.431, "latitude": 31.097, "address": "苏州市吴中区东山镇", "city": "苏州"}, "rating": 4.3, "durationMin": 240, "ticketPrice": 0.0, "tags": ["自然", "古镇", "免费"]},
        {"name": "锦溪古镇", "location": {"longitude": 120.961, "latitude": 31.169, "address": "苏州市昆山市锦溪镇", "city": "苏州"}, "rating": 4.3, "durationMin": 180, "ticketPrice": 65.0, "tags": ["古镇", "水乡"]},
    ],
    "厦门": [
        {"name": "园博苑", "location": {"longitude": 118.102, "latitude": 24.560, "address": "厦门市集美区杏林湾路", "city": "厦门"}, "rating": 4.2, "durationMin": 180, "ticketPrice": 60.0, "tags": ["园林", "亲子"]},
    ],
    "桂林": [
        {"name": "穿山公园", "location": {"longitude": 110.319, "latitude": 25.257, "address": "桂林市七星区穿山路", "city": "桂林"}, "rating": 4.1, "durationMin": 60, "ticketPrice": 20.0, "tags": ["山岳", "溶洞"]},
    ],
    "济南": [
        {"name": "五龙潭公园", "location": {"longitude": 117.011, "latitude": 36.666, "address": "济南市历下区趵突泉北路", "city": "济南"}, "rating": 4.2, "durationMin": 60, "ticketPrice": 0.0, "tags": ["泉水", "免费"]},
    ],
    "南昌": [
        {"name": "佑民寺", "location": {"longitude": 115.898, "latitude": 28.688, "address": "南昌市东湖区民德路181号", "city": "南昌"}, "rating": 4.2, "durationMin": 60, "ticketPrice": 0.0, "tags": ["寺庙", "免费"]},
    ],
    "开封": [
        {"name": "禹王台公园", "location": {"longitude": 114.371, "latitude": 34.781, "address": "开封市禹王台区禹王台路", "city": "开封"}, "rating": 4.1, "durationMin": 60, "ticketPrice": 15.0, "tags": ["公园", "历史"]},
    ],
}

TRULY_FINAL = {
    "杭州": [
        {"name": "杭州国家版本馆", "location": {"longitude": 119.993, "latitude": 30.286, "address": "杭州市余杭区文润路1号", "city": "杭州"}, "rating": 4.6, "durationMin": 120, "ticketPrice": 0.0, "tags": ["博物馆", "建筑", "免费"]},
        {"name": "富阳龙门古镇", "location": {"longitude": 119.821, "latitude": 29.901, "address": "杭州市富阳区龙门镇", "city": "杭州"}, "rating": 4.3, "durationMin": 150, "ticketPrice": 60.0, "tags": ["古镇", "历史"]},
    ],
    "桂林": [
        {"name": "大圩古镇", "location": {"longitude": 110.397, "latitude": 25.193, "address": "桂林市灵川县大圩镇", "city": "桂林"}, "rating": 4.2, "durationMin": 120, "ticketPrice": 0.0, "tags": ["古镇", "免费"]},
    ],
    "济南": [
        {"name": "济南方特东方神画", "location": {"longitude": 117.268, "latitude": 36.558, "address": "济南市槐荫区济齐路", "city": "济南"}, "rating": 4.3, "durationMin": 360, "ticketPrice": 280.0, "tags": ["游乐", "亲子"]},
    ],
    "开封": [
        {"name": "翰园碑林", "location": {"longitude": 114.348, "latitude": 34.808, "address": "开封市龙亭区龙亭北路15号", "city": "开封"}, "rating": 4.2, "durationMin": 90, "ticketPrice": 25.0, "tags": ["书法", "碑刻"]},
        {"name": "朱仙镇岳飞庙", "location": {"longitude": 114.195, "latitude": 34.627, "address": "开封市祥符区朱仙镇", "city": "开封"}, "rating": 4.1, "durationMin": 60, "ticketPrice": 30.0, "tags": ["历史", "纪念馆"]},
        {"name": "开封城摞城博物馆", "location": {"longitude": 114.351, "latitude": 34.794, "address": "开封市龙亭区中山路北段", "city": "开封"}, "rating": 4.0, "durationMin": 60, "ticketPrice": 30.0, "tags": ["博物馆", "考古"]},
    ],
}

for city, truly_finals in TRULY_FINAL.items():
    if city in REPLACEMENTS:
        REPLACEMENTS[city].extend(truly_finals)
    else:
        REPLACEMENTS[city] = truly_finals

for city, lasts in LAST_REPLACEMENTS.items():
    if city in REPLACEMENTS:
        REPLACEMENTS[city].extend(lasts)
    else:
        REPLACEMENTS[city] = lasts

for city, finals in FINAL_REPLACEMENTS.items():
    if city in REPLACEMENTS:
        REPLACEMENTS[city].extend(finals)
    else:
        REPLACEMENTS[city] = finals

for city, extras in EXTRA_REPLACEMENTS.items():
    if city in REPLACEMENTS:
        REPLACEMENTS[city].extend(extras)
    else:
        REPLACEMENTS[city] = extras


def find_duplicates(attractions, city):
    """返回需要移除的索引集合"""
    to_remove = set()
    for i in range(len(attractions)):
        if i in to_remove:
            continue
        for j in range(i + 1, len(attractions)):
            if j in to_remove:
                continue
            if are_semantically_same(attractions[i]['name'], attractions[j]['name'], city):
                to_remove.add(j)
    return to_remove


def process(input_path, output_path):
    with open(input_path, 'r', encoding='utf-8') as f:
        data = json.load(f)

    total_removed = 0
    total_replaced = 0

    for city_data in data['cities']:
        city = city_data['city']
        attractions = city_data['attractions']
        to_remove = find_duplicates(attractions, city)

        if not to_remove:
            continue

        replacement_pool = list(REPLACEMENTS.get(city, []))
        kept_names = {a['name'] for i, a in enumerate(attractions) if i not in to_remove}
        repl_idx = 0

        for idx in sorted(to_remove):
            old_id = attractions[idx]['id']
            old_name = attractions[idx]['name']
            replaced = False
            while repl_idx < len(replacement_pool):
                candidate = replacement_pool[repl_idx]
                repl_idx += 1
                conflict = False
                for kn in kept_names:
                    if are_semantically_same(candidate['name'], kn, city):
                        conflict = True
                        print(f"  [{city}] 跳过冲突替换: \"{candidate['name']}\" 与 \"{kn}\"")
                        break
                if not conflict:
                    new_entry = dict(candidate)
                    new_entry['id'] = old_id
                    attractions[idx] = new_entry
                    kept_names.add(new_entry['name'])
                    total_replaced += 1
                    replaced = True
                    print(f"  [{city}] 替换: \"{old_name}\" -> \"{new_entry['name']}\"")
                    break
            if not replaced:
                print(f"  [{city}] 警告: 无足够替换景点, 保留 \"{old_name}\"")

        total_removed += len(to_remove)

    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)

    print(f"\n完成! 共处理 {total_removed} 个重复, 成功替换 {total_replaced} 个")
    return total_removed, total_replaced


def verify(path):
    """验证去重后无重复"""
    with open(path, 'r', encoding='utf-8') as f:
        data = json.load(f)

    issues = 0
    for city_data in data['cities']:
        city = city_data['city']
        attractions = city_data['attractions']
        dups = find_duplicates(attractions, city)
        if dups:
            issues += len(dups)
            for idx in sorted(dups):
                print(f"  残留重复: [{city}] \"{attractions[idx]['name']}\"")
    if issues == 0:
        print("验证通过: 0 个重复")
    else:
        print(f"验证失败: 仍有 {issues} 个重复")
    return issues


if __name__ == '__main__':
    base = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    input_file = os.path.join(base, 'huazai-trip-skills', 'src', 'main', 'resources', 'attractions', 'knowledge-base.json')
    output_file = input_file

    print("=== 景点去重 + 替换 ===")
    process(input_file, output_file)

    print("\n=== 验证结果 ===")
    verify(output_file)
