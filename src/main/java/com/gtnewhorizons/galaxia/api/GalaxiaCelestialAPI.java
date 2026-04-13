package com.gtnewhorizons.galaxia.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialHierarchy;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectClass;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;

public final class GalaxiaCelestialAPI {

    private GalaxiaCelestialAPI() {}

    public static void register(CelestialObject registration) {
        CelestialRegistry.register(registration);
    }

    public static boolean isFrozen() {
        return CelestialRegistry.isFrozen();
    }

    public static Optional<CelestialObject> get(CelestialObjectId id) {
        return CelestialRegistry.get(id);
    }

    public static Optional<CelestialObject> get(String id) {
        CelestialObjectId enumId = CelestialObjectId.fromString(id);
        return enumId != null ? CelestialRegistry.get(enumId) : Optional.empty();
    }

    public static List<CelestialObject> getAll() {
        return CelestialRegistry.getAll();
    }

    public static List<CelestialObject> getRoots() {
        return CelestialRegistry.getRoots();
    }

    public static CelestialObject getPrimaryRoot() {
        return CelestialRegistry.getPrimaryRoot();
    }

    public static Optional<CelestialObject> findByDimension(DimensionEnum dimension) {
        return CelestialRegistry.findByDimension(dimension);
    }

    public static CelestialHierarchy getHierarchy() {
        return CelestialRegistry.hierarchy;
    }

    public static Optional<CelestialObject> findBodyById(CelestialObjectId id) {
        return CelestialRegistry.findById(id);
    }

    public static List<CelestialObject> getChildren(CelestialObject parent) {
        return CelestialRegistry.hierarchy.childrenByParentId()
            .getOrDefault(parent.id(), Collections.emptyList());
    }

    public static List<CelestialObject> getChildren(CelestialObjectId parentId) {
        return CelestialRegistry.hierarchy.childrenByParentId()
            .getOrDefault(parentId, Collections.emptyList());
    }

    public static Map<CelestialObjectId, CelestialObject> getAllBodies() {
        return CelestialRegistry.hierarchy.bodiesById();
    }

    public static CelestialObject root() {
        return CelestialRegistry.getPrimaryRoot();
    }

    public static Optional<CelestialObject> findCurrentStar(int dimensionId) {
        for (DimensionEnum dim : DimensionEnum.values()) {
            if (dim.getId() == dimensionId) {
                return findCurrentStar(dim);
            }
        }
        return getPrimaryStar();
    }

    public static Optional<CelestialObject> findCurrentStar(DimensionEnum dim) {
        return findByDimension(dim).flatMap(body -> findAncestorOfClass(root(), body, CelestialObjectClass.STAR));
    }

    public static Optional<CelestialObject> getPrimaryStar() {
        return findFirstByClass(root(), CelestialObjectClass.STAR);
    }

    private static Optional<CelestialObject> findAncestorOfClass(CelestialObject current, CelestialObject target,
        CelestialObjectClass objectClass) {
        return findAncestorOfClass(current, target, objectClass, new ArrayList<>());
    }

    private static Optional<CelestialObject> findAncestorOfClass(CelestialObject current, CelestialObject target,
        CelestialObjectClass objectClass, List<CelestialObject> ancestors) {
        if (current == target) {
            for (int i = ancestors.size() - 1; i >= 0; i--) {
                CelestialObject ancestor = ancestors.get(i);
                if (ancestor.objectClass() == objectClass) {
                    return Optional.of(ancestor);
                }
            }
            return Optional.empty();
        }

        for (CelestialObject child : getChildren(current)) {
            ArrayList<CelestialObject> nextAncestors = new ArrayList<>(ancestors);
            nextAncestors.add(current);
            Optional<CelestialObject> found = findAncestorOfClass(child, target, objectClass, nextAncestors);
            if (found.isPresent()) {
                return found;
            }
        }

        return Optional.empty();
    }

    private static Optional<CelestialObject> findFirstByClass(CelestialObject current,
        CelestialObjectClass objectClass) {
        if (current.objectClass() == objectClass) {
            return Optional.of(current);
        }
        for (CelestialObject child : getChildren(current)) {
            Optional<CelestialObject> found = findFirstByClass(child, objectClass);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }
}