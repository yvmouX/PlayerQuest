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
              <option value="give_item">发放物品</option>
              <option value="execute_command">执行命令</option>
              <option value="send_message">发送消息</option>
              <option value="play_effect">播放特效</option>
              <option value="sound">播放音效</option>
              <option value="give_xp">发放经验</option>
              <option value="custom">自定义</option>
            </select>
          </div>
          <template v-if="form.type === 'give_item'">
            <div class="form-group">
              <label>物品ID</label>
              <input v-model="form.item" placeholder="minecraft:diamond" />
            </div>
            <div class="form-group">
              <label>数量</label>
              <input v-model.number="form.amount" type="number" min="1" />
            </div>
          </template>
          <template v-else-if="form.type === 'execute_command'">
            <div class="form-group">
              <label>命令</label>
              <input v-model="form.command" placeholder="/say Hello %player%" />
            </div>
          </template>
          <template v-else-if="form.type === 'send_message'">
            <div class="form-group">
              <label>消息</label>
              <textarea v-model="form.message" rows="2"></textarea>
            </div>
          </template>
          <template v-else-if="form.type === 'give_xp'">
            <div class="form-group">
              <label>经验值</label>
              <input v-model.number="form.xp" type="number" min="1" />
            </div>
          </template>
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
import type {ActionTemplate} from '../types'

const props = defineProps<{
  show: boolean
  template?: ActionTemplate
}>()

const emit = defineEmits<{
  save: [template: ActionTemplate]
  cancel: []
}>()

const form = reactive({
  name: '',
  description: '',
  type: 'give_item',
  item: '',
  amount: 1,
  command: '',
  message: '',
  xp: 0
})

watch(() => props.template, (t) => {
  form.name = t?.name || ''
  form.description = t?.description || ''
  form.type = t?.type || 'give_item'
  const cfg = t?.defaultConfig || {}
  form.item = (cfg as any).item || ''
  form.amount = (cfg as any).amount || 1
  form.command = (cfg as any).command || ''
  form.message = (cfg as any).message || ''
  form.xp = (cfg as any).xp || 0
}, { immediate: true })

const handleSubmit = () => {
  const defaultConfig: Record<string, any> = {}
  if (form.type === 'give_item') {
    defaultConfig.item = form.item
    defaultConfig.amount = form.amount
  } else if (form.type === 'execute_command') {
    defaultConfig.command = form.command
  } else if (form.type === 'send_message') {
    defaultConfig.message = form.message
  } else if (form.type === 'give_xp') {
    defaultConfig.xp = form.xp
  }

  const template: ActionTemplate = {
    name: form.name,
    description: form.description,
    type: form.type as ActionTemplate['type'],
    defaultConfig
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
.form-group input, .form-group select, .form-group textarea {
  width: 100%; padding: 0.5rem;
  border: 1px solid #ddd; border-radius: 4px;
  box-sizing: border-box;
}
.actions { display: flex; gap: 0.5rem; justify-content: flex-end; margin-top: 1.5rem; }
.actions button {
  padding: 0.5rem 1rem; border: 1px solid #ddd; border-radius: 4px; cursor: pointer;
}
.actions button:last-child { background: #f97316; color: white; border-color: #f97316; }
</style>
