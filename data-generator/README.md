# 数据生成器 (Data Generator)

基于Python的Excel数据生成工具，可以根据配置文件自动生成模拟数据并填充到Excel文件中。

## 功能特性

- 🎯 智能字段映射: 根据类名和字段名自动匹配数据生成规则（仅使用JSON配置）
- 📊 Excel模板支持: 读取Excel模板，以sheet名作为类名，列标题作为字段名
- 🎲 多种数据类型: 支持String、Integer、Long、Double、Boolean等数据类型
- 🔧 灵活配置: 支持枚举、范围、正则模式等多种数据生成策略
- 🚀 Faker集成: 可选使用Faker库生成更真实的数据
- 📝 配置管理: JSON格式的配置文件，易于维护和扩展

## 项目结构

```
data-generator/
├── main.py                    # 主程序入口
├── requirements.txt           # Python依赖
├── README.md                 # 项目说明
├── config/
│   └── field_mapping.json   # 字段映射配置
├── generators/
│   ├── __init__.py
│   ├── data_types.py         # 数据类型定义
│   └── mock_value_generator.py # 数据生成器
├── utils/
│   ├── __init__.py
│   ├── excel_handler.py      # Excel处理工具
│   └── config_loader.py      # 配置加载器
└── templates/
    └── sample_template.xlsx  # 示例模板
```

## 安装依赖

```bash
cd /Users/fred/git/ruleforge/data-generator
pip install -r requirements.txt
```

## 使用方法

### 1. 查看配置摘要

```bash
python main.py --summary
```

### 2. 创建Excel模板

```bash
# 根据配置创建模板
python main.py --create-template --output templates/my_template.xlsx

# 为指定类创建模板
python main.py --create-template --output templates/user_template.xlsx --classes User Product
```

### 3. 验证模板文件

```bash
python main.py --validate --template templates/my_template.xlsx
```

### 4. 生成数据（JSON规则，集中一次性写入）

```bash
# 基本用法（仅JSON配置）
python main.py --template templates/my_template.xlsx --output output/generated_data.xlsx

# 指定生成行数
python main.py --template templates/my_template.xlsx --output output/generated_data.xlsx --rows 500

# 不使用Faker（使用简单随机数据）
python main.py --template templates/my_template.xlsx --output output/generated_data.xlsx --no-faker
```

参数说明：
- `--template/-t`: Excel模板文件路径（ruleforge批量模板）
- `--output/-o`: 输出Excel文件路径（新文件，所有sheet生成完成后一次性写入）
- `--rows/-r`: 每个sheet生成的行数（默认100）
- `--no-faker`: 关闭Faker（默认开启，邮箱等将用Faker生成）

规则匹配优先级（仅JSON）：
1. `class_mappings[Sheet名][列名]` 精确匹配
2. `field_patterns[列名]` 通用规则
3. 字段名类型推断 + `default_rules`

输出行为说明：
- 所有sheet的数据在内存中生成完成后，集中一次性写入到输出Excel（原子性更好，性能更优）。
- 布尔值统一输出为数字 0/1（生成器内部已处理）。
- 枚举字段建议使用数值编码（例如 `{ "type": "enum", "items": [{"value":1},{"value":2}] }`）。
- 邮箱可使用 Faker（`{"type":"pattern","pattern":"email","faker_type":"email"}`）。
- 数字字符串范围支持前导零（`{"type":"range","min":0,"max":999999,"width":6}`）。

## 配置文件说明

配置文件位于 `config/field_mapping.json`，包含以下部分：

### 1. 默认规则 (default_rules)

为每种数据类型定义默认的生成规则：

```json
{
  "default_rules": {
    "String": {
      "datatype": "String",
      "ext_json": null
    },
    "Integer": {
      "datatype": "Integer", 
      "ext_json": "{\"type\": \"range\", \"min\": 1, \"max\": 1000}"
    }
  }
}
```

### 2. 类映射 (class_mappings)

为特定类的字段定义生成规则：

```json
{
  "class_mappings": {
    "User": {
      "id": {
        "datatype": "Long",
        "ext_json": "{\"type\": \"range\", \"min\": 1, \"max\": 999999}"
      },
      "name": {
        "datatype": "String",
        "ext_json": "{\"type\": \"faker\", \"pattern\": \"name\"}"
      }
    }
  }
}
```

### 3. 字段模式 (field_patterns)

为常见字段名定义通用规则：

```json
{
  "field_patterns": {
    "email": {
      "datatype": "String",
      "ext_json": "{\"type\": \"pattern\", \"pattern\": \"email\", \"faker_type\": \"email\"}"
    },
    "age": {
      "datatype": "Integer",
      "ext_json": "{\"type\": \"range\", \"min\": 18, \"max\": 70}"
    }
  }
}
```

## 常见问题

1. Excel模板列头必须在第一行；sheet名将作为类名参与规则匹配
2. 配置项匹配不到时会回退到类型推断与 default_rules；建议完善 `class_mappings`/`field_patterns`
3. JSON格式错误: 验证配置文件的JSON格式是否正确
4. Excel读取失败: 确保Excel文件格式正确，且第一行为列标题

### 调试模式

设置环境变量启用详细日志：

```bash
export PYTHONPATH=/Users/fred/git/ruleforge/data-generator
python -m logging.basicConfig --level=DEBUG main.py [参数]
```