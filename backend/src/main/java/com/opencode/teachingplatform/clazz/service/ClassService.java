package com.opencode.teachingplatform.clazz.service;

import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.clazz.entity.ClassRoom;
import com.opencode.teachingplatform.clazz.entity.ClassTeacher;
import com.opencode.teachingplatform.clazz.repository.ClassTeacherRepository;
import com.opencode.teachingplatform.common.api.PagedResult;
import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.school.entity.SchoolSubject;
import com.opencode.teachingplatform.school.repository.SchoolSubjectRepository;
import com.opencode.teachingplatform.student.repository.ClassMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ClassService {

    private final ClassAccessService classAccessService;
    private final ClassMemberRepository classMemberRepository;
    private final ClassTeacherRepository classTeacherRepository;
    private final SchoolSubjectRepository schoolSubjectRepository;

    public ClassService(
            ClassAccessService classAccessService,
            ClassMemberRepository classMemberRepository,
            ClassTeacherRepository classTeacherRepository,
            SchoolSubjectRepository schoolSubjectRepository
    ) {
        this.classAccessService = classAccessService;
        this.classMemberRepository = classMemberRepository;
        this.classTeacherRepository = classTeacherRepository;
        this.schoolSubjectRepository = schoolSubjectRepository;
    }

    @Transactional(readOnly = true)
    public PagedResult<Map<String, Object>> listTeacherClasses(CurrentUser currentUser) {
        List<Map<String, Object>> items = classAccessService.listAssignedClasses(currentUser).stream()
                .map(room -> toView(room, classAccessService.findAssignment(currentUser, room.getId())))
                .toList();
        return new PagedResult<>(items, 1, 10, items.size());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getClass(CurrentUser currentUser, Long id) {
        ClassRoom room = classAccessService.listAssignedClasses(currentUser).stream()
                .filter(item -> item.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new BusinessException(40300, "无权限访问该班级"));
        ClassTeacher assignment = classAccessService.findAssignment(currentUser, id);
        return toView(room, assignment);
    }

    public void assertTeacherOwnsClass(CurrentUser currentUser, Long classId) {
        classAccessService.assertTeacherAssignedToClass(currentUser, classId);
    }

    private Map<String, Object> toView(ClassRoom room, ClassTeacher assignment) {
        Map<String, Object> myAssignment = new LinkedHashMap<>();
        myAssignment.put("role", assignment.getRole().name());
        myAssignment.put("subjectId", assignment.getSubjectId());
        String subjectName = null;
        if (assignment.getSubjectId() != null) {
            subjectName = schoolSubjectRepository.findById(assignment.getSubjectId())
                    .map(SchoolSubject::getName)
                    .orElse(null);
        }
        myAssignment.put("subjectName", subjectName);

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", room.getId());
        view.put("name", room.getName());
        view.put("code", room.getCode());
        view.put("status", room.getStatus().name());
        view.put("studentCount", classMemberRepository.countByClassId(room.getId()));
        view.put("myAssignment", myAssignment);
        return view;
    }
}
