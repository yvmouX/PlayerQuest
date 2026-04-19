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
            <label>描述</label>
            <input v-model="form.description" />
          </div>
          <div class="form-group">
            <label>类型</label>
            <select v-model="form.type" required>
              <option value="kill_mob">击杀生物</option>
              <option value="collect_item">收集物品</option>
              <option value="break_block">破坏方块</option>
              <option value="talk_to_npc">与NPC对话</option>
              <option value="reach_location">到达位置</option>
              <option value="custom">自定义</option>
            </select>
          </div>
          <div class="form-group">
            <label>目标标识</label>
            <input v-model="form.target" placeholder="如: ZOMBIE, DIAMOND, STONE" />
          </div>
          <div class="form-group">
            <label>数量</label>
            <input v-model.number="form.amount" type="number" min="1" placeholder="1" />
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
import {reactive, watch} from 'vue'
import type {ObjectiveTemplate} from '../types'

const props = defineProps<{
  show: boolean
  template?: ObjectiveTemplate
}>()

const emit = defineEmits<{
  save: [template: ObjectiveTemplate]
  cancel: []
}>()

const form = reactive({
  name: '',
  description: '',
  type: 'kill_mob',
  target: '',
  amount: 1
})

watch(() => props.template, (t) => {
  form.name = t?.name || ''
  form.description = t?.description || ''
  form.type = t?.type || 'kill_mob'
  form.target = (t?.defaultConfig as any)?.target || ''
  form.amount = (t?.defaultConfig as any)?.amount || 1
}, { immediate: true })

const handleSubmit = () => {
  const template: ObjectiveTemplate = {
    name: form.name,
    description: form.description,
    type: form.type as ObjectiveTemplate['type'],
    defaultConfig: {
      target: form.target,
      amount: form.amount
    }
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
.actions button:last-child { background: #8b5cf6; color: white; border-color: #8b5cf6; }
</style>
