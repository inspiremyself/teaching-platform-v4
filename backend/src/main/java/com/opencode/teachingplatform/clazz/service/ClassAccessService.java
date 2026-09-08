package com.opencode.teachingplatform.clazz.service;

import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.clazz.entity.ClassRoom;
import com.opencode.teachingplatform.clazz.entity.ClassTeacher;
import com.opencode.teachingplatform.clazz.repository.ClassRoomRepository;
import com.opencode.teachingplatform.clazz.repository.ClassTeacherRepository;
import com.opencode.teachingplatform.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ClassAccessService {

    private final ClassTeacherRepository classTeacherRepository;
    private final ClassRoomRepository classRoomRepository;

    public ClassAccessService(ClassTeacherRepository classTeacherRepository, ClassRoomRepository classRoomRepository) {
        this.classTeacherRepository = classTeacherRepository;
        this.classRoomRepository = classRoomRepository;
    }

    @Transactional(readOnly = true)
    public void assertTeacherAssignedToClass(CurrentUser currentUser, Long classId) {
        if (currentUser.role() != com.opencode.teachingplatform.common.enums.UserRole.TEACHER) {
            throw new BusinessException(40300, "无权限访问该资源");
        }
        findAssignment(currentUser, classId);
    }

    @Transactional(readOnly = true)
    public List<ClassRoom> listAssignedClasses(CurrentUser currentUser) {
        if (currentUser.role() != com.opencode.teachingplatform.common.enums.UserRole.TEACHER) {
            throw new BusinessException(40300, "无权限访问该资源");
        }
        return classTeacherRepository.findByTeacherUserIdAndSchoolId(currentUser.id(), currentUser.schoolId()).stream()
                .map(ClassTeacher::getClassId)
                .distinct()
                .map(classId -> classRoomRepository.findByIdAndSchoolId(classId, currentUser.schoolId())
                        .orElseThrow(() -> new BusinessException(40300, "无权限访问该资源")))
                .toList();
    }

    @Transactional(readOnly = true)
    public ClassTeacher findAssignment(CurrentUser currentUser, Long classId) {
        ClassRoom classRoom = classRoomRepository.findByIdAndSchoolId(classId, currentUser.schoolId())
                .orElseThrow(() -> new BusinessException(40300, "无权限访问该资源"));
        if (currentUser.role() != com.opencode.teachingplatform.common.enums.UserRole.TEACHER) {
            throw new BusinessException(40300, "无权限访问该资源");
        }
        return classTeacherRepository.findByClassIdAndTeacherUserId(classRoom.getId(), currentUser.id())
                .orElseThrow(() -> new BusinessException(40300, "无权限访问该资源"));
    }

    public void assertActiveTeacher(CurrentUser currentUser) {
        if (currentUser.role() != com.opencode.teachingplatform.common.enums.UserRole.TEACHER) {
            throw new BusinessException(40300, "无权限访问该资源");
        }
    }
}
