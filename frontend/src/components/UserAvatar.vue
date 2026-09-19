<template>
  <span
    class="user-avatar"
    :style="avatarStyle"
    role="img"
    :aria-label="name || $t('profile.avatar')"
  >
    <img v-if="imageUrl && !imageFailed" :src="imageUrl" alt="" @error="imageFailed = true" />
    <span v-else class="avatar-fallback">{{ initial }}</span>
  </span>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { resolveBackendAssetUrl } from '../utils/backend'

const props = defineProps({
  avatar: { type: String, default: '' },
  name: { type: String, default: '' },
  size: { type: Number, default: 36 }
})

const imageFailed = ref(false)
const imageUrl = computed(() => resolveBackendAssetUrl(props.avatar))
const initial = computed(() => {
  const value = String(props.name || '').trim()
  return value ? value.slice(0, 1).toUpperCase() : 'U'
})
const avatarStyle = computed(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`,
  fontSize: `${Math.max(12, Math.round(props.size * 0.36))}px`
}))

watch(() => props.avatar, () => {
  imageFailed.value = false
})
</script>

<style scoped>
.user-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  overflow: hidden;
  border-radius: 50%;
  color: var(--text-inverse);
  background: var(--color-primary-gradient);
  border: 2px solid var(--bg-card);
  box-shadow: var(--shadow-soft);
  line-height: 1;
  user-select: none;
}

.user-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center;
}

.avatar-fallback {
  font-weight: 700;
  text-transform: uppercase;
}
</style>
