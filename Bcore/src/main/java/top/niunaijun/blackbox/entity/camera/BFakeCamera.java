package top.niunaijun.blackbox.entity.camera;

import android.os.Parcel;
import android.os.Parcelable;


public class BFakeCamera implements Parcelable {
    public static final int DISABLED = 0;
    public static final int LOCAL_VIDEO = 1;
    public static final int NETWORK_STREAM = 2;
    public static final int LOCAL_IMAGE = 3;

    private int mMode = DISABLED;
    private String mSourcePath = "";
    private int mWidth = 1280;
    private int mHeight = 720;
    private boolean mAudioEnabled = false;

    public BFakeCamera() {
    }

    public BFakeCamera(int mode, String sourcePath) {
        this.mMode = mode;
        this.mSourcePath = sourcePath;
    }

    public BFakeCamera(Parcel in) {
        readFromParcel(in);
    }

    public int getMode() {
        return mMode;
    }

    public void setMode(int mode) {
        this.mMode = mode;
    }

    public String getSourcePath() {
        return mSourcePath;
    }

    public void setSourcePath(String sourcePath) {
        this.mSourcePath = sourcePath;
    }

    public int getWidth() {
        return mWidth;
    }

    public void setWidth(int width) {
        this.mWidth = width;
    }

    public int getHeight() {
        return mHeight;
    }

    public void setHeight(int height) {
        this.mHeight = height;
    }

    public boolean isAudioEnabled() {
        return mAudioEnabled;
    }

    public void setAudioEnabled(boolean audioEnabled) {
        this.mAudioEnabled = audioEnabled;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.mMode);
        dest.writeString(this.mSourcePath);
        dest.writeInt(this.mWidth);
        dest.writeInt(this.mHeight);
        dest.writeInt(this.mAudioEnabled ? 1 : 0);
    }

    public void readFromParcel(Parcel in) {
        this.mMode = in.readInt();
        this.mSourcePath = in.readString();
        this.mWidth = in.readInt();
        this.mHeight = in.readInt();
        this.mAudioEnabled = in.readInt() == 1;
    }

    public boolean isEmpty() {
        return mMode == DISABLED || (mSourcePath == null || mSourcePath.isEmpty());
    }

    public static final Parcelable.Creator<BFakeCamera> CREATOR = new Parcelable.Creator<BFakeCamera>() {
        @Override
        public BFakeCamera createFromParcel(Parcel source) {
            return new BFakeCamera(source);
        }

        @Override
        public BFakeCamera[] newArray(int size) {
            return new BFakeCamera[size];
        }
    };

    @Override
    public String toString() {
        return "BFakeCamera{" +
                "mode: " + mMode +
                ", sourcePath: '" + mSourcePath + '\'' +
                ", width: " + mWidth +
                ", height: " + mHeight +
                ", audioEnabled: " + mAudioEnabled +
                '}';
    }
}
