package de.tum.cit.aet.artemis.math.dto;

import java.util.List;

import de.tum.cit.aet.artemis.assessment.domain.ComplaintResponse;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;

/**
 * Request body for updating a math assessment in response to a student complaint. Carries the tutor's (possibly
 * adjusted) manual score and feedback plus the {@link ComplaintResponse} that accepts or rejects the complaint.
 * <p>
 * Unlike the shared {@code AssessmentUpdateDTO}, this includes an explicit {@code score} because math's manual score
 * is authoritative rather than summed from feedback credits.
 *
 * @param score             the (possibly revised) manual score in [0, 100]
 * @param feedbacks         the tutor's unreferenced feedback
 * @param complaintResponse the tutor's response resolving the complaint (accepted/rejected + response text)
 */
public record MathAssessmentUpdateDTO(double score, List<Feedback> feedbacks, ComplaintResponse complaintResponse) {
}
