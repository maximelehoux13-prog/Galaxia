package com.gtnewhorizons.galaxia.registry.celestial;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.galaxia.registry.orbital.Hierarchy.OrbitalCelestialBody;

import javax.annotation.Nonnull;

@Desugar
public record CelestialHierarchy(
    Map<CelestialObjectId, OrbitalCelestialBody> bodiesById,
    Map<CelestialObjectId, List<OrbitalCelestialBody>> childrenByParentId,
    List<OrbitalCelestialBody> roots
) {

    public final class Builder {
        private Map<CelestialObjectId, OrbitalCelestialBody> bodiesById = new HashMap<>();
        private Map<CelestialObjectId, List<OrbitalCelestialBody>> childrenByParentId = new HashMap<>();
        private List<OrbitalCelestialBody> roots = new ArrayList<>();

        private Builder() {}

        public Builder builder() {
            return new Builder();
        }

        public Builder add(@Nonnull CelestialObjectRegistration registration) {
            if (registration.parentId() != null) {
                childrenByParentId.computeIfAbsent(registration.parentId(), k -> new ArrayList<>()).add(registration);
            } else {
                roots.add(buildBody(registration, bodiesById, childrenByParentId, childrenByParent));
            }

            return this;
        }
    }

    private static CelestialHierarchy INSTANCE;

    public static CelestialHierarchy getInstance() {
        return INSTANCE;
    }

    public static void build(List<CelestialObjectRegistration> registrations) {
        if (INSTANCE != null) {
            return;
        }

        Map<CelestialObjectId, OrbitalCelestialBody> bodiesById = new HashMap<>();
        Map<CelestialObjectId, List<OrbitalCelestialBody>> childrenByParentId = new HashMap<>();
        List<OrbitalCelestialBody> roots = new ArrayList<>();

        Map<CelestialObjectId, List<CelestialObjectRegistration>> childrenByParent = new HashMap<>();
        for (CelestialObjectRegistration reg : registrations) {
            if (reg.parentId() != null) {
                childrenByParent.computeIfAbsent(reg.parentId(), k -> new ArrayList<>()).add(reg);
            }
        }

        for (CelestialObjectRegistration reg : registrations) {
            if (reg.parentId() == null) {
                roots.add(buildBody(reg, bodiesById, childrenByParentId, childrenByParent));
            }
        }

        INSTANCE = new CelestialHierarchy(bodiesById, childrenByParentId, roots);
    }

    private static OrbitalCelestialBody buildBody(CelestialObjectRegistration registration,
        Map<CelestialObjectId, OrbitalCelestialBody> bodiesById,
        Map<CelestialObjectId, List<OrbitalCelestialBody>> childrenByParentId,
        Map<CelestialObjectId, List<CelestialObjectRegistration>> childrenByParent) {

        List<OrbitalCelestialBody> children = new ArrayList<>();
        List<CelestialObjectRegistration> childRegs = childrenByParent.get(registration.id());
        if (childRegs != null) {
            for (CelestialObjectRegistration childReg : childRegs) {
                children.add(buildBody(childReg, bodiesById, childrenByParentId, childrenByParent));
            }
        }

        CelestialObjectId id = registration.id();
        bodiesById.put(id, null);
        OrbitalCelestialBody body = new OrbitalCelestialBody(registration, children);
        bodiesById.put(id, body);

        CelestialObjectId parentId = registration.parentId();
        if (parentId != null) {
            childrenByParentId.computeIfAbsent(parentId, k -> new ArrayList<>()).add(body);
        }

        return body;
    }

    public Optional<OrbitalCelestialBody> findById(CelestialObjectId id) {
        return Optional.ofNullable(bodiesById.get(id));
    }

    public List<OrbitalCelestialBody> getChildren(CelestialObjectId parentId) {
        return childrenByParentId.getOrDefault(parentId, Collections.emptyList());
    }

    public List<OrbitalCelestialBody> getRoots() {
        return roots;
    }

    public Map<CelestialObjectId, OrbitalCelestialBody> getAllBodies() {
        return bodiesById;
    }

    public Map<CelestialObjectId, List<OrbitalCelestialBody>> getAllChildren() {
        return childrenByParentId;
    }
}
