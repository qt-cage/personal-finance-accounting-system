<template>
  <header class="page-header-shell">
    <div class="page-header-copy">
      <span v-if="stamp" class="page-stamp">{{ stamp }}</span>
      <h1>{{ title }}</h1>
      <p v-if="description">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="page-header-actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  description: { type: String, default: '' },
  stamp: { type: String, default: '' }
})
</script>

<style scoped>
.page-header-shell {
  min-height: 74px;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 24px;
}

.page-header-copy {
  min-width: 0;
}

.page-stamp {
  display: inline-block;
  margin-bottom: 8px;
  color: var(--text-secondary);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.13em;
  font-variant-numeric: tabular-nums;
}

h1 {
  margin: 0;
  color: var(--text-primary);
  font-family: "Songti SC", STSong, "Noto Serif CJK SC", serif;
  font-size: clamp(28px, 3vw, 34px);
  font-weight: 600;
  line-height: 1.16;
  letter-spacing: 0.025em;
  text-wrap: balance;
}

p {
  max-width: 680px;
  margin: 9px 0 0;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.65;
  text-wrap: pretty;
}

.page-header-actions {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 10px;
}

@media (max-width: 720px) {
  .page-header-shell {
    min-height: 0;
    align-items: flex-start;
    flex-direction: column;
    gap: 16px;
  }

  .page-header-actions {
    width: 100%;
    flex-wrap: wrap;
  }
}
</style>

