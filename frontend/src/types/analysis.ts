export type AnalysisSummaryValue = string | number;

export interface AnalysisSummaryCard {
  key: string;
  title: string;
  value: AnalysisSummaryValue;
  description: string;
}

export interface TeacherTrendItem {
  date: string;
  scoreCount: number;
  avgScore: number;
}

export interface TeacherComparisonItem {
  className: string;
  avgScore: number;
  gradedCount: number;
}

export interface TeacherBusinessBreakdownItem {
  businessType: 'LAB' | 'HOMEWORK' | 'EXAM';
  label: string;
  avgScore: number;
  scoreCount: number;
}

export interface AnalysisInsight {
  tone: 'positive' | 'highlight' | 'warning';
  title: string;
  description: string;
}

export type ParticipationBusinessType = 'LAB' | 'HOMEWORK' | 'EXAM';

export interface TeacherParticipationActivity {
  businessId: number;
  businessTitle: string;
  expectedCount: number;
  participatedCount: number;
  completedCount: number;
  notStartedCount: number;
  participationRate: number;
  completionRate: number;
}

export interface TeacherParticipationOverviewRow {
  businessType: ParticipationBusinessType;
  label: string;
  activityCount: number;
  expectedCount: number;
  participatedCount: number;
  completedCount: number;
  notStartedCount: number;
  participationRate: number;
  completionRate: number;
  activities: TeacherParticipationActivity[];
}

export type TeacherParticipationOverview = TeacherParticipationOverviewRow[];

export interface TeacherParticipationStudent {
  studentId: number;
  studentNo: string;
  studentName: string;
  participation: 'NOT_STARTED' | 'PARTICIPATED';
  completion: 'COMPLETED' | 'IN_PROGRESS';
  submissionId: number | null;
  submitStatus: string | null;
  submittedAt: string | null;
}

export interface TeacherParticipationDetail {
  businessType: ParticipationBusinessType;
  businessId: number;
  businessTitle: string;
  classId: number;
  className: string;
  expectedCount: number;
  participatedCount: number;
  completedCount: number;
  notStartedCount: number;
  participationRate: number;
  completionRate: number;
  students: TeacherParticipationStudent[];
}

export interface TeacherAnalysisOverview {
  summaryCards: AnalysisSummaryCard[];
  trend: TeacherTrendItem[];
  sectionComparison: TeacherComparisonItem[];
  businessBreakdown: TeacherBusinessBreakdownItem[];
  insights: AnalysisInsight[];
  participationOverview: TeacherParticipationOverview;
}

export interface TeacherDashboardData {
  summaryCards: AnalysisSummaryCard[];
  trend: TeacherTrendItem[];
  recentTasks: Array<{
    label: string;
    title: string;
    actor: string;
    eventTime: string | null;
    route: string;
  }>;
  quickLinks: Array<{ label: string; route: string; badge: number }>;
}

export interface StudentDashboardData {
  summaryCards: AnalysisSummaryCard[];
  upcomingTasks: Array<{ type: string; title: string; dueAt: string | null; route: string }>;
  recentFeedback: Array<{
    businessType: string;
    businessName: string;
    score: number;
    gradedAt: string | null;
  }>;
  recentMaterials: Array<{ title: string; category: string; fileName: string }>;
  quickLinks: Array<{ label: string; route: string; badge: number }>;
}

export interface StudentScoreRecord {
  id: number;
  businessType: 'LAB' | 'HOMEWORK' | 'EXAM';
  businessName: string;
  score: number;
  gradedAt: string | null;
}

export interface StudentGroupedScore {
  businessType: 'LAB' | 'HOMEWORK' | 'EXAM';
  label: string;
  avgScore: number;
  scoreCount: number;
}

export interface StudentScoreOverview {
  summaryCards: AnalysisSummaryCard[];
  groupedScores: StudentGroupedScore[];
  recentRecords: StudentScoreRecord[];
  feedbackNotes: AnalysisInsight[];
  completedItems?: number;
  totalItems?: number;
}
