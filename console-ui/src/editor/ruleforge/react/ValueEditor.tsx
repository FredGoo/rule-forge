/**
 * ValueEditor — controlled editor for a `ValueExpr` (`<value>` element).
 *
 * The right-hand side of an atom, a named criterion, an action argument, a
 * method/function parameter, etc. — anything that serializes through
 * serializeValue() in model/serialize.ts.
 *
 * Data flow (single direction, parent owns state):
 *   props.value  →  rendered fields
 *   field edit   →  props.onChange(nextValue)
 *
 * The parent must replace its ValueExpr with the new object; this component
 * never mutates `props.value`.
 *
 * Implemented: Input / Variable / Parameter / Constant (the common cases).
 * TODO: VariableCategory / Method / CommonFunction / NamedReference /
 *       complex-arith trailing chain — these need richer pickers (knowledge
 *       tree dialog for Method/CommonFunction bean selection) that are out of
 *       scope for the first React pass; the value is preserved verbatim and
 *       the fields render read-only.
 */
import { Input, Select } from 'antd';
import type { ValueExpr } from '../model/types';
import { VALUE_TYPE_OPTIONS } from './constants';

export interface ValueEditorProps {
  /** The current value expression (controlled). */
  value: ValueExpr;
  /** Called with a new ValueExpr on every field edit. */
  onChange: (next: ValueExpr) => void;
  /** Optional compact mode (no margin under fields). */
  compact?: boolean;
}

/**
 * Patch the value with a partial update, preserving the existing type.
 * Returns a shallow clone so React sees a new object reference.
 */
function patch(v: ValueExpr, p: Partial<ValueExpr>): ValueExpr {
  return { ...v, ...p };
}

/** Build a fresh ValueExpr when the user switches `type`. */
function freshValueForType(type: ValueExpr['type']): ValueExpr {
  switch (type) {
    case 'Input':
      return { type, content: '' };
    case 'Variable':
      return { type, varCategory: '', var: '', varLabel: '', datatype: '' };
    case 'VariableCategory':
      return { type, varCategory: '' };
    case 'Parameter':
      return { type, varCategory: '参数', var: '', varLabel: '', datatype: '' };
    case 'Constant':
      return { type, constCategory: '', const: '', constLabel: '' };
    case 'Method':
      return { type, beanName: '', beanLabel: '', methodName: '', methodLabel: '' };
    case 'CommonFunction':
      return { type, functionName: '', functionLabel: '' };
    case 'NamedReference':
      return { type, referenceName: '', propertyName: '', propertyLabel: '', datatype: '' };
    default:
      return { type: 'Input', content: '' };
  }
}

export function ValueEditor({ value, onChange, compact }: ValueEditorProps) {
  const onTypeChange = (next: string) => {
    onChange(freshValueForType(next as ValueExpr['type']));
  };

  const gap = compact ? 4 : 8;
  const rowStyle: React.CSSProperties = { marginBottom: gap };

  return (
    <div>
      <div style={rowStyle}>
        <Select
          size="small"
          style={{ width: '100%' }}
          value={value.type}
          onChange={onTypeChange}
          options={VALUE_TYPE_OPTIONS}
        />
      </div>

      {value.type === 'Input' && (
        <div style={rowStyle}>
          <Input
            size="small"
            placeholder="输入值"
            value={value.content ?? ''}
            onChange={(e) => onChange(patch(value, { content: e.target.value }))}
          />
        </div>
      )}

      {value.type === 'Variable' && (
        <>
          <div style={rowStyle}>
            <Input
              size="small"
              addonBefore="变量分类"
              placeholder="如 客户.客户"
              value={value.varCategory ?? ''}
              onChange={(e) => onChange(patch(value, { varCategory: e.target.value }))}
            />
          </div>
          <div style={rowStyle}>
            <Input
              size="small"
              addonBefore="变量名"
              placeholder="如 age"
              value={value.var ?? ''}
              onChange={(e) => onChange(patch(value, { var: e.target.value }))}
            />
          </div>
          <div style={rowStyle}>
            <Input
              size="small"
              addonBefore="标签"
              placeholder="如 年龄"
              value={value.varLabel ?? ''}
              onChange={(e) => onChange(patch(value, { varLabel: e.target.value }))}
            />
          </div>
          <div style={rowStyle}>
            <Input
              size="small"
              addonBefore="类型"
              placeholder="如 Integer"
              value={value.datatype ?? ''}
              onChange={(e) => onChange(patch(value, { datatype: e.target.value }))}
            />
          </div>
        </>
      )}

      {value.type === 'VariableCategory' && (
        <div style={rowStyle}>
          <Input
            size="small"
            addonBefore="变量分类"
            placeholder="变量分类名"
            value={value.varCategory ?? ''}
            onChange={(e) => onChange(patch(value, { varCategory: e.target.value }))}
          />
        </div>
      )}

      {value.type === 'Parameter' && (
        <>
          <div style={rowStyle}>
            <Input
              size="small"
              addonBefore="参数名"
              placeholder="如 amount"
              value={value.var ?? ''}
              onChange={(e) => onChange(patch(value, { var: e.target.value }))}
            />
          </div>
          <div style={rowStyle}>
            <Input
              size="small"
              addonBefore="标签"
              placeholder="参数标签"
              value={value.varLabel ?? ''}
              onChange={(e) => onChange(patch(value, { varLabel: e.target.value }))}
            />
          </div>
          <div style={rowStyle}>
            <Input
              size="small"
              addonBefore="类型"
              placeholder="如 BigDecimal"
              value={value.datatype ?? ''}
              onChange={(e) => onChange(patch(value, { datatype: e.target.value }))}
            />
          </div>
        </>
      )}

      {value.type === 'Constant' && (
        <>
          <div style={rowStyle}>
            <Input
              size="small"
              addonBefore="常量分类"
              placeholder="常量分类名"
              value={value.constCategory ?? ''}
              onChange={(e) => onChange(patch(value, { constCategory: e.target.value }))}
            />
          </div>
          <div style={rowStyle}>
            <Input
              size="small"
              addonBefore="常量名"
              placeholder="如 ONE_HUNDRED"
              value={value.const ?? ''}
              onChange={(e) => onChange(patch(value, { const: e.target.value }))}
            />
          </div>
          <div style={rowStyle}>
            <Input
              size="small"
              addonBefore="标签"
              placeholder="常量标签"
              value={value.constLabel ?? ''}
              onChange={(e) => onChange(patch(value, { constLabel: e.target.value }))}
            />
          </div>
        </>
      )}

      {value.type === 'Method' && (
        // TODO: wire a knowledge-tree picker for bean/method selection.
        // For now the existing Method fields are preserved verbatim; the user
        // can't edit them in this pass.
        <div style={rowStyle}>
          <Input
            size="small"
            addonBefore="bean.method"
            disabled
            value={
              (value.beanName ?? '') +
              '.' +
              (value.methodName ?? '') +
              (value.methodLabel ? ' (' + value.methodLabel + ')' : '')
            }
          />
        </div>
      )}

      {value.type === 'CommonFunction' && (
        // TODO: wire a function picker (functionName / functionParameter).
        <div style={rowStyle}>
          <Input
            size="small"
            addonBefore="函数"
            disabled
            value={(value.functionName ?? '') + (value.functionLabel ? ' (' + value.functionLabel + ')' : '')}
          />
        </div>
      )}

      {value.type === 'NamedReference' && (
        <>
          <div style={rowStyle}>
            <Input
              size="small"
              addonBefore="引用名"
              placeholder="命名引用 reference-name"
              value={value.referenceName ?? ''}
              onChange={(e) => onChange(patch(value, { referenceName: e.target.value }))}
            />
          </div>
          <div style={rowStyle}>
            <Input
              size="small"
              addonBefore="属性"
              placeholder="property-name"
              value={value.propertyName ?? ''}
              onChange={(e) => onChange(patch(value, { propertyName: e.target.value }))}
            />
          </div>
          <div style={rowStyle}>
            <Input
              size="small"
              addonBefore="类型"
              placeholder="如 String"
              value={value.datatype ?? ''}
              onChange={(e) => onChange(patch(value, { datatype: e.target.value }))}
            />
          </div>
        </>
      )}
    </div>
  );
}

export default ValueEditor;
