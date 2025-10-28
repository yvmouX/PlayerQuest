<script setup lang="ts">
import {ref} from "vue";

const isMenuOpen = ref(false)

defineProps<{
  logo?: string,
  item1?: string,
  item2?: string,
  item3?: string
}>()
</script>

<template>
  <header class="NAME_NAV bg-white shadow-md fixed top-0 left-0 right-0 z-50">
    <div class="flex justify-between items-center h-16 px-20">
      <!-- Logo/网站名称 -->
      <a href="#" class="flex items-center">
        <span class="text-2xl font-bold text-blue-600">{{logo}}</span>
      </a>

      <!-- 导航链接 -->
      <nav class="max-md:hidden flex">
        <a href="#" class="text-gray-700 hover:text-blue-600 font-medium transition-colors duration-200">{{item1}}</a>
        <a href="#" class="text-gray-700 hover:text-blue-600 font-medium transition-colors duration-200">{{item2}}</a>
        <a href="#" class="text-gray-700 hover:text-blue-600 font-medium transition-colors duration-200">{{item3}}</a>
      </nav>
      <button
          class="md:hidden flex items-center justify-end cursor-pointer"
          @click="isMenuOpen = !isMenuOpen"
      >
        ☰
      </button>
    </div>

    <!-- 桌面端菜单适配 -->
    <div
        v-if="isMenuOpen"
        class="md:hidden bg-white absolute shadow-md top-full right-1/20 rounded-xl"
    >
      <nav class="flex flex-col p-5 items-end">
        <a href="#" class="text-gray-700 hover:text-blue-600 font-medium transition-colors duration-200">首页</a>
        <a href="#" class="text-gray-700 hover:text-blue-600 font-medium transition-colors duration-200">关于</a>
        <a href="#" class="text-gray-700 hover:text-blue-600 font-medium transition-colors duration-200">联系</a>
      </nav>
    </div>
  </header>
</template>

<style scoped>

</style>