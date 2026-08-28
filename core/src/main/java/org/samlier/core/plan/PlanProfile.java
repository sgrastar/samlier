package org.samlier.core.plan;

public enum PlanProfile {
    IDP_CORE(TargetRole.IDP, false),
    IDP_FULL(TargetRole.IDP, true),
    SP_CORE(TargetRole.SP, false),
    SP_FULL(TargetRole.SP, true);

    private final TargetRole role;
    private final boolean full;

    PlanProfile(TargetRole role, boolean full) {
        this.role = role;
        this.full = full;
    }

    public TargetRole role() { return role; }
    public boolean full() { return full; }
}
