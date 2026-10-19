package me.sparklee.phantomplayers.platform;

public interface Cancellable {

    Cancellable NOOP = new Cancellable() {
        @Override
        public void cancel() {
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    };

    void cancel();

    boolean isCancelled();
}
