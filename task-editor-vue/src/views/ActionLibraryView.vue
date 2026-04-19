<template>
  <div class="page">
    <Header title="行为库">
      <template #actions>
        <button class="primary" @click="openCreate">+ 新建模板</button>
      </template>
    </Header>
    
    <div class="content">
      <div v-if="loading" class="loading">加载中...</div>
      <div v-else-if="templates.length === 0" class="empty">
        暂无行为模板，点击上方按钮创建
      </div>
      <div v-else class="grid">
        <ActionCard
          v-for="t in templates"
          :key="t.id"
          :template="t"
          @edit="openEdit(t)"
          @delete="confirmDelete(t)"
        />
      </div>
    </div>

    <ActionForm
      :show="showForm"
      :template="editingTemplate"
      @save="handleSave"
      @cancel="closeForm"
    />
    
    <ConfirmDialog
      :show="showDeleteConfirm"
      title="删除确认"
      message="确定要删除这个行为模板吗？"
      @confirm="handleDelete"
      @cancel="showDeleteConfirm = false"
    />
  </div>
</template>

<script setup lang="ts">
import {onMounted, ref} from 'vue'
import Header from '../components/layout/Header.vue'
import ActionCard from '../components/ActionCard.vue'
import ActionForm from '../components/ActionForm.vue'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import {ActionService} from '../services/api'
import type {ActionTemplate} from '../types'

const templates = ref<ActionTemplate[]>([])
const loading = ref(true)
const showForm = ref(false)
const editingTemplate = ref<ActionTemplate>()
const showDeleteConfirm = ref(false)
const deletingTemplate = ref<ActionTemplate>()

const loadTemplates = async () => {
  loading.value = true
  try {
    const res = await ActionService.getTemplates()
    templates.value = res.data || []
  } catch (e) {
    console.error('Failed to load action templates:', e)
    templates.value = []
  }
  loading.value = false
}

const openCreate = () => { editingTemplate.value = undefined; showForm.value = true }
const openEdit = (t: ActionTemplate) => { editingTemplate.value = t; showForm.value = true }
const closeForm = () => { showForm.value = false }

const handleSave = async (template: ActionTemplate) => {
  await ActionService.saveTemplate(template)
  closeForm()
  loadTemplates()
}

const confirmDelete = (t: ActionTemplate) => { deletingTemplate.value = t; showDeleteConfirm.value = true }
const handleDelete = async () => {
  if (deletingTemplate.value) {
    await ActionService.deleteTemplate(deletingTemplate.value.id)
    showDeleteConfirm.value = false
    loadTemplates()
  }
}

onMounted(loadTemplates)
</script>

<style scoped>
.page { display: flex; flex-direction: column; height: 100%; }
.content { flex: 1; padding: 1.5rem; overflow: auto; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 1rem; }
.loading, .empty { text-align: center; padding: 3rem; color: #666; }
.primary { background: #f97316; color: white; border: none; padding: 0.5rem 1rem; border-radius: 4px; cursor: pointer; }
</style>
