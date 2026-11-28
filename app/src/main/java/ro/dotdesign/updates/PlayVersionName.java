package ro.dotdesign.updates;

/** Dialer Play builds encode the three version components in the build code. */
public final class PlayVersionName {
    private PlayVersionName() {}

    public static String fromOffer(int code) {
        return (code / 100) + "." + (code / 10 % 10) + "." + (code % 10);
    }
}
