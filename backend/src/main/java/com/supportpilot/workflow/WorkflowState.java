package com.supportpilot.workflow;

/**
 * Valid states in the SupportPilot ticket lifecycle state pattern.
 */
public enum WorkflowState {
    RECEIVED,
    TRIAGED,
    PROCESSING,
    DRAFTED,
    CRITIQUED,
    AWAITING_APPROVAL,
    APPROVED,
    SENT,
    REVISING,
    REJECTED,
    FAILED,
    DEAD_LETTER
}
