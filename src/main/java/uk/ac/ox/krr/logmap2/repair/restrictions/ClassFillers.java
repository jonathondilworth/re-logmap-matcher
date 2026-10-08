package uk.ac.ox.krr.logmap2.repair.restrictions;


/** Fillers of object-property restrictions: classes, related through the closure and the index. */
final class ClassFillers implements FillerRelations {

    private final RestrictionStore store;
    private final SupportedClosure classes;
    private final Disjointness disjointness;

    ClassFillers(RestrictionStore store, SupportedClosure classes, Disjointness disjointness) {
        this.store = store;
        this.classes = classes;
        this.disjointness = disjointness;
    }

    @Override
    public AlternativeSupports subsumption(int sub, int sup) {
        if (store.isTop(sup)) {
            return AlternativeSupports.FACT;
        }
        return classes.supportsOf(sub, sup);
    }

    @Override
    public AlternativeSupports disjointness(int first, int second) {
        return disjointness.supportsOf(first, second);
    }
}