<template>
  <section
    class="app-state"
    :class="[`is-${type}`, { 'is-compact': compact }]"
    :role="type === 'error' ? 'alert' : 'status'"
    aria-live="polite"
    :aria-busy="type === 'loading'"
  >
    <template v-if="type === 'loading'">
      <span class="sr-only">{{ title }}</span>
      <el-skeleton animated :rows="rows" />
    </template>

    <template v-else>
      <span class="state-mark" aria-hidden="true">
        <el-icon><WarningFilled v-if="type === 'error'" /><Document v-else /></el-icon>
      </span>
      <h2 class="state-title">{{ title }}</h2>
      <p v-if="description" class="state-description">{{ description }}</p>
      <el-button v-if="actionLabel" type="primary" plain @click="$emit('retry')">
        {{ actionLabel }}
      </el-button>
    </template>
  </section>
</template>

<script setup>
import { Document, WarningFilled } from '@element-plus/icons-vue'

defineProps({
  type: {
    type: String,
    default: 'empty',
    validator: value => ['loading', 'empty', 'error'].includes(value)
  },
  title: { type: String, default: '' },
  description: { type: String, default: '' },
  actionLabel: { type: String, default: '' },
  rows: { type: Number, default: 5 },
  compact: { type: Boolean, default: false }
})

defineEmits(['retry'])
</script>

<style scoped>
.app-state {
  min-height: 280px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 40px clamp(20px, 5vw, 64px);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  background: var(--bg-card);
  box-shadow: var(--shadow-soft);
  color: var(--text-primary);
  text-align: center;
}

.app-state.is-loading {
  align-items: stretch;
  justify-content: flex-start;
  text-align: left;
}

.app-state.is-compact {
  min-height: 190px;
  padding-block: 28px;
  border: 0;
  box-shadow: none;
}

.state-mark {
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  border: 1px solid var(--border-color);
  border-radius: 50%;
  background: var(--color-primary-light);
  color: var(--color-primary);
  font-size: 19px;
}

.is-error .state-mark {
  background: var(--color-danger-light);
  color: var(--color-danger);
}

.state-title {
  margin: 2px 0 0;
  font-size: 17px;
  font-weight: 650;
  line-height: 1.4;
  text-wrap: balance;
}

.state-description {
  max-width: 460px;
  margin: 0 0 4px;
  color: var(--text-secondary);
  font-size: 14px;
  line-height: 1.7;
  text-wrap: pretty;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

@media (max-width: 720px) {
  .app-state {
    min-height: 220px;
    padding: 30px 18px;
  }
}
</style>
