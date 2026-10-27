package com.supportpilot.workflow;

/**
 * Thrown when an illegal state transition is attempted on a ticket.
 */
public class IllegalStateTransitionException extends RuntimeException {

    public IllegalStateTransitionException(WorkflowState fromState, WorkflowState toState) {
        super("Illegal state transition attempted from " + fromState + " to " + toState);
    }
}
