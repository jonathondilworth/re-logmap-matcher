package uk.ac.ox.krr.logmap2.repair.restrictions;

import uk.ac.ox.krr.logmap2.repair.hornSAT.Support;

/** Fillers of data-property restrictions: data ranges, related through the built-in datatypes, never through correspondences. */
final class DatatypeFillers implements FillerRelations {

    private final RestrictionStore store;

    DatatypeFillers(RestrictionStore store) {
        this.store = store;
    }

    @Override
    public Support subsumption(int sub, int sup) {
        if (store.isDataTop(sup)) {
            return Support.EMPTY;
        }
        if (store.isDataTop(sub)) {
            return null;
        }
        return Datatypes.contains(store.dataRange(sub), store.dataRange(sup)) ? Support.EMPTY : null;
    }

    @Override
    public Support disjointness(int first, int second) {
        if (store.isDataTop(first) || store.isDataTop(second)) {
            return null;
        }
        return Datatypes.disjoint(store.dataRange(first), store.dataRange(second)) ? Support.EMPTY : null;
    }
}