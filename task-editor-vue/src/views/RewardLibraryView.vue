<template>
  <div class="page">
    <Header title="奖励库">
      <template #actions>
        <button class="primary" @click="openCreate">+ 新建模板</button>
      </template>
    </Header>
    
    <div class="content">
      <div v-if="loading" class="loading">加载中...</div>
      <div v-else-if="templates.length === 0" class="empty">
        暂无奖励模板，点击上方按钮创建
      </div>
      <div v-else class="grid">
        <RewardCard
          v-for="t in templates"
          :key="t.id"
          :template="t"
          @edit="openEdit(t)"
          @delete="confirmDelete(t)"
        />
      </div>
    </div>

    <RewardForm
      :show="showForm"
      :template="editingTemplate"
      @save="handleSave"
      @cancel="closeForm"
    />
    
    <ConfirmDialog
      :show="showDeleteConfirm"
      title="删除确认"
      message="确定要删除这个奖励模板吗？"
      @confirm="handleDelete"
      @cancel="showDeleteConfirm = false"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import Header from '../components/layout/Header.vue'
import RewardCard from '../components/RewardCard.vue'
import RewardForm from '../components/RewardForm.vue'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import { RewardService } from '../services/api'
import type { RewardTemplate } from '../types'

const templates = ref<RewardTemplate[]>([])
const loading = ref(true)
const showForm = ref(false)
const editingTemplate = ref<RewardTemplate>()
const showDeleteConfirm = ref(false)
const deletingTemplate = ref<RewardTemplate>()

const loadTemplates = async () => {
  loading.value = true
  const res = await RewardService.getTemplates()
  templates.value = res.data
  loading.value = false
}

const openCreate = () => { editingTemplate.value = undefined; showForm.value = true }
const openEdit = (t: RewardTemplate) => { editingTemplate.value = t; showForm.value = true }
const closeForm = () => { showForm.value = false }

const handleSave = async (template: RewardTemplate) => {
  await RewardService.saveTemplate(template)
  closeForm()
  loadTemplates()
}

const confirmDelete = (t: RewardTemplate) => { deletingTemplate.value = t; showDeleteConfirm.value = true }
const handleDelete = async () => {
  if (deletingTemplate.value) {
    await RewardService.deleteTemplate(deletingTemplate.value.id)
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
.primary { background: #198754; color: white; border: none; padding: 0.5rem 1rem; border-radius: 4px; cursor: pointer; }
</style>
