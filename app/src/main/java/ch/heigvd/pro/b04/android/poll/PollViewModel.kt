package ch.heigvd.pro.b04.android.poll

import android.app.Application
import androidx.lifecycle.MutableLiveData
import ch.heigvd.pro.b04.android.datamodel.Question
import ch.heigvd.pro.b04.android.network.RequestsViewModel
import kotlinx.coroutines.FlowPreview

@FlowPreview
class PollViewModel(application: Application,
                    idModerator : Int,
                    idPoll : Int,
                    token : String
) : RequestsViewModel(application, idModerator, idPoll, token) {
    val questionToView : MutableLiveData<Question> = MutableLiveData()

    fun goToQuestion(question: Question) {
        questionToView.postValue(question)
    }
}
