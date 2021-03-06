package ch.heigvd.pro.b04.android.datamodel;

import com.google.gson.annotations.SerializedName;

public class Session {
    @SerializedName("idModerator")
    private String idModerator;

    @SerializedName("idPoll")
    private String idPoll;

    @SerializedName("idSession")
    private String idSession;

    @SerializedName("code")
    private String code;

    @SerializedName("status")
    private String status;

    public String getIdModerator() {
        return idModerator;
    }

    public String getIdPoll() {
        return idPoll;
    }

    public String getCode() {
        return code;
    }
}
