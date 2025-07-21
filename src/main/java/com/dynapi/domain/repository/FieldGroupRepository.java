package com.dynapi.domain.repository;

import com.dynapi.domain.model.FieldGroup;
import java.util.Optional;
import java.util.List;

public interface FieldGroupRepository {
    FieldGroup save(FieldGroup fieldGroup);
    Optional<FieldGroup> findById(String id);
    List<FieldGroup> findAll();
    void deleteById(String id);
    boolean existsById(String id);
}
