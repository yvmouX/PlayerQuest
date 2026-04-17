<template>
  <div class="editor-view">
    <Header title="任务编辑器">
      <template #actions>
        <button @click="saveQuest">保存</button>
        <button @click="addQuest">新建任务</button>
      </template>
    </Header>
    <div class="canvas-container">
      <VueFlow :nodes="nodes" :edges="edges" @node-click="onNodeClick" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { VueFlow } from '@vue-flow/core'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import Header from '../components/layout/Header.vue'

const nodes = ref([])
const edges = ref([])

const saveQuest = () => { /* TODO */ }
const addQuest = () => { /* TODO */ }
const onNodeClick = (event) => { /* TODO */ }
</script>

<style scoped>
.editor-view {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.canvas-container {
  flex: 1;
}
</style>
