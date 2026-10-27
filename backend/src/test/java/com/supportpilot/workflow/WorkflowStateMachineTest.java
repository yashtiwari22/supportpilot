package com.supportpilot.workflow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowStateMachineTest {

    private WorkflowStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new WorkflowStateMachine();
    }

    @ParameterizedTest
    @CsvSource({
            "RECEIVED, TRIAGED",
            "TRIAGED, PROCESSING",
            "PROCESSING, DRAFTED",
            "DRAFTED, CRITIQUED",
            "CRITIQUED, AWAITING_APPROVAL",
            "CRITIQUED, APPROVED",
            "CRITIQUED, REVISING",
            "REVISING, DRAFTED",
            "AWAITING_APPROVAL, APPROVED",
            "AWAITING_APPROVAL, REJECTED",
            "APPROVED, SENT",
            "REJECTED, SENT",
            "RECEIVED, FAILED",
            "PROCESSING, FAILED",
            "FAILED, DEAD_LETTER"
    })
    @DisplayName("Legal state transitions are validated without exception")
    void validTransitionsSucceed(WorkflowState from, WorkflowState to) {
        assertTrue(stateMachine.canTransition(from, to));
        assertDoesNotThrow(() -> stateMachine.validateTransition(from, to));
    }

    @ParameterizedTest
    @CsvSource({
            "RECEIVED, APPROVED",
            "RECEIVED, DRAFTED",
            "SENT, RECEIVED",
            "SENT, DRAFTED",
            "DEAD_LETTER, APPROVED",
            "AWAITING_APPROVAL, SENT",
            "TRIAGED, SENT"
    })
    @DisplayName("Illegal state transitions throw IllegalStateTransitionException")
    void illegalTransitionsThrowException(WorkflowState from, WorkflowState to) {
        assertFalse(stateMachine.canTransition(from, to));
        assertThrows(IllegalStateTransitionException.class, () -> stateMachine.validateTransition(from, to));
    }

    @Test
    @DisplayName("Idempotent transition to same state is always allowed")
    void idempotentSameStateTransition() {
        assertTrue(stateMachine.canTransition(WorkflowState.RECEIVED, WorkflowState.RECEIVED));
        assertDoesNotThrow(() -> stateMachine.validateTransition(WorkflowState.TRIAGED, WorkflowState.TRIAGED));
    }
}
