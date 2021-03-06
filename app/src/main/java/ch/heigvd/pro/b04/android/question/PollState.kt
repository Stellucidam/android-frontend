package ch.heigvd.pro.b04.android.question

import ch.heigvd.pro.b04.android.datamodel.Answer
import ch.heigvd.pro.b04.android.datamodel.Poll
import ch.heigvd.pro.b04.android.datamodel.Question
import ch.heigvd.pro.b04.android.network.RockinAPI
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

/**
 * How long the user's vote is considered as more relevant than the server value.
 */
const val GRACE_DELAY_IN_MILLIS = 7500L

/**
 * The general refresh frequency. The smaller the delay, the more real-time-ish the app.
 */
const val FRESH_DELAY_IN_MILLIS = 1000L

/**
 * A type alias for a type which has a sequence of states with an identifier different for two
 * strictly consecutive sequence items.
 */
typealias Sequenced<T> = Pair<T, Int>

/**
 * A data class representing the [Model] of the currently displayed poll. The [map] values will
 * contain a best-effort guess of what the server state is, and takes into consideration the user
 * for the last [GRACE_DELAY_IN_MILLIS].
 */
data class Model(
        val poll: Poll,
        var current: Question,
        val token: String,
        var map: MutableMap<Question, List<FetchedAnswer>>,
        var rejected: Sequenced<Unit?>,
        var invalidToken: Boolean
)

/**
 * A data class representing an [Answer], as well as a freshness stamp indicating when it was
 * fetched from the server.
 *
 * @see GRACE_DELAY_IN_MILLIS
 */
data class FetchedAnswer(
        var timestamp: Long,
        var answer: Answer
)

/**
 * A sealed class representing all the different events that might be triggered by the model. These
 * events might be launched based on user triggers (such as voting for an answer), or recurrent
 * triggers, like when the user loads the application.
 */
sealed class Event {

    object NoOp : Event()

    // User events.
    object MoveToNext : Event()
    object MoveToPrevious : Event()
    class SetVote(val answer: Answer) : Event()

    // Data events.
    object GotInvalidToken : Event()
    class GotQuestions(val questions: List<Question>) : Event()
    class GotAnswers(val question: Question, val answers: List<FetchedAnswer>) : Event()

    // Refresh events.
    object RefreshQuestions : Event()
    object RefreshCurrentAnswers : Event()
    object RejectVote : Event()
}

/**
 * A class representing the state model of the poll and its different questions. The architecture is
 * very inspired by the Elm Architecture : a Model gets updated through Messages, and the view is
 * notified of Model changes to render the right data.
 *
 * @param scope A [CoroutineScope] to execute some flows in.
 * @param poll The [Poll] to display.
 * @param question The selected [Question] at start.
 * @param token The user token.
 *
 * @param moveToNext A [Flow] that emits when the user clicks the next button.
 * @param moveToPrevious A [Flow] that emits when the user clicks the previous button.
 * @param votes A [Flow] that emits when the user votes for an answer.
 */
@FlowPreview
@ExperimentalCoroutinesApi
class PollState(
        // Scope
        scope: CoroutineScope,

        // Data.
        poll: Poll,
        question: Question,
        token: String,

        // User actions.
        moveToNext: Flow<Unit>,
        moveToPrevious: Flow<Unit>,
        votes: Flow<Answer>
) {

    /**
     * A [BroadcastChannel] that consists of a buffer of all the [Event]s that have not been
     * processed by the main loop yet.
     */
    private val buffer = BroadcastChannel<Event>(Channel.Factory.BUFFERED)

    /**
     * A [MutableStateFlow] that acts as a single source of truth for all the [Flow]s that are
     * exposed outside of the [PollState]. A [Sequenced] [Model] is required, since the instance
     * does not change otherwise.
     *
     * Using a [MutableStateFlow] avoids duplicate [Flow]s, since [Flow]s are cold by default.
     */
    private val innerState: MutableStateFlow<Sequenced<Model>> = MutableStateFlow(Pair(Model(
            poll,
            question,
            token,
            mutableMapOf<Question, List<FetchedAnswer>>(),
            Pair(null, 0),
            false
    ), 0))

    /**
     * The model that is exposed to the outside world directly. This might be hidden in the future,
     * to avoid unwanted mutable state modifications.
     */
    @Deprecated(level = DeprecationLevel.WARNING, message = "Please do not work with the internal mutable state directly")
    val data = innerState.map { it.first }

    /**
     * A [Flow] of all the [Event]s that need to be processed by the pipeline.
     */
    private val events = merge(
            buffer.asFlow(),
            reloadAllQuestionsPeriodically(),
            reloadAnswersPeriodically(),
            markAnswerChecked(votes),
            moveToNext.map { Event.MoveToNext },
            moveToPrevious.map { Event.MoveToPrevious }
    )

    val minCheckedAnswers: Flow<Int?> =
            innerState.map { it.first }
                    .map { it.current.answerMin to (it.map[it.current] ?: emptyList()) }
                    .map { (required, answers) ->
                        val actual = answers.count { it.answer.isChecked }
                        if (actual == 0 || actual >= required) {
                            return@map null
                        } else {
                            return@map required
                        }
                    }

    /**
     * A [Flow] that triggers messages whenever a vote is rejected.
     */
    val tooManyAnswers: Flow<Int> =
            innerState.map { it.first }
                    .map { it.current.answerMax to it.rejected }
                    .filter { it.second.first != null }
                    .distinctUntilChanged()
                    .map { it.first }

    /**
     * A [Flow] with a boolean value indicating if the previous button should be displayed.
     */
    val previousButtonVisible: Flow<Boolean> =
            innerState.map { it.first }
                    .map { s -> s.map.keys.any { q -> q.indexInPoll < s.current.indexInPoll } }

    /**
     * A [Flow] with a boolean value indicating if the next button should be displayed.
     */
    val nextButtonVisible: Flow<Boolean> =
            innerState.map { it.first }
                    .map { s -> s.map.keys.any { q -> q.indexInPoll > s.current.indexInPoll } }

    init {
        scope.launch {
            events.collect { event ->
                val (current, number) = innerState.value
                val (next, actions) = transform(current, event)
                scope.launch { actions.collect { event -> buffer.send(event) } }
                innerState.value = next to (number + 1)
            }
        }
    }
}

/**
 * Following the Elm Architecture design, a [Model] is updated with an [Event], and results in a
 * tuple of a [Model] with updated content, and a [Flow] of [Event] that might be triggered later.
 *
 * @param data The current state.
 * @param event The processed event.
 *
 * @return A [Pair] of a [Model] and a [Flow] of [Event].
 */
private suspend fun transform(data: Model, event: Event): Pair<Model, Flow<Event>> {
    return when (event) {
        is Event.NoOp -> data to emptyFlow()
        // We got an invalid token. This means we have been disconnected from the poll.
        is Event.GotInvalidToken -> {
            data.copy(invalidToken = true) to emptyFlow()
        }
        // Increment the sequence number of rejected votes (needed for consecutive events)
        is Event.RejectVote -> {
            val nextRejection = data.rejected.copy(first = Unit, second = data.rejected.second + 1)
            data.copy(rejected = nextRejection) to emptyFlow()
        }
        // Get the current question, and move to the next one (based on index)
        is Event.MoveToNext -> {
            val nextCurrent = data.map.keys
                    .filter { it.indexInPoll > data.current.indexInPoll }
                    .minBy { it.indexInPoll }
                    ?: data.current
            data.copy(current = nextCurrent, rejected = Pair(null, 0)) to flowOf(Event.RefreshCurrentAnswers)
        }
        // Get the current question, and move to the previous one (based on index)
        is Event.MoveToPrevious -> {
            val nextCurrent = data.map.keys
                    .filter { it.indexInPoll < data.current.indexInPoll }
                    .maxBy { it.indexInPoll }
                    ?: data.current
            data.copy(current = nextCurrent, rejected = Pair(null, 0)) to flowOf(Event.RefreshCurrentAnswers)
        }
        // Vote for a certain answer, persist the state locally, then inform the server. Reset the
        // grace period
        is Event.SetVote -> {
            val fetched = data.map[data.current]?.first { it.answer.idAnswer == event.answer.idAnswer }
            val positive = data.map[data.current]?.count { it.answer.isChecked } ?: 0
            val votingFalse = (fetched?.answer?.isChecked ?: false)
            // We can perform the change if the answers max is not set, we're toggling off an answer
            // or we have enough margin for the next positive vote
            if (data.current.answerMax == 0 || votingFalse || positive + 1 <= data.current.answerMax) {
                fetched?.answer?.toggle()
                fetched?.timestamp = System.currentTimeMillis()
                val effect = flow {
                    emit(fetched?.answer?.let {
                        RockinAPI.voteForAnswerSuspending(it, data.token)
                    })
                }.map { Event.NoOp }.catch { emit(Event.NoOp) }

                data to effect
            } else {
                data to flow { emit(Event.RejectVote) }
            }
        }
        // Update the list of displayed questions
        is Event.GotQuestions -> {
            val updated = mutableMapOf<Question, List<FetchedAnswer>>()
            for ((question, values) in data.map) {
                val update = event.questions.firstOrNull { it.idQuestion == question.idQuestion }
                if (update != null) {
                    updated[update] = values
                }
            }
            for (question in event.questions) {
                if (!updated.keys.any { it.idQuestion == question.idQuestion })
                    updated[question] = emptyList()
            }
            data.map = updated
            // Update the current question, if it exists.
            data.current = event.questions
                    .firstOrNull { it.idQuestion == data.current.idQuestion }
                    ?: data.current
            data to emptyFlow()
        }
        // Update the list of displayed answers
        is Event.GotAnswers -> {
            val answers = data.map[event.question] ?: emptyList()
            val updated = mutableListOf<FetchedAnswer>()
            for (local in answers) {
                val remote = event.answers.firstOrNull { it.answer.idAnswer == local.answer.idAnswer }
                if (remote != null && remote.timestamp - GRACE_DELAY_IN_MILLIS >= local.timestamp) {
                    updated.add(remote)
                } else if (remote != null) {
                    // If the question exits and the grace period is not expired, use the local
                    // question.
                    updated.add(local)
                }
            }
            for (update in event.answers) {
                if (!updated.any { it.answer.idAnswer == update.answer.idAnswer }) {
                    updated.add(update)
                }
            }
            data.map[event.question] = updated
            data to emptyFlow()
        }
        // Ask the runtime to refresh the list of all the questions
        is Event.RefreshQuestions -> {
            val events =
                    flow { emit(RockinAPI.getQuestionsSuspending(data.poll, data.token)) }
                            .map {
                                val response = it.body()
                                when {
                                    response != null -> Event.GotQuestions(response)
                                    it.code() == 403 -> Event.GotInvalidToken
                                    else -> Event.GotQuestions(emptyList())
                                }
                            }
                            .catch { /* Ignore. */ }

            data to events
        }
        // Ask the runtime to refresh the list of the answers for the current question
        is Event.RefreshCurrentAnswers -> {
            val now = System.currentTimeMillis()
            val events = flow {
                emit(RockinAPI.getAnswersSuspending(data.current, data.token))
            }.map {
                val response = it.body()
                when {
                    response != null -> Event.GotAnswers(
                            data.current,
                            response.map { answer -> FetchedAnswer(now, answer) })
                    it.code() == 403 -> Event.GotInvalidToken
                    else -> Event.GotQuestions(emptyList())
                }
            }.catch { /* Ignore */ }

            data to events
        }
    }
}

/**
 * A [Flow] that emits regularly to get a list of all the questions.
 */
fun reloadAllQuestionsPeriodically() = flow<Event> {
    do {
        emit(Event.RefreshQuestions)
        delay(FRESH_DELAY_IN_MILLIS)
    } while (true)
}

/**
 * A [Flow] that transforms the checked [Answer] instances into a [Flow] of [Event].
 */
@ExperimentalCoroutinesApi
fun markAnswerChecked(state: Flow<Answer>): Flow<Event> {
    return state.map { answer -> Event.SetVote(answer) }
}

/**
 * A [Flow] that emits regularly to refresh the currently displayed question.
 */
@ExperimentalCoroutinesApi
fun reloadAnswersPeriodically() = flow<Event> {
    do {
        emit(Event.RefreshCurrentAnswers)
        delay(FRESH_DELAY_IN_MILLIS)
    } while (true)
}