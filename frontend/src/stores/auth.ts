import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import { getUserInfo, login as loginApi, updateProfile as updateProfileApi } from '@/api/auth';
import type { LoginDto, UpdateProfilePayload, User } from '@/types/user';

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('token') || '');
  const user = ref<User | null>(null);
  const permissions = ref<string[]>([]);

  const homePath = computed(() => {
    if (user.value?.role === 'TEACHER') {
      return '/teacher/dashboard';
    }
    if (user.value?.role === 'STUDENT') {
      return '/student/dashboard';
    }
    if (user.value?.role === 'SCHOOL_ADMIN') {
      return '/admin/dashboard';
    }
    return '/profile';
  });

  const setToken = (newToken: string) => {
    token.value = newToken;
    localStorage.setItem('token', newToken);
  };

  const login = async (data: LoginDto) => {
    const res = await loginApi(data);
    setToken(res.token);
    user.value = res.user;
    permissions.value = res.permissions;
    await fetchUserInfo();
  };

  const fetchUserInfo = async () => {
    if (!token.value) return;
    const res = await getUserInfo();
    user.value = res;
    permissions.value = res.permissions ?? permissions.value;
  };

  const updateProfile = async (data: UpdateProfilePayload) => {
    const res = await updateProfileApi(data);
    user.value = res;
    permissions.value = res.permissions ?? permissions.value;
    return res;
  };

  const logout = () => {
    token.value = '';
    user.value = null;
    permissions.value = [];
    localStorage.removeItem('token');
  };

  return { token, user, permissions, homePath, login, logout, fetchUserInfo, setToken, updateProfile };
});
