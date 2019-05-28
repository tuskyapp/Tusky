// from https://proandroiddev.com/viewmodel-with-dagger2-architecture-components-2e06f06c9455

package com.keylesspalace.tusky.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.keylesspalace.tusky.components.conversation.ConversationsViewModel
import com.keylesspalace.tusky.viewmodel.*
import com.keylesspalace.tusky.viewmodel.ListsViewModel
import dagger.Binds
import dagger.MapKey
import dagger.Module
import dagger.multibindings.IntoMap
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.reflect.KClass

@Singleton
class ViewModelFactory @Inject constructor(private val viewModels: MutableMap<Class<out ViewModel>, Provider<ViewModel>>) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = viewModels[modelClass]?.get() as T
}

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
@kotlin.annotation.Retention(AnnotationRetention.RUNTIME)
@MapKey
internal annotation class ViewModelKey(val value: KClass<out ViewModel>)

@Module
abstract class ViewModelModule {

    @Binds
    internal abstract fun bindViewModelFactory(factory: ViewModelFactory): ViewModelProvider.Factory

    @Binds
    @IntoMap
    @ViewModelKey(AccountViewModel::class)
    internal abstract fun accountViewModel(viewModel: AccountViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(EditProfileViewModel::class)
    internal abstract fun editProfileViewModel(viewModel: EditProfileViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(ConversationsViewModel::class)
    internal abstract fun conversationsViewModel(viewModel: ConversationsViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(ListsViewModel::class)
    internal abstract fun listsViewModel(viewModel: ListsViewModel): ViewModel


    @Binds
    @IntoMap
    @ViewModelKey(AccountsInListViewModel::class)
    internal abstract fun accountsInListViewModel(viewModel: AccountsInListViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(ReportViewModel::class)
    internal abstract fun reportViewModel(viewModel: ReportViewModel): ViewModel

    //Add more ViewModels here
}