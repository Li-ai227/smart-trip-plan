// 基础响应数据格式（对齐 .harness/wiki/接口协议.md §5 通用约定）
export type BaseResponse<T> = {
  code: string
  message: string
  data: T
}

// 统一错误响应结构: { code, message, traceId }
export type ErrorResponse = {
  code: string
  message: string
  traceId?: string
}
