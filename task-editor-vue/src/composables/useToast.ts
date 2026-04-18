import {ref} from 'vue'

const toast = ref<{ show: (msg: string, type?: 'info' | 'error' | 'success', duration?: number) => void } | null>(null)

export function useToast() {
  return {
    success: (msg: string) => toast.value?.show(msg, 'success'),
    error: (msg: string) => toast.value?.show(msg, 'error'),
    info: (msg: string) => toast.value?.show(msg, 'info')
  }
}

export function setToast(t: typeof toast) {
  toast.value = t.value
}
