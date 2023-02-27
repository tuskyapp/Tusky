// from https://proandroiddev.com/viewmodel-with-dagger2-architecture-components-2e06f06c9455

package com.keylesspalace.tusky.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.keylesspalace.tusky.components.account.AccountViewModel
import com.keylesspalace.tusky.components.account.list.ListsForAccountViewModel
import com.keylesspalace.tusky.components.account.media.AccountMediaViewModel
import com.keylesspalace.tusky.components.announcements.AnnouncementsViewModel
import com.keylesspalace.tusky.components.compose.ComposeViewModel
import com.keylesspalace.tusky.components.conversation.ConversationsViewModel
import com.keylesspalace.tusky.components.drafts.DraftsViewModel
import com.keylesspalace.tusky.components.followedtags.FollowedTagsViewModel
import com.keylesspalace.tusky.components.login.LoginWebViewViewModel
import com.keylesspalace.tusky.components.report.ReportViewModel
import com.keylesspalace.tusky.components.scheduled.ScheduledStatusViewModel
import com.keylesspalace.tusky.components.search.SearchViewModel
import com.keylesspalace.tusky.components.timeline.viewmodel.CachedTimelineViewModel
import com.keylesspalace.tusky.components.timeline.viewmodel.NetworkTimelineViewModel
import com.keylesspalace.tusky.components.trending.viewmodel.TrendingViewModel
import com.keylesspalace.tusky.components.viewthread.ViewThreadViewModel
import com.keylesspalace.tusky.components.viewthread.edits.ViewEditsViewModel
import com.keylesspalace.tusky.viewmodel.AccountsInListViewModel
import com.keylesspalace.tusky.viewmodel.EditProfileViewModel
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
@Retention(AnnotationRetention.RUNTIME)
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

    @Binds
    @IntoMap
    @ViewModelKey(SearchViewModel::class)
    internal abstract fun searchViewModel(viewModel: SearchViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(ComposeViewModel::class)
    internal abstract fun composeViewModel(viewModel: ComposeViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(ScheduledStatusViewModel::class)
    internal abstract fun scheduledStatusViewModel(viewModel: ScheduledStatusViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(AnnouncementsViewModel::class)
    internal abstract fun announcementsViewModel(viewModel: AnnouncementsViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(DraftsViewModel::class)
    internal abstract fun draftsViewModel(viewModel: DraftsViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(CachedTimelineViewModel::class)
    internal abstract fun cachedTimelineViewModel(viewModel: CachedTimelineViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(NetworkTimelineViewModel::class)
    internal abstract fun networkTimelineViewModel(viewModel: NetworkTimelineViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(ViewThreadViewModel::class)
    internal abstract fun viewThreadViewModel(viewModel: ViewThreadViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(ViewEditsViewModel::class)
    internal abstract fun viewEditsViewModel(viewModel: ViewEditsViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(AccountMediaViewModel::class)
    internal abstract fun accountMediaViewModel(viewModel: AccountMediaViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(LoginWebViewViewModel::class)
    internal abstract fun loginWebViewViewModel(viewModel: LoginWebViewViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(FollowedTagsViewModel::class)
    internal abstract fun followedTagsViewModel(viewModel: FollowedTagsViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(ListsForAccountViewModel::class)
    internal abstract fun listsForAccountViewModel(viewModel: ListsForAccountViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(TrendingViewModel::class)
    internal abstract fun trendingViewModel(viewModel: TrendingViewModel): ViewModel

    // Add more ViewModels here
}
