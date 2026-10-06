package uk.ac.ox.krr.logmap2.repair.restrictions;

/**
 * One restriction as it occurs in a normalised ontology, in LogMap's int vocabulary: the
 * property is an object- or data-property identifier (the kind says which, since the two
 * are numbered separately) and the filler is a proposition of the store (a named class
 * identifier, a fresh class or TOP for an object property; a data range or the data top
 * for a data property). Two occurrences with the same components are the same
 * restriction, which is what lets the store give each restriction one proposition.
 */


/**
 * An (normalised) object-property restriction
 * Restriction
 * @param kind
 * @param property
 * @param cardinality
 * @param filler
 */
public record Restriction(RestrictionKind kind, PropertyKind propertyKind, int property, int cardinality, int filler) {

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
        return some(PropertyKind.OBJECT, property, filler);
    }

    public static Restriction some(PropertyKind propertyKind, int property, int filler) {
        return new Restriction(RestrictionKind.SOME, propertyKind, property, 1, filler);
    }

    public static Restriction only(int property, int filler) {
        return only(PropertyKind.OBJECT, property, filler);
    }

    public static Restriction only(PropertyKind propertyKind, int property, int filler) {
        return new Restriction(RestrictionKind.ONLY, propertyKind, property, 0, filler);
    }

    /** `≥n R.C`; `n = 1` is {@link #some}. */
    public static Restriction atLeast(int cardinality, int property, int filler) {
        return atLeast(PropertyKind.OBJECT, cardinality, property, filler);
    }

    public static Restriction atLeast(PropertyKind propertyKind, int cardinality, int property, int filler) {
        if (cardinality == 1) {
            return some(propertyKind, property, filler);
        }
        return new Restriction(RestrictionKind.AT_LEAST, propertyKind, property, cardinality, filler);
    }

    public static Restriction atMost(int cardinality, int property, int filler) {
        return atMost(PropertyKind.OBJECT, cardinality, property, filler);
    }

    public static Restriction atMost(PropertyKind propertyKind, int cardinality, int property, int filler) {
        return new Restriction(RestrictionKind.AT_MOST, propertyKind, property, cardinality, filler);
    }

    public boolean isExistential() {
        return kind == RestrictionKind.SOME || kind == RestrictionKind.AT_LEAST;
    }

    public boolean isUniversal() {
        return kind == RestrictionKind.ONLY;
    }
}