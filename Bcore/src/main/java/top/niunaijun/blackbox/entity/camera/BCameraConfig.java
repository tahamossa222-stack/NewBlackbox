package top.niunaijun.blackbox.entity.camera;

import android.os.Parcel;
import android.os.Parcelable;


public class BCameraConfig implements Parcelable {
    public int pattern;
    public BFakeCamera fakeCamera;
    public boolean enabled;

    public BCameraConfig() {
        this.pattern = BFakeCamera.DISABLED;
        this.fakeCamera = new BFakeCamera();
        this.enabled = false;
    }

    public BCameraConfig(Parcel in) {
        readFromParcel(in);
    }

    public void readFromParcel(Parcel in) {
        this.pattern = in.readInt();
        this.fakeCamera = in.readParcelable(BFakeCamera.class.getClassLoader());
        this.enabled = in.readInt() == 1;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.pattern);
        dest.writeParcelable(this.fakeCamera, flags);
        dest.writeInt(this.enabled ? 1 : 0);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<BCameraConfig> CREATOR = new Creator<BCameraConfig>() {
        @Override
        public BCameraConfig createFromParcel(Parcel source) {
            return new BCameraConfig(source);
        }

        @Override
        public BCameraConfig[] newArray(int size) {
            return new BCameraConfig[size];
        }
    };
}
