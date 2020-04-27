package ch.heigvd.pro.b04.android.Poll;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;

import ch.heigvd.pro.b04.android.Poll.Question.QuestionActivity;
import ch.heigvd.pro.b04.android.Poll.Question.QuestionAdapter;
import ch.heigvd.pro.b04.android.R;

public class PollActivity extends AppCompatActivity {
    private PollViewModel state;

        @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_poll);
        Intent intent = getIntent();
        String idPoll = intent.getStringExtra("idPoll");
        String idModerator = intent.getStringExtra("idModerator");
        String token = intent.getStringExtra("token");

        state = new ViewModelProvider(this).get(PollViewModel.class);
        state.getPoll(idPoll, idModerator, token);

        RecyclerView questionList = findViewById(R.id.poll_questions_view);
        LinearLayoutManager manager = new LinearLayoutManager(this);

        QuestionAdapter questionAdapter = new QuestionAdapter(state, this);

        questionList.setAdapter(questionAdapter);
        questionList.setLayoutManager(manager);

        state.getQuestionToView().observe(this, question -> {
            Intent questionIntent = new Intent(this, QuestionActivity.class);
            questionIntent.putExtra("QUESTION_ID", question.getId());
            startActivity(questionIntent);
        });
    }

}
