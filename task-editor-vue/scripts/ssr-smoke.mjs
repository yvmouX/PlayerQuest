/**
 * 组件能否「装起来」的自检。
 *
 * <h2>为什么需要它</h2>
 * {@code vue-tsc} 只做类型检查，<b>不会执行</b> setup()。因此「setup 期读了还没初始化的变量」
 * 这类错误（例如 {@code immediate: true} 的 watch 在 composable 声明之前同步跑了一次，
 * 撞上 const 的暂时性死区）在构建里完全看不出来，只有真打开那个页面才会白屏。
 * 这里用 SSR 把两个编辑器渲染一遍：不碰浏览器 API，但会把 setup() 完整执行一次。
 */
import { build } from 'vite'
import vue from '@vitejs/plugin-vue'
import { rm } from 'node:fs/promises'
import path from 'node:path'
import { pathToFileURL } from 'node:url'

/** 要验证「装得起来」的视图；新增复杂视图时加进来即可。 */
const VIEWS = ['QuestEditorView', 'PresetView', 'QuestListView', 'OverviewView']

/**
 * 产物落在项目内而不是系统临时目录：ssr 构建把 vue / vue-router 留作外部依赖，
 * 放在临时目录里 Node 找不到它们（裸包名按文件位置解析）。
 */
const outDir = path.resolve('node_modules/.cache/ptx-ssr-smoke')
try {
  await build({
    configFile: false,
    logLevel: 'silent',
    plugins: [vue()],
    build: {
      outDir,
      emptyOutDir: true,
      minify: false,
      ssr: true,
      rollupOptions: {
        input: Object.fromEntries(
          VIEWS.map(name => [name, path.resolve(`src/views/${name}.vue`)])
        ),
        external: ['vue', 'vue-router', 'axios'],
        output: { entryFileNames: '[name].mjs' }
      }
    }
  })

  const { createSSRApp, h } = await import('vue')
  const { createRouter, createMemoryHistory } = await import('vue-router')
  const { renderToString } = await import('vue/server-renderer')

  // 路由名齐了视图里的 RouterLink 才解析得动；组件本身用空壳占位（这里不验证路由渲染）
  const stub = { template: '<div/>' }
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'overview', component: stub },
      { path: '/quests', name: 'quest-list', component: stub },
      { path: '/quests/new', name: 'quest-new', component: stub },
      { path: '/quests/:id/edit', name: 'quest-edit', component: stub, props: true },
      { path: '/presets', name: 'presets', component: stub },
      { path: '/players', name: 'players', component: stub },
      { path: '/players/:uuid', name: 'player-detail', component: stub, props: true },
      { path: '/langs', name: 'langs', component: stub }
    ]
  })

  for (const name of VIEWS) {
    const module = await import(pathToFileURL(path.join(outDir, `${name}.mjs`)).href)
    const app = createSSRApp({ render: () => h(module.default) })
    app.use(router)
    router.push('/')
    await router.isReady()
    const html = await renderToString(app)
    process.stdout.write(`  ✓ ${name} 可以渲染（${html.length} 字节）\n`)
  }
  process.stdout.write(`前端组件自检通过（${VIEWS.length} 个视图）\n`)
} finally {
  await rm(outDir, { recursive: true, force: true })
}
