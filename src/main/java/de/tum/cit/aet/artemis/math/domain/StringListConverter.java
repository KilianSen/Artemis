package de.tum.cit.aet.artemis.math.domain;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;

/**
 * Stores a {@code List<String>} as a JSON array in a single column — the string analogue of
 * {@link IntegerListConverter}.
 * <p>
 * Deliberately a converted column rather than a second EAGER {@code @ElementCollection}: {@link MathProblem} is
 * join-fetched by several repository queries, and every additional eagerly-collected association multiplies those
 * result sets (and pushes the fetch graph past the size the exercise-import query allows).
 */
@Converter
public class StringListConverter implements AttributeConverter<List<String>, String> {

    private static final ObjectMapper objectMapper = JsonObjectMapper.get();

    @Override
    public String convertToDatabaseColumn(List<String> list) {
        if (list == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(list);
        }
        catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not convert List<String> to JSON", e);
        }
    }

    @Override
    public List<String> convertToEntityAttribute(String jsonData) {
        if (jsonData == null || jsonData.isBlank()) {
            return Collections.emptyList();
        }
        try {
            CollectionType type = objectMapper.getTypeFactory().constructCollectionType(List.class, String.class);
            return objectMapper.readValue(jsonData, type);
        }
        catch (IOException e) {
            throw new IllegalArgumentException("Could not convert JSON to List<String>", e);
        }
    }
}
