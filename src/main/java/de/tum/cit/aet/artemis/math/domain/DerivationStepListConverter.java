package de.tum.cit.aet.artemis.math.domain;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.math.dto.MathSubmissionDTO.DerivationStepDTO;

/**
 * JPA {@link AttributeConverter} that serialises a single {@code List<DerivationStepDTO>} to/from a
 * JSON {@code longtext} column. The list is one complete worked example derivation for a single
 * {@link MathProblem}.
 */
@Converter
public class DerivationStepListConverter implements AttributeConverter<List<DerivationStepDTO>, String> {

    private static final ObjectMapper objectMapper = JsonObjectMapper.get();

    private static final JavaType DERIVATION_TYPE;

    static {
        JavaType stepType = objectMapper.getTypeFactory().constructType(DerivationStepDTO.class);
        DERIVATION_TYPE = objectMapper.getTypeFactory().constructCollectionType(List.class, stepType);
    }

    @Override
    public String convertToDatabaseColumn(List<DerivationStepDTO> derivation) {
        if (derivation == null || derivation.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(derivation);
        }
        catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not convert example derivation to JSON", e);
        }
    }

    @Override
    public List<DerivationStepDTO> convertToEntityAttribute(String jsonData) {
        if (jsonData == null || jsonData.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(jsonData, DERIVATION_TYPE);
        }
        catch (IOException e) {
            throw new IllegalArgumentException("Could not convert JSON to example derivation", e);
        }
    }
}
