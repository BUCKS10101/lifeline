package com.personalos.backend.tasks;

/** How much a task matters to its owner. Only used for ordering and a quiet marker; never a deadline. */
public enum Priority {
    LOW, NORMAL, HIGH
}
