/**
 * LeftValueEditor — controlled editor for a `LeftValue` (`<left>` element).
 *
 * The left-hand side of an atom. `type` selects which fields are populated:
 *   - variable    → varCategory / var / varLabel / datatype
 *   - parameter   → same fields (var-category is implicitly "参数" on the
 *                   server side, so the varCategory field is hidden)
 *   - method      → TODO (needs bean/method picker)
 *   - commonfunction → TODO (needs function picker)
 *
 * Data flow is single-direction: parent owns the LeftValue object; field
 * edits emit a shallow-cloned next value via onChange.
 */
import { Input, Select } from 'antd';
import type { LeftValue } from '../model/types';
import { LEFT_TYPE_OPTIONS, ARITH_OP_OPTIONS } from './constants';

export interface LeftValueEditorProps {
  /** The current left value (controlled). */
  value: LeftValue;
  /** Called with a new LeftValue on every field edit. */
  onChange: (next: LeftValue) => void;
  /** Optional compact mode. */
  compact?: boolean;
}

function patch(v: LeftValue, p: Partial<LeftValue>): LeftValue {
  return { ...v, ...p };
}

/**
 * Build a fresh LeftValue when the user switches `type`, dropping fields that
 * don't belong to the new type.
 */
function freshLeftForType(type: LeftValue['type']): LeftValue {
  switch (type) {
    case 'variable':
      return { type, varCategory: '', var: '', varLabel: '', datatype: '' };
    case 'parameter':
      // Server implicitly sets var-category="参数" for parameter lefts.
      return { type, var: '', varLabel: '', datatype: '' };
    case 'method':
      return { type, beanName: '', beanLabel: '', methodName: '', methodLabel: '' };
    case 'commonfunction':
      return { type, functionName: '', functionLabel: '' };
    default:
      return { type: 'variable', varCategory: '', var: '', varLabel: '', datatype: '' };
  }
}

export function LeftValueEditor({ value, onChange, compact }: LeftValueEditorProps) {
  const gap = compact ? 4 : 8;
  const rowStyle: React.CSSProperties = { marginBottom: gap };
  const onTypeChange = (next: string) => {
    onChange(freshLeftForType(next as LeftValue['type']));
  };

  return (
    <div>
      <div style={rowStyle}>
        <Select
          size="small"
          style={{ width: '100%' }}
          value={value.type}
          onChange={onTypeChange}
          options={LEFT_TYPE_OPTIONS}
        />
      </div>

      {(value.type === 'variable' || value.type === 'parameter') && (
        <>
          {value.type === 'variable' && (
            <div style={rowStyle}>
              <Input
                size="small"
                addonBefore="变量分类"
                placeholder="如 客户.客户"
                value={value.varCategory ?? ''}
                onChange={(e) => onChange(patch(value, { varCategory: e.target.value }))}
              />
            </div>
          )}
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

      {value.type === 'method' && (
        // TODO: wire a bean/method knowledge-tree picker.
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

      {value.type === 'commonfunction' && (
        // TODO: wire a function picker.
        <div style={rowStyle}>
          <Input
            size="small"
            addonBefore="函数"
            disabled
            value={(value.functionName ?? '') + (value.functionLabel ? ' (' + value.functionLabel + ')' : '')}
          />
        </div>
      )}

      {/* Optional simple-arith chain — for the first pass we only render the
          head node; nested chains are preserved verbatim. */}
      {value.arithmetic && (
        <div style={rowStyle}>
          <Select
            size="small"
            style={{ width: 100 }}
            value={value.arithmetic.type}
            onChange={(next) =>
              onChange(patch(value, { arithmetic: { ...value.arithmetic!, type: next as 'Add' | 'Sub' | 'Mul' | 'Div' | 'Mod' } }))}
            options={ARITH_OP_OPTIONS}
          />
          <Input
            size="small"
            style={{ width: 'calc(100% - 104px)', marginLeft: 4 }}
            placeholder="算术值"
            value={value.arithmetic.value}
            onChange={(e) => onChange(patch(value, { arithmetic: { ...value.arithmetic!, value: e.target.value } }))}
          />
        </div>
      )}
    </div>
  );
}

export default LeftValueEditor;
