package ch.heigvd.pro.b04.android.Home;

import android.app.Application;
import android.graphics.Color;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import ch.heigvd.pro.b04.android.Datamodel.Session;
import ch.heigvd.pro.b04.android.Datamodel.SessionCode;
import ch.heigvd.pro.b04.android.Datamodel.Token;
import ch.heigvd.pro.b04.android.Network.RetrofitClient;
import ch.heigvd.pro.b04.android.Network.RockinAPI;
import ch.heigvd.pro.b04.android.R;
import ch.heigvd.pro.b04.android.Utils.Persistent;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class HomeViewModel extends AndroidViewModel {
    private String token;
    private Boolean triedToGetToken = false;

    private MutableLiveData<List<String>> pollInfo = new MutableLiveData<>();
    private MutableLiveData<Integer> codeColor = new MutableLiveData<>();
    private MutableLiveData<List<Emoji>> queue = new MutableLiveData<>();
    private MutableLiveData<Set<Emoji>> selectedEmoji = new MutableLiveData<>();
    private MutableLiveData<String> registrationCode = new MutableLiveData<>();
    private MutableLiveData<List<Emoji>> registrationCodeEmoji = new MutableLiveData<>();

    private Callback<Session> callbackSession = new Callback<Session>() {
        @Override
        public void onResponse(Call<Session> call, Response<Session> response) {
            if (response.isSuccessful()) {
                Log.w("localDebug", "Success, session is " + response.body().getStatus());
                List<String> info = new LinkedList<>();
                info.add(response.body().getIdPoll());
                info.add(response.body().getIdModerator());
                pollInfo.postValue(info);
            } else {
                Log.w("localDebug", "Received error, HTTP status is " + response.code());
                Log.w("localDebug", "The request was " + call.request().url());

                try {
                    Log.w("localDebug", response.errorBody().string());
                } catch (IOException e) {
                    Log.e("localDebug", "Error in error, rip");
                }
            }
        }

        @Override
        public void onFailure(Call<Session> call, Throwable t) {
            Log.e("localDebug", "We had a super bad error in callbackToken");
        }
    };

    private Callback<Token> callbackToken = new Callback<Token>() {
        @Override
        public void onResponse(Call<Token> call, Response<Token> response) {
            if (response.isSuccessful()) {
                registrationCodeEmoji.postValue(new ArrayList<>());
                token = response.body().getToken();

                Persistent.writeToken(getApplication().getApplicationContext(), token);

                RetrofitClient.getRetrofitInstance()
                        .create(RockinAPI.class)
                        .getSession(response.body().getToken())
                        .enqueue(callbackSession);
            } else {
                token = "Error";
                triedToGetToken = true;
                codeColor.postValue(ContextCompat.getColor(getApplication().getApplicationContext(),
                        R.color.colorAccent));

                Log.w("localDebug", "Received error, HTTP status is " + response.code());
                Log.w("localDebug", "Registration code was : " + registrationCode.getValue());

                try {
                    Log.w("localDebug", response.errorBody().string());
                } catch (IOException e) {
                    Log.e("localDebug", "Error in error, rip");
                }
            }
        }

        @Override
        public void onFailure(Call<Token> call, Throwable t) {
            Log.e("localDebug", "We had a super bad error in callbackToken");
        }
    };

    public HomeViewModel(@NonNull Application application) {
        super(application);
    }

    public void addNewEmoji(Emoji emoji) {

        List<Emoji> emojisBuffer = registrationCodeEmoji.getValue();

        if (emojisBuffer == null) emojisBuffer = new LinkedList<>();

        if (triedToGetToken) {
            emojisBuffer.clear();
            codeColor.postValue(Color.TRANSPARENT);
            triedToGetToken = false;
        }

        if (emojisBuffer.size() < 4) {
            emojisBuffer.add(emoji);
        }

        if (emojisBuffer.size() == 4) {
            Iterator<Emoji> emojis = emojisBuffer.iterator();
            StringBuilder code = new StringBuilder();
            code.append("0x");
            while (emojis.hasNext()) {
                code.append(emojis.next().getHex());
            }
            registrationCode.postValue(code.toString());

            RetrofitClient.getRetrofitInstance()
                    .create(RockinAPI.class)
                    .postConnect(new SessionCode(code.toString()))
                    .enqueue(callbackToken);

        }

        queue.postValue(emojisBuffer);
        registrationCodeEmoji.postValue(emojisBuffer);
        selectedEmoji.postValue(new HashSet<>(emojisBuffer));
    }

    public LiveData<List<Emoji>> getCodeEmoji() {
        return this.registrationCodeEmoji;
    }

    public MutableLiveData<List<String>> getPollInfo() {
        return this.pollInfo;
    }

    public LiveData<Set<Emoji>> getSelectedEmoji() {
        return this.selectedEmoji;
    }

    public String getToken() {
        return token;
    }

    public LiveData<Integer> getCodeColor() {
        return codeColor;
    }

}
