package uk.ac.ox.krr.logmap2.repair.restrictions;

/**
 * One object-property restriction as it occurs in a normalised ontology, in LogMap's int
 * vocabulary: the property is an object-property identifier and the filler is a class
 * proposition (a named class identifier, a fresh dummy class or the store's TOP). Two
 * occurrences with the same components are the same restriction, which is what lets the
 * store give each restriction one proposition.
 */


/**
 * An (normalised) object-property restriction
 * Restriction
 * @param kind
 * @param property
 * @param cardinality
 * @param filler
 */
public record Restriction(RestrictionKind kind, int property, int cardinality, int filler) {

    public Restriction {
        if (kind == RestrictionKind.SOME && cardinality != 1) {
            throw new IllegalArgumentException("some is at-least-one; cardinality " + cardinality);
        }
        if (kind == RestrictionKind.ONLY && cardinality != 0) {
            throw new IllegalArgumentException("only carries no cardinality; got " + cardinality);
        }
        if (kind == RestrictionKind.AT_LEAST && cardinality < 2) {
            throw new IllegalArgumentException("at-least below 2 is some or trivial; got " + cardinality);
        }
        if (kind == RestrictionKind.AT_MOST && cardinality < 1) {
            throw new IllegalArgumentException("at-most 0 is read as the absence of a successor; got " + cardinality);
        }
    }

    public static Restriction some(int property, int filler) {
        return new Restriction(RestrictionKind.SOME, property, 1, filler);
    }

    public static Restriction only(int property, int filler) {
        return new Restriction(RestrictionKind.ONLY, property, 0, filler);
    }

    /** `≥n R.C`; `n = 1` is {@link #some}. */
    public static Restriction atLeast(int cardinality, int property, int filler) {
        if (cardinality == 1) {
            return some(property, filler); // some
        }
        return new Restriction(RestrictionKind.AT_LEAST, property, cardinality, filler);
    }

    public static Restriction atMost(int cardinality, int property, int filler) {
        return new Restriction(RestrictionKind.AT_MOST, property, cardinality, filler);
    }

    public boolean isExistential() {
        return kind == RestrictionKind.SOME || kind == RestrictionKind.AT_LEAST;
    }

    public boolean isUniversal() {
        return kind == RestrictionKind.ONLY;
    }
}