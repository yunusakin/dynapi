package com.example.dynapi.domain.repository;

import com.example.dynapi.domain.model.FormSubmission;
import java.util.Optional;
import java.util.List;

public interface FormSubmissionRepository {
    FormSubmission save(FormSubmission formSubmission);
    Optional<FormSubmission> findById(String id);
    List<FormSubmission> findAll();
    void deleteById(String id);
    List<FormSubmission> findByFieldGroupId(String fieldGroupId);
    boolean existsById(String id);
}
