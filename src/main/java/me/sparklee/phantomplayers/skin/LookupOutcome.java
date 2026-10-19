package me.sparklee.phantomplayers.skin;

public record LookupOutcome(LookupStatus status, SkinData skin, String detail) {

    public LookupOutcome {
        skin = skin == null ? SkinData.EMPTY : skin;
        detail = detail == null ? "" : detail;
    }

    public static LookupOutcome found(SkinData skin) {
        return new LookupOutcome(LookupStatus.FOUND, skin, "");
    }

    public static LookupOutcome notFound(String detail) {
        return new LookupOutcome(LookupStatus.NOT_FOUND, SkinData.EMPTY, detail);
    }

    public static LookupOutcome error(String detail) {
        return new LookupOutcome(LookupStatus.ERROR, SkinData.EMPTY, detail);
    }
}
