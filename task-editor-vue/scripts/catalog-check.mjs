/**
 * 素材目录「值域 / 来源」筛选的自检。
 *
 * <h2>为什么需要它</h2>
 * 这一层的错法全都是静默的：值域写错导致选择器列出苹果供「挖掘方块」选、切换值域后
 * 残留上一轮的来源标签让列表空掉、鱼 id 混进方块列表、旧版后端缺 {@code kinds} 时整片空白——
 * 界面都不会报错，只是让人找不到东西、或者选到一个永远不会命中的值。
 * 这里对**真实代码**（{@code src/utils/catalog.ts}，用 vite 现打成临时 ESM 再 import）
 * 跑断言，并挂进 {@code npm run build}：任何一处退化都会让构建失败。
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

/** 一份像真目录那样混着原版与三家插件内容、且每条都带值域的响应。 */
function fakeCatalog(overrides = {}) {
  return catalog.normalizeCatalog({
    materials: [
      { id: 'STONE', en: 'Stone', zh: '石头', category: 'block', source: 'minecraft', kinds: ['block', 'placeable', 'item'] },
      { id: 'SPAWNER', en: 'Monster Spawner', zh: '刷怪笼', category: 'block', source: 'minecraft', kinds: ['block'] },
      { id: 'APPLE', en: 'Apple', zh: '苹果', category: 'food', source: 'minecraft', kinds: ['item', 'food'] },
      { id: 'BREAD', en: 'Bread', zh: '面包', category: 'food', source: 'minecraft', kinds: ['item', 'food'] },
      { id: 'craftengine:default:bench', en: 'CraftEngine: default:bench', zh: '', category: 'item', source: 'craftengine', kinds: ['item'] },
      { id: 'craftengine:default:torch', en: 'CraftEngine: default:torch', zh: '', category: 'block', source: 'craftengine', kinds: ['block', 'placeable'] },
      { id: 'itemsadder:myitems:ruby', en: 'ItemsAdder: myitems:ruby', zh: '', category: 'item', source: 'itemsadder', kinds: ['item'] }
    ],
    entities: [
      { id: 'ZOMBIE', en: 'Zombie', zh: '僵尸', category: 'entity', source: 'minecraft', kinds: ['entity', 'living'] },
      { id: 'SHEEP', en: 'Sheep', zh: '羊', category: 'entity', source: 'minecraft', kinds: ['entity', 'living', 'breedable', 'shearable'] },
      { id: 'PIG', en: 'Pig', zh: '猪', category: 'entity', source: 'minecraft', kinds: ['entity', 'living', 'breedable'] },
      { id: 'WOLF', en: 'Wolf', zh: '狼', category: 'entity', source: 'minecraft', kinds: ['entity', 'living', 'tameable'] },
      { id: 'mythic:SkeletalKnight', en: 'MythicMobs: SkeletalKnight', zh: '', category: 'entity', source: 'mythicmobs', kinds: ['entity', 'living'] }
    ],
    fish: [
      { id: 'my_custom_fish', en: '<yellow>大鱼', zh: '', category: 'fish', source: 'customfishing', kinds: ['fish'] }
    ],
    enchantments: [
      { id: 'SHARPNESS', en: 'Sharpness', zh: '锋利', category: 'enchantment', source: 'minecraft', kinds: ['enchantment'] }
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
  // ------------------------------------------------------------ 值域过滤
  check('挖掘方块只列方块：苹果选不到，刷怪笼选得到', () => {
    const blocks = catalog.entriesForKinds(fakeCatalog(), ['block']).map(entry => entry.id)
    assert.ok(blocks.includes('STONE'))
    assert.ok(blocks.includes('SPAWNER'), '「能挖但没有物品形态」的方块必须能选到（这正是过去漏掉的）')
    assert.ok(!blocks.includes('APPLE'), `方块值域里混进了苹果：${blocks.join(',')}`)
    assert.ok(!blocks.includes('ZOMBIE'), '实体不该出现在方块字段里')
  })

  check('放置方块只列「可放置」：刷怪笼与物品都不行', () => {
    const placeable = catalog.entriesForKinds(fakeCatalog(), ['placeable']).map(entry => entry.id)
    assert.deepEqual(placeable.sort(), ['STONE', 'craftengine:default:torch'])
  })

  check('消耗/合成只列物品：方块如果能吃也照样算物品', () => {
    const items = catalog.entriesForKinds(fakeCatalog(), ['item']).map(entry => entry.id)
    assert.ok(items.includes('APPLE') && items.includes('BREAD'))
    assert.ok(items.includes('STONE'), '石块也是物品（能拿在手里）')
    assert.ok(!items.includes('SPAWNER'), '刷怪笼没有物品形态')
  })

  check('剪切只列能剪毛的：猪与僵尸都不行', () => {
    const shearable = catalog.entriesForKinds(fakeCatalog(), ['shearable']).map(entry => entry.id)
    assert.deepEqual(shearable, ['SHEEP'])
  })

  check('繁殖只列能繁殖的动物；驯服只列能驯服的', () => {
    assert.deepEqual(catalog.entriesForKinds(fakeCatalog(), ['breedable']).map(e => e.id).sort(),
      ['PIG', 'SHEEP'])
    assert.deepEqual(catalog.entriesForKinds(fakeCatalog(), ['tameable']).map(e => e.id), ['WOLF'])
  })

  check('击杀列活体（含 MythicMobs 的怪）', () => {
    const living = catalog.entriesForKinds(fakeCatalog(), ['living']).map(entry => entry.id)
    assert.ok(living.includes('ZOMBIE') && living.includes('mythic:SkeletalKnight'))
  })

  check('值域是「或」：交互字段同时列方块与实体', () => {
    const ids = catalog.entriesForKinds(fakeCatalog(), ['block', 'entity']).map(entry => entry.id)
    for (const id of ['STONE', 'craftengine:default:torch', 'ZOMBIE', 'mythic:SkeletalKnight']) {
      assert.ok(ids.includes(id), `并集里缺 ${id}`)
    }
    assert.ok(!ids.includes('APPLE'), '苹果既不是方块也不是实体：右键交互永远点不到它')
    assert.ok(!ids.includes('my_custom_fish'), '鱼只在 FISH 值域里出现')
  })

  check('附魔只列附魔；鱼只列 CustomFishing 的鱼', () => {
    assert.deepEqual(catalog.entriesForKinds(fakeCatalog(), ['enchantment']).map(e => e.id), ['SHARPNESS'])
    assert.deepEqual(catalog.entriesForKinds(fakeCatalog(), ['fish']).map(e => e.id), ['my_custom_fish'])
  })

  // ------------------------------------------------------------ 标签
  check('分类栏只出现当前值域里真有的分类', () => {
    assert.deepEqual(catalog.categoryTabs(fakeCatalog(), ['block']).map(tab => tab.value),
      ['ALL', 'block'])
    assert.deepEqual(catalog.categoryTabs(fakeCatalog(), ['fish']).map(tab => tab.value), ['ALL', 'fish'])
    assert.deepEqual(catalog.categoryTabs(fakeCatalog(), ['enchantment']).map(tab => tab.value),
      ['ALL', 'enchantment'])
    assert.deepEqual(catalog.categoryTabs(fakeCatalog(), ['shearable']).map(tab => tab.value),
      ['ALL', 'entity'])
  })

  check('来源标签只列当前值域里真的有条目的插件，顺序随后端', () => {
    const tabs = catalog.sourceTabs(fakeCatalog(), ['block'])
    assert.deepEqual(tabs.map(tab => tab.value), ['ALL', 'minecraft', 'craftengine'],
      '只装了原版与 CraftEngine 有方块：ItemsAdder / MythicMobs 不该出现')
    assert.deepEqual(tabs.map(tab => tab.label), ['全部来源', '原版', 'CraftEngine'],
      '显示名来自后端，界面不自己编')
  })

  check('只有一个来源时整排标签不显示', () => {
    const vanillaOnly = catalog.normalizeCatalog({
      materials: [{ id: 'STONE', en: 'Stone', zh: '', category: 'block', kinds: ['block'] }],
      categories: ['block'],
      sources: [{ id: 'minecraft', label: '原版' }]
    })
    assert.deepEqual(catalog.sourceTabs(vanillaOnly, ['block']), [])
  })

  // ------------------------------------------------------------ 旧版后端
  check('旧后端没有 kinds：按所在列表粗判，选择器不至于空掉', () => {
    const legacy = catalog.normalizeCatalog({
      materials: [
        { id: 'STONE', en: 'Stone', zh: '', category: 'block' },
        { id: 'APPLE', en: 'Apple', zh: '', category: 'food' },
        { id: 'craftengine:default:bench', en: 'bench', zh: '', category: 'item' }
      ],
      entities: [{ id: 'ZOMBIE', en: 'Zombie', zh: '' }],
      categories: ['block', 'item', 'food']
    })
    assert.deepEqual(legacy.sources.map(source => source.id), ['minecraft', 'craftengine'],
      '旧后端没有 sources 字段，也要能推出这一排标签')
    assert.ok(catalog.entriesForKinds(legacy, ['block']).some(entry => entry.id === 'STONE'))
    assert.ok(catalog.entriesForKinds(legacy, ['entity']).some(entry => entry.id === 'ZOMBIE'))
    assert.deepEqual(catalog.entriesForKinds(legacy, ['fish']), [], '旧后端没有 fish 字段时不该抛异常')
  })

  // ------------------------------------------------------------ 与分类、搜索叠加
  check('值域 + 分类 + 来源 + 关键词四者叠加', () => {
    const f = fakeCatalog()
    const pool = catalog.entriesForKinds(f, ['block'])
    assert.deepEqual(
      catalog.visibleEntries(pool, '', 'block', 'craftengine').items.map(entry => entry.id),
      ['craftengine:default:torch'])
    assert.deepEqual(
      catalog.visibleEntries(pool, 'stone', 'ALL', 'ALL').items.map(entry => entry.id),
      ['STONE'])
    assert.equal(catalog.visibleEntries(pool, 'apple', 'ALL', 'ALL').total, 0,
      '方块值域里搜苹果就该是空的')
  })

  process.stdout.write(`素材目录筛选自检通过（${checks} 项）\n`)
} finally {
  await cleanup()
}
