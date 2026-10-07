package uk.ac.ox.krr.logmap2.repair.hornSAT;

/**
 * One surviving direction of an object- or data-property correspondence inside
 * Dowling–Gallier: `source ⊑ target` as a clause with no arc. It exists so that a plan can mask it and a
 * supported restriction clause can name it in its support and blame it; the plan search
 * then treats a property direction like a class direction, and applying it weakens or
 * deletes the correspondence. Equality is by the kind and the two properties and never
 * against a plain clause, because the identifier spaces overlap.
 */
public final class PropertyDirectionClause extends HornClause {

    private final CorrespondenceDirection.Kind kind;
    private final int source;
    private final int target;

    public PropertyDirectionClause(CorrespondenceDirection.Kind kind, int source, int target, int label) {
        super(source, target, label, PROPERTY_MAP, L2R);
        this.kind = kind;
        this.source = source;
        this.target = target;
    }

    public CorrespondenceDirection.Kind kind() {
        return kind;
    }

    public boolean isDataProperty() {
        return kind == CorrespondenceDirection.Kind.DATA_PROPERTY;
    }

    public int source() {
        return source;
    }

    public int target() {
        return target;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof PropertyDirectionClause that)) {
            return false;
        }
        return kind == that.kind && source == that.source && target == that.target;
    }

    @Override
    public boolean equals(HornClause other) {
        return equals((Object) other);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * (31 * 7 + kind.ordinal()) + source) + target;
    }

    @Override
    public String toString() {
        return (isDataProperty() ? "data property " : "property ") + source + " -> " + target + " (PROPERTY_MAP) (" + getLabel() + ")";
    }
}