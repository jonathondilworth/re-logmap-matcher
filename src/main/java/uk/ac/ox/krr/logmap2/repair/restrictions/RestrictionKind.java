package uk.ac.ox.krr.logmap2.repair.restrictions;

/**
 * The kinds of restriction the repair reasons about: `∃R.C`, `∀R.C`, `≥n R.C`, `≤n R.C`
 * and `hasSelf(R)`. `some` is `≥1`, kept apart because it is by far the most common and
 * carries no number; `self` has no filler of its own.
 */
public enum RestrictionKind {
    
    SOME("some"),
    ONLY("only"),
    AT_LEAST("atLeast"),    // min
    AT_MOST("atMost"),      // max
    SELF("self");

    private final String displayName;

    RestrictionKind(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}