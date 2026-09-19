<template>
  <div
    ref="rootRef"
    class="interactive-ledger-illustration"
    :class="{ 'is-dragging': dragging }"
    :style="{ '--carousel-drag-offset': `${dragOffset}px` }"
    role="region"
    tabindex="0"
    :aria-label="$t('auth.illustrationCarouselHint')"
    @mouseenter="pauseForPointer"
    @mouseleave="resumeAfterPointer"
    @focusin="pauseForFocus"
    @focusout="resumeAfterFocus"
    @pointerdown="handlePointerDown"
    @pointermove="handlePointerMove"
    @pointerup="handlePointerUp"
    @pointercancel="cancelPointerGesture"
    @keydown.left.prevent="showPreviousSlide"
    @keydown.right.prevent="showNextSlide"
    @keydown.enter.prevent="showNextSlide"
    @keydown.space.prevent="showNextSlide"
  >
    <div class="illustration-stage">
      <img
        v-for="(illustration, index) in illustrations"
        :key="illustration.key"
        :src="illustration.src"
        alt=""
        width="1254"
        height="1254"
        loading="eager"
        :fetchpriority="index === 0 ? 'high' : 'auto'"
        decoding="async"
        draggable="false"
        class="illustration-art"
        :class="{ 'is-active': index === currentSlide }"
        :aria-hidden="index === currentSlide ? 'false' : 'true'"
      />

      <button
        v-if="currentSlide === 0"
        type="button"
        class="book-hotspot"
        :class="{ 'is-lifting': lifting }"
        :aria-label="$t('auth.turnIllustrationPage')"
        :title="$t('auth.turnIllustrationPage')"
        @click="triggerManualLift"
      >
        <span class="page-lift-overlay" aria-hidden="true"></span>
        <span class="spine-shadow" aria-hidden="true"></span>
      </button>
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import interiorUrl from '../assets/login-illustration-interior-v1.webp'
import stillLifeUrl from '../assets/login-illustration-collage-v1.webp'
import sealedLedgerUrl from '../assets/login-illustration-sealed-ledger-v1.webp'
import gouacheLedgerUrl from '../assets/login-illustration-gouache-ledger-v1.webp'
import printCollageUrl from '../assets/login-illustration-print-collage-v1.webp'

const FIRST_LIFT_DELAY = 10000
const NEXT_LIFT_DELAY = 20000
const LIFT_DURATION = 520
const FIRST_SLIDE_DELAY = 14000
const NEXT_SLIDE_DELAY = 9000
const DRAG_THRESHOLD = 52
const TAP_SLOP = 8
const MAX_DRAG_OFFSET = 36

const illustrations = [
  { key: 'interior', src: interiorUrl },
  { key: 'paper-still-life', src: stillLifeUrl },
  { key: 'sealed-ledger', src: sealedLedgerUrl },
  { key: 'gouache-ledger', src: gouacheLedgerUrl },
  { key: 'print-collage', src: printCollageUrl }
]

const rootRef = ref()
const lifting = ref(false)
const inView = ref(true)
const pointerPaused = ref(false)
const focusPaused = ref(false)
const currentSlide = ref(0)
const dragging = ref(false)
const dragOffset = ref(0)

let liftAutoTimer
let liftTimer
let carouselTimer
let observer
let motionQuery
let initialLiftPending = true
let activePointerId
let pointerStartX = 0
let pointerStartY = 0

function clearLiftAutoTimer() {
  window.clearTimeout(liftAutoTimer)
  liftAutoTimer = undefined
}

function clearCarouselTimer() {
  window.clearTimeout(carouselTimer)
  carouselTimer = undefined
}

function clearMotionTimers() {
  clearLiftAutoTimer()
  clearCarouselTimer()
}

function prefersReducedMotion() {
  return motionQuery?.matches ?? false
}

function formHasFocus() {
  const form = document.querySelector('.login-form')
  return Boolean(form && form.contains(document.activeElement))
}

function shouldPauseMotion() {
  return document.hidden || !inView.value || pointerPaused.value || focusPaused.value || formHasFocus() || prefersReducedMotion()
}

function runLift() {
  if (lifting.value || currentSlide.value !== 0) return false
  lifting.value = true
  window.clearTimeout(liftTimer)
  liftTimer = window.setTimeout(() => {
    lifting.value = false
  }, LIFT_DURATION)
  return true
}

function scheduleAutoLift(delay = NEXT_LIFT_DELAY) {
  clearLiftAutoTimer()
  if (prefersReducedMotion()) return
  liftAutoTimer = window.setTimeout(() => {
    liftAutoTimer = undefined
    if (shouldPauseMotion() || currentSlide.value !== 0) {
      scheduleAutoLift(3000)
      return
    }
    runLift()
    initialLiftPending = false
    scheduleAutoLift(NEXT_LIFT_DELAY)
  }, delay)
}

function triggerManualLift() {
  clearLiftAutoTimer()
  initialLiftPending = false
  runLift()
  scheduleAutoLift(NEXT_LIFT_DELAY)
}

function scheduleCarousel(delay = NEXT_SLIDE_DELAY) {
  clearCarouselTimer()
  if (prefersReducedMotion()) return
  carouselTimer = window.setTimeout(() => {
    carouselTimer = undefined
    if (shouldPauseMotion()) {
      scheduleCarousel(3000)
      return
    }
    currentSlide.value = (currentSlide.value + 1) % illustrations.length
    lifting.value = false
    window.clearTimeout(liftTimer)
    scheduleCarousel(NEXT_SLIDE_DELAY)
  }, delay)
}

function selectSlide(index) {
  currentSlide.value = (index + illustrations.length) % illustrations.length
  lifting.value = false
  window.clearTimeout(liftTimer)
  scheduleCarousel(NEXT_SLIDE_DELAY)
  if (currentSlide.value === 0) scheduleAutoLift(initialLiftPending ? FIRST_LIFT_DELAY : NEXT_LIFT_DELAY)
}

function showNextSlide() {
  selectSlide(currentSlide.value + 1)
}

function showPreviousSlide() {
  selectSlide(currentSlide.value - 1)
}

function isInteractiveTarget(target) {
  return target instanceof Element && Boolean(target.closest('button, a, input, select, textarea'))
}

function handlePointerDown(event) {
  if (!event.isPrimary || event.button !== 0 || isInteractiveTarget(event.target)) return
  activePointerId = event.pointerId
  pointerStartX = event.clientX
  pointerStartY = event.clientY
  dragging.value = false
  dragOffset.value = 0
  clearMotionTimers()
  event.currentTarget.setPointerCapture?.(event.pointerId)
}

function handlePointerMove(event) {
  if (event.pointerId !== activePointerId) return
  const deltaX = event.clientX - pointerStartX
  const deltaY = event.clientY - pointerStartY
  if (Math.abs(deltaX) <= TAP_SLOP || Math.abs(deltaX) <= Math.abs(deltaY)) return
  dragging.value = true
  dragOffset.value = Math.max(-MAX_DRAG_OFFSET, Math.min(MAX_DRAG_OFFSET, deltaX * 0.18))
  event.preventDefault()
}

function resetPointerGesture(event) {
  if (event?.currentTarget?.hasPointerCapture?.(event.pointerId)) {
    event.currentTarget.releasePointerCapture(event.pointerId)
  }
  activePointerId = undefined
  dragging.value = false
  dragOffset.value = 0
}

function handlePointerUp(event) {
  if (event.pointerId !== activePointerId) return
  const deltaX = event.clientX - pointerStartX
  const deltaY = event.clientY - pointerStartY
  const horizontalGesture = Math.abs(deltaX) > Math.abs(deltaY)
  const isTap = Math.hypot(deltaX, deltaY) <= TAP_SLOP
  resetPointerGesture(event)

  if (horizontalGesture && Math.abs(deltaX) >= DRAG_THRESHOLD) {
    if (deltaX < 0) showNextSlide()
    else showPreviousSlide()
  } else if (isTap) {
    showNextSlide()
  } else {
    scheduleCarousel(NEXT_SLIDE_DELAY)
    scheduleAutoLift(NEXT_LIFT_DELAY)
  }

  if (document.activeElement === rootRef.value) rootRef.value.blur()
}

function cancelPointerGesture(event) {
  if (event.pointerId !== activePointerId) return
  resetPointerGesture(event)
  scheduleCarousel(NEXT_SLIDE_DELAY)
  scheduleAutoLift(NEXT_LIFT_DELAY)
}

function pauseForPointer() {
  pointerPaused.value = true
  clearMotionTimers()
}

function resumeAfterPointer() {
  pointerPaused.value = false
  scheduleCarousel(NEXT_SLIDE_DELAY)
  scheduleAutoLift(NEXT_LIFT_DELAY)
}

function pauseForFocus() {
  focusPaused.value = true
  clearMotionTimers()
}

function resumeAfterFocus() {
  window.requestAnimationFrame(() => {
    if (rootRef.value?.contains(document.activeElement)) return
    focusPaused.value = false
    scheduleCarousel(NEXT_SLIDE_DELAY)
    scheduleAutoLift(NEXT_LIFT_DELAY)
  })
}

function handleVisibilityChange() {
  if (document.hidden) {
    clearMotionTimers()
    return
  }
  scheduleCarousel(NEXT_SLIDE_DELAY)
  scheduleAutoLift(NEXT_LIFT_DELAY)
}

onMounted(() => {
  motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
  observer = new IntersectionObserver(([entry]) => {
    inView.value = entry?.isIntersecting ?? true
    if (inView.value) {
      if (!carouselTimer) scheduleCarousel(NEXT_SLIDE_DELAY)
      scheduleAutoLift(initialLiftPending ? FIRST_LIFT_DELAY : NEXT_LIFT_DELAY)
    } else {
      clearMotionTimers()
    }
  }, { threshold: 0.35 })
  if (rootRef.value) observer.observe(rootRef.value)
  document.addEventListener('visibilitychange', handleVisibilityChange)
  scheduleAutoLift(FIRST_LIFT_DELAY)
  scheduleCarousel(FIRST_SLIDE_DELAY)
})

onBeforeUnmount(() => {
  clearMotionTimers()
  window.clearTimeout(liftTimer)
  observer?.disconnect()
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})
</script>

<style scoped>
.interactive-ledger-illustration {
  --illustration-filter: saturate(0.96) brightness(1.02);
  --page-highlight: rgba(255, 248, 230, 0.20);
  --page-shadow: rgba(45, 35, 23, 0.20);
  position: absolute;
  inset: 0;
  overflow: hidden;
  touch-action: pan-y;
  user-select: none;
  animation: illustrationFadeIn 0.45s ease-out both;
}

.interactive-ledger-illustration:focus-visible {
  outline: 1px solid rgba(244, 237, 220, 0.66);
  outline-offset: -3px;
}

.illustration-stage {
  position: absolute;
  top: 50%;
  left: 50%;
  width: max(100%, 100vh);
  aspect-ratio: 1 / 1;
  transform: translate(-50%, -50%);
}

.illustration-art {
  position: absolute;
  inset: 0;
  z-index: 0;
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
  opacity: 0;
  transform: scale(1.012);
  filter: var(--illustration-filter);
  transition:
    opacity 0.9s cubic-bezier(0.22, 0.72, 0.25, 1),
    transform 1.25s cubic-bezier(0.22, 0.72, 0.25, 1),
    filter 0.22s ease;
  pointer-events: none;
  user-select: none;
}

.illustration-art.is-active {
  z-index: 1;
  opacity: 1;
  transform: translateX(var(--carousel-drag-offset, 0px)) scale(1);
}

.interactive-ledger-illustration.is-dragging .illustration-art.is-active {
  transition: none;
}

.book-hotspot {
  position: absolute;
  top: 60.606%;
  left: 48.644%;
  z-index: 3;
  width: 15.151%;
  height: 8.772%;
  padding: 0;
  background: transparent;
  border: 0;
  cursor: pointer;
  transition: opacity 0.16s ease;
}

.interactive-ledger-illustration.is-dragging .book-hotspot {
  opacity: 0;
  pointer-events: none;
}

.book-hotspot:focus-visible {
  outline: 2px solid rgba(240, 229, 204, 0.82);
  outline-offset: 3px;
  border-radius: 4px;
}

.page-lift-overlay {
  position: absolute;
  inset: 0;
  display: block;
  clip-path: polygon(0 0, 86% 9%, 99% 88%, 0 76%);
  background: linear-gradient(
    103deg,
    transparent 0 18%,
    var(--page-highlight) 46%,
    rgba(255, 248, 230, 0.06) 67%,
    transparent 88%
  );
  filter: drop-shadow(-1px 2px 2px rgba(45, 35, 23, 0.08));
  opacity: 0;
  transform: translateY(0);
  transition: opacity 0.20s ease, transform 0.20s ease, filter 0.20s ease;
}

.spine-shadow {
  position: absolute;
  top: 6%;
  bottom: 22%;
  left: -1%;
  width: 5%;
  background: linear-gradient(90deg, var(--page-shadow), transparent);
  opacity: 0;
  transform: translateX(0);
  transition: opacity 0.20s ease, transform 0.20s ease;
}

.book-hotspot:hover:not(.is-lifting) .page-lift-overlay,
.book-hotspot:focus-visible:not(.is-lifting) .page-lift-overlay {
  opacity: 0.44;
  transform: translateY(-2px);
  filter: drop-shadow(-2px 3px 2px rgba(45, 35, 23, 0.14));
}

.book-hotspot:hover:not(.is-lifting) .spine-shadow,
.book-hotspot:focus-visible:not(.is-lifting) .spine-shadow {
  opacity: 0.34;
  transform: translateX(-1px);
}

.book-hotspot.is-lifting .page-lift-overlay {
  animation: illustrationPageLift 0.52s cubic-bezier(0.32, 0.02, 0.18, 1) both;
}

.book-hotspot.is-lifting .spine-shadow {
  animation: illustrationSpineShadow 0.52s ease-out both;
}

@keyframes illustrationFadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes illustrationPageLift {
  0% { opacity: 0.12; transform: translateY(0); filter: none; }
  38% { opacity: 0.68; transform: translateY(-3px); filter: drop-shadow(-3px 4px 3px rgba(45, 35, 23, 0.18)); }
  72% { opacity: 0.38; transform: translateY(-1px); filter: drop-shadow(-1px 2px 2px rgba(45, 35, 23, 0.10)); }
  100% { opacity: 0; transform: translateY(0); filter: none; }
}

@keyframes illustrationSpineShadow {
  0%, 100% { opacity: 0; transform: translateX(0); }
  42% { opacity: 0.48; transform: translateX(-2px); }
}

[data-theme="dark"] .interactive-ledger-illustration {
  --illustration-filter: brightness(0.80) saturate(0.78) contrast(1.04);
  --page-highlight: rgba(238, 226, 201, 0.16);
  --page-shadow: rgba(16, 13, 9, 0.28);
}

[data-theme="eye-care"] .interactive-ledger-illustration {
  --illustration-filter: sepia(0.13) saturate(0.80) brightness(0.94);
  --page-highlight: rgba(247, 231, 198, 0.18);
  --page-shadow: rgba(52, 39, 24, 0.22);
}

@media (prefers-reduced-motion: reduce) {
  .interactive-ledger-illustration {
    animation: none;
  }

  .illustration-art,
  .page-lift-overlay,
  .spine-shadow,
  .book-hotspot {
    transition: none;
  }

  .illustration-art,
  .illustration-art.is-active {
    transform: none;
  }

  .book-hotspot.is-lifting .page-lift-overlay,
  .book-hotspot.is-lifting .spine-shadow {
    animation: none;
    opacity: 0.26;
  }
}
</style>
