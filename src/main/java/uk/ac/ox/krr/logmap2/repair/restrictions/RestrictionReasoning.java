package uk.ac.ox.krr.logmap2.repair.restrictions;

import java.util.ArrayList;
import java.util.Collection;
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

    /** The proposition every propagation starts from besides the entity, so that clauses on TOP hold everywhere. */
    public int top() {
        return store.top();
    }

    /**
     * @param fixedMappings correspondences already repaired, both directions, never masked
     * @param mappingsUnderRepair correspondences under repair, both directions of an equivalence
     * @param removedDirections the mapping clauses earlier plans removed in this repair
     * @param propertyDirections the surviving directions of the object- and data-property correspondences under repair
     */
    public List<HornInclusion> clausesFor(Map<Integer, Set<Integer>> fixedMappings,
            Map<Integer, Set<Integer>> mappingsUnderRepair, Set<HornClause> removedDirections,
            Collection<CorrespondenceDirection> propertyDirections) {
                
        SupportedClosure classes = classClosure(fixedMappings, mappingsUnderRepair, removedDirections);
        SupportedClosure objectProperties = propertyClosure(PropertyKind.OBJECT, CorrespondenceDirection.Kind.OBJECT_PROPERTY, propertyDirections);
        SupportedClosure dataProperties = propertyClosure(PropertyKind.DATA, CorrespondenceDirection.Kind.DATA_PROPERTY, propertyDirections);
        FillerRelations classFillers = new ClassFillers(store, classes, new Disjointness(index, store, classes));
        FillerRelations datatypeFillers = new DatatypeFillers(store);

        List<HornInclusion> clauses = new ArrayList<>(store.inclusions());
        clauses.addAll(rulesOver(PropertyKind.OBJECT, objectProperties, classFillers));
        clauses.addAll(rulesOver(PropertyKind.DATA, dataProperties, datatypeFillers));
        return clauses;
    }

    /** The datatype relations over a store's data ranges; for tests, which cannot see the package-private types. */
    public static FillerRelations datatypeFillersOf(RestrictionStore store) {
        return new DatatypeFillers(store);
    }

    /** The links and clashes among the restrictions of one property kind. */
    private List<HornInclusion> rulesOver(PropertyKind kind, SupportedClosure properties, FillerRelations fillers) {
        Functionality functionality = new Functionality(store, kind, properties);
        List<HornInclusion> clauses = new ArrayList<>();
        clauses.addAll(new SubsumptionLinkRules(store, kind, properties, fillers).links());
        if (kind == PropertyKind.OBJECT) {
            clauses.addAll(new SelfRules(store, properties, functionality).memberships());
        }
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
                    classes.addCorrespondence(origin, target, CorrespondenceDirection.ofClasses(origin, target));
                }
            }
        }

        return classes;
    }

    /** The sub-property facts of one kind, and each surviving direction of that kind as a supported edge. */
    private SupportedClosure propertyClosure(PropertyKind kind, CorrespondenceDirection.Kind directionKind,
            Collection<CorrespondenceDirection> propertyDirections) {
        SupportedClosure properties = subPropertyFacts(kind);
        for (CorrespondenceDirection direction : propertyDirections) {
            if (direction.kind() == directionKind) {
                properties.addCorrespondence(direction.origin(), direction.target(), direction);
            }
        }
        return properties;
    }


    private SupportedClosure subPropertyFacts(PropertyKind kind) {
        SupportedClosure properties = new SupportedClosure();
        for (int subProperty : store.propertiesWithSuperProperties(kind)) {
            for (int superProperty : store.superPropertiesOf(kind, subProperty)) {
                properties.addFact(subProperty, superProperty);
            }
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