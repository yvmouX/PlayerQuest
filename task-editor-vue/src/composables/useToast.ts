/**
 * 全局 Toast 入口。
 *
 * <p>模块级单例：App.vue 挂载 Toast 组件时登记，任何视图/服务都能直接
 * {@code useToast().error(...)}，不必层层传递 ref。
 */
import { ref } from 'vue'

export type ToastType = 'info' | 'success' | 'error'

export interface ToastApi {
  show: (message: string, type?: ToastType, duration?: number) => void
}

const api = ref<ToastApi | null>(null)

/** 由 Toast 组件在挂载时调用。 */
export function registerToast(instance: ToastApi | null): void {
  api.value = instance
}

export function useToast() {
  return {
    info: (message: string): void => api.value?.show(message, 'info'),
    success: (message: string): void => api.value?.show(message, 'success'),
    error: (message: string): void => api.value?.show(message, 'error')
  }
}
