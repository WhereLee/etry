<template>
  <AppLayout v-if="!publicPage">
    <RouterView />
  </AppLayout>
  <RouterView v-else />
</template>

<script setup>
import { computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppLayout from './components/AppLayout.vue'
import { useAuthStore } from './stores/auth'
import { fetchMe } from './api/auth'

const route = useRoute()
const auth = useAuthStore()

const publicPage = computed(() => !!route.meta.public)

async function restoreUser() {
  if (auth.token && !auth.user) {
    try {
      auth.setUser(await fetchMe())
    } catch (e) {
      auth.clear()
    }
  }
}

onMounted(restoreUser)
watch(() => auth.token, restoreUser)
</script>
