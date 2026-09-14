/**
 * 路由表。
 *
 * <p>用 hash 模式是刻意的：后端只托管 {@code /} 与 {@code /assets/*}，
 * history 模式下直接访问 {@code /quests} 会 404。
 */
import { createRouter, createWebHashHistory } from 'vue-router'
import OverviewView from '../views/OverviewView.vue'
import PlayerProgressView from '../views/PlayerProgressView.vue'
import PresetView from '../views/PresetView.vue'
import QuestEditorView from '../views/QuestEditorView.vue'
import QuestListView from '../views/QuestListView.vue'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    // 首页 = 概览仪表盘
    { path: '/', name: 'overview', component: OverviewView },
    { path: '/quests', name: 'quest-list', component: QuestListView },
    // 新建：id 由用户填写，因此单独一条路由
    { path: '/quests/new', name: 'quest-new', component: QuestEditorView },
    { path: '/quests/:id/edit', name: 'quest-edit', component: QuestEditorView, props: true },
    { path: '/presets', name: 'presets', component: PresetView },
    { path: '/players', name: 'players', component: PlayerProgressView },
    { path: '/players/:uuid', name: 'player-detail', component: PlayerProgressView, props: true },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ]
})

export default router
