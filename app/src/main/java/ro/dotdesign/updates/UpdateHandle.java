package ro.dotdesign.updates;

public interface UpdateHandle {
    default void showAboutUpdate(int target, String version, Runnable openListing) {}
    default void showAboutUpToDate(String message) {}
    default void dismissAboutUpdate() {}

    void onResume();
    void onPause();
    void onDestroy();
    boolean onActivityResult(int requestCode, int resultCode);
}
