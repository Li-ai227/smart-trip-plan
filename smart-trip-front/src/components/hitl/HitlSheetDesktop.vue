<script setup lang="ts">
/**
 * 人工介入弹窗（桌面端）—— 居中模态框，更丰富的交互
 */
import type { InterveneAction } from '@/models/trip-plan'

const props = defineProps<{
  reason?: string
  advice?: string
}>()
const emit = defineEmits<{
  (e: 'action', action: InterveneAction): void
  (e: 'close'): void
}>()
</script>

<template>
  <div class="desktop-hitl-overlay" @click.self="emit('close')">
    <div class="desktop-hitl-modal">
      <!-- 标题栏 -->
      <div class="modal-header">
        <div class="header-left">
          <div class="header-icon">⚠️</div>
          <h2>需要你确认</h2>
        </div>
        <button class="modal-close-btn" @click="emit('close')" title="关闭">✕</button>
      </div>

      <!-- 原因说明 -->
      <div class="reason-section">
        <p class="reason-text">{{ reason ?? '方案需要你的确认' }}</p>
        <div v-if="advice" class="advice-box">
          <span class="advice-icon">💡</span>
          <span class="advice-text">{{ advice }}</span>
        </div>
      </div>

      <!-- 操作按钮 -->
      <div class="action-buttons">
        <button class="btn btn-primary" @click="emit('action', 'CONFIRM')">
          <span class="btn-icon">✓</span>
          <span class="btn-text">接受建议</span>
        </button>
        <button class="btn btn-secondary" @click="emit('action', 'MODIFY')">
          <span class="btn-icon">✏️</span>
          <span class="btn-text">我来修改</span>
        </button>
        <button class="btn btn-danger" @click="emit('action', 'REJECT')">
          <span class="btn-icon">↻</span>
          <span class="btn-text">重新规划</span>
        </button>
      </div>

      <!-- 底部说明 -->
      <div class="modal-footer">
        <p>AI 已尽力规划，但某些决策仍需您的专业判断</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.desktop-hitl-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  backdrop-filter: blur(4px);
}

.desktop-hitl-modal {
  background: #fff;
  border-radius: 24px;
  max-width: 600px;
  width: 90%;
  padding: 40px;
  box-shadow: 0 24px 64px rgba(0, 0, 0, 0.2);
  animation: slideUp 0.3s ease;
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
  margin-bottom: 24px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.modal-close-btn {
  background: transparent;
  border: none;
  font-size: 20px;
  color: #999;
  cursor: pointer;
  padding: 6px 10px;
  border-radius: 8px;
  transition: all 0.2s ease;
  line-height: 1;
}

.modal-close-btn:hover {
  color: #333;
  background: #f2f2f2;
}

.header-icon {
  font-size: 48px;
}

.modal-header h2 {
  font-size: 24px;
  font-weight: 700;
  color: #2b2b2b;
  margin: 0;
}

.reason-section {
  margin-bottom: 32px;
}

.reason-text {
  font-size: 16px;
  color: #333;
  line-height: 1.6;
  margin-bottom: 16px;
}

.advice-box {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px;
  background: #fff8e1;
  border-radius: 12px;
  border-left: 4px solid #ff8a00;
}

.advice-icon {
  font-size: 20px;
  flex-shrink: 0;
}

.advice-text {
  font-size: 14px;
  color: #666;
  line-height: 1.6;
}

/* 操作按钮 */
.action-buttons {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 16px 24px;
  border-radius: 16px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  border: none;
}

.btn-primary {
  background: linear-gradient(135deg, #ff2442 0%, #ff4d6d 100%);
  color: #fff;
  box-shadow: 0 4px 16px rgba(255, 36, 66, 0.3);
}

.btn-primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(255, 36, 66, 0.4);
}

.btn-secondary {
  background: #fff;
  color: #ff2442;
  border: 2px solid #ffe9ed;
}

.btn-secondary:hover {
  background: #ffe9ed;
  border-color: #ff2442;
}

.btn-danger {
  background: #f5f5f5;
  color: #888;
}

.btn-danger:hover {
  background: #e8e8e8;
  color: #333;
}

.btn-icon {
  font-size: 18px;
}

.btn-text {
  font-size: 15px;
}

.modal-footer {
  text-align: center;
  padding-top: 24px;
  border-top: 1px solid #f0f0f0;
}

.modal-footer p {
  font-size: 13px;
  color: #999;
  line-height: 1.6;
}
</style>
