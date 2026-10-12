<script setup lang="ts">
/**
 * 桌面端标签选择弹窗（C-014）
 * @description 允许用户从推荐标签列表中选择多个偏好标签
 */
import { computed, ref, watch } from 'vue'

const props = defineProps<{
  tags: string[]
  selectedTags?: string[]
}>()

const emit = defineEmits<{
  (e: 'select', tags: string[]): void
  (e: 'cancel'): void
}>()

const show = defineModel<boolean>({ required: true })

// 内部选中状态（本地 ref，打开时从 props 同步，避免 computed get/set 循环）
const selected = ref<string[]>([...(props.selectedTags || [])])
watch(show, (visible) => {
  if (visible) {
    selected.value = [...(props.selectedTags || [])]
  }
})

// 确认选择
const handleConfirm = () => {
  emit('select', selected.value)
  show.value = false
}

// 取消
const handleCancel = () => {
  show.value = false
  emit('cancel')
}

// 全选/取消全选
const isAllSelected = computed(() => {
  return selected.value.length === props.tags.length && props.tags.length > 0
})

const toggleAll = () => {
  if (isAllSelected.value) {
    selected.value = []
  } else {
    selected.value = [...props.tags]
  }
}
</script>

<template>
  <div class="desktop-tag-modal-overlay" v-if="show" @click.self="handleCancel">
    <div class="desktop-tag-modal">
      <div class="modal-header">
        <h3>选择偏好标签</h3>
        <button class="close-btn" @click="handleCancel">×</button>
      </div>

      <div class="modal-body">
        <!-- 全选按钮 -->
        <div class="select-all">
          <label class="checkbox-label">
            <input type="checkbox" :checked="isAllSelected" @change="toggleAll" />
            <span>全选 ({{ tags.length }})</span>
          </label>
        </div>

        <!-- 标签列表 -->
        <div class="tag-list">
          <label v-for="tag in tags" :key="tag" class="tag-item">
            <input
              type="checkbox"
              :value="tag"
              v-model="selected"
              :disabled="selected.length >= 10 && !selected.includes(tag)"
            />
            <span>{{ tag }}</span>
          </label>
        </div>

        <!-- 提示 -->
        <div class="hint" v-if="selected.length >= 10">
          最多选择 10 个标签
        </div>
      </div>

      <div class="modal-footer">
        <button class="btn btn-cancel" @click="handleCancel">取消</button>
        <button class="btn btn-confirm" @click="handleConfirm">
          确定 ({{ selected.length }}/10)
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.desktop-tag-modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  backdrop-filter: blur(4px);
  animation: fadeIn 0.2s ease;
}

.desktop-tag-modal {
  background: #fff;
  border-radius: 20px;
  width: 90%;
  max-width: 600px;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 24px 64px rgba(0, 0, 0, 0.2);
  animation: slideUp 0.3s ease;
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(40px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24px 32px;
  border-bottom: 1px solid #f0f0f0;
}

.modal-header h3 {
  font-size: 20px;
  font-weight: 700;
  color: #2b2b2b;
  margin: 0;
}

.close-btn {
  width: 36px;
  height: 36px;
  border: none;
  background: transparent;
  font-size: 24px;
  color: #888;
  cursor: pointer;
  border-radius: 8px;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  justify-content: center;
}

.close-btn:hover {
  background: #f5f5f5;
  color: #333;
}

.modal-body {
  flex: 1;
  overflow-y: auto;
  padding: 24px 32px;
}

.select-all {
  margin-bottom: 16px;
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
  color: #333;
  cursor: pointer;
  user-select: none;
}

.checkbox-label input[type="checkbox"] {
  width: 18px;
  height: 18px;
  cursor: pointer;
  accent-color: #ff2442;
}

.tag-list {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.tag-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: #f9f9f9;
  border: 2px solid transparent;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: 14px;
  color: #333;
  user-select: none;
}

.tag-item:hover:not(.disabled) {
  background: #ffe9ed;
  border-color: #ff2442;
}

.tag-item input[type="checkbox"] {
  width: 18px;
  height: 18px;
  cursor: pointer;
  accent-color: #ff2442;
  flex-shrink: 0;
}

.tag-item.disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.hint {
  margin-top: 16px;
  padding: 12px;
  background: #fff6e9;
  border-radius: 8px;
  font-size: 13px;
  color: #ff8a00;
  text-align: center;
}

.modal-footer {
  display: flex;
  gap: 12px;
  padding: 24px 32px;
  border-top: 1px solid #f0f0f0;
}

.btn {
  flex: 1;
  padding: 14px 24px;
  border: none;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-cancel {
  background: #f5f5f5;
  color: #666;
}

.btn-cancel:hover {
  background: #e8e8e8;
}

.btn-confirm {
  background: linear-gradient(135deg, #ff2442 0%, #ff4d6d 100%);
  color: #fff;
  box-shadow: 0 4px 16px rgba(255, 36, 66, 0.3);
}

.btn-confirm:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(255, 36, 66, 0.4);
}
</style>
