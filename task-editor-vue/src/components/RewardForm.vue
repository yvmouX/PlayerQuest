<template>
  <Teleport to="body">
    <div v-if="show" class="modal-overlay" @click.self="$emit('cancel')">
      <div class="modal">
        <h3>{{ template ? '编辑模板' : '新建模板' }}</h3>
        <form @submit.prevent="handleSubmit">
          <div class="form-group">
            <label>名称</label>
            <input v-model="form.name" required />
          </div>
          <div class="form-group">
            <label>类型</label>
            <select v-model="form.type" required>
              <option value="item">物品</option>
              <option value="xp">经验</option>
              <option value="money">金币</option>
              <option value="command">命令</option>
            </select>
          </div>
          <div class="form-group">
            <label>值</label>
            <input v-model="form.value" required />
          </div>
          <div class="actions">
            <button type="button" @click="$emit('cancel')">取消</button>
            <button type="submit">保存</button>
          </div>
        </form>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue'
import type { RewardTemplate } from '../types'

const props = defineProps<{
  show: boolean
  template?: RewardTemplate
}>()

const emit = defineEmits<{
  save: [template: RewardTemplate]
  cancel: []
}>()

const form = reactive({
  name: '',
  type: 'item',
  value: ''
})

watch(() => props.template, (t) => {
  form.name = t?.name || ''
  form.type = t?.type || 'item'
  form.value = t?.value || ''
}, { immediate: true })

const handleSubmit = () => {
  const template: RewardTemplate = {
    name: form.name,
    type: form.type as RewardTemplate['type'],
    value: form.value
  }
  if (props.template?.id) {
    template.id = props.template.id
  }
  emit('save', template)
}
</script>

<style scoped>
.modal-overlay {
  position: fixed; inset: 0;
  background: rgba(0,0,0,0.5);
  display: flex; align-items: center; justify-content: center;
  z-index: 1000;
}
.modal {
  background: white; border-radius: 8px;
  padding: 1.5rem; width: 400px;
}
.modal h3 { margin: 0 0 1rem; }
.form-group { margin-bottom: 1rem; }
.form-group label { display: block; margin-bottom: 0.25rem; font-weight: 500; }
.form-group input, .form-group select {
  width: 100%; padding: 0.5rem;
  border: 1px solid #ddd; border-radius: 4px;
  box-sizing: border-box;
}
.actions { display: flex; gap: 0.5rem; justify-content: flex-end; margin-top: 1.5rem; }
.actions button {
  padding: 0.5rem 1rem; border: 1px solid #ddd; border-radius: 4px; cursor: pointer;
}
.actions button:last-child { background: #198754; color: white; border-color: #198754; }
</style>
