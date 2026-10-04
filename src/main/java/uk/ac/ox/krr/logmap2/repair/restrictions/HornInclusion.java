package uk.ac.ox.krr.logmap2.repair.restrictions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * One Horn clause read from a normalised ontology: the conjunction of the body
 * propositions implies the head proposition, or FALSE when there is no head. Propositions
 * are class identifiers, fresh classes, restrictions and TOP, all ints from the store.
 * Attachments (`A → ∃R.C`, `∃R.C → A`), fresh-class definitions, disjointness seeds
 * (`D ∧ ∃R.C → FALSE`) and global facts (`TOP → ∀R.E`) are all instances of this one form.
 */
public record HornInclusion(List<Integer> body, int head) {

    // the same sentinel Dowling–Gallier uses for its FALSE node
    public static final int FALSE = -2;

    public HornInclusion {
        List<Integer> sortedBody = new ArrayList<>(body);
        Collections.sort(sortedBody);
        body = Collections.unmodifiableList(sortedBody);
        if (body.isEmpty()) {
            throw new IllegalArgumentException("a clause needs at least one body proposition; head " + head);
        }
    }

    public static HornInclusion of(Collection<Integer> body, int head) {
        return new HornInclusion(new ArrayList<>(body), head);
    }

    public boolean isClash() {
        return head == FALSE;   // a FALSE-headed clause is a clash
    }
}