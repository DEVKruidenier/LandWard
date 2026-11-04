package io.github.devkruidenier.landward.model;

public record MemberPermissions(boolean build, boolean containers, boolean entities) {

    public static final MemberPermissions FULL = new MemberPermissions(true, true, true);
    public static final MemberPermissions NONE = new MemberPermissions(false, false, false);

    public MemberPermissions with(Permission permission, boolean enabled) {
        return switch (permission) {
            case BUILD -> new MemberPermissions(enabled, containers, entities);
            case CONTAINERS -> new MemberPermissions(build, enabled, entities);
            case ENTITIES -> new MemberPermissions(build, containers, enabled);
        };
    }

    public enum Permission {
        BUILD,
        CONTAINERS,
        ENTITIES
    }
}
