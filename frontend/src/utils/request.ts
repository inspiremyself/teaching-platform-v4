import axios from 'axios';
import { ElMessage } from 'element-plus';
import { useAuthStore } from '@/stores/auth';
import type { AxiosRequestConfig } from 'axios';

const KICKED_OUT_NOTICE_KEY = 'kicked_out_notice';

export class ApiError extends Error {
  code: number;

  constructor(code: number, message: string) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
  }
}

const instance = axios.create({
  baseURL: '/api/v1',
  timeout: 10000
});

instance.interceptors.request.use(
  config => {
    const authStore = useAuthStore();
    if (authStore.token) {
      config.headers.Authorization = `Bearer ${authStore.token}`;
    }
    return config;
  },
  error => Promise.reject(error)
);

instance.interceptors.response.use(
  response => {
    const res = response.data;
    if (res.code !== 0) {
      if (res.code !== 40901 && res.code !== 40101 && res.code !== 40103) {
        ElMessage.error(res.message || '请求失败');
      }
      if (res.code === 40102) {
        sessionStorage.setItem(KICKED_OUT_NOTICE_KEY, res.message || '账号已在其他设备登录，请重新登录');
      }
      if (res.code === 401 || res.code === 40100) {
        const authStore = useAuthStore();
        authStore.logout();
        window.location.href = '/login';
      }
      return Promise.reject(new ApiError(res.code, res.message || '请求失败'));
    }
    return res.data;
  },
  error => {
    const responseData = error.response?.data;
    if (responseData && typeof responseData.code === 'number') {
      if (responseData.code !== 40901 && responseData.code !== 40101 && responseData.code !== 40103) {
        ElMessage.error(responseData.message || '请求失败');
      }
      if (responseData.code === 40102) {
        sessionStorage.setItem(KICKED_OUT_NOTICE_KEY, responseData.message || '账号已在其他设备登录，请重新登录');
      }
      if ([401, 40100, 40102].includes(responseData.code)) {
        const authStore = useAuthStore();
        authStore.logout();
        window.location.href = '/login';
      }
      return Promise.reject(new ApiError(responseData.code, responseData.message || '请求失败'));
    }
    ElMessage.error(error.message || '请求失败，请稍后重试');
    return Promise.reject(error);
  }
);

const request = {
  get<T>(url: string, config?: AxiosRequestConfig) {
    return instance.get<any, T>(url, config);
  },
  post<T>(url: string, data?: unknown, config?: AxiosRequestConfig) {
    return instance.post<any, T>(url, data, config);
  },
  put<T>(url: string, data?: unknown, config?: AxiosRequestConfig) {
    return instance.put<any, T>(url, data, config);
  },
  delete<T>(url: string, config?: AxiosRequestConfig) {
    return instance.delete<any, T>(url, config);
  },
};

export default request;
