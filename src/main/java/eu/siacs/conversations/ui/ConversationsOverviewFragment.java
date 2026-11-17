/*
 * Copyright (c) 2018, Daniel Gultsch All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package eu.siacs.conversations.ui;

import static androidx.recyclerview.widget.ItemTouchHelper.LEFT;
import static androidx.recyclerview.widget.ItemTouchHelper.RIGHT;

import android.content.Intent;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import android.graphics.Canvas;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.ui.util.AvatarWorkerTask;
import java.util.Comparator;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuProvider;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import eu.siacs.conversations.BuildConfig;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.FragmentConversationsOverviewBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Conversational;
import eu.siacs.conversations.services.QuickConversationsService;
import eu.siacs.conversations.ui.adapter.ConversationAdapter;
import eu.siacs.conversations.ui.activity.SettingsActivity;
import eu.siacs.conversations.ui.interfaces.OnConversationArchived;
import eu.siacs.conversations.ui.interfaces.OnConversationSelected;
import eu.siacs.conversations.ui.util.PendingActionHelper;
import eu.siacs.conversations.ui.util.PendingItem;
import eu.siacs.conversations.ui.util.ScrollState;
import eu.siacs.conversations.ui.util.OperattHandle;
import androidx.appcompat.app.AppCompatDelegate;
import eu.siacs.conversations.utils.AccountUtils;
import eu.siacs.conversations.xmpp.manager.EasyOnboardingManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class ConversationsOverviewFragment extends XmppFragment {

    private static final String OPERATT_SELECTED_ACCOUNT = "operatt_selected_account";

    private static final String STATE_SCROLL_POSITION =
            ConversationsOverviewFragment.class.getName() + ".scroll_state";

    private final List<Conversation> conversations = new ArrayList<>();
    private final List<Contact> visibleContacts = new ArrayList<>();
    private int currentTab = R.id.operatt_nav_chats;
    private final PendingItem<Conversation> swipedConversation = new PendingItem<>();
    private final PendingItem<ScrollState> pendingScrollState = new PendingItem<>();
    private FragmentConversationsOverviewBinding binding;
    private ConversationAdapter conversationsAdapter;
    private final PendingActionHelper pendingActionHelper = new PendingActionHelper();

    private final ItemTouchHelper.SimpleCallback callback =
            new ItemTouchHelper.SimpleCallback(0, LEFT | RIGHT) {
                @Override
                public boolean onMove(
                        @NonNull RecyclerView recyclerView,
                        @NonNull RecyclerView.ViewHolder viewHolder,
                        @NonNull RecyclerView.ViewHolder target) {
                    return false;
                }

                @Override
                public void onChildDraw(
                        @NonNull Canvas c,
                        @NonNull RecyclerView recyclerView,
                        @NonNull RecyclerView.ViewHolder viewHolder,
                        float dX,
                        float dY,
                        int actionState,
                        boolean isCurrentlyActive) {
                    if (viewHolder
                            instanceof
                            ConversationAdapter.ConversationViewHolder conversationViewHolder) {
                        getDefaultUIUtil()
                                .onDraw(
                                        c,
                                        recyclerView,
                                        conversationViewHolder.binding.frame,
                                        dX,
                                        dY,
                                        actionState,
                                        isCurrentlyActive);
                    }
                }

                @Override
                public void clearView(
                        @NonNull RecyclerView recyclerView,
                        @NonNull RecyclerView.ViewHolder viewHolder) {
                    if (viewHolder
                            instanceof
                            ConversationAdapter.ConversationViewHolder conversationViewHolder) {
                        getDefaultUIUtil().clearView(conversationViewHolder.binding.frame);
                    }
                }

                @Override
                public float getSwipeEscapeVelocity(final float defaultEscapeVelocity) {
                    return 32 * defaultEscapeVelocity;
                }

                @Override
                public void onSwiped(
                        final RecyclerView.ViewHolder viewHolder, final int direction) {
                    int position = viewHolder.getLayoutPosition();
                    final Conversation conversation;
                    try {
                        conversation = conversations.get(position);
                    } catch (final IndexOutOfBoundsException e) {
                        return;
                    }
                    onConversationSwiped(conversation, position);
                }
            };

    private final MenuProvider menuProvider =
            new MenuProvider() {
                @Override
                public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                    menuInflater.inflate(R.menu.fragment_conversations_overview, menu);
                    AccountUtils.showHideMenuItems(menu);
                    final MenuItem easyOnboardInvite = menu.findItem(R.id.action_easy_invite);
                    easyOnboardInvite.setVisible(
                            EasyOnboardingManager.anyHasSupport(
                                    requireXmppActivity().xmppConnectionService));
                    final MenuItem privacyPolicyMenuItem =
                            menu.findItem(R.id.action_privacy_policy);
                    privacyPolicyMenuItem.setVisible(
                            BuildConfig.PRIVACY_POLICY != null
                                    && QuickConversationsService.isPlayStoreFlavor());
                }

                @Override
                public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                    final var id = menuItem.getItemId();
                    if (id == R.id.operatt_switch_accounts) {
                        startActivity(new Intent(getActivity(), ManageAccountActivity.class));
                        return true;
                    } else if (id == R.id.operatt_sticker_studio) {
                        startActivity(new Intent(getActivity(), StickerStudioActivity.class));
                        return true;
                    } else if (id == R.id.action_search) {
                        startActivity(new Intent(getActivity(), SearchActivity.class));
                        return true;
                    } else if (id == R.id.action_easy_invite) {
                        selectAccountToStartEasyInvite();
                        return true;
                    } else {
                        return false;
                    }
                }
            };

    private void onConversationSwiped(final Conversation c, final int position) {
        pendingActionHelper.execute();
        this.swipedConversation.push(c);
        conversationsAdapter.remove(swipedConversation.peek(), position);
        toggleHintVisibility();
        requireXmppActivity().xmppConnectionService.markRead(swipedConversation.peek());
        final boolean formerlySelected =
                ConversationFragment.getConversation(getActivity()) == swipedConversation.peek();
        if (getActivity() instanceof OnConversationArchived callback) {
            callback.onConversationArchived(swipedConversation.peek());
        }
        final int title;
        if (c.getMode() == Conversational.MODE_MULTI) {
            if (c.getMucOptions().isPrivateAndNonAnonymous()) {
                title = R.string.title_undo_swipe_out_group_chat;
            } else {
                title = R.string.title_undo_swipe_out_channel;
            }
        } else {
            title = R.string.title_undo_swipe_out_chat;
        }

        final Snackbar snackbar =
                Snackbar.make(binding.list, title, 5000)
                        .setAction(R.string.undo, v -> undoSwipe(formerlySelected, position))
                        .addCallback(
                                new Snackbar.Callback() {
                                    @Override
                                    public void onDismissed(
                                            Snackbar transientBottomBar, int event) {
                                        switch (event) {
                                            case DISMISS_EVENT_SWIPE:
                                            case DISMISS_EVENT_TIMEOUT:
                                                pendingActionHelper.execute();
                                                break;
                                        }
                                    }
                                });

        pendingActionHelper.push(
                () -> {
                    if (snackbar.isShownOrQueued()) {
                        snackbar.dismiss();
                    }
                    final Conversation conversation = swipedConversation.pop();
                    if (conversation == null) {
                        return;
                    }
                    if (!conversation.isRead()
                            && conversation.getMode() == Conversation.MODE_SINGLE) {
                        return;
                    }
                    requireXmppActivity().xmppConnectionService.archiveConversation(c);
                });
        snackbar.show();
    }

    private void undoSwipe(final boolean formerlySelected, final int position) {
        pendingActionHelper.undo();
        final var c = swipedConversation.pop();
        if (!conversations.contains(c)) {
            conversationsAdapter.insert(c, position);
        }
        toggleHintVisibility();
        if (formerlySelected) {
            if (getActivity() instanceof OnConversationSelected on) {
                on.onConversationSelected(c);
            }
        }
        if (binding.list.getLayoutManager() instanceof LinearLayoutManager llm
                && position > llm.findLastVisibleItemPosition()) {
            binding.list.smoothScrollToPosition(position);
        }
    }

    private ItemTouchHelper touchHelper;

    public static Conversation getSuggestion(final FragmentActivity activity) {
        final Conversation exception;
        Fragment fragment =
                activity.getSupportFragmentManager().findFragmentById(R.id.main_fragment);
        if (fragment instanceof ConversationsOverviewFragment conversationsOverviewFragment) {
            exception = conversationsOverviewFragment.swipedConversation.peek();
        } else {
            exception = null;
        }
        return getSuggestion(activity, exception);
    }

    public static @Nullable Conversation getSuggestion(
            final FragmentActivity activity, final Conversation exception) {
        Fragment fragment =
                activity.getSupportFragmentManager().findFragmentById(R.id.main_fragment);
        if (fragment instanceof ConversationsOverviewFragment conversationsOverviewFragment) {
            final var conversations = conversationsOverviewFragment.conversations;
            final var filtered = Collections2.filter(conversations, c -> c != exception);
            if (filtered.isEmpty()) {
                return null;
            } else {
                return Iterables.getFirst(filtered, null);
            }
        }
        return null;
    }

    public void onViewCreated(@NonNull final View view, final Bundle savedInstanceState) {
        requireActivity()
                .addMenuProvider(menuProvider, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
        if (savedInstanceState == null) {
            return;
        }
        pendingScrollState.push(savedInstanceState.getParcelable(STATE_SCROLL_POSITION));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        this.binding = null;
        this.conversationsAdapter = null;
        this.touchHelper = null;
    }

    @Override
    public void onPause() {
        final View toolbar = requireActivity().findViewById(R.id.toolbar);
        if (toolbar != null) toolbar.setVisibility(View.VISIBLE);
        pendingActionHelper.execute();
        super.onPause();
    }

    @Override
    public View onCreateView(
            @NonNull final LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState) {
        this.binding =
                DataBindingUtil.inflate(
                        inflater, R.layout.fragment_conversations_overview, container, false);
        this.binding.fab.setOnClickListener(
                (view) -> StartConversationActivity.launch(getActivity()));
        this.binding.operattMenuButton.setOnClickListener(view -> showOperattMenu());
        this.binding.operattAllChatsButton.setOnClickListener(v -> showAccountFilterDialog());
        this.binding.operattSearchButton.setOnClickListener(
                (view) -> startActivity(new Intent(getActivity(), SearchActivity.class)));
        this.binding.operattAccountsButton.setOnClickListener(
                (view) -> startActivity(new Intent(getActivity(), ManageAccountActivity.class)));
        this.binding.operattAccountFilterButton.setOnClickListener(view -> showAccountFilterDialog());
        this.binding.operattBottomNav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.operatt_nav_stories) {
                startActivity(new Intent(requireContext(), OperattStoriesActivity.class));
                return false; // Preserve the selected v5 home tab underneath Stories.
            }
            showTab(item.getItemId());
            return true;
        });
        this.binding.operattBottomNav.setSelectedItemId(currentTab);
        this.binding.operattContactAddButton.setOnClickListener(v -> StartConversationActivity.launch(getActivity()));
        this.binding.operattContactsList.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= visibleContacts.size()) return;
            final Contact contact = visibleContacts.get(position);
            final var service = requireXmppActivity().xmppConnectionService;
            if (service == null) return;
            final Conversation target = service.findOrCreateConversation(contact.getAccount(), contact.getAddress(), false, true);
            if (getActivity() instanceof OnConversationSelected callback) {
                callback.onConversationSelected(target);
            }
        });
        setupHomeActions();

        this.conversationsAdapter =
                new ConversationAdapter(requireXmppActivity(), this.conversations);
        this.conversationsAdapter.setConversationClickListener(
                (view, conversation) -> {
                    if (getActivity() instanceof OnConversationSelected callback) {
                        callback.onConversationSelected(conversation);
                    } else {
                        Log.w(
                                ConversationsOverviewFragment.class.getCanonicalName(),
                                "Activity does not implement OnConversationSelected");
                    }
                });
        this.binding.list.setAdapter(this.conversationsAdapter);
        this.binding.list.setLayoutManager(
                new LinearLayoutManager(getActivity(), LinearLayoutManager.VERTICAL, false));
        this.binding.list.addOnScrollListener(ExtendedFabSizeChanger.of(binding.fab));
        this.touchHelper = new ItemTouchHelper(this.callback);
        this.touchHelper.attachToRecyclerView(this.binding.list);
        return binding.getRoot();
    }

    @Override
    public void onBackendConnected() {
        refresh();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle bundle) {
        super.onSaveInstanceState(bundle);
        ScrollState scrollState = getScrollState();
        if (scrollState != null) {
            bundle.putParcelable(STATE_SCROLL_POSITION, scrollState);
        }
    }

    private ScrollState getScrollState() {
        if (this.binding == null) {
            return null;
        }
        if (this.binding.list.getLayoutManager()
                instanceof LinearLayoutManager linearLayoutManager) {
            final int position = linearLayoutManager.findFirstVisibleItemPosition();
            final View view = this.binding.list.getChildAt(0);
            if (view != null) {
                return new ScrollState(position, view.getTop());
            } else {
                return new ScrollState(position, 0);
            }
        }
        return null;
    }

    @Override
    public void onStart() {
        super.onStart();
        final View toolbar = requireActivity().findViewById(R.id.toolbar);
        if (toolbar != null && requireActivity().findViewById(R.id.secondary_fragment) == null) {
            toolbar.setVisibility(View.GONE);
        }
        showTab(currentTab);
        if (requireXmppActivity().xmppConnectionService != null) {
            refresh();
        }
    }

    private void selectAccountToStartEasyInvite() {
        final var accounts =
                EasyOnboardingManager.getAccounts(requireXmppActivity().xmppConnectionService);
        if (accounts.isEmpty()) {
            // This can technically happen if opening the menu item races with accounts reconnecting
            // or something
            Toast.makeText(
                            requireContext(),
                            R.string.no_active_accounts_support_this,
                            Toast.LENGTH_LONG)
                    .show();
        } else if (accounts.size() == 1) {
            openEasyInviteScreen(accounts.get(0));
        } else {
            final var selectedAccount = new AtomicReference<>(accounts.get(0));
            final var alertDialogBuilder = new MaterialAlertDialogBuilder(requireContext());
            alertDialogBuilder.setTitle(R.string.choose_account);
            final var asStrings = AccountUtils.asStrings(accounts);
            alertDialogBuilder.setSingleChoiceItems(
                    asStrings, 0, (dialog, which) -> selectedAccount.set(accounts.get(which)));
            alertDialogBuilder.setNegativeButton(R.string.cancel, null);
            alertDialogBuilder.setPositiveButton(
                    R.string.ok, (dialog, which) -> openEasyInviteScreen(selectedAccount.get()));
            alertDialogBuilder.create().show();
        }
    }

    private void openEasyInviteScreen(final Account account) {
        EasyOnboardingInviteActivity.launch(account, requireContext());
    }

    private void showTab(final int tab) {
        currentTab = tab;
        if (binding == null) return;
        binding.operattChatsPanel.setVisibility(tab == R.id.operatt_nav_chats ? View.VISIBLE : View.GONE);
        binding.operattContactsPanel.setVisibility(tab == R.id.operatt_nav_contacts ? View.VISIBLE : View.GONE);
        binding.operattSettingsPanel.setVisibility(tab == R.id.operatt_nav_settings ? View.VISIBLE : View.GONE);
        binding.operattProfilePanel.setVisibility(tab == R.id.operatt_nav_profile ? View.VISIBLE : View.GONE);
        if (tab == R.id.operatt_nav_contacts) refreshContacts();
        if (tab == R.id.operatt_nav_profile) refreshProfile();
    }

    private void openSettings(final String section) {
        final Intent intent = new Intent(requireContext(), SettingsActivity.class);
        intent.putExtra(SettingsActivity.EXTRA_SECTION, section);
        startActivity(intent);
    }

    private void openSelectedProfile() {
        final String jid = getSelectedAccountId();
        if (jid == null) {
            startActivity(new Intent(requireContext(), ManageAccountActivity.class));
            return;
        }
        final Intent intent = new Intent(requireContext(), EditAccountActivity.class);
        intent.putExtra("jid", jid);
        startActivity(intent);
    }

    private void setupHomeActions() {
        binding.operattSettingsAccount.setOnClickListener(v -> openSelectedProfile());
        binding.operattSettingsAppearance.setOnClickListener(v -> openSettings("appearance"));
        binding.operattSettingsPrivacy.setOnClickListener(v -> openSettings("privacy"));
        binding.operattSettingsNotifications.setOnClickListener(v -> openSettings("notifications"));
        binding.operattSettingsStorage.setOnClickListener(v -> openSettings("storage"));
        binding.operattSettingsBackup.setOnClickListener(v -> openSettings("backup"));
        binding.operattSettingsCalls.setOnClickListener(v -> startActivity(new Intent(requireContext(), OperattCallHistoryActivity.class)));
        binding.operattSettingsConnection.setOnClickListener(v -> openSettings("connection"));
        binding.operattSettingsAccounts.setOnClickListener(v -> startActivity(new Intent(requireContext(), ManageAccountActivity.class)));
        binding.operattSettingsStickers.setOnClickListener(v -> startActivity(new Intent(requireContext(), StickerStudioActivity.class)));
        binding.operattProfileEditButton.setOnClickListener(v -> openSelectedProfile());
        binding.operattProfileManageButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), ManageAccountActivity.class)));
        binding.operattProfilePhotoButton.setOnClickListener(v -> {
            final String jid = getSelectedAccountId();
            if (jid == null) return;
            final Intent i = new Intent(requireContext(), PublishProfilePictureActivity.class);
            i.putExtra(XmppActivity.EXTRA_ACCOUNT, jid);
            startActivity(i);
        });
    }

    private void refreshProfile() {
        if (binding == null) return;
        final var service = requireXmppActivity().xmppConnectionService;
        if (service == null) return;
        final String jid = getSelectedAccountId();
        for (Account account : service.getAccounts()) {
            if (account.getJid().asBareJid().toString().equals(jid)) {
                final String displayName = account.getDisplayName();
                binding.operattProfileName.setText(displayName == null || displayName.isEmpty()
                        ? jid.substring(0, jid.indexOf("@") >= 0 ? jid.indexOf("@") : jid.length())
                        : displayName);
                binding.operattProfileJid.setText(OperattHandle.display(jid));
                binding.operattProfileStatus.setText(account.getStatus().getReadableId());
                AvatarWorkerTask.loadAvatar(account, binding.operattProfileAvatar, R.dimen.avatar_on_details_screen_size);
                return;
            }
        }
    }

    private void refreshContacts() {
        if (binding == null) return;
        final var service = requireXmppActivity().xmppConnectionService;
        visibleContacts.clear();
        if (service != null) {
            final String jid = getSelectedAccountId();
            for (Account account : service.getAccounts()) {
                if (!account.getJid().asBareJid().toString().equals(jid)) continue;
                for (Contact contact : account.getRoster().getContacts()) {
                    if (contact.showInRoster() && !contact.isSelf()) visibleContacts.add(contact);
                }
            }
        }
        visibleContacts.sort(Comparator.comparing(Contact::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        final List<String> items = new ArrayList<>();
        for (Contact contact : visibleContacts) {
            items.add(contact.getDisplayName() + "  ·  " + OperattHandle.display(contact.getAddress().asBareJid().toString()));
        }
        binding.operattContactsHint.setText(items.isEmpty() ? R.string.operatt_contacts_hint : R.string.operatt_tab_contacts);
        binding.operattContactsList.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, items));
    }

    /** Three main shortcuts, like a compact messenger overflow menu. */
    private void showOperattMenu() {
        final androidx.appcompat.widget.PopupMenu popup =
                new androidx.appcompat.widget.PopupMenu(requireContext(), binding.operattMenuButton);
        popup.getMenu().add(0, 1, 0, R.string.operatt_night_mode)
                .setIcon(R.drawable.ic_dark_mode_24dp);
        popup.getMenu().add(0, 2, 1, R.string.operatt_new_group)
                .setIcon(R.drawable.ic_group_24dp);
        popup.getMenu().add(0, 3, 2, R.string.operatt_saved_messages)
                .setIcon(R.drawable.ic_bookmark_24dp);
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1 -> toggleNightMode();
                case 2 -> StartConversationActivity.launchForNewGroup(requireContext());
                case 3 -> openSavedMessages();
                default -> { return false; }
            }
            return true;
        });
        popup.show();
    }

    private void toggleNightMode() {
        final boolean dark = Activities.isNightMode(requireContext());
        PreferenceManager.getDefaultSharedPreferences(requireContext())
                .edit().putString("theme", dark ? "light" : "dark").apply();
        AppCompatDelegate.setDefaultNightMode(
                dark ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES);
    }

    private void openSavedMessages() {
        final var service = requireXmppActivity().xmppConnectionService;
        if (service == null) return;
        final String jid = getSelectedAccountId();
        for (Account account : service.getAccounts()) {
            if (account.getJid().asBareJid().toString().equals(jid)) {
                final Conversation selfChat = service.findOrCreateConversation(
                        account, account.getJid().asBareJid(), false, true);
                if (getActivity() instanceof OnConversationSelected callback) {
                    callback.onConversationSelected(selfChat);
                }
                return;
            }
        }
    }

    private String getSelectedAccountId() {
        final var service = requireXmppActivity().xmppConnectionService;
        if (service == null || service.getAccounts().isEmpty()) {
            return null;
        }
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        final String saved = prefs.getString(OPERATT_SELECTED_ACCOUNT, null);
        for (Account account : service.getAccounts()) {
            if (account.getJid().asBareJid().toString().equals(saved)) {
                return saved;
            }
        }
        return service.getAccounts().get(0).getJid().asBareJid().toString();
    }

    private void showAccountFilterDialog() {
        final var service = requireXmppActivity().xmppConnectionService;
        if (service == null || service.getAccounts().isEmpty()) {
            startActivity(new Intent(requireContext(), ManageAccountActivity.class));
            return;
        }
        final var accounts = service.getAccounts();
        final String[] labels = new String[accounts.size() + 1];
        int checked = -1;
        final String selected = getSelectedAccountId();
        for (int i = 0; i < accounts.size(); i++) {
            labels[i] = OperattHandle.display(accounts.get(i).getJid().asBareJid().toString());
            if (accounts.get(i).getJid().asBareJid().toString().equals(selected)) checked = i;
        }
        labels[accounts.size()] = getString(R.string.operatt_manage_accounts);
        final var dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.operatt_select_account)
                .setSingleChoiceItems(labels, checked, (whichDialog, which) -> {
                    whichDialog.dismiss();
                    if (which == accounts.size()) {
                        startActivity(new Intent(requireContext(), ManageAccountActivity.class));
                    } else {
                        PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()
                                .putString(OPERATT_SELECTED_ACCOUNT,
                                        accounts.get(which).getJid().asBareJid().toString()).apply();
                        refresh();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.show();
    }

    private void updateConnectionLabel(final String selectedJid) {
        if (binding == null) return;
        final var service = requireXmppActivity().xmppConnectionService;
        if (service == null) {
            binding.operattConnectionStatus.setText(R.string.operatt_disconnected);
            return;
        }
        for (Account account : service.getAccounts()) {
            if (account.getJid().asBareJid().toString().equals(selectedJid)) {
                binding.operattConnectionStatus.setText(connectionLabel(account));
                final int color = androidx.core.content.ContextCompat.getColor(requireContext(),
                        account.getStatus() == Account.State.ONLINE ? R.color.operatt_online : R.color.operatt_offline);
                binding.operattConnectionStatus.setTextColor(color);
                return;
            }
        }
        binding.operattConnectionStatus.setText(R.string.operatt_disconnected);
    }

    private int connectionLabel(final Account account) {
        return switch (account.getStatus()) {
            case ONLINE -> R.string.operatt_connected;
            case CONNECTING -> R.string.operatt_connecting;
            default -> R.string.operatt_disconnected;
        };
    }

    @Override
    void refresh() {
        if (this.binding == null) {
            Log.d(
                    Config.LOGTAG,
                    "ConversationsOverviewFragment.refresh() skipped updated because view binding"
                            + " or activity was null");
            return;
        }
        this.requireXmppActivity()
                .xmppConnectionService
                .populateWithOrderedConversations(this.conversations);
        final String selectedAccount = getSelectedAccountId();
        if (selectedAccount != null) {
            this.conversations.removeIf(c -> !c.getAccount().getJid().asBareJid().toString().equals(selectedAccount));
        }
        this.binding.operattAccountFilterButton.setText(
                selectedAccount == null ? getString(R.string.operatt_select_account) : OperattHandle.display(selectedAccount));
        updateConnectionLabel(selectedAccount);
        if (currentTab == R.id.operatt_nav_contacts) refreshContacts();
        if (currentTab == R.id.operatt_nav_profile) refreshProfile();
        Conversation removed = this.swipedConversation.peek();
        if (removed != null) {
            if (removed.isRead()) {
                this.conversations.remove(removed);
            } else {
                pendingActionHelper.execute();
            }
        }
        if (this.conversations.isEmpty()) {
            this.binding.list.setVisibility(View.GONE);
            this.binding.emptyChatHint.setVisibility(View.VISIBLE);
        } else {
            this.binding.emptyChatHint.setVisibility(View.GONE);
            this.binding.list.setVisibility(View.VISIBLE);
            this.conversationsAdapter.notifyDataSetChanged();
            final var scrollState = pendingScrollState.pop();
            if (scrollState != null) {
                setScrollPosition(scrollState);
            }
        }
    }

    private void toggleHintVisibility() {
        if (this.conversations.isEmpty()) {
            this.binding.list.setVisibility(View.GONE);
            this.binding.emptyChatHint.setVisibility(View.VISIBLE);
        } else {
            this.binding.emptyChatHint.setVisibility(View.GONE);
            this.binding.list.setVisibility(View.VISIBLE);
        }
    }

    private void setScrollPosition(@NonNull final ScrollState scrollPosition) {
        if (binding.list.getLayoutManager() instanceof LinearLayoutManager linearLayoutManager) {
            linearLayoutManager.scrollToPositionWithOffset(
                    scrollPosition.position, scrollPosition.offset);
            if (scrollPosition.position > 0) {
                binding.fab.shrink();
            } else {
                binding.fab.extend();
            }
            binding.fab.clearAnimation();
        }
    }
}
