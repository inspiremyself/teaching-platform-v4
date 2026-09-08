export interface User {
  id: number;
  username: string;
  displayName: string;
  role: 'TEACHER' | 'STUDENT' | 'SCHOOL_ADMIN';
  schoolId?: number;
  schoolName?: string;
  email?: string | null;
  phone?: string | null;
  officeHours?: string | null;
  bio?: string | null;
  mustChangePassword?: boolean;
  permissions?: string[];
}

export interface LoginDto {
  username: string;
  password: string;
  role: 'TEACHER' | 'STUDENT' | 'SCHOOL_ADMIN';
  forceLogin?: boolean;
}

export interface UpdateProfilePayload {
  displayName: string;
  email: string;
  phone: string;
  officeHours: string;
  bio: string;
}
