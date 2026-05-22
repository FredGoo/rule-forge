import json
import os

CONFIG_PATH = os.path.join(os.path.dirname(os.path.dirname(__file__)), 'config', 'field_mapping.json')

# 构造增量映射（按 sheet 名）
updates = {
    "inAntiFraud": {
        "手机号是否击中内部黑名单": {"datatype": "Boolean", "ext_spec": {"type": "enum", "items": [{"value": True}, {"value": False}] }},
        "手机号关联证件号个数": {"datatype": "Integer", "ext_spec": {"type": "range", "min": 0, "max": 5}},
        "近30天手机号被几个客户填为联系人": {"datatype": "Integer", "ext_spec": {"type": "range", "min": 0, "max": 10}},
        "证件号是否击中内部黑名单": {"datatype": "Boolean", "ext_spec": {"type": "enum", "items": [{"value": True}, {"value": False}] }},
        "近30天证件号关联手机号个数": {"datatype": "Integer", "ext_spec": {"type": "range", "min": 0, "max": 10}},
        "设备号是否为缺失值": {"datatype": "Boolean", "ext_spec": {"type": "enum", "items": [{"value": True}, {"value": False}] }},
        "设备号是否击中内部黑名单": {"datatype": "Boolean", "ext_spec": {"type": "enum", "items": [{"value": True}, {"value": False}] }},
        "设备号近7天关联用户数": {"datatype": "Integer", "ext_spec": {"type": "range", "min": 0, "max": 10}},
        "用户设备为模拟器": {"datatype": "Boolean", "ext_spec": {"type": "enum", "items": [{"value": True}, {"value": False}] }},
        "用户设备时区不在墨西哥范围内": {"datatype": "Boolean", "ext_spec": {"type": "enum", "items": [{"value": True}, {"value": False}] }},
        "用户设备语言不为英语或西语": {"datatype": "Boolean", "ext_spec": {"type": "enum", "items": [{"value": True}, {"value": False}] }},
        "设备号近30天关联用户数": {"datatype": "Integer", "ext_spec": {"type": "range", "min": 0, "max": 20}},
        "紧急联系人手机号中含连续6位数字及以上": {"datatype": "Boolean", "ext_spec": {"type": "enum", "items": [{"value": True}, {"value": False}] }},
        "紧急联系人手机号中相同数字重复6次及以上": {"datatype": "Boolean", "ext_spec": {"type": "enum", "items": [{"value": True}, {"value": False}] }},
        "近30天共用联系人客户数": {"datatype": "Integer", "ext_spec": {"type": "range", "min": 0, "max": 20}},
        "联系人是否击中黑名单": {"datatype": "Boolean", "ext_spec": {"type": "enum", "items": [{"value": True}, {"value": False}] }},
        "联系人历史最大逾期天数": {"datatype": "Integer", "ext_spec": {"type": "range", "min": 0, "max": 180}},
        "联系人是否当前处于逾期状态": {"datatype": "Boolean", "ext_spec": {"type": "enum", "items": [{"value": True}, {"value": False}] }}
    },
    "ADV": {
        "adv多头x19": {"datatype": "Integer", "ext_spec": {"type": "range", "min": 0, "max": 50}},
        "adv多头x21": {"datatype": "Integer", "ext_spec": {"type": "range", "min": 0, "max": 50}},
        "OCR证件号被客户修改": {"datatype": "Boolean", "ext_spec": {"type": "enum", "items": [{"value": True}, {"value": False}] }},
        "ADV人脸相似度": {"datatype": "Double", "ext_spec": {"type": "range", "min": 0.6, "max": 0.99}},
        "ADV假证检测结果": {"datatype": "Integer", "ext_spec": {"type": "enum", "items": [{"value": 0}, {"value": 1}] }},
        "advance curp 检测姓名相似度": {"datatype": "Double", "ext_spec": {"type": "range", "min": 0.6, "max": 0.99}},
        "是否击中ADV黑名单": {"datatype": "Boolean", "ext_spec": {"type": "enum", "items": [{"value": True}, {"value": False}] }},
        "ADV重复人脸个数": {"datatype": "Integer", "ext_spec": {"type": "range", "min": 0, "max": 3}}
    },
    "inBehavior": {
        # 注意：单位改为“秒”（原始为分钟分段）
        "出额到用信申请间隔分钟数": {"datatype": "Integer", "ext_spec": {"type": "range", "min": 0, "max": 600000}}
    },
    "Dyna": {
        "dyna-D30-day-diff": {"datatype": "Integer", "ext_spec": {"type": "range", "min": -100, "max": 100}},
        "dyna-d60_f3": {"datatype": "Double", "ext_spec": {"type": "range", "min": 0.0, "max": 1.0}},
        "Dynagd_x_10_d_13": {"datatype": "Double", "ext_spec": {"type": "range", "min": 0.0, "max": 1.0}}
    },
    "BJ": {
        "冰鉴还款能力分V1": {"datatype": "Integer", "ext_spec": {"type": "range", "min": 300, "max": 900}},
        "冰鉴还款能力分V2": {"datatype": "Integer", "ext_spec": {"type": "range", "min": 300, "max": 900}},
        "冰鉴还款能力分V3": {"datatype": "Integer", "ext_spec": {"type": "range", "min": 300, "max": 900}},
        "冰鉴his-single-max": {"datatype": "Integer", "ext_spec": {"type": "range", "min": 0, "max": 30}}
    },
    "inUserInput": {
        "自填写年龄": {"datatype": "Integer", "ext_spec": {"type": "range", "min": 18, "max": 60}},
        "自填写学历": {"datatype": "String", "ext_spec": {"type": "enum", "items": [{"value": "高中"}, {"value": "本科"}, {"value": "硕士"}, {"value": "博士"}]}},
        "婚姻状况": {"datatype": "String", "ext_spec": {"type": "enum", "items": [{"value": "未婚"}, {"value": "已婚"}, {"value": "离异"}, {"value": "丧偶"}]}},
        "住房情况": {"datatype": "String", "ext_spec": {"type": "enum", "items": [{"value": "租房"}, {"value": "自有住房"}, {"value": "与父母同住"}, {"value": "宿舍"}]}},
        "是否有车": {"datatype": "Boolean", "ext_spec": {"type": "enum", "items": [{"value": True}, {"value": False}]}},
        "邮编": {"datatype": "String", "ext_spec": {"type": "pattern", "pattern": r"^\d{5}$", "rangeNumeric": {"min": 10000, "max": 99999}}},
        "电子邮箱": {"datatype": "String", "ext_spec": {"type": "pattern", "pattern": "@", "faker_type": "email"}},
        "whatsapp账号": {"datatype": "String", "ext_spec": {"type": "pattern", "pattern": "digits", "min_len": 10, "max_len": 12}},
        "facebook账号": {"datatype": "String", "ext_spec": {"type": "pattern", "pattern": "digits", "min_len": 10, "max_len": 12}},
        "待还贷款笔数": {"datatype": "Integer", "ext_spec": {"type": "range", "min": 0, "max": 10}},
        "待还贷款金额": {"datatype": "BigDecimal", "ext_spec": {"type": "range", "min": 0.0, "max": 100000.0}},
        "今日到期贷款笔数": {"datatype": "Integer", "ext_spec": {"type": "range", "min": 0, "max": 5}},
        "按时还款贷款笔数": {"datatype": "Integer", "ext_spec": {"type": "range", "min": 0, "max": 20}},
        "工作类型": {"datatype": "String", "ext_spec": {"type": "enum", "items": [{"value": "全职"}, {"value": "兼职"}, {"value": "临时"}, {"value": "自由职业"}]}},
        "行业": {"datatype": "String", "ext_spec": {"type": "enum", "items": [{"value": "制造业"}, {"value": "服务业"}, {"value": "金融"}, {"value": "互联网"}, {"value": "教育"}]}},
        "职业": {"datatype": "String", "ext_spec": {"type": "enum", "items": [{"value": "销售"}, {"value": "客服"}, {"value": "工程师"}, {"value": "司机"}, {"value": "教师"}]}},
        "月收入": {"datatype": "BigDecimal", "ext_spec": {"type": "range", "min": 1000.0, "max": 20000.0}},
        "雇佣期限": {"datatype": "Integer", "ext_spec": {"type": "range", "min": 1, "max": 120}},
        "工资支付方式": {"datatype": "String", "ext_spec": {"type": "enum", "items": [{"value": "银行卡"}, {"value": "现金"}, {"value": "转账"}]}},
        "发薪日": {"datatype": "Integer", "ext_spec": {"type": "range", "min": 1, "max": 31}},
        "纳税人识别号": {"datatype": "String", "ext_spec": {"type": "pattern", "pattern": r"^\d+$"}}
    },
    "94AI": {
        "94AI电核标签": {"datatype": "String", "ext_spec": {"type": "enum", "items": [{"value": "通过"}, {"value": "需要核实"}, {"value": "不通过"}]}}
    },
    "IZI": {
        "izi 是否注册whatsapp": {"datatype": "Boolean", "ext_spec": {"type": "enum", "items": [{"value": True}, {"value": False}] }}
    }
}


def apply_updates(config: dict, updates: dict) -> dict:
    class_mappings = config.setdefault("class_mappings", {})
    for sheet, fields in updates.items():
        sheet_map = class_mappings.setdefault(sheet, {})
        for field, rule in fields.items():
            datatype = rule.get("datatype", "String")
            ext_spec = rule.get("ext_spec")
            ext_json = json.dumps(ext_spec, ensure_ascii=False) if ext_spec is not None else None
            sheet_map[field] = {"datatype": datatype, "ext_json": ext_json}
    return config


def main():
    with open(CONFIG_PATH, 'r', encoding='utf-8') as f:
        config = json.load(f)
    config = apply_updates(config, updates)
    with open(CONFIG_PATH, 'w', encoding='utf-8') as f:
        json.dump(config, f, ensure_ascii=False, indent=2)
    print("field_mapping.json 已更新（新增 urule 中文列头映射）")


if __name__ == '__main__':
    main()