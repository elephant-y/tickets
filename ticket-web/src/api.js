import axios from "axios";

// 统一请求封装(相当于后端的「服务层」):
//   请求拦截器 → 自动带 token(对应网关的 Authorization 校验)
//   响应拦截器 → 统一剥 Result 壳,调用方直接拿 data
const http = axios.create({
  baseURL: "/api", // 相对路径,由 Vite 代理转发到网关
  timeout: 8000,
});

http.interceptors.request.use((config) => {
  const token = localStorage.getItem("token");
  if (token) {
    config.headers.Authorization = "Bearer " + token;
  }
  return config;
});

http.interceptors.response.use(
  (res) => {
    const body = res.data; // 后端 Result:{code, message, data}
    if (body.code !== 0) {
      return Promise.reject(new Error(body.message));
    }
    return body.data; // 剥壳:调用方拿到的就是业务数据
  },
  (err) => {
    const msg = err.response?.data?.message || err.message || "请求失败";
    return Promise.reject(new Error(msg));
  }
);

export default http;
