package de.tum.cit.aet.artemis.math.dto;

import de.tum.cit.aet.artemis.math.domain.MathGradingJobStatus;

/**
 * Pushed to a student over the websocket when the asynchronous grading of one of their submissions reaches a
 * terminal state that has no automatic result — i.e. it is escalated to manual tutor review ({@code REVIEW} for an
 * inconclusive verdict, {@code FAILED} for a backend error). A {@code COMPLETED} outcome is conveyed by the normal
 * result push instead, so this message only carries the review/failed transitions.
 *
 * @param submissionId    the submission whose grading settled
 * @param participationId the participation the submission belongs to (lets the client match it to the open editor)
 * @param status          the terminal grading-job status
 */
public record MathGradingStatusDTO(Long submissionId, Long participationId, MathGradingJobStatus status) {
}
