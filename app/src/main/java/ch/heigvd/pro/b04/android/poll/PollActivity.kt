package ch.heigvd.pro.b04.android.poll

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ch.heigvd.pro.b04.android.R
import ch.heigvd.pro.b04.android.authentication.AuthenticationTokenLiveData
import ch.heigvd.pro.b04.android.network.NetworkError
import ch.heigvd.pro.b04.android.question.QuestionActivity
import kotlinx.coroutines.flow.collect
import java.util.*

class PollActivity : AppCompatActivity() {
    private lateinit var state: PollViewModel
    private var tokenLiveData: AuthenticationTokenLiveData? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.poll_activity)

        val idModerator = intent.getIntExtra(EXTRA_ID_MODERATOR, 0)
        val idPoll = intent.getIntExtra(EXTRA_ID_POLL, 0)
        val token: String = intent.getStringExtra(EXTRA_TOKEN) ?: "empty"

        tokenLiveData = AuthenticationTokenLiveData(this)

        state = ViewModelProvider(this, PollViewModelFactory(
            application,
            idModerator,
            idPoll,
            token
        )).get(PollViewModel::class.java)

        // Show the list of questions
        val questionList = findViewById<RecyclerView>(R.id.poll_questions_view)
        val pollAdapter = PollAdapter(state)
        val manager = LinearLayoutManager(this)

        questionList.itemAnimator = DefaultItemAnimator()
        questionList.adapter = pollAdapter
        questionList.layoutManager = manager

        // Setup exit button behaviour
        val exitButton = findViewById<Button>(R.id.poll_exit_button)
        exitButton.setOnClickListener { disconnectFromPoll() }

        // React to change of state
        lifecycleScope.launchWhenStarted {
            state.networkErrors().collect {
                if (it == NetworkError.TokenNotValid)
                    disconnectFromPoll()
            }
        }

        state.questionToView.observe(this, Observer {
            val questionIntent = Intent(this, QuestionActivity::class.java)
                    .putExtra(EXTRA_TOKEN, intent.getStringExtra(EXTRA_TOKEN))
                    .putExtra(EXTRA_QUESTION, it)

            startActivity(questionIntent)
        })

        // If the token is not correct anymore, quit activity
        tokenLiveData!!.observe(this, Observer { s: Optional<String?> ->
            if (!s.isPresent) {
                disconnectFromPoll()
            }
        })
    }

    /**
     * Helper method that disconnects us from the current Poll
     */
    private fun disconnectFromPoll() {
        tokenLiveData?.logout()
        finish()
    }

    /**
     * If we use the usual android back button, remove token
     */
    override fun onBackPressed() {
        tokenLiveData?.logout()
        super.onBackPressed()
    }

    companion object {
        const val EXTRA_ID_MODERATOR = "idModerator"
        const val EXTRA_ID_POLL = "idPoll"
        const val EXTRA_TOKEN = "token"
        const val EXTRA_QUESTION = "question"
    }
}