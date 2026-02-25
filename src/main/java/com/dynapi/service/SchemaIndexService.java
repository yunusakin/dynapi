package com.dynapi.service;

import com.dynapi.domain.model.FieldDefinition;
import com.dynapi.domain.model.FieldType;
import com.dynapi.domain.model.SchemaVersion;
import com.dynapi.dto.SchemaIndexSyncResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SchemaIndexService {
    private static final Set<FieldType> INDEX_SUPPORTED_TYPES =
            Set.of(FieldType.STRING, FieldType.NUMBER, FieldType.BOOLEAN, FieldType.DATE);

    private final SchemaLifecycleService schemaLifecycleService;
    private final MongoTemplate mongoTemplate;

    public SchemaIndexSyncResult syncIndexes(String entity) {
        SchemaVersion published = schemaLifecycleService.latestPublished(entity);
        List<IndexSpec> indexSpecs = collectIndexSpecs(published.getFields());

        if (indexSpecs.isEmpty()) {
            return new SchemaIndexSyncResult(entity, published.getVersion(), 0, 0, List.of(), List.of());
        }

        IndexOperations indexOperations = mongoTemplate.indexOps(entity);
        List<IndexInfo> existingIndexes = indexOperations.getIndexInfo();

        int ensured = 0;
        for (IndexSpec indexSpec : indexSpecs) {
            IndexInfo existing = findSingleFieldIndex(existingIndexes, indexSpec.path());
            if (existing != null) {
                if (!isCompatible(existing, indexSpec)) {
                    throw new IllegalArgumentException(
                            "Index sync conflict for field '"
                                    + indexSpec.path()
                                    + "': existing index is non-unique but schema requires unique");
                }
                ensured++;
                continue;
            }

            Index index =
                    new Index()
                            .on(indexSpec.path(), Sort.Direction.ASC)
                            .named(indexName(entity, indexSpec.path(), indexSpec.unique()))
                            .partial(PartialIndexFilter.of(Criteria.where("deleted").ne(true)));
            if (indexSpec.unique()) {
                index.unique();
            }

            indexOperations.ensureIndex(index);
            ensured++;
        }

        List<String> uniqueFields =
                indexSpecs.stream().filter(IndexSpec::unique).map(IndexSpec::path).toList();
        List<String> indexedFields =
                indexSpecs.stream().filter(indexSpec -> !indexSpec.unique()).map(IndexSpec::path).toList();

        return new SchemaIndexSyncResult(
                entity, published.getVersion(), indexSpecs.size(), ensured, uniqueFields, indexedFields);
    }

    private List<IndexSpec> collectIndexSpecs(List<FieldDefinition> fields) {
        Map<String, Boolean> uniqueByPath = new LinkedHashMap<>();
        collectIndexSpecs(fields, "", uniqueByPath);

        List<IndexSpec> specs = new ArrayList<>(uniqueByPath.size());
        for (Map.Entry<String, Boolean> entry : uniqueByPath.entrySet()) {
            specs.add(new IndexSpec(entry.getKey(), entry.getValue()));
        }
        return specs;
    }

    private void collectIndexSpecs(
            List<FieldDefinition> fields, String parentPath, Map<String, Boolean> uniqueByPath) {
        if (fields == null || fields.isEmpty()) {
            return;
        }

        for (FieldDefinition field : fields) {
            if (field == null || field.getFieldName() == null || field.getFieldName().isBlank()) {
                continue;
            }

            String path =
                    parentPath.isEmpty() ? field.getFieldName() : parentPath + "." + field.getFieldName();

            if (field.isUnique() || field.isIndexed()) {
                ensureIndexSupported(path, field.getType());
                uniqueByPath.merge(path, field.isUnique(), (existing, incoming) -> existing || incoming);
            }

            if ((field.getType() == FieldType.OBJECT || field.getType() == FieldType.ARRAY)
                    && field.getSubFields() != null
                    && !field.getSubFields().isEmpty()) {
                collectIndexSpecs(field.getSubFields(), path, uniqueByPath);
            }
        }
    }

    private void ensureIndexSupported(String path, FieldType type) {
        if (type == null || !INDEX_SUPPORTED_TYPES.contains(type)) {
            throw new IllegalArgumentException(
                    "Indexes are only supported for STRING, NUMBER, BOOLEAN, DATE fields: '" + path + "'");
        }
    }

    private IndexInfo findSingleFieldIndex(List<IndexInfo> indexInfos, String fieldPath) {
        if (indexInfos == null || indexInfos.isEmpty()) {
            return null;
        }

        return indexInfos.stream()
                .filter(indexInfo -> indexInfo.getIndexFields().size() == 1)
                .filter(indexInfo -> fieldPath.equals(indexInfo.getIndexFields().getFirst().getKey()))
                .findFirst()
                .orElse(null);
    }

    private boolean isCompatible(IndexInfo existing, IndexSpec requested) {
        if (requested.unique()) {
            return existing.isUnique();
        }
        return true;
    }

    private String indexName(String entity, String path, boolean unique) {
        String normalizedEntity = normalize(entity);
        String normalizedPath = normalize(path);
        return "dynapi_" + normalizedEntity + "_" + normalizedPath + "_" + (unique ? "uniq" : "idx");
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replaceAll("[^A-Za-z0-9]+", "_");
    }

    private record IndexSpec(String path, boolean unique) {
    }
}
