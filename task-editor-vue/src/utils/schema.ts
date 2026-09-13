/**
 * schema 相关的纯函数工具。
 *
 * <p>这是「表单由 schema 驱动」的核心：界面只认识 {@link TypeSchema}，
 * 不认识 break_block / money 之类的具体类型。后端新增一种类型时，
 * 这里以及所有视图都无需改动。
 */
import type { FieldSchema, Properties, PropertyValue, TypeSchema } from '../types'

/** 按字段类型给一个合理的空值。 */
function emptyValue(field: FieldSchema): PropertyValue {
  switch (field.type) {
    case 'BOOLEAN':
      return false
    case 'INTEGER':
    case 'DECIMAL':
      return 0
    default:
      return ''
  }
}

/** 字段默认值：后端可能给 null，此时按类型补一个空值。 */
export function fieldDefault(field: FieldSchema): PropertyValue {
  const value = field.defaultValue
  if (value === null || value === undefined) {
    return emptyValue(field)
  }
  return value
}

/** 按 schema 生成一份默认属性表（新增目标/奖励时使用）。 */
export function defaultProperties(schema: TypeSchema | undefined): Properties {
  const result: Properties = {}
  if (!schema) {
    return result
  }
  for (const field of schema.fields) {
    result[field.key] = fieldDefault(field)
  }
  return result
}

/**
 * 合并默认值：补齐 schema 有而 properties 缺的键。
 *
 * <p>刻意只做「补」，不做「删」——后端删掉某个字段时，旧任务里
 * 残留的值会在下次保存时原样带回，不会因为一次打开编辑器而丢失。
 */
export function withDefaults(schema: TypeSchema | undefined, properties: Properties | undefined): Properties {
  const merged: Properties = { ...(properties ?? {}) }
  if (!schema) {
    return merged
  }
  for (const field of schema.fields) {
    const value = merged[field.key]
    if (value === undefined) {
      merged[field.key] = fieldDefault(field)
    }
  }
  return merged
}

/**
 * 把目标/奖励补成「可直接提交」的形态：按 schema 补上缺失的默认值。
 *
 * <p>编辑器在三条路径上都需要它，任何一条漏了都会让后端拿到空配置：提交前
 * （用户没碰过的字段也要写入）、schema 迟到（任务先到、类型定义后到）以及从
 * 后端装载时。补而不是删，见 {@link withDefaults}。
 */
export function normalizeInstances(
  instances: { type: string; properties: Properties }[],
  schemas: Record<string, TypeSchema>
): { type: string; properties: Properties }[] {
  return instances.map(instance => ({
    type: instance.type,
    properties: withDefaults(schemas[instance.type], instance.properties)
  }))
}

/** 类型下拉的显示文案：显示名（类型 id）。 */
export function typeLabel(schema: TypeSchema): string {
  if (!schema.displayName || schema.displayName === schema.id) {
    return schema.id
  }
  return `${schema.displayName}（${schema.id}）`
}

/**
 * 类型下拉的候选项。
 *
 * <p>奖励可以「软依赖缺失」：预设在保存前就要拦住这类类型，实例卡片则靠同一条
 * 判断把不可用项置灰，两处的措辞与禁用规则必须一致，否则会出现「预设里能选、
 * 编辑器里选不了」这种自相矛盾的界面。因此统一在这里生成。
 */
export function schemaOptions(
  schemas: Record<string, TypeSchema>
): { id: string; label: string; disabled: boolean }[] {
  return Object.values(schemas).map(schema => {
    const unavailable = schema.available === false
    return {
      id: schema.id,
      label: unavailable ? `${typeLabel(schema)} — 不可用` : typeLabel(schema),
      disabled: unavailable
    }
  })
}

/** 数字输入框的文本 → 属性值；留空按 null 提交，由后端按默认值处理。 */
export function parseNumberInput(raw: string, integer: boolean): PropertyValue {
  const text = raw.trim()
  if (text === '') {
    return null
  }
  const value = Number(text)
  if (!Number.isFinite(value)) {
    return null
  }
  return integer ? Math.trunc(value) : value
}

/** 属性值 → 输入框文本。 */
export function toInputText(value: PropertyValue | undefined): string {
  if (value === null || value === undefined) {
    return ''
  }
  return String(value)
}

/** 属性值是否算「空」，用于必填提示。 */
export function isEmptyValue(value: PropertyValue | undefined): boolean {
  if (value === null || value === undefined) {
    return true
  }
  return typeof value === 'string' && value.trim() === ''
}

/**
 * 生成一行属性摘要，用于在列表里预览目标/奖励内容。
 * 完全按 schema 的字段顺序拼装，不认识具体类型。
 */
export function summarizeProperties(properties: Properties, schema: TypeSchema | undefined): string {
  const keys = schema ? schema.fields.map(field => field.key) : Object.keys(properties)
  const parts: string[] = []
  for (const key of keys) {
    const value = properties[key]
    if (isEmptyValue(value)) {
      continue
    }
    const label = schema?.fields.find(field => field.key === key)?.label ?? key
    parts.push(`${label}=${String(value)}`)
  }
  return parts.join(' · ')
}
