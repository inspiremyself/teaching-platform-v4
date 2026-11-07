import request from '@/utils/request';
import type { LoginDto, UpdateProfilePayload, User } from '@/types/user';

export const login = (data: LoginDto) => {
  return request.post<{ token: string; user: User; permissions: string[] }>('/auth/login', data);
};

export const getUserInfo = () => {
  return request.get<User>('/auth/me');
};

export const updateProfile = (data: UpdateProfilePayload) => {
  return request.put<User>('/auth/profile', data);
};

export const changePassword = (data: { oldPassword: string; newPassword: string }) => {
  return request.post<void>('/auth/change-password', data);
};

export const logoutRequest = () => {
  return request.post<void>('/auth/logout');
};
