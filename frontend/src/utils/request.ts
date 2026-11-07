import axios from 'axios';
import { ElMessage } from 'element-plus';
import type { AxiosRequestConfig } from 'axios';
import { clearToken, getToken } from '@/utils/authToken';
import type { ApiResponse } from '@/types/common';

declare module 'axios' {
  export interface AxiosRequestConfig {
    /** 为 true 时抑制全局错误提示，由调用方自行呈现错误 */
    silent?: boolean;
  }
}

export const API_BASE_URL = '/api/v1';

const KICKED_OUT_NOTICE_KEY = 'kicked_out_notice';

/** toast 静默码：这些错误由调用方自行呈现，不弹全局提示。 */
export const SILENT_CODES = new Set([40901, 40101, 40103, 40903, 40102]);

/** 登录态失效码：路由守卫与响应拦截器共用，命中即登出。 */
export const AUTH_FAILURE_CODES = new Set([401, 40100, 40102]);

let kickedOutHandled = false;

function handleKickedOut(message?: string) {
  if (kickedOutHandled) {
    return;
  }
  kickedOutHandled = true;
  sessionStorage.setItem(KICKED_OUT_NOTICE_KEY, message || '账号已在其他设备登录，请重新登录');
  clearToken();
  window.location.href = '/login';
}

export class ApiError extends Error {
  code: number;

  constructor(code: number, message: string) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
  }
}

const instance = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000
});

instance.interceptors.request.use(
  config => {
    const token = getToken();
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  error => Promise.reject(error)
);

instance.interceptors.response.use(
  response => {
    const res: ApiResponse<unknown> = response.data;
    const silent = response.config.silent === true;
    if (res.code !== 0) {
      if (!silent && !SILENT_CODES.has(res.code)) {
        ElMessage.error(res.message || '请求失败');
      }
      if (res.code === 40102) {
        handleKickedOut(res.message);
      } else if (AUTH_FAILURE_CODES.has(res.code)) {
        clearToken();
        window.location.href = '/login';
      }
      return Promise.reject(new ApiError(res.code, res.message || '请求失败'));
    }
    return response.data.data;
  },
  error => {
    const responseData: ApiResponse<unknown> | undefined = error.response?.data;
    const silent = error.config?.silent === true;
    if (responseData && typeof responseData.code === 'number') {
      if (!silent && !SILENT_CODES.has(responseData.code)) {
        ElMessage.error(responseData.message || '请求失败');
      }
      if (responseData.code === 40102) {
        handleKickedOut(responseData.message);
      } else if (AUTH_FAILURE_CODES.has(responseData.code)) {
        clearToken();
        window.location.href = '/login';
      }
      return Promise.reject(new ApiError(responseData.code, responseData.message || '请求失败'));
    }
    if (!silent) {
      ElMessage.error(error.message || '请求失败，请稍后重试');
    }
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
