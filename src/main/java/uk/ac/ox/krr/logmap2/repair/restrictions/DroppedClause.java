package uk.ac.ox.krr.logmap2.repair.restrictions;

/**
 * A normalised clause or axiom the store did not use, with the reason. Every drop is a
 * sound omission: the theory handed to Dowling–Gallier is weaker, never wrong. The report
 * of these is what tells a reader how much of an ontology the repair sees.
 */
public record DroppedClause(Reason reason, String description) {

    public enum Reason {
        NOT_HORN("two or more heads (a union)"),
        INVERSE_PROPERTY("a restriction or inclusion over an inverse property"),
        DATA_AXIOM("a disjoint-data-properties axiom or a datatype definition"),
        UNSUPPORTED_DATA_RANGE("a data range other than a datatype or a facet restriction"),
        OTHER("another construct"),
        ENUMERATION("an enumeration of several individuals or literals"),
        ASSERTION("an assertion other than a type or a distinctness of individuals that occur in a hasValue"),
        PROPERTY_CHAIN("transitivity or a property chain"),
        PROPERTY_CHARACTERISTIC("asymmetry"),
        DISJOINT_PROPERTIES("disjoint properties"),
        KEY("a key"),
        COMPLEMENT_FILLER("a cardinality over a complemented filler"),
        UNKNOWN_ENTITY("an entity the index does not know");

        private final String explanation;

        Reason(String explanation) {
            this.explanation = explanation;
        }

        public String explanation() {
            return explanation;
        }
    }
}