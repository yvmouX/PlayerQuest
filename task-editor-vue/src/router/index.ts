import {createRouter, createWebHashHistory} from 'vue-router'
import EditorView from '../views/EditorView.vue'
import ObjectiveLibraryView from '../views/ObjectiveLibraryView.vue'
import ActionLibraryView from '../views/ActionLibraryView.vue'
import PlayerProgressView from '../views/PlayerProgressView.vue'
import StatisticsView from '../views/StatisticsView.vue'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', redirect: '/editor' },
    { path: '/editor', name: 'editor', component: EditorView },
    { path: '/objectives', name: 'objectives', component: ObjectiveLibraryView },
    { path: '/actions', name: 'actions', component: ActionLibraryView },
    { path: '/players', name: 'players', component: PlayerProgressView },
    { path: '/stats', name: 'stats', component: StatisticsView }
  ]
})

export default router
