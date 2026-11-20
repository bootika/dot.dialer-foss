package ro.dotdesign.updates;

public interface UpdateHandle {
    void onResume();
    void onPause();
    void onDestroy();
    boolean onActivityResult(int requestCode, int resultCode);
}
