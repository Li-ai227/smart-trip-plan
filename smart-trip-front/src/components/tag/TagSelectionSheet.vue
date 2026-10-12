<script setup lang="ts">
/**
 * 移动端标签选择抽屉（C-014）
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
  <div class="mobile-tag-sheet-overlay" v-if="show" @click.self="handleCancel">
    <div class="mobile-tag-sheet">
      <div class="sheet-header">
        <h3>选择偏好标签</h3>
        <button class="close-btn" @click="handleCancel">×</button>
      </div>

      <div class="sheet-body">
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

      <div class="sheet-footer">
        <button class="btn btn-cancel" @click="handleCancel">取消</button>
        <button class="btn btn-confirm" @click="handleConfirm">
          确定 ({{ selected.length }}/10)
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.mobile-tag-sheet-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: flex-end;
  z-index: 1000;
  backdrop-filter: blur(4px);
  animation: fadeIn 0.2s ease;
}

.mobile-tag-sheet {
  background: #fff;
  width: 100%;
  border-radius: 22px 22px 0 0;
  max-height: 85vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 -8px 32px rgba(0, 0, 0, 0.15);
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
    transform: translateY(100%);
  }
  to {
    transform: translateY(0);
  }
}

.sheet-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 18px;
  border-bottom: 1px solid #f0f0f0;
}

.sheet-header h3 {
  font-size: 17px;
  font-weight: 700;
  color: #2b2b2b;
  margin: 0;
}

.close-btn {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  font-size: 20px;
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

.sheet-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px 18px;
}

.select-all {
  margin-bottom: 12px;
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 8px;
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
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}

.tag-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  background: #f9f9f9;
  border: 2px solid transparent;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: 13px;
  color: #333;
  user-select: none;
}

.tag-item:hover:not(.disabled) {
  background: #ffe9ed;
  border-color: #ff2442;
}

.tag-item input[type="checkbox"] {
  width: 16px;
  height: 16px;
  cursor: pointer;
  accent-color: #ff2442;
  flex-shrink: 0;
}

.tag-item.disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.hint {
  margin-top: 12px;
  padding: 10px;
  background: #fff6e9;
  border-radius: 8px;
  font-size: 12px;
  color: #ff8a00;
  text-align: center;
}

.sheet-footer {
  display: flex;
  gap: 10px;
  padding: 16px 18px;
  border-top: 1px solid #f0f0f0;
}

.btn {
  flex: 1;
  padding: 12px 20px;
  border: none;
  border-radius: 12px;
  font-size: 14px;
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
  box-shadow: 0 4px 12px rgba(255, 36, 66, 0.3);
}

.btn-confirm:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(255, 36, 66, 0.4);
}
</style>
