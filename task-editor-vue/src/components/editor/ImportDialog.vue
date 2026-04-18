<template>
  <Teleport to="body">
    <div v-if="show" class="dialog-overlay" @click.self="$emit('close')">
      <div class="dialog">
        <h3>导入任务</h3>
        
        <div class="tabs">
          <button :class="{ active: tab === 'paste' }" @click="tab = 'paste'">粘贴JSON</button>
          <button :class="{ active: tab === 'upload' }" @click="tab = 'upload'">上传文件</button>
        </div>
        
        <div v-if="tab === 'paste'" class="tab-content">
          <textarea 
            v-model="jsonText" 
            placeholder='粘贴JSON格式的任务数据...'
            rows="10"
          />
        </div>
        
        <div v-if="tab === 'upload'" class="tab-content">
          <input type="file" @change="handleFileUpload" accept=".json" />
        </div>
        
        <div v-if="error" class="error">{{ error }}</div>
        
        <div class="dialog-actions">
          <button @click="$emit('close')">取消</button>
          <button @click="handleImport" class="btn-primary">导入</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { Quest } from '../../types'

const props = defineProps<{
  show: boolean
}>()

const emit = defineEmits<{
  close: []
  import: [quests: Quest[]]
}>()

const tab = ref<'paste' | 'upload'>('paste')
const jsonText = ref('')
const error = ref('')

function handleFileUpload(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (file) {
    const reader = new FileReader()
    reader.onload = (e) => {
      jsonText.value = e.target?.result as string || ''
    }
    reader.readAsText(file)
  }
}

function handleImport() {
  error.value = ''
  try {
    const data = JSON.parse(jsonText.value)
    let quests: Quest[]
    
    if (Array.isArray(data)) {
      quests = data
    } else if (data.nodes && Array.isArray(data.nodes)) {
      quests = data.nodes
    } else {
      throw new Error('Invalid format: expected array or object with nodes array')
    }
    
    if (quests.length === 0) {
      throw new Error('No quests found in the data')
    }
    
    emit('import', quests)
    emit('close')
    jsonText.value = ''
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Invalid JSON format'
  }
}
</script>

<style scoped>
.dialog-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}
.dialog {
  background: white;
  padding: 1.5rem;
  border-radius: 8px;
  width: 500px;
  max-width: 90vw;
}
.dialog h3 {
  margin-top: 0;
}
.tabs {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 1rem;
}
.tabs button {
  padding: 0.5rem 1rem;
  border: 1px solid #ddd;
  background: white;
  border-radius: 4px;
  cursor: pointer;
}
.tabs button.active {
  background: #3b82f6;
  color: white;
  border-color: #3b82f6;
}
.tab-content textarea {
  width: 100%;
  padding: 0.5rem;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  font-family: monospace;
  resize: vertical;
}
.tab-content input {
  width: 100%;
  padding: 0.5rem;
}
.error {
  color: #dc3545;
  margin-top: 0.5rem;
  font-size: 0.875rem;
}
.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
  margin-top: 1.5rem;
}
.dialog-actions button {
  padding: 0.5rem 1rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  cursor: pointer;
}
.btn-primary {
  background: #3b82f6;
  color: white;
  border-color: #3b82f6;
}
</style>
