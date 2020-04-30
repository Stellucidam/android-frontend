package ch.heigvd.pro.b04.android.Network;

import java.util.List;

import ch.heigvd.pro.b04.android.Datamodel.QuestionDataModel;
import ch.heigvd.pro.b04.android.Datamodel.PollDataModel;
import ch.heigvd.pro.b04.android.Datamodel.Session;
import ch.heigvd.pro.b04.android.Datamodel.SessionCode;
import ch.heigvd.pro.b04.android.Datamodel.Token;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface RockinAPI {

    @POST("/connect")
    Call<Token> postConnect(
            @Body SessionCode code);

    @GET("/session")
    Call<Session> getSession(
            @Query("token") String userToken);

    @GET("/mod/{idModerator}/poll/{idPoll}")
    Call<PollDataModel> getPoll(
            @Path("idModerator") String idModerator,
            @Path("idPoll") String idPoll,
            @Query("token") String userToken);

    @GET("/mod/{idModerator}/poll/{idPoll}/question")
    Call<List<QuestionDataModel>> getQuestions(
            @Path("idModerator") String idModerator,
            @Path("idPoll") String idPoll,
            @Query("token") String userToken);

    @GET("/mod/{idModerator}/poll/{idPoll}/question/{idQuestion}")
    Call<QuestionDataModel> getQuestion(
            @Path("idModerator") String idModerator,
            @Path("idPoll") String idPoll,
            @Path("idQuestion") String idQuestion,
            @Query("token") String token);
}