package gov.nasa.jpl.ammos.mpsa.aerie.merlinsdk.constraints;

import gov.nasa.jpl.ammos.mpsa.aerie.merlinsdk.time.Windows;

import java.util.Set;

public final class ConstraintViolation {
    public final String id;
    public final String name;
    public final String message;
    public final String category;
    public final Set<String> associatedActivityIds;
    public final Set<String> associatedStateIds;
    public final Windows violationWindows;

    public ConstraintViolation(Windows violationWindows, ViolableConstraint violableConstraint) {
        this.violationWindows = new Windows(violationWindows);
        this.id = violableConstraint.id;
        this.name = violableConstraint.name;
        this.message = violableConstraint.message;
        this.category = violableConstraint.category;
        this.associatedActivityIds = Set.copyOf(violableConstraint.getActivityIds());
        this.associatedStateIds = Set.copyOf(violableConstraint.getStateIds());
    }

    @Override
    public String toString() {
        return "ConstraintViolation { " +
            "id='" + this.id + "'" +
            ", name='" + this.name + "'" +
            ", message='" + this.message + "'" +
            ", category='" + this.category + "'" +
            ", associatedActivityIds=" + this.associatedActivityIds +
            ", associatedStateIds=" + this.associatedStateIds +
            ", violationWindows=" + this.violationWindows +
            " }";
    }
}
