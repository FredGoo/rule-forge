"""
数据类型定义和转换工具
"""
from decimal import Decimal
from typing import Any, Union


class DataTypes:
    """数据类型常量"""
    INTEGER = "Integer"
    LONG = "Long"
    DOUBLE = "Double"
    FLOAT = "Float"
    BIGDECIMAL = "BigDecimal"
    BOOLEAN = "Boolean"
    STRING = "String"


class TypeConverter:
    """类型转换器"""
    
    @staticmethod
    def cast_by_type(value: Any, datatype: str) -> Any:
        """根据数据类型转换值"""
        if value is None:
            return TypeConverter.default_by_type(datatype)
        
        datatype_lower = datatype.lower()
        
        try:
            if datatype_lower in ["integer", "int"]:
                if isinstance(value, (int, float)):
                    return int(value)
                return int(str(value))
            
            elif datatype_lower == "long":
                if isinstance(value, (int, float)):
                    return int(value)
                return int(str(value))
            
            elif datatype_lower == "double":
                if isinstance(value, (int, float)):
                    return float(value)
                return float(str(value))
            
            elif datatype_lower == "float":
                if isinstance(value, (int, float)):
                    return float(value)
                return float(str(value))
            
            elif datatype_lower == "bigdecimal":
                if isinstance(value, Decimal):
                    return value
                if isinstance(value, (int, float)):
                    return Decimal(str(value))
                return Decimal(str(value))
            
            elif datatype_lower == "boolean":
                # 统一输出 0/1
                if isinstance(value, bool):
                    return 1 if value else 0
                s = str(value).strip().lower()
                truthy = s in ["1", "true", "yes", "on"]
                return 1 if truthy else 0
            
            else:  # String or unknown type
                return str(value)
                
        except (ValueError, TypeError):
            return TypeConverter.default_by_type(datatype)
    
    @staticmethod
    def default_by_type(datatype: str) -> Any:
        """根据数据类型返回默认值"""
        datatype_lower = datatype.lower()
        
        if datatype_lower in ["integer", "int", "long"]:
            return 0
        elif datatype_lower in ["double", "float"]:
            return 0.0
        elif datatype_lower == "bigdecimal":
            return Decimal("0")
        elif datatype_lower == "boolean":
            # 布尔默认值为 0
            return 0
        else:  # String or unknown type
            return ""
    
    @staticmethod
    def as_number(value: Any) -> Union[float, int, None]:
        """将值转换为数字"""
        if value is None:
            return None
        if isinstance(value, (int, float)):
            return value
        try:
            # 尝试转换为整数
            if isinstance(value, str) and '.' not in value:
                return int(value)
            # 否则转换为浮点数
            return float(value)
        except (ValueError, TypeError):
            return None