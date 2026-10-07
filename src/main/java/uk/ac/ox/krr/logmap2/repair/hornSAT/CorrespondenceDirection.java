package uk.ac.ox.krr.logmap2.repair.hornSAT;

/**
 * One direction of a correspondence under repair, `origin ⊑ target`, between two class
 * identifiers, two object-property identifiers or two data-property identifiers; the kind
 * tells them apart, since LogMap numbers each of the three from 0. A class direction is what a
 * mapping clause stands for; a property direction is what a property-direction clause
 * stands for; both are what a plan masks. A derived clause that holds only because of a
 * direction names it in its support.
 */
public record CorrespondenceDirection(Kind kind, int origin, int target) implements Comparable<CorrespondenceDirection> {

    public enum Kind {
        CLASS,
        OBJECT_PROPERTY,
        DATA_PROPERTY
    }

    public static CorrespondenceDirection ofClasses(int origin, int target) {
        return new CorrespondenceDirection(Kind.CLASS, origin, target);
    }

    public static CorrespondenceDirection ofObjectProperties(int origin, int target) {
        return new CorrespondenceDirection(Kind.OBJECT_PROPERTY, origin, target);
    }

    public static CorrespondenceDirection ofDataProperties(int origin, int target) {
        return new CorrespondenceDirection(Kind.DATA_PROPERTY, origin, target);
    }

    public boolean isProperty() {
        return kind != Kind.CLASS;
    }

    @Override
    public int compareTo(CorrespondenceDirection other) {
        if (kind != other.kind) {
            return kind.compareTo(other.kind);
        }
        if (origin != other.origin) {
            return Integer.compare(origin, other.origin);
        }
        return Integer.compare(target, other.target);
    }

    @Override
    public String toString() {
        return (kind == Kind.CLASS ? "" : kind == Kind.OBJECT_PROPERTY ? "property " : "data property ") + origin + " -> " + target;
    }
}