package com.opencode.teachingplatform.common;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 1B 跨校回归共享夹具：只在测试库内造第二所学校与六表业务行，不写迁移 seed、不改演示账号。
 */
public final class SchoolScopeFixtures {

    public static final long SCHOOL_A = 1L;
    public static final long SCHOOL_B = 2L;
    public static final long CLASS_B = 9200L;
    public static final long TEACHER_ID = 1L;
    public static final long STUDENT_ID = 2L;

    public static final long LAB_B = 9201L;
    public static final long HOMEWORK_B = 9202L;
    public static final long EXAM_B = 9203L;
    public static final long QUESTION_B = 9204L;
    public static final long SCORE_B = 9205L;
    public static final long PLAGIARISM_B = 9206L;

    private SchoolScopeFixtures() {
    }

    /** 清理并重建 B 校数据；可重复调用，保证测试间不互相污染。 */
    public static void seedSecondSchool(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM score_record WHERE school_id = ?", SCHOOL_B);
        jdbc.update("DELETE FROM plagiarism_task WHERE school_id = ?", SCHOOL_B);
        jdbc.update("DELETE FROM question_bank WHERE school_id = ?", SCHOOL_B);
        jdbc.update("DELETE FROM exam WHERE school_id = ?", SCHOOL_B);
        jdbc.update("DELETE FROM homework WHERE school_id = ?", SCHOOL_B);
        jdbc.update("DELETE FROM t_experiment WHERE school_id = ?", SCHOOL_B);
        jdbc.update("DELETE FROM class_room WHERE id = ?", CLASS_B);
        jdbc.update("DELETE FROM school WHERE id = ?", SCHOOL_B);

        jdbc.update("INSERT INTO school (id, name, code, status, created_at, updated_at) "
                + "VALUES (?, '第二学校', 'SCHOOL-B', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", SCHOOL_B);
        jdbc.update("INSERT INTO class_room (id, name, code, status, school_id, created_at, updated_at) "
                + "VALUES (?, 'B校班级', 'CLASS-B', 'ACTIVE', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                CLASS_B, SCHOOL_B);

        jdbc.update("INSERT INTO t_experiment "
                + "(experiment_id, experiment_no, experiment_name, status, class_id, created_by, school_id) "
                + "VALUES (?, ?, 'B校实验', 'PUBLISHED', ?, ?, ?)",
                LAB_B, 9201, CLASS_B, TEACHER_ID, SCHOOL_B);
        jdbc.update("INSERT INTO homework (id, title, class_id, created_by, status, school_id) "
                + "VALUES (?, 'B校作业', ?, ?, 'PUBLISHED', ?)",
                HOMEWORK_B, CLASS_B, TEACHER_ID, SCHOOL_B);
        jdbc.update("INSERT INTO exam (id, title, class_id, duration_minutes, status, created_by, school_id) "
                + "VALUES (?, 'B校考试', ?, 60, 'PUBLISHED', ?, ?)",
                EXAM_B, CLASS_B, TEACHER_ID, SCHOOL_B);
        jdbc.update("INSERT INTO question_bank (id, code, type, stem, difficulty, default_score, created_by, school_id) "
                + "VALUES (?, 'QB-B-9204', 'SINGLE', 'B校题目', 'EASY', 5, ?, ?)",
                QUESTION_B, TEACHER_ID, SCHOOL_B);
        jdbc.update("INSERT INTO score_record "
                + "(id, business_type, business_id, student_id, class_id, score, graded_at, school_id) "
                + "VALUES (?, 'EXAM', ?, ?, ?, 60, CURRENT_TIMESTAMP, ?)",
                SCORE_B, EXAM_B, STUDENT_ID, CLASS_B, SCHOOL_B);
        jdbc.update("INSERT INTO plagiarism_task "
                + "(id, business_type, business_id, student_id, status, similarity_rate, school_id) "
                + "VALUES (?, 'HOMEWORK', ?, ?, 'COMPLETED', 55, ?)",
                PLAGIARISM_B, HOMEWORK_B, STUDENT_ID, SCHOOL_B);
    }

    /** 六表字段名，供 schema 断言遍历。 */
    public static final String[] TABLES = {
            "T_EXPERIMENT", "HOMEWORK", "EXAM", "QUESTION_BANK", "SCORE_RECORD", "PLAGIARISM_TASK"
    };

    /** 六条索引名，与 V26 第 7 段一致。 */
    public static final String[] INDEXES = {
            "IDX_T_EXPERIMENT_SCHOOL_CLASS", "IDX_HOMEWORK_SCHOOL_CLASS", "IDX_EXAM_SCHOOL_CLASS",
            "IDX_QUESTION_BANK_SCHOOL_CREATED_BY", "IDX_SCORE_RECORD_SCHOOL_STUDENT",
            "IDX_PLAGIARISM_TASK_SCHOOL_STUDENT"
    };
}
