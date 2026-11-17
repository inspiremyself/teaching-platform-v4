import { ref } from 'vue';
import { getTeacherParticipation } from '@/api/teacher';
import type { ParticipationBusinessType, TeacherParticipationDetail } from '@/types/analysis';

export function useTeacherParticipation() {
  const detail = ref<TeacherParticipationDetail | null>(null);
  const loading = ref(false);
  const error = ref('');
  const dialogVisible = ref(false);
  const requestSeq = ref(0);

  const openDetail = async (businessType: ParticipationBusinessType, businessId: number) => {
    const seq = ++requestSeq.value;
    dialogVisible.value = true;
    error.value = '';
    detail.value = null;
    loading.value = true;
    try {
      const data = await getTeacherParticipation(businessType, businessId);
      if (seq !== requestSeq.value) {
        return;
      }
      detail.value = data;
    } catch (caught) {
      if (seq !== requestSeq.value) {
        return;
      }
      error.value = caught instanceof Error ? caught.message : '名单加载失败';
    } finally {
      loading.value = false;
    }
  };

  const closeDetail = () => {
    requestSeq.value += 1;
    dialogVisible.value = false;
    detail.value = null;
    error.value = '';
    loading.value = false;
  };

  return { detail, loading, error, dialogVisible, openDetail, closeDetail };
}
