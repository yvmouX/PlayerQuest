/**
 * 素材目录筛选的自检（来源 / 分类 / 范围 / 搜索）。
 *
 * <h2>为什么需要它</h2>
 * 「按插件筛选」的错法全都是静默的：标签点了没反应、切了插件却留下上一个分类的组合、
 * 鱼 id 混进方块列表、旧版后端缺 {@code sources} 时整排标签消失——界面都不会报错，
 * 只是让人找不到东西。这里对**真实代码**（{@code src/utils/catalog.ts}，用 vite 现打成
 * 临时 ESM 再 import）跑断言，并挂进 {@code npm run build}：任何一处退化都会让构建失败。
 */
import assert from 'node:assert/strict'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { pathToFileURL } from 'node:url'
import { build } from 'vite'

/** 把 TS 工具打成临时 ESM 并载入，断言的对象就是真正跑在浏览器里的那份代码。 */
async function loadCatalogModule() {
  const outDir = await mkdtemp(path.join(tmpdir(), 'ptx-catalog-check-'))
  await build({
    configFile: false,
    logLevel: 'silent',
    build: {
      outDir,
      emptyOutDir: true,
      minify: false,
      lib: { entry: path.resolve('src/utils/catalog.ts'), formats: ['es'], fileName: () => 'catalog.mjs' }
    }
  })
  const module = await import(pathToFileURL(path.join(outDir, 'catalog.mjs')).href)
  return { module, cleanup: () => rm(outDir, { recursive: true, force: true }) }
}

const { module: catalog, cleanup } = await loadCatalogModule()

let checks = 0
function check(name, fn) {
  fn()
  checks++
  process.stdout.write(`  ✓ ${name}\n`)
}

/** 一份像真目录那样混着原版与三家插件内容的响应。 */
function fakeCatalog(overrides = {}) {
  return catalog.normalizeCatalog({
    materials: [
      { id: 'STONE', en: 'Stone', zh: '石头', category: 'block', source: 'minecraft' },
      { id: 'BREAD', en: 'Bread', zh: '面包', category: 'food', source: 'minecraft' },
      { id: 'craftengine:default:bench', en: 'CraftEngine: default:bench', zh: '', category: 'item', source: 'craftengine' },
      { id: 'itemsadder:myitems:ruby', en: 'ItemsAdder: myitems:ruby', zh: '', category: 'item', source: 'itemsadder' }
    ],
    entities: [
      { id: 'ZOMBIE', en: 'Zombie', zh: '僵尸', source: 'minecraft' },
      { id: 'mythic:SkeletalKnight', en: 'MythicMobs: SkeletalKnight', zh: '', source: 'mythicmobs' }
    ],
    fish: [
      { id: 'my_custom_fish', en: '<yellow>大鱼', zh: '', category: 'fish', source: 'customfishing' }
    ],
    categories: ['block', 'item', 'food', 'fish'],
    sources: [
      { id: 'minecraft', label: '原版' },
      { id: 'mythicmobs', label: 'MythicMobs' },
      { id: 'itemsadder', label: 'ItemsAdder' },
      { id: 'craftengine', label: 'CraftEngine' },
      { id: 'customfishing', label: 'CustomFishing' }
    ],
    serverVersion: 'test',
    hasChinese: true,
    ...overrides
  })
}

try {
  // ------------------------------------------------------------ 来源标签
  check('来源标签只列当前范围里真的有内容的插件，顺序随后端', () => {
    const tabs = catalog.sourceTabs(fakeCatalog(), 'material')
    assert.deepEqual(tabs.map(tab => tab.value),
      ['ALL', 'minecraft', 'itemsadder', 'craftengine'],
      '实体专用的 MythicMobs、鱼专用的 CustomFishing 不该出现在材质范围里')
    assert.deepEqual(tabs.map(tab => tab.label),
      ['全部来源', '原版', 'ItemsAdder', 'CraftEngine'],
      '显示名来自后端，界面不自己编')
  })

  check('实体范围只列在册的来源；只有一个来源时整排不显示', () => {
    assert.deepEqual(catalog.sourceTabs(fakeCatalog(), 'entity').map(tab => tab.value),
      ['ALL', 'minecraft', 'mythicmobs'])
    // 全是原版时（没装任何插件）没必要占一排标签
    const vanillaOnly = catalog.normalizeCatalog({
      materials: [{ id: 'STONE', en: 'Stone', zh: '', category: 'block', source: 'minecraft' }],
      categories: ['block'],
      sources: [{ id: 'minecraft', label: '原版' }]
    })
    assert.deepEqual(catalog.sourceTabs(vanillaOnly, 'material'), [])
  })

  check('鱼的范围只看 CustomFishing：分类栏只给「全部 / 鱼」，条目自带来源标记', () => {
    const fishCatalog = fakeCatalog()
    assert.deepEqual(catalog.categoryTabs(fishCatalog, 'fish').map(tab => tab.value), ['ALL', 'fish'])
    const fishEntries = catalog.entriesForScope(fishCatalog, 'fish')
    assert.deepEqual(fishEntries.map(entry => entry.id), ['my_custom_fish'])
    assert.equal(catalog.sourceOf(fishEntries[0]), 'customfishing',
      '条目仍要带来源：列表右侧会标出「CustomFishing」，不用靠筛选标签猜')
    assert.deepEqual(catalog.sourceTabs(fishCatalog, 'fish'), [],
      '只有一家来源时不必占一排标签（鱼永远只有一个来源）')
  })

  check('材质范围里不出现鱼：填进方块目标只会永远命中不了', () => {
    const ids = catalog.entriesForScope(fakeCatalog(), 'material').map(entry => entry.id)
    assert.ok(!ids.includes('my_custom_fish'), `材质列表混进了鱼：${ids.join(',')}`)
    assert.ok(!catalog.categoryTabs(fakeCatalog(), 'material').some(tab => tab.value === 'fish'))
  })

  // ------------------------------------------------------------ 筛选组合
  check('按来源筛选：只剩该插件的条目', () => {
    const materials = catalog.entriesForScope(fakeCatalog(), 'material')
    assert.deepEqual(
      catalog.filterBySource(materials, 'craftengine').map(entry => entry.id),
      ['craftengine:default:bench'])
    assert.deepEqual(catalog.filterBySource(materials, 'ALL').length, materials.length)
  })

  check('来源与分类、关键词三者叠加', () => {
    const materials = catalog.entriesForScope(fakeCatalog(), 'material')
    // 只看原版的方块
    assert.deepEqual(
      catalog.visibleEntries(materials, '', 'block', 'minecraft').items.map(entry => entry.id),
      ['STONE'])
    // 切到 CraftEngine 后仍带着「物品」分类：只剩它自己那一条
    assert.deepEqual(
      catalog.visibleEntries(materials, '', 'item', 'craftengine').items.map(entry => entry.id),
      ['craftengine:default:bench'])
    // 关键词与来源同时生效
    assert.deepEqual(
      catalog.visibleEntries(materials, 'ruby', 'ALL', 'itemsadder').items.map(entry => entry.id),
      ['itemsadder:myitems:ruby'])
    assert.equal(catalog.visibleEntries(materials, 'ruby', 'ALL', 'minecraft').total, 0,
      '切了来源还要能按关键词筛空')
  })

  check('来源 id 认得出前缀：后端没标 source 时也能按插件筛', () => {
    const legacy = catalog.normalizeCatalog({
      materials: [
        { id: 'STONE', en: 'Stone', zh: '', category: 'block' },
        { id: 'craftengine:default:bench', en: 'bench', zh: '', category: 'item' }
      ],
      categories: ['block', 'item']
    })
    assert.deepEqual(legacy.sources.map(source => source.id), ['minecraft', 'craftengine'],
      '旧版后端没有 sources 字段，也要能推出这一排标签')
    assert.deepEqual(
      catalog.filterBySource(legacy.materials, 'craftengine').map(entry => entry.id),
      ['craftengine:default:bench'],
      '带前缀的 id 必须能被认出来源，否则旧后端上「按插件筛选」整个失效')
    assert.deepEqual(catalog.entriesForScope(legacy, 'fish'), [], '旧后端没有 fish 字段时不该抛异常')
  })

  check('范围 → 字段类型的映射包含 FISH', () => {
    assert.equal(catalog.scopeForFieldType('FISH'), 'fish')
    assert.equal(catalog.scopeForFieldType('MATERIAL'), 'material')
    assert.equal(catalog.scopeForFieldType('ENTITY'), 'entity')
    assert.equal(catalog.scopeForFieldType('TARGET'), 'both')
  })

  check('范围取并集时鱼也在里面（TARGET 字段不该漏掉任何一类）', () => {
    const ids = catalog.entriesForScope(fakeCatalog(), 'both').map(entry => entry.id)
    for (const id of ['STONE', 'ZOMBIE', 'mythic:SkeletalKnight', 'my_custom_fish']) {
      assert.ok(ids.includes(id), `并集里缺 ${id}`)
    }
  })

  process.stdout.write(`素材目录筛选自检通过（${checks} 项）\n`)
} finally {
  await cleanup()
}
