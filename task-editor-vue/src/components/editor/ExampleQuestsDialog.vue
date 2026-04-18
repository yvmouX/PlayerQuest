<template>
  <Teleport to="body">
    <div class="dialog-overlay" @click.self="$emit('close')">
      <div class="dialog">
        <h3>加载示例任务</h3>
        <p class="subtitle">选择一个示例任务加载到编辑器</p>
        
        <div class="example-list" v-if="!loading">
          <div
            v-for="example in examples"
            :key="example.file"
            class="example-card"
          >
            <div class="example-info">
              <h4>{{ example.data.name || '未命名' }}</h4>
              <p>{{ example.data.description || '' }}</p>
            </div>
            <button @click="handleLoad(example.data)" class="btn-load">加载</button>
          </div>
        </div>
        
        <div v-if="loading" class="loading">加载中...</div>
        
        <div class="dialog-actions">
          <button @click="$emit('close')">关闭</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import {ref, onMounted} from 'vue'
import type {Quest} from '../../types'

const emit = defineEmits<{
  close: []
  load: [quests: Quest[]]
}>()

const examples = ref<{file: string; data: Quest}[]>([])
const loading = ref(true)

onMounted(async () => {
  const exampleFiles = [
    'simple-quest.json',
    'daily-quest.json',
    'branching-quest.json',
    'event-quest.json'
  ]
  
  for (const file of exampleFiles) {
    try {
      const response = await fetch(`/examples/${file}`)
      if (response.ok) {
        const data = await response.json()
        examples.value.push({ file, data })
      }
    } catch (error) {
      console.error(`Failed to load example ${file}:`, error)
    }
  }
  loading.value = false
})

function handleLoad(quest: Quest) {
  emit('load', [quest])
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
  max-height: 80vh;
  overflow-y: auto;
}
.dialog h3 {
  margin-top: 0;
}
.subtitle {
  color: #666;
  margin-bottom: 1rem;
}
.example-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}
.example-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1rem;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
}
.example-info h4 {
  margin: 0 0 0.25rem;
  font-size: 1rem;
}
.example-info p {
  margin: 0;
  font-size: 0.875rem;
  color: #6b7280;
}
.btn-load {
  padding: 0.5rem 1rem;
  background: #3b82f6;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}
.btn-load:hover {
  background: #2563eb;
}
.loading {
  text-align: center;
  padding: 2rem;
  color: #666;
}
.dialog-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 1.5rem;
}
.dialog-actions button {
  padding: 0.5rem 1rem;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  cursor: pointer;
}
</style>
