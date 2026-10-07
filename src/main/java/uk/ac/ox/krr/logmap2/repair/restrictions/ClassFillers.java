package uk.ac.ox.krr.logmap2.repair.restrictions;

import uk.ac.ox.krr.logmap2.repair.hornSAT.Support;

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
    public Support subsumption(int sub, int sup) {
        if (store.isTop(sup)) {
            return Support.EMPTY;
        }
        return classes.supportOf(sub, sup);
    }

    @Override
    public Support disjointness(int first, int second) {
        return disjointness.supportOf(first, second);
    }
}