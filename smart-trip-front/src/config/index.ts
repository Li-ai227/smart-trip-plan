// 前缀留空 —— Vite dev 时由 proxy 转发到 localhost:8080，生产构建走 CORS
export const apiPrefix: string = ''

// 是否使用本地 Mock 数据。true = 绕过所有后端请求（纯前端演示）；false = 真实联调。
export const useMock: boolean = false

// 规划进度轮询间隔（毫秒），对齐第 25 章「异步状态机驱动」的 2~3s 轮询
export const pollIntervalMs: number = 2500
