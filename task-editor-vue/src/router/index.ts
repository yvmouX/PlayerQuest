import { createRouter, createWebHistory } from 'vue-router'
import EditorView from '../views/EditorView.vue'
import RewardLibraryView from '../views/RewardLibraryView.vue'
import PlayerProgressView from '../views/PlayerProgressView.vue'
import StatisticsView from '../views/StatisticsView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/editor' },
    { path: '/editor', name: 'editor', component: EditorView },
    { path: '/rewards', name: 'rewards', component: RewardLibraryView },
    { path: '/players', name: 'players', component: PlayerProgressView },
    { path: '/stats', name: 'stats', component: StatisticsView }
  ]
})

export default router
