package com.opencode.teachingplatform.school;

import com.opencode.teachingplatform.school.entity.School;
import com.opencode.teachingplatform.school.repository.SchoolRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class SchoolRepositoryTests {

    @Autowired
    private SchoolRepository schoolRepository;

    @Test
    void findByCodeReturnsOnlyTheMatchingSchool() {
        Optional<School> found = schoolRepository.findByCode("DEMO-SCHOOL");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("演示学校");
        assertThat(found.get().getCode()).isEqualTo("DEMO-SCHOOL");
        assertThat(schoolRepository.findByCode("NOT-EXIST-SCHOOL")).isEmpty();
    }

    @Test
    void seedSchoolIsMappedWithStatus() {
        assertThat(schoolRepository.count()).isGreaterThanOrEqualTo(1L);
        assertThat(schoolRepository.findAll())
                .allSatisfy(school -> assertThat(school.getStatus()).isNotNull());
    }
}
