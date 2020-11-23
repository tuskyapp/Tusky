package com.keylesspalace.tusky.components.drafts

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import com.keylesspalace.tusky.BaseActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.di.ViewModelFactory
import javax.inject.Inject

class DraftsActivity: BaseActivity() {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private val viewModel: DraftsViewModel by viewModels { viewModelFactory }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_saved_toot)
    }



    companion object {
        fun newIntent(context: Context) = Intent(context, DraftsActivity::class.java)
    }

}