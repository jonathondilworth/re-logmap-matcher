package uk.ac.ox.krr.logmap2.repair.restrictions;

import uk.ac.ox.krr.logmap2.repair.hornSAT.Support;

/**
 * What the S- and D-rules ask of two fillers: is one below the other, are they disjoint,
 * and under which correspondences? Object-property restrictions answer from the class
 * closure and the index's disjointness ({@link ClassFillers}); data-property
 * restrictions from the built-in datatypes ({@link DatatypeFillers}). A null answer means
 * "not known", which the rules read as "no". Design spec §6.1 and §6.4.
 */
public interface FillerRelations {

    /** The minimal support under which `sub ⊑ sup`, or null. */
    Support subsumption(int sub, int sup);

    /** The minimal support under which the two fillers are disjoint, or null. */
    Support disjointness(int first, int second);
}