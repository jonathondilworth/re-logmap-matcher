package uk.ac.ox.krr.logmap2.repair.restrictions;

/**
 * The four kinds of object-property restriction the repair reasons about:
 *   \exists R.C (some; >= 1)   \forall R.C (only)    \geq R.C     \leq R.C
 */
public enum RestrictionKind {
    
    SOME("some"),
    ONLY("only"),
    AT_LEAST("atLeast"),    // min
    AT_MOST("atMost");      // max

    private final String displayName;

    RestrictionKind(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}