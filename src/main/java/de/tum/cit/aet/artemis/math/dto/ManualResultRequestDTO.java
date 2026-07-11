package de.tum.cit.aet.artemis.math.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.Feedback;

/**
 * Request body for {@code PUT /api/math/math-submissions/{submissionId}/manual-result}.
 *
 * @param score     the tutor-supplied manual score in {@code [0, 100]} (authoritative for math — set independently of
 *                      feedback credits, unlike text/file-upload which sum credits)
 * @param feedbacks the tutor's unreferenced feedback comments to attach to the result (may be null/empty)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ManualResultRequestDTO(double score, List<Feedback> feedbacks) {
}
