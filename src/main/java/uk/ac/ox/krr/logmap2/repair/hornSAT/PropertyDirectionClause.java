package uk.ac.ox.krr.logmap2.repair.hornSAT;

/**
 * One surviving direction of an object-property correspondence inside Dowling–Gallier:
 * `source ⊑ target` as a clause with no arc. It exists so that a plan can mask it and a
 * supported restriction clause can name it in its support and blame it; the plan search
 * then treats a property direction like a class direction, and applying it weakens or
 * deletes the correspondence. Equality is by the two properties and never against a
 * plain clause, because property and class identifiers overlap. Design spec §8.8 item 2.
 */
public final class PropertyDirectionClause extends HornClause {

    private final int source;
    private final int target;

    public PropertyDirectionClause(int source, int target, int label) {
        super(source, target, label, PROPERTY_MAP, L2R);
        this.source = source;
        this.target = target;
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
        return source == that.source && target == that.target;
    }

    @Override
    public boolean equals(HornClause other) {
        return equals((Object) other);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * 7 + source) + target;
    }

    @Override
    public String toString() {
        return "property " + source + " -> " + target + " (PROPERTY_MAP) (" + getLabel() + ")";
    }
}