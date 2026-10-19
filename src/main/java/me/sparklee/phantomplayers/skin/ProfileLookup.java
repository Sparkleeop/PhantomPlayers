package me.sparklee.phantomplayers.skin;

public interface ProfileLookup {

    LookupOutcome lookup(String username) throws Exception;
}
