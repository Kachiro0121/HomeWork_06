package otus.homework.reactivecats

import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.net.UnknownHostException

fun Disposable.addTo(compositeDisposable: CompositeDisposable){
    compositeDisposable.add(this)
}

class CatsViewModel(
    private val catsService: CatsService,
    private val localCatFactsGenerator: LocalCatFactsGenerator
) : ViewModel() {

    private val compositeDisposable = CompositeDisposable()
    private val errMessage = R.string.default_error_text

    private val _catsLiveData = MutableLiveData<Result>()
    val catsLiveData: LiveData<Result> = _catsLiveData

    init {
        getFacts()
    }

    private fun getFacts() {
        localCatFactsGenerator.generateCatFactPeriodically()
            .flatMapSingle {
                catsService.getCatFact()
                    .subscribeOn(Schedulers.io())
                    .onErrorResumeNext(localCatFactsGenerator.generateCatFact())
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(::handleSuccess, ::handleError)
            .addTo(compositeDisposable)
    }

    private fun handleSuccess(fact: Fact) {
        _catsLiveData.value = Success(fact)
    }

    private fun handleError(throwable: Throwable) {
        throwable.printStackTrace()
        if (throwable is UnknownHostException)
            _catsLiveData.value = ServerError
        else
            _catsLiveData.value = ErrorRes(errMessage)
    }

    override fun onCleared() {
        super.onCleared()
        compositeDisposable.clear()
    }
}

class CatsViewModelFactory(
    private val catsRepository: CatsService,
    private val localCatFactsGenerator: LocalCatFactsGenerator
) :
    ViewModelProvider.NewInstanceFactory() {

    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        CatsViewModel(catsRepository, localCatFactsGenerator) as T
}

sealed class Result
data class Success(val fact: Fact) : Result()
data class Error(val message: String) : Result()
data class ErrorRes(@StringRes val message: Int) : Result()
data object ServerError : Result()