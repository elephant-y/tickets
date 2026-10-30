import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

// 联调关键:开发期跨域解决方案 —— 前端所有 /api 请求由 Vite 开发服务器代发到网关,
// 浏览器视角是同源请求,跨域消失(跨域是浏览器策略,管不了服务器之间互发)。
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: "http://localhost:8081", // 网关
        changeOrigin: true,
      },
    },
  },
});
