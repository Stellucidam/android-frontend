package ch.heigvd.pro.b04.android.Question;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.LinkedList;
import java.util.List;

import ch.heigvd.pro.b04.android.Datamodel.Answer;
import ch.heigvd.pro.b04.android.Datamodel.Poll;
import ch.heigvd.pro.b04.android.Datamodel.Question;
import ch.heigvd.pro.b04.android.Network.Rockin;
import ch.heigvd.pro.b04.android.Utils.LocalDebug;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class QuestionViewModel extends ViewModel {
    private Answer checkedAnswer;
    private String token;
    private MutableLiveData<Question> currentQuestion = new MutableLiveData<>();
    private MutableLiveData<List<Answer>> currentAnswers = new MutableLiveData<>(new LinkedList<>());

    private Callback<List<Answer>> callbackAnswers = new Callback<List<Answer>>() {
        @Override
        public void onResponse(Call<List<Answer>> call, Response<List<Answer>> response) {
            if (response.isSuccessful()) {
                saveAnswers(response.body());
            } else {
                LocalDebug.logUnsuccessfulRequest(call, response);
            }
        }

        @Override
        public void onFailure(Call<List<Answer>> call, Throwable t) {
            LocalDebug.logFailedRequest(call, t);
        }
    };

    private Callback<ResponseBody> callbackVote = new Callback<ResponseBody>() {
        @Override
        public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
            if (!response.isSuccessful()) {
                LocalDebug.logUnsuccessfulRequest(call, response);
                checkedAnswer.toggle();
            }
        }

        @Override
        public void onFailure(Call<ResponseBody> call, Throwable t) {
            LocalDebug.logFailedRequest(call, t);
            checkedAnswer.toggle();
        }
    };

    private void saveAnswers(List<Answer> answers) {
        currentAnswers.postValue(answers);
    }

    public QuestionViewModel() {}

    public MutableLiveData<List<Answer>> getCurrentAnswers() {
        return currentAnswers;
    }

    public void setAnswers(String token, Question question) {
        if (question == null)
            return;

        Rockin.api()
                .getAnswers(
                        question.getIdModerator(),
                        question.getIdPoll(),
                        String.valueOf(question.getIdQuestion()),
                        token)
                .enqueue(callbackAnswers);
    }

    public void getAllQuestionsFromBackend(Poll poll, String token) {
        this.token = token;
        QuestionUtils.sendGetQuestionRequest(poll, token);
    }

    public LiveData<Question> getCurrentQuestion() {
        return currentQuestion;
    }

    public void setCurrentQuestion(Question question) {
        currentQuestion.postValue(question);
    }

    public void changeToPreviousQuestion() {
        double currentIndex = currentQuestion.getValue().getIndexInPoll();
        double candidateIndex = Double.MIN_VALUE;
        Question candidate = null;

        for (Question q : QuestionUtils.getQuestions().getValue()) {
            double newIndex = q.getIndexInPoll();
            if (newIndex < currentIndex && newIndex > candidateIndex) {
                candidateIndex = newIndex;
                candidate = q;
            }
        }

        if (candidate != null)
            currentQuestion.setValue(candidate);
    }

    public void changeToNextQuestion() {
        double currentIndex = currentQuestion.getValue().getIndexInPoll();
        double candidateIndex = Double.MAX_VALUE;
        Question candidate = null;

        for (Question q : QuestionUtils.getQuestions().getValue()) {
            double newIndex = q.getIndexInPoll();
            if (newIndex > currentIndex && newIndex < candidateIndex) {
                    candidateIndex = newIndex;
                    candidate = q;
                }
        }

        if (candidate != null)
            currentQuestion.setValue(candidate);
    }

    public void selectAnswer(Answer answer) {
        answer.toggle();
        checkedAnswer = answer;
        Rockin.api().voteForAnswer(
                    answer.getIdModerator(),
                    answer.getIdPoll(),
                    answer.getIdQuestion(),
                    answer.getIdAnswer(),
                    token,
                    answer
                ).enqueue(callbackVote);
    }
}
