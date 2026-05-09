package net.osmand.aidlapi.logcat;

import android.os.Parcel;
import android.os.Parcelable;

public class OnLogcatMessageParams implements Parcelable {
    public String message;

    public OnLogcatMessageParams() {}

    protected OnLogcatMessageParams(Parcel in) {
        message = in.readString();
    }

    public static final Creator<OnLogcatMessageParams> CREATOR = new Creator<OnLogcatMessageParams>() {
        @Override
        public OnLogcatMessageParams createFromParcel(Parcel in) {
            return new OnLogcatMessageParams(in);
        }

        @Override
        public OnLogcatMessageParams[] newArray(int size) {
            return new OnLogcatMessageParams[size];
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(message);
    }

    @Override
    public int describeContents() {
        return 0;
    }
}