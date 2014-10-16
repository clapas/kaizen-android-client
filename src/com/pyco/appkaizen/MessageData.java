package com.pyco.appkaizen;

import android.os.Parcel;
import android.os.Parcelable;


public class MessageData implements Parcelable {
    private int received;
    private String body;

    public MessageData(Boolean received, String body) {
        this.received = received ? 1 : 0;
        this.body = body;
    }
    public Boolean getReceived() {
        return received != 0;
    }
    public String getBody() {
        return body;
    }
    // 99.9% of the time you can just ignore this
    public int describeContents() {
        return 0;
    }
    private MessageData(Parcel in) {
        received = in.readInt();
        body = in.readString();
    }
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(received);
        out.writeString(body);
    }
    public static final Parcelable.Creator<MessageData> CREATOR = new Parcelable.Creator<MessageData>() {
        public MessageData createFromParcel(Parcel in) {
            return new MessageData(in);
        }
        public MessageData[] newArray(int size) {
            return new MessageData[size];
        }
    };
}

