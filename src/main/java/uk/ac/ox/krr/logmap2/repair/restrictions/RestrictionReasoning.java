package uk.ac.ox.krr.logmap2.repair.restrictions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import uk.ac.ox.krr.logmap2.indexing.IndexManager;
import uk.ac.ox.krr.logmap2.repair.hornSAT.CorrespondenceDirection;
import uk.ac.ox.krr.logmap2.repair.hornSAT.HornClause;
import uk.ac.ox.krr.logmap2.repair.hornSAT.HornInclusion;

/**
 * The restriction clauses of one Dowling–Gallier build: the store's own clauses plus the
 * links and clashes derived from the current correspondences. Rebuilt at every build, so a
 * correspondence removed by an earlier plan no longer supports anything.
 */
public final class RestrictionReasoning {

    private final IndexManager index;
    private final RestrictionStore store;

    public RestrictionReasoning(IndexManager index) {
        this.index = index;
        this.store = index.getRestrictionStore();
    }

    /**
     * @param fixedMappings correspondences already repaired, both directions, never masked
     * @param mappingsUnderRepair correspondences under repair, both directions of an equivalence
     * @param removedDirections the mapping clauses earlier plans removed in this repair
     * @param propertyCorrespondences object-property correspondences, source to target
     */
    public List<HornInclusion> clausesFor(Map<Integer, Set<Integer>> fixedMappings,
            Map<Integer, Set<Integer>> mappingsUnderRepair, Set<HornClause> removedDirections,
            Map<Integer, Integer> propertyCorrespondences) {
        SupportedClosure classes = classClosure(fixedMappings, mappingsUnderRepair, removedDirections);
        SupportedClosure properties = propertyClosure(propertyCorrespondences);
        Disjointness disjointness = new Disjointness(index, classes, store.top());

        List<HornInclusion> clauses = new ArrayList<>(store.inclusions());
        clauses.addAll(new SubsumptionLinkRules(store, classes, properties).links());
        clauses.addAll(new ClashRules(store, classes, properties, disjointness).clashes());
        return clauses;
    }

    private SupportedClosure classClosure(Map<Integer, Set<Integer>> fixedMappings,
            Map<Integer, Set<Integer>> mappingsUnderRepair, Set<HornClause> removedDirections) {
        SupportedClosure classes = new SupportedClosure();

        for (Map.Entry<Integer, Set<Integer>> parentAndChildren : index.getDirectSubClasses(false).entrySet()) {
            for (int child : parentAndChildren.getValue()) {
                classes.addFact(child, parentAndChildren.getKey());
            }
        }
        for (Map.Entry<Integer, Set<Integer>> classAndEquivalents : index.getEquivalentClasses().entrySet()) {
            for (int equivalent : classAndEquivalents.getValue()) {
                classes.addFact(classAndEquivalents.getKey(), equivalent);
            }
        }
        for (HornInclusion inclusion : store.inclusions()) {
            if (isClassInclusion(inclusion)) {
                classes.addFact(inclusion.body().get(0), inclusion.head());
            }
        }
        for (Map.Entry<Integer, Set<Integer>> originAndTargets : fixedMappings.entrySet()) {
            for (int target : originAndTargets.getValue()) {
                classes.addFact(originAndTargets.getKey(), target);
            }
        }
        for (Map.Entry<Integer, Set<Integer>> originAndTargets : mappingsUnderRepair.entrySet()) {
            int origin = originAndTargets.getKey();
            for (int target : originAndTargets.getValue()) {
                if (!removedDirections.contains(mappingClauseOf(origin, target))) {
                    classes.addCorrespondence(origin, target, new CorrespondenceDirection(origin, target));
                }
            }
        }

        return classes;
    }

    /** At this step property correspondences are facts: neither masked nor blamed. */
    private SupportedClosure propertyClosure(Map<Integer, Integer> propertyCorrespondences) {
        SupportedClosure properties = new SupportedClosure();

        for (int subProperty : store.propertiesWithSuperProperties()) {
            for (int superProperty : store.superPropertiesOf(subProperty)) {
                properties.addFact(subProperty, superProperty);
            }
        }
        for (Map.Entry<Integer, Integer> sourceAndTarget : propertyCorrespondences.entrySet()) {
            properties.addFact(sourceAndTarget.getKey(), sourceAndTarget.getValue());
            properties.addFact(sourceAndTarget.getValue(), sourceAndTarget.getKey());
        }

        return properties;
    }

    /** A clause `X → Y` between two class propositions (a fresh-class definition, mostly). */
    private boolean isClassInclusion(HornInclusion inclusion) {
        if (inclusion.body().size() != 1 || inclusion.isClash()) {
            return false;
        }
        int body = inclusion.body().get(0);
        return !store.isRestriction(body) && !store.isRestriction(inclusion.head()) && !store.isTop(body);
    }

    /**
     * The mapping clause Dowling–Gallier builds for a direction (addMappingClauses1N): L2R
     * from the smaller identifier, R2L otherwise; equality ignores the label.
     */
    private static HornClause mappingClauseOf(int origin, int target) {
        if (origin < target) {
            return new HornClause(origin, target, 0, HornClause.MAP, HornClause.L2R);
        }
        return new HornClause(target, origin, 0, HornClause.MAP, HornClause.R2L);
    }
}