package com.opencode.teachingplatform.student;

import com.opencode.teachingplatform.student.entity.ClassMember;
import com.opencode.teachingplatform.student.repository.ClassMemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ClassMemberRepositoryTests {

    @Autowired
    private ClassMemberRepository classMemberRepository;

    @Test
    void findByStudentUserIdReturnsSeedMembership() {
        List<ClassMember> members = classMemberRepository.findByStudentUserId(2L);

        assertThat(members).hasSize(1);
        assertThat(members.get(0).getClassId()).isEqualTo(1L);
        assertThat(members.get(0).getStudentUserId()).isEqualTo(2L);
    }

    @Test
    void findByClassIdAndStudentUserIdIsScopedToBothKeys() {
        assertThat(classMemberRepository.findByClassIdAndStudentUserId(1L, 2L)).isPresent();
        assertThat(classMemberRepository.findByClassIdAndStudentUserId(999L, 2L)).isEmpty();
        assertThat(classMemberRepository.findByStudentUserId(999999L)).isEmpty();
    }
}
