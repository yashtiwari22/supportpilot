package com.supportpilot.workflow;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * State machine enforcing the State pattern across ticket lifecycle transitions.
 */
@Component
public class WorkflowStateMachine {

    private static final Map<WorkflowState, Set<WorkflowState>> VALID_TRANSITIONS = new EnumMap<>(WorkflowState.class);

    static {
        VALID_TRANSITIONS.put(WorkflowState.RECEIVED, EnumSet.of(
                WorkflowState.TRIAGED,
                WorkflowState.FAILED,
                WorkflowState.DEAD_LETTER
        ));

        VALID_TRANSITIONS.put(WorkflowState.TRIAGED, EnumSet.of(
                WorkflowState.PROCESSING,
                WorkflowState.FAILED
        ));

        VALID_TRANSITIONS.put(WorkflowState.PROCESSING, EnumSet.of(
                WorkflowState.DRAFTED,
                WorkflowState.FAILED
        ));

        VALID_TRANSITIONS.put(WorkflowState.DRAFTED, EnumSet.of(
                WorkflowState.CRITIQUED,
                WorkflowState.FAILED
        ));

        VALID_TRANSITIONS.put(WorkflowState.CRITIQUED, EnumSet.of(
                WorkflowState.REVISING,
                WorkflowState.AWAITING_APPROVAL,
                WorkflowState.APPROVED,
                WorkflowState.FAILED
        ));

        VALID_TRANSITIONS.put(WorkflowState.REVISING, EnumSet.of(
                WorkflowState.DRAFTED,
                WorkflowState.FAILED
        ));

        VALID_TRANSITIONS.put(WorkflowState.AWAITING_APPROVAL, EnumSet.of(
                WorkflowState.APPROVED,
                WorkflowState.REJECTED,
                WorkflowState.FAILED
        ));

        VALID_TRANSITIONS.put(WorkflowState.APPROVED, EnumSet.of(
                WorkflowState.SENT,
                WorkflowState.FAILED
        ));

        VALID_TRANSITIONS.put(WorkflowState.REJECTED, EnumSet.of(
                WorkflowState.SENT,
                WorkflowState.FAILED
        ));

        VALID_TRANSITIONS.put(WorkflowState.FAILED, EnumSet.of(
                WorkflowState.RECEIVED,     // Manual retry
                WorkflowState.DEAD_LETTER
        ));

        VALID_TRANSITIONS.put(WorkflowState.SENT, EnumSet.noneOf(WorkflowState.class));
        VALID_TRANSITIONS.put(WorkflowState.DEAD_LETTER, EnumSet.noneOf(WorkflowState.class));
    }

    /**
     * Asserts that transitioning from fromState to toState is legal; throws otherwise.
     */
    public void validateTransition(WorkflowState fromState, WorkflowState toState) {
        if (fromState == toState) {
            return; // Idempotent no-op
        }
        Set<WorkflowState> allowed = VALID_TRANSITIONS.getOrDefault(fromState, Set.of());
        if (!allowed.contains(toState)) {
            throw new IllegalStateTransitionException(fromState, toState);
        }
    }

    public boolean canTransition(WorkflowState fromState, WorkflowState toState) {
        if (fromState == toState) return true;
        Set<WorkflowState> allowed = VALID_TRANSITIONS.getOrDefault(fromState, Set.of());
        return allowed.contains(toState);
    }
}
