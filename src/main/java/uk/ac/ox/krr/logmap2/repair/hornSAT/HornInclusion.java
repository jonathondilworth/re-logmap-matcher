package uk.ac.ox.krr.logmap2.repair.hornSAT;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

/**
 * One Horn clause over propositions: the conjunction of the body implies the head, or
 * FALSE when there is no head, together with the support it holds under (the
 * correspondence directions it depends on; empty for a clause of the ontologies alone).
 * Propositions are class identifiers, fresh classes, restrictions and TOP, all ints from
 * the restriction store. Attachments (`A → ∃R.C`), fresh-class definitions, clash seeds
 * (`D ∧ ∃R.C → FALSE`), global facts (`TOP → ∀R.E`) and derived links (`r1 → r2` under a
 * support) are all instances of this one form.
 */
public record HornInclusion(List<Integer> body, int head, Support support) {

    /** The same sentinel Dowling–Gallier uses for its FALSE node. */
    public static final int FALSE = -2;

    public HornInclusion {
        // Sorted and without repeats: an atom listed twice would give Dowling-Gallier two
        // identical arcs, which its arc set collapses, and the clause would never fire.
        body = Collections.unmodifiableList(new ArrayList<>(new TreeSet<>(body)));
        if (body.isEmpty()) {
            throw new IllegalArgumentException("a clause needs at least one body proposition; head " + head);
        }
    }

    /** A clause of the ontologies alone. */
    public static HornInclusion of(Collection<Integer> body, int head) {
        return new HornInclusion(new ArrayList<>(body), head, Support.EMPTY);
    }

    public static HornInclusion of(Collection<Integer> body, int head, Support support) {
        return new HornInclusion(new ArrayList<>(body), head, support);
    }

    public boolean isClash() {
        return head == FALSE;   // a FALSE-headed clause is a clash
    }

}