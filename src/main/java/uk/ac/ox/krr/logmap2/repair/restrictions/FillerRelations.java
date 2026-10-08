package uk.ac.ox.krr.logmap2.repair.restrictions;

/**
 * What the S- and D-rules ask of two fillers: is one below the other, are they disjoint,
 * and under which correspondences? Object-property restrictions answer from the class
 * closure and the index's disjointness ({@link ClassFillers}); data-property
 * restrictions from the built-in datatypes ({@link DatatypeFillers}). No support means
 * "not known", which the rules read as "no". Design spec §6.1 and §6.4.
 */
public interface FillerRelations {

    /** The minimal supports under which `sub ⊑ sup`. */
    AlternativeSupports subsumption(int sub, int sup);

    /** The minimal supports under which the two fillers are disjoint. */
    AlternativeSupports disjointness(int first, int second);
}