package uk.ac.ox.krr.logmap2.repair.restrictions;

/**
 * One restriction as it occurs in a normalised ontology, in LogMap's int vocabulary: the
 * property is an object- or data-property identifier (the kind says which, since the two
* are numbered separately), `incoming` says the restriction reads through the inverse of
* that property (`∃R⁻.C`; §3.1a), and the filler is a proposition of the store (a named class
 * identifier, a fresh class, a nominal or TOP for an object property; a data range or the
 * data top for a data property; TOP for a self-edge, which has no filler of its own). Two
 * occurrences with the same components are the same
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
public record Restriction(RestrictionKind kind, PropertyKind propertyKind, int property, boolean incoming, int cardinality, int filler) {

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
        if (kind == RestrictionKind.SELF && (propertyKind != PropertyKind.OBJECT || cardinality != 0)) {
            throw new IllegalArgumentException("self is a self-edge over an object property and carries no cardinality");
        }
        if (incoming && (propertyKind != PropertyKind.OBJECT || kind == RestrictionKind.SELF)) {
            throw new IllegalArgumentException("only an object-property restriction reads through an inverse, and a self-edge holds both ways");
        }
    }

    public static Restriction some(int property, int filler) {
        return some(PropertyKind.OBJECT, property, filler);
    }

    public static Restriction some(int property, boolean incoming, int filler) {
        return new Restriction(RestrictionKind.SOME, PropertyKind.OBJECT, property, incoming, 1, filler);
    }

     public static Restriction some(PropertyKind propertyKind, int property, int filler) {
        return new Restriction(RestrictionKind.SOME, propertyKind, property, false, 1, filler);
     }
 
     public static Restriction only(int property, int filler) {
         return only(PropertyKind.OBJECT, property, filler);
     }
 
    public static Restriction only(int property, boolean incoming, int filler) {
        return new Restriction(RestrictionKind.ONLY, PropertyKind.OBJECT, property, incoming, 0, filler);
    }

     public static Restriction only(PropertyKind propertyKind, int property, int filler) {
        return new Restriction(RestrictionKind.ONLY, propertyKind, property, false, 0, filler);
     }
 
     /** `≥n R.C`; `n = 1` is {@link #some}. */
    public static Restriction atLeast(int cardinality, int property, int filler) {
        return atLeast(PropertyKind.OBJECT, cardinality, property, filler);
    }

    public static Restriction atLeast(int cardinality, int property, boolean incoming, int filler) {
        if (cardinality == 1) {
            return some(property, incoming, filler);
        }
        return new Restriction(RestrictionKind.AT_LEAST, PropertyKind.OBJECT, property, incoming, cardinality, filler);
    }


    public static Restriction atLeast(PropertyKind propertyKind, int cardinality, int property, int filler) {
        if (cardinality == 1) {
            return some(propertyKind, property, filler);
        }
        return new Restriction(RestrictionKind.AT_LEAST, propertyKind, property, false, cardinality, filler);
    }

    public static Restriction atMost(int cardinality, int property, int filler) {
        return atMost(PropertyKind.OBJECT, cardinality, property, filler);
    }

    public static Restriction atMost(int cardinality, int property, boolean incoming, int filler) {
        return new Restriction(RestrictionKind.AT_MOST, PropertyKind.OBJECT, property, incoming, cardinality, filler);
    }

    public static Restriction atMost(PropertyKind propertyKind, int cardinality, int property, int filler) {
        return new Restriction(RestrictionKind.AT_MOST, propertyKind, property, false, cardinality, filler);
    }

    /** `hasSelf(R)`, a self-edge; TOP stands in for the filler it does not have. */
    public static Restriction self(int property, int top) {
        return new Restriction(RestrictionKind.SELF, PropertyKind.OBJECT, property, false, 0, top);
    }

    /** The property as the closure's node: `2R`, or `2R + 1` when the restriction reads through `R⁻`. */
    public int propertyToken() {
        return SignedProperties.token(property, incoming);
    }

    public boolean isExistential() {
        return kind == RestrictionKind.SOME || kind == RestrictionKind.AT_LEAST;
    }

    public boolean isUniversal() {
        return kind == RestrictionKind.ONLY;
    }
}