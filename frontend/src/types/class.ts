export type ClassStatus = 'ACTIVE' | 'ARCHIVED';

export interface ClassAssignment {
  role: 'HEAD' | 'SUBJECT';
  subjectName: string | null;
}

export interface TeachingClass {
  id: number;
  name: string;
  code: string;
  status: ClassStatus;
  studentCount: number;
  myAssignment: ClassAssignment;
}
