package uk.ac.ox.krr.logmap2.repair.hornSAT;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A restriction clause inside Dowling–Gallier: a Horn inclusion whose support has been
 * resolved to the mapping clauses of the current build. It is a HornClause because
 * Dowling–Gallier stores every clause in one map and every blamed clause in one set.
 * The propagation skips its arcs while any support clause is masked, and blames its
 * MAP-origin supports when it fires.
 */
public final class RestrictionHornClause extends HornClause {

    private final List<Integer> body;
    private final int head;
    private final Set<HornClause> support;

    public RestrictionHornClause(HornInclusion inclusion, int label, Set<HornClause> support) {
        super(new HashSet<>(inclusion.body()), inclusion.head(), label, RESTRICTION, L2R);
        this.body = inclusion.body();
        this.head = inclusion.head();
        this.support = Collections.unmodifiableSet(new LinkedHashSet<>(support));
    }

    /**
     * Whether a support clause is masked, read from the two mask maps the propagation
     * already consults: a mapping clause is masked by its link under its origin
     * proposition, the origin being the left-hand side of an L2R clause and the
     * right-hand side of an R2L one (AnchorAssessment.addParticularIgnoreLink).
     */
    public boolean isMaskedBy(Map<Integer, Set<Link>> generalMasks, Map<Integer, Set<Link>> queryMasks) {
        for (HornClause supportClause : support) {
            int origin = supportClause.getDirImplication() == L2R ? supportClause.getLeftHS1() : supportClause.getRightHS();
            int target = supportClause.getDirImplication() == L2R ? supportClause.getRightHS() : supportClause.getLeftHS1();
            Link link = new Link(supportClause.getLabel(), target);

            if (contains(generalMasks, origin, link) || contains(queryMasks, origin, link)) {
                return true;
            }
        }
        return false;
    }

    /** The supports that are correspondences under repair, class or property; fixed ones are never blamed. */
    public Set<HornClause> blamedSupport() {
        Set<HornClause> blamed = new LinkedHashSet<>();
        for (HornClause supportClause : support) {
            if (supportClause.getOrigin() == MAP || supportClause.getOrigin() == PROPERTY_MAP) {
                blamed.add(supportClause);
            }
        }
        return blamed;
    }

    private static boolean contains(Map<Integer, Set<Link>> masks, int origin, Link link) {
        Set<Link> links = masks.get(origin);
        return links != null && links.contains(link);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof RestrictionHornClause that)) {
            return false;
        }
        return body.equals(that.body) && head == that.head && support.equals(that.support);
    }

    @Override
    public boolean equals(HornClause other) {
        return equals((Object) other);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * body.hashCode() + head) + support.hashCode();
    }

    @Override
    public String toString() {
        String supportedBy = support.isEmpty() ? "" : " supported by " + support;
        return body + " -> " + head + " (RESTRICTION) (" + getLabel() + ")" + supportedBy;
    }
}