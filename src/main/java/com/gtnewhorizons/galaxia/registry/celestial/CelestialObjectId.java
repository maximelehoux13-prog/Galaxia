package com.gtnewhorizons.galaxia.registry.celestial;

public enum CelestialObjectId {

    NOVA_CAELUM("novum_caelum"),
    VAEL("vael"),
    ILIA("ilia"),
    PROXIMA_CENTAURI("proxima_centauri"),
    ROMULUS("romulus"),
    REMUS("remus"),
    EGORA("egora"),
    PAN_SPRIA("panspira"),
    HEMATERIA("hemateria"),
    THEIA("theia"),
    FROZEN_BELT("frozen_belt"),
    AMBERGRIS_FRAGMENT("ambergris_fragment"),
    VITRIS_SPACE("vitris_space");

    private final String id;

    CelestialObjectId(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static CelestialObjectId fromString(String id) {
        for (CelestialObjectId value : values()) {
            if (value.id.equals(id)) {
                return value;
            }
        }
        return null;
    }
}
