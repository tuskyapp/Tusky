package com.keylesspalace.tusky.components.domainblocks

import android.os.Bundle
import com.keylesspalace.tusky.BaseActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.ActivityAccountListBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DomainBlocksActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityAccountListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.includedToolbar.toolbar)
        supportActionBar?.apply {
            setTitle(R.string.title_domain_mutes)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fragment_container, DomainBlocksFragment())
            .commit()
    }
}
