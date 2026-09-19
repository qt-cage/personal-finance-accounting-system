<template>
  <section class="metric-strip" :style="{ '--metric-count': items.length }">
    <article v-for="item in items" :key="item.key" class="metric-item" :class="`tone-${item.tone || 'neutral'}`">
      <span class="metric-label">{{ item.label }}</span>
      <strong class="metric-value">{{ item.value }}</strong>
      <div class="metric-support">
        <span v-if="item.caption" class="metric-caption">{{ item.caption }}</span>
        <div v-if="item.progress != null" class="metric-progress" aria-hidden="true">
          <span :style="{ width: `${Math.max(0, Math.min(item.progress, 100))}%` }"></span>
        </div>
      </div>
    </article>
  </section>
</template>

<script setup>
defineProps({
  items: { type: Array, required: true }
})
</script>

<style scoped>
.metric-strip {
  position: relative;
  display: grid;
  grid-template-columns: repeat(var(--metric-count), minmax(0, 1fr));
  margin-bottom: 22px;
  overflow: hidden;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  box-shadow: none;
}

.metric-strip::after {
  content: "";
  position: absolute;
  right: 20px;
  bottom: 17px;
  left: 20px;
  height: 1px;
  background: var(--border-lighter);
  pointer-events: none;
}

.metric-item {
  min-width: 0;
  min-height: 124px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  border-left: 1px solid var(--border-color);
}

.metric-item:first-child {
  border-left: 0;
}

.metric-label {
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.metric-value {
  margin-top: 12px;
  color: var(--metric-color, var(--text-primary));
  font-size: clamp(24px, 2.4vw, 32px);
  font-weight: 650;
  line-height: 1.08;
  letter-spacing: -0.025em;
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
}

.metric-support {
  min-height: 28px;
  margin-top: auto;
  padding-top: 13px;
}

.metric-caption {
  display: block;
  overflow: hidden;
  color: var(--text-secondary);
  font-size: 11px;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metric-progress {
  height: 3px;
  margin-top: 8px;
  overflow: hidden;
  background: var(--bg-menu-hover);
  border-radius: 999px;
}

.metric-progress span {
  display: block;
  height: 100%;
  background: var(--metric-color, var(--color-primary));
  border-radius: inherit;
}

.tone-primary { --metric-color: var(--color-primary); }
.tone-success { --metric-color: var(--color-success); }
.tone-danger { --metric-color: var(--color-danger); }
.tone-warning { --metric-color: var(--color-warning); }

@media (max-width: 1000px) {
  .metric-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .metric-item:nth-child(odd) {
    border-left: 0;
  }

  .metric-item:nth-child(n + 3) {
    border-top: 1px solid var(--border-color);
  }
}

@media (max-width: 620px) {
  .metric-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .metric-item {
    min-height: 112px;
    padding: 16px 12px;
  }

  .metric-item:first-child {
    border-top: 0;
  }
  .metric-value { font-size: 23px; }
  .metric-caption { white-space: normal; }
}
</style>
