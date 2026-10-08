package uk.ac.ox.krr.logmap2.repair.restrictions;


/** Fillers of data-property restrictions: data ranges, related through the built-in datatypes, never through correspondences. */
final class DatatypeFillers implements FillerRelations {

    private final RestrictionStore store;

    DatatypeFillers(RestrictionStore store) {
        this.store = store;
    }

    @Override
    public AlternativeSupports subsumption(int sub, int sup) {
        if (store.isDataTop(sup)) {
            return AlternativeSupports.FACT;
        }
        if (store.isDataTop(sub)) {
            return AlternativeSupports.NONE;
        }
        return Datatypes.contains(store.dataRange(sub), store.dataRange(sup)) ? AlternativeSupports.FACT : AlternativeSupports.NONE;
    }

    @Override
    public AlternativeSupports disjointness(int first, int second) {
        if (store.isDataTop(first) || store.isDataTop(second)) {
            return AlternativeSupports.NONE;
        }
        return Datatypes.disjoint(store.dataRange(first), store.dataRange(second)) ? AlternativeSupports.FACT : AlternativeSupports.NONE;
    }
}