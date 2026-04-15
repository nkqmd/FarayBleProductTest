package com.faray.leproducttest

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.faray.leproducttest.databinding.ActivityMainBinding
import com.faray.leproducttest.ui.shared.SharedSessionViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private val sharedSessionViewModel by viewModels<SharedSessionViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            binding.bottomNavigation.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        setupBottomNavigation()
        observeNavigation()
        observeShellState()
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val shell = sharedSessionViewModel.uiState.value
            if (shell?.navigationLocked == true) {
                false
            } else {
                navigateToTopLevel(item.itemId)
                true
            }
        }
        binding.bottomNavigation.setOnItemReselectedListener { }
    }

    private fun observeNavigation() {
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val isLoginDestination = destination.id == R.id.loginFragment
            binding.toolbar.isVisible = !isLoginDestination
            binding.bottomNavigation.isVisible = !isLoginDestination
            binding.toolbar.title = when (destination.id) {
                R.id.configFragment -> getString(R.string.title_config)
                R.id.productionFragment -> getString(R.string.title_production)
                R.id.resultFragment -> getString(R.string.title_result)
                else -> getString(R.string.title_login)
            }
            if (!isLoginDestination) {
                binding.bottomNavigation.menu.findItem(destination.id)?.isChecked = true
            }
        }
    }

    private fun observeShellState() {
        sharedSessionViewModel.uiState.observe(this) { shell ->
            binding.toolbar.subtitle = if (!shell.batchId.isNullOrBlank() && !shell.factoryId.isNullOrBlank()) {
                getString(R.string.subtitle_shell_format, shell.batchId, shell.factoryId)
            } else {
                getString(R.string.subtitle_shell_empty)
            }

            binding.bottomNavigation.alpha = if (shell.navigationLocked) 0.55f else 1f
            for (index in 0 until binding.bottomNavigation.menu.size()) {
                binding.bottomNavigation.menu.getItem(index).isEnabled = !shell.navigationLocked
            }

            if (shell.authenticated && navController.currentDestination?.id == R.id.loginFragment) {
                navController.navigate(R.id.action_loginFragment_to_configFragment)
            }

            if (!shell.authenticated && navController.currentDestination?.id != R.id.loginFragment && !shell.navigationLocked) {
                val options = NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .setPopUpTo(R.id.app_nav_graph, true)
                    .build()
                navController.navigate(R.id.loginFragment, null, options)
                return@observe
            }

            if (shell.navigationLocked && navController.currentDestination?.id != R.id.productionFragment) {
                navigateToTopLevel(R.id.productionFragment)
            }
        }
    }

    private fun navigateToTopLevel(destinationId: Int) {
        if (navController.currentDestination?.id == destinationId) {
            return
        }
        val options = NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setRestoreState(true)
            .build()
        navController.navigate(destinationId, null, options)
    }
}
