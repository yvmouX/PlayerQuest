/**
 * YAML 往返自检。
 *
 * <h2>为什么把它接进构建</h2>
 * YAML 视图是「文本 ⇄ 模型」的第二次翻译，出错方式全都是<b>静默</b>的：
 * 一个材质名被解析成布尔、一行描述被折行拆开、一个未知键被悄悄丢掉，
 * 界面都不会报错，只会把错的任务存进库。因此这里对**真实代码**
 * （{@code src/utils/yaml.ts}，用 vite 现打成临时 ESM 再 import）跑断言，
 * 并挂进 {@code npm run build}：任何一处退化都会让构建失败。
 *
 * <h2>重点钉住的是 YAML 1.1 的那几个坑</h2>
 * 本项目的语言文件已经栽过一次（{@code common.yes} 从未生效）：YAML 1.1 把裸写的
 * yes/no/on/off 当布尔。任务里的目标名完全可能是这些词（发言关键词 "yes"、
 * 材质名 "NO"…），因此逐个验证它们往返后仍是字符串。
 */
import assert from 'node:assert/strict'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { pathToFileURL } from 'node:url'
import { build } from 'vite'

/** 把 TS 工具打成临时 ESM 并载入，这样断言的对象就是真正跑在浏览器里的那份代码。 */
async function loadYamlModule() {
  const outDir = await mkdtemp(path.join(tmpdir(), 'ptx-yaml-check-'))
  await build({
    configFile: false,
    logLevel: 'silent',
    build: {
      outDir,
      emptyOutDir: true,
      minify: false,
      lib: { entry: path.resolve('src/utils/yaml.ts'), formats: ['es'], fileName: () => 'yaml.mjs' }
    }
  })
  const module = await import(pathToFileURL(path.join(outDir, 'yaml.mjs')).href)
  return { module, cleanup: () => rm(outDir, { recursive: true, force: true }) }
}

const { module: yaml, cleanup } = await loadYamlModule()

/** 断言计数：至少让人知道这个脚本真的跑了东西。 */
let checks = 0
function check(name, fn) {
  fn()
  checks++
  process.stdout.write(`  ✓ ${name}\n`)
}

try {
  // ---------------------------------------------------------------- 任务往返
  check('任务往返：字段、目标、奖励、前置全部保留', () => {
    const quest = {
      id: 'daily_mine',
      name: '<yellow>挖矿日常',
      description: ['<gray>挖掘 64 个石头', '第二行'],
      icon: 'STONE_PICKAXE',
      category: '每日',
      type: 'DAILY',
      prerequisites: ['p1', 'p2'],
      objectives: [
        { type: 'break_block', properties: { target: 'STONE', amount: 64 } },
        { type: 'chat', properties: { target: '你好', amount: 1 } }
      ],
      rewards: [{ type: 'exp', properties: { amount: 200 } }],
      refreshCost: 1000,
      enabled: false
    }

    const result = yaml.questFromYaml(yaml.questToYaml(quest), 'fallback')

    assert.equal(result.error, '', '不应有解析错误')
    assert.deepEqual(result.warnings, [], '不应有警告')
    assert.equal(result.value.id, 'daily_mine')
    assert.equal(result.value.name, '<yellow>挖矿日常', '颜色标签必须原样保留')
    assert.deepEqual(result.value.description, quest.description, '描述是按行存的，不能折行或丢行')
    assert.equal(result.value.type, 'DAILY')
    assert.equal(result.value.enabled, false)
    assert.equal(result.value.refreshCost, 1000)
    assert.deepEqual(result.value.prerequisites, ['p1', 'p2'])
    assert.deepEqual(result.value.objectives, quest.objectives, '目标的结构与顺序都不能变')
    assert.deepEqual(result.value.rewards, quest.rewards)
  })

  check('YAML 1.1 的布尔/数字字面量不会把字符串改掉', () => {
    // 这些都是「看起来像别的类型」的合法配置值：发言关键词、材质名、尺寸文本…
    const tricky = ['yes', 'no', 'on', 'off', 'NO', 'YES', 'y', 'n', 'true', 'false',
      '1.20', '123', '0x10', '*', '~', 'null', 'NULL', '', '1:30', '2024-01-01', '1_000']
    const quest = {
      id: 'q',
      name: 'tricky',
      description: [],
      icon: 'PAPER',
      category: '',
      type: 'NORMAL',
      prerequisites: [],
      objectives: tricky.map(value => ({ type: 'chat', properties: { target: value, amount: 1 } })),
      rewards: [],
      refreshCost: 0,
      enabled: true
    }

    const result = yaml.questFromYaml(yaml.questToYaml(quest), 'q')

    assert.equal(result.error, '')
    const values = result.value.objectives.map(objective => objective.properties.target)
    assert.deepEqual(values, tricky, `字符串被解析成了别的类型：${JSON.stringify(values)}`)
    values.forEach(value => assert.equal(typeof value, 'string', `「${value}」不再是字符串`))
  })

  check('日期形状的字符串不会被解析成 Date（CORE_SCHEMA，而非默认的 1.1 时间戳规则）', () => {
    const result = yaml.questFromYaml('id: q\nobjectives:\n  - type: chat\n    properties: { target: 2024-01-01 }\n', 'q')

    assert.equal(result.error, '')
    const target = result.value.objectives[0].properties.target
    assert.equal(typeof target, 'string', `被解析成了 ${target instanceof Date ? 'Date' : typeof target}`)
    assert.equal(target, '2024-01-01')
  })

  check('整数与后端一致：012 是十进制 12、0x10 是 16（两边读同一份文件必须得到同样的值）', () => {
    const result = yaml.questFromYaml(
      'id: q\nobjectives:\n  - type: chat\n    properties: { a: 012, b: 0x10, c: 64 }\n', 'q')
    const properties = result.value.objectives[0].properties

    assert.equal(properties.a, 12)
    assert.equal(properties.b, 16)
    assert.equal(properties.c, 64)
  })

  check('数值与布尔属性保持原类型', () => {
    const quest = {
      id: 'q',
      name: 'types',
      description: [],
      icon: 'PAPER',
      category: '',
      type: 'NORMAL',
      prerequisites: [],
      objectives: [{ type: 'x', properties: { amount: 64, ratio: 1.5, flag: true, note: null } }],
      rewards: [],
      refreshCost: 0,
      enabled: true
    }

    const result = yaml.questFromYaml(yaml.questToYaml(quest), 'q')
    const properties = result.value.objectives[0].properties

    assert.equal(typeof properties.amount, 'number')
    assert.equal(properties.amount, 64)
    assert.equal(properties.ratio, 1.5)
    assert.equal(properties.flag, true)
    assert.equal(properties.note, null)
  })

  check('未知顶层字段：不静默丢弃，列进警告并给出可用字段', () => {
    const result = yaml.questFromYaml('id: q\nname: n\nreward: []\n', 'q')

    assert.equal(result.error, '')
    assert.equal(result.warnings.length, 1, `应有一条警告，实际：${JSON.stringify(result.warnings)}`)
    assert.ok(result.warnings[0].includes('reward'), result.warnings[0])
    assert.ok(result.warnings[0].includes('rewards'), '警告里要列出可用字段')
  })

  check('非法 YAML / 空文本 / 顶层不是映射：报错且不产出值', () => {
    assert.notEqual(yaml.questFromYaml('id: [', 'q').error, '', '括号没闭合要报错')
    assert.notEqual(yaml.questFromYaml('   ', 'q').error, '', '空文本要报错')
    assert.notEqual(yaml.questFromYaml('- a\n- b\n', 'q').error, '', '顶层是列表要报错')
    for (const text of ['id: [', '   ', '- a\n- b\n']) {
      assert.equal(yaml.questFromYaml(text, 'q').value, null)
    }
  })

  check('没有目标的任务按「解析成功」处理，由后端校验标红（与可视化表单口径一致）', () => {
    const result = yaml.questFromYaml('id: q\nname: n\n', 'q')
    assert.equal(result.error, '')
    assert.deepEqual(result.value.objectives, [])
  })

  check('新任务：id 缺失时用回退 id，不至于解析失败', () => {
    const result = yaml.questFromYaml('objectives:\n  - type: chat\n    properties: {}\n', 'brand_new')
    assert.equal(result.error, '')
    assert.equal(result.value.id, 'brand_new')
  })

  check('空字段不写进 YAML（免得让人以为必须填）', () => {
    const text = yaml.questToYaml({
      id: 'q', name: 'n', description: [], icon: 'PAPER', category: '', type: 'NORMAL',
      prerequisites: [], objectives: [], rewards: [], refreshCost: 0, enabled: true
    })

    assert.ok(!text.includes('description'), text)
    assert.ok(!text.includes('category'), text)
    assert.ok(!text.includes('prerequisites'), text)
    assert.ok(text.includes('objectives'), '关键字段仍要显式写出')
  })

  // ---------------------------------------------------------------- 预设往返
  check('预设往返：名称、类型、属性保留；未知字段有警告', () => {
    const preset = {
      id: 'p1',
      name: '挖 64 个石头',
      type: 'break_block',
      description: '常用',
      properties: { target: 'NO', amount: 64 }
    }

    const result = yaml.presetFromYaml(yaml.presetToYaml(preset))

    assert.equal(result.error, '')
    assert.deepEqual(result.warnings, [])
    assert.deepEqual(result.value, preset, '材质名 NO 不能被改成布尔')

    const withUnknown = yaml.presetFromYaml('name: x\ntype: exp\npropertie: {}\n')
    assert.equal(withUnknown.error, '')
    assert.ok(withUnknown.warnings[0].includes('propertie'), withUnknown.warnings[0])
  })

  check('预设缺 type 时被拒（后端也要求它非空）', () => {
    const result = yaml.presetFromYaml('name: 只有名字\n')
    assert.equal(result.value, null)
    assert.notEqual(result.error, '')
    // 新建预设时 id 可以留空：由后端生成
    assert.equal(yaml.presetFromYaml('type: exp\nproperties:\n  amount: 10\n').value.id, '')
  })

  process.stdout.write(`YAML 往返自检通过（${checks} 项）\n`)
} finally {
  await cleanup()
}
