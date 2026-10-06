package uk.ac.ox.krr.logmap2.repair.restrictions;

/**
 * Whether a restriction, a property closure or a correspondence is over object
 * properties or over data properties. The two live in separate worlds: LogMap numbers
 * them from 0 each, a rule never pairs one with the other, and the fillers of one are
 * classes while the fillers of the other are data ranges. Design spec §3.6.
 */
public enum PropertyKind {
    OBJECT,
    DATA
}