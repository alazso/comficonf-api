package com.comficonf.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** Outcome of validating proposed values: a list of errors and warnings. Immutable. */
public final class ValidationResult {

    private static final ValidationResult OK = new ValidationResult(List.of());

    private final List<ValidationIssue> issues;

    private ValidationResult(List<ValidationIssue> issues) {
        this.issues = issues;
    }

    /** Returns a result without issues. */
    public static ValidationResult ok() {
        return OK;
    }

    /** Returns a result containing the given issues. */
    public static ValidationResult of(Collection<ValidationIssue> issues) {
        Objects.requireNonNull(issues, "issues");
        return issues.isEmpty() ? OK : new ValidationResult(List.copyOf(issues));
    }

    /** Returns a result with a single error. */
    public static ValidationResult error(String path, String message) {
        return new ValidationResult(List.of(ValidationIssue.error(path, message)));
    }

    /** Starts a builder. */
    public static Builder builder() {
        return new Builder();
    }

    /** Returns all issues in the order they were added. */
    public List<ValidationIssue> issues() {
        return issues;
    }

    /** Returns the issues with severity {@link Severity#ERROR}. */
    public List<ValidationIssue> errors() {
        return issues.stream().filter(ValidationIssue::isError).toList();
    }

    /** Returns the issues with severity {@link Severity#WARNING}. */
    public List<ValidationIssue> warnings() {
        return issues.stream().filter(issue -> !issue.isError()).toList();
    }

    /** Returns whether there are no errors. Warnings do not make a result invalid. */
    public boolean isValid() {
        return issues.stream().noneMatch(ValidationIssue::isError);
    }

    /** Returns a result containing the issues of this result followed by those of {@code other}. */
    public ValidationResult merge(ValidationResult other) {
        Objects.requireNonNull(other, "other");
        if (other.issues.isEmpty()) {
            return this;
        }
        if (issues.isEmpty()) {
            return other;
        }
        List<ValidationIssue> merged = new ArrayList<>(issues);
        merged.addAll(other.issues);
        return new ValidationResult(List.copyOf(merged));
    }

    @Override
    public String toString() {
        return "ValidationResult" + issues;
    }

    /** Builder of {@link ValidationResult}. Not thread-safe. */
    public static final class Builder {
        private final List<ValidationIssue> issues = new ArrayList<>();

        private Builder() {
        }

        /** Adds an error with the plugin code. */
        public Builder error(String path, String message) {
            issues.add(ValidationIssue.error(path, message));
            return this;
        }

        /** Adds a warning with the plugin code. */
        public Builder warning(String path, String message) {
            issues.add(ValidationIssue.warning(path, message));
            return this;
        }

        /** Adds an issue. */
        public Builder issue(ValidationIssue issue) {
            issues.add(Objects.requireNonNull(issue, "issue"));
            return this;
        }

        /** Returns whether an error has been added. */
        public boolean hasErrors() {
            return issues.stream().anyMatch(ValidationIssue::isError);
        }

        public ValidationResult build() {
            return ValidationResult.of(issues);
        }
    }
}
