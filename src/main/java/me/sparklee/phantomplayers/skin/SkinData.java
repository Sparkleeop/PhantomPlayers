package me.sparklee.phantomplayers.skin;

public record SkinData(String value, String signature) {

    public static final SkinData EMPTY = new SkinData("", "");

    public SkinData {
        value = value == null ? "" : value.trim();
        signature = signature == null ? "" : signature.trim();
    }

    public boolean hasTexture() {
        return !value.isEmpty();
    }
}
