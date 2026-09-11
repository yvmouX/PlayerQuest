<!-- 二次确认对话框，用于删除任务这类不可撤销操作。 -->
<template>
  <Teleport to="body">
    <div v-if="show" class="dialog-overlay" @click.self="emit('cancel')">
      <div class="dialog">
        <h3>{{ title }}</h3>
        <p>{{ message }}</p>
        <!-- 需要额外选项（例如导入时的「替换」开关）时由调用方塞进来 -->
        <div v-if="$slots.options" class="dialog-options">
          <slot name="options"></slot>
        </div>
        <div class="dialog-actions">
          <button class="btn" type="button" @click="emit('cancel')">取消</button>
          <button :class="['btn', danger ? 'btn-danger' : 'btn-primary']" type="button" @click="emit('confirm')">
            {{ confirmText }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    show: boolean
    title: string
    message: string
    confirmText?: string
    danger?: boolean
  }>(),
  {
    confirmText: '确认',
    danger: false
  }
)

const emit = defineEmits<{
  confirm: []
  cancel: []
}>()
</script>

<style scoped>
/* 遮罩、对话框外框、标题与底部按钮区是三个对话框共用的骨架，定义在 main.css；
   这里只留确认框自己的文案排版与附加选项区。 */
.dialog p {
  margin: 0 0 1.25rem;
  color: var(--text-dim);
  font-size: 0.9rem;
  line-height: 1.5;
  word-break: break-word;
  /* 文案里用 \n 断行，批量删除时会列出数量与 id 片段 */
  white-space: pre-line;
  max-height: 40vh;
  overflow: auto;
}

.dialog-options {
  margin: -0.6rem 0 1rem;
  padding: 0.6rem 0.7rem;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg-input);
}
</style>
