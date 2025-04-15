package com.example.CodeCareer.repository;

import java.util.List;

import com.example.CodeCareer.util.constant.LevelEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.example.CodeCareer.domain.Job;
import com.example.CodeCareer.domain.Skill;

@Repository
public interface JobRepository extends JpaRepository<Job, Long>,
                JpaSpecificationExecutor<Job> {

        List<Job> findBySkillsIn(List<Skill> skills);

        List<Job> findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(String name, String description);
        List<Job> findByLocationContainingIgnoreCase(String location);
        List<Job> findByLevel(LevelEnum level);
        List<Job> findByActiveTrue();

        @Query("SELECT j FROM Job j " +
                "JOIN j.skills s " +
                "WHERE LOWER(s.name) LIKE LOWER(CONCAT('%', :skillName, '%'))")
        List<Job> findBySkillsNameContainingIgnoreCase(String skillName);
}
