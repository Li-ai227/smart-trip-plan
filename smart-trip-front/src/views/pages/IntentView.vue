<script setup lang="ts">
/** 页面：① 意图输入 → 触发 POST /trip-plan → 进入规划进行中 */
import { useRouter } from 'vue-router'
import PhoneFrame from '@/components/PhoneFrame.vue'
import IntentForm from '@/components/intent/IntentForm.vue'
import { useTripPlanStore } from '@/stores/trip-plan'
import type { TripPlanRequest } from '@/models/trip-plan'

const router = useRouter()
const store = useTripPlanStore()

const onSubmit = async (payload: TripPlanRequest) => {
  const planId = await store.submit(payload)
  const status = store.detail?.status
  if (status === 'confirmed' || status === 'review') {
    router.push({ name: 'plan', params: { planId } })
  } else {
    router.push({ name: 'planning', params: { planId } })
  }
}
</script>

<template>
  <div class="stage">
    <PhoneFrame>
      <IntentForm :model-value="store.request" @submit="onSubmit" />
    </PhoneFrame>
  </div>
</template>
