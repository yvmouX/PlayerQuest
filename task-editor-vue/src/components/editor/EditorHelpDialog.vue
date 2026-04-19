<template>
  <Teleport to="body">
    <div class="dialog-overlay" @click.self="$emit('close')">
      <div class="dialog">
        <h3>任务编辑器使用帮助</h3>
        
        <div v-if="loading" class="loading">加载中...</div>
        
        <div v-else-if="helpContent" class="help-content">
          <section class="quick-start">
            <h4>{{ helpContent.quickStart.title }}</h4>
            <ol class="steps-list">
              <li v-for="(step, index) in helpContent.quickStart.steps" :key="index">
                {{ step }}
              </li>
            </ol>
          </section>
          
          <section class="node-types">
            <h4>节点类型说明</h4>
            <div class="node-list">
              <div
                v-for="node in helpContent.nodeTypes"
                :key="node.type"
                class="node-item"
              >
                <span class="node-name">{{ node.name }}</span>
                <span class="node-desc">{{ node.desc }}</span>
              </div>
            </div>
          </section>
        </div>
        
        <div class="dialog-actions">
          <button @click="$emit('close')">关闭</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import {onMounted, ref} from 'vue'

defineEmits<{
  close: []
}>()

interface NodeTypeHelp {
  type: string
  name: string
  desc: string
}

interface HelpContent {
  quickStart: {
    title: string
    steps: string[]
  }
  nodeTypes: NodeTypeHelp[]
}

const helpContent = ref<HelpContent | null>(null)
const loading = ref(true)

onMounted(async () => {
  try {
    const response = await fetch('/help/editor-guide.json')
    if (response.ok) {
      helpContent.value = await response.json()
    }
  } catch (error) {
    console.error('Failed to load help content:', error)
  }
  loading.value = false
})
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
  width: 550px;
  max-height: 80vh;
  overflow-y: auto;
}
.dialog h3 {
  margin-top: 0;
}
.help-content {
  margin-top: 1rem;
}
.quick-start {
  margin-bottom: 1.5rem;
}
.quick-start h4 {
  margin: 0 0 0.75rem;
  color: #3b82f6;
}
.steps-list {
  margin: 0;
  padding-left: 1.5rem;
}
.steps-list li {
  margin-bottom: 0.5rem;
  line-height: 1.5;
}
.node-types h4 {
  margin: 0 0 0.75rem;
  color: #3b82f6;
}
.node-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}
.node-item {
  display: flex;
  gap: 0.75rem;
  padding: 0.5rem;
  background: #f9fafb;
  border-radius: 4px;
  font-size: 0.9rem;
}
.node-name {
  font-weight: 500;
  color: #374151;
  min-width: 80px;
}
.node-desc {
  color: #6b7280;
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
