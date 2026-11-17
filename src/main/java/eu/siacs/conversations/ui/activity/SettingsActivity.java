package eu.siacs.conversations.ui.activity;

import android.app.Notification;
import android.os.Bundle;

import androidx.databinding.DataBindingUtil;
import androidx.preference.PreferenceFragmentCompat;

import com.google.common.collect.ImmutableSet;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivitySettingsBinding;
import eu.siacs.conversations.ui.Activities;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.ui.fragment.settings.MainSettingsFragment;
import eu.siacs.conversations.ui.fragment.settings.NotificationsSettingsFragment;
import eu.siacs.conversations.ui.fragment.settings.InterfaceSettingsFragment;
import eu.siacs.conversations.ui.fragment.settings.PrivacySettingsFragment;
import eu.siacs.conversations.ui.fragment.settings.AttachmentsSettingsFragment;
import eu.siacs.conversations.ui.fragment.settings.BackupSettingsFragment;
import eu.siacs.conversations.ui.fragment.settings.ConnectionSettingsFragment;
import eu.siacs.conversations.ui.fragment.settings.XmppPreferenceFragment;

import java.util.Collections;

public class SettingsActivity extends XmppActivity {
    public static final String EXTRA_SECTION = "operatt.settings.section";

    @Override
    protected void refreshUiReal() {}

    @Override
    protected void onBackendConnected() {
        final var fragmentManager = getSupportFragmentManager();
        final var currentFragment = fragmentManager.findFragmentById(R.id.fragment_container);
        if (currentFragment instanceof XmppPreferenceFragment xmppPreferenceFragment) {
            xmppPreferenceFragment.onBackendConnected();
        }
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final ActivitySettingsBinding binding =
                DataBindingUtil.setContentView(this, R.layout.activity_settings);
        setSupportActionBar(binding.materialToolbar);
        Activities.setStatusAndNavigationBarColors(this, binding.getRoot());

        final var intent = getIntent();
        final var categories = intent == null ? Collections.emptySet() : intent.getCategories();
        final PreferenceFragmentCompat preferenceFragment;
        final String section = intent == null ? null : intent.getStringExtra(EXTRA_SECTION);
        if (ImmutableSet.of(Notification.INTENT_CATEGORY_NOTIFICATION_PREFERENCES).equals(categories)) {
            preferenceFragment = new NotificationsSettingsFragment();
        } else if (section == null) {
            preferenceFragment = new MainSettingsFragment();
        } else {
            preferenceFragment = switch (section) {
                case "appearance" -> new InterfaceSettingsFragment();
                case "privacy" -> new PrivacySettingsFragment();
                case "notifications" -> new NotificationsSettingsFragment();
                case "storage" -> new AttachmentsSettingsFragment();
                case "backup" -> new BackupSettingsFragment();
                case "connection" -> new ConnectionSettingsFragment();
                default -> new MainSettingsFragment();
            };
        }

        final var fragmentManager = getSupportFragmentManager();
        final var currentFragment = fragmentManager.findFragmentById(R.id.fragment_container);
        if (currentFragment == null) {
            fragmentManager
                    .beginTransaction()
                    .replace(R.id.fragment_container, preferenceFragment)
                    .commit();
        }
        binding.materialToolbar.setNavigationOnClickListener(
                view -> {
                    if (fragmentManager.getBackStackEntryCount() == 0) {
                        finish();
                    } else {
                        fragmentManager.popBackStack();
                    }
                });
    }
}
