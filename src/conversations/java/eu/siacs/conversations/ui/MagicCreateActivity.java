package eu.siacs.conversations.ui;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import androidx.databinding.DataBindingUtil;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityMagicCreateBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.utils.InstallReferrerUtils;
import eu.siacs.conversations.xmpp.Jid;

public class MagicCreateActivity extends XmppActivity implements TextWatcher {

    public static final String EXTRA_DOMAIN = "domain";
    public static final String EXTRA_PRE_AUTH = "pre_auth";
    public static final String EXTRA_USERNAME = "username";

    private ActivityMagicCreateBinding binding;
    private String domain;
    private String username;
    private String preAuth;

    @Override
    protected void refreshUiReal() {}

    @Override
    protected void onBackendConnected() {}

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        final Intent data = getIntent();
        this.domain = data == null ? null : data.getStringExtra(EXTRA_DOMAIN);
        this.preAuth = data == null ? null : data.getStringExtra(EXTRA_PRE_AUTH);
        this.username = data == null ? null : data.getStringExtra(EXTRA_USERNAME);
        if (getResources().getBoolean(R.bool.portrait_only)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        super.onCreate(savedInstanceState);
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_magic_create);
        Activities.setStatusAndNavigationBarColors(this, binding.getRoot());
        setSupportActionBar(this.binding.toolbar);
        configureActionBar(getSupportActionBar(), this.domain == null);
        if (username != null && domain != null) {
            binding.title.setText(R.string.your_server_invitation);
            binding.instructions.setText(getString(R.string.magic_create_text_fixed, domain));
            binding.username.setEnabled(false);
            binding.username.setText(this.username);
            updateFullJidInformation(this.username);
        } else if (domain != null) {
            binding.instructions.setText(getString(R.string.magic_create_text_on_x, domain));
        }
        // Invitation URLs already carry a server-issued one-time token. A manual
        // token is needed only when the user entered the signup screen directly.
        binding.registrationCodeLayout.setVisibility(
                this.preAuth == null ? View.VISIBLE : View.GONE);
        binding.registrationCodeExplanation.setVisibility(
                this.preAuth == null ? View.VISIBLE : View.GONE);
        binding.createAccount.setOnClickListener(
                v -> {
                    try {
                        final String username = binding.username.getText().toString().trim();
                        final String password = binding.password.getText().toString();
                        final String confirmPassword = binding.confirmPassword.getText().toString();
                        final Jid jid;
                        final boolean fixedUsername;
                        if (this.domain != null && this.username != null) {
                            fixedUsername = true;
                            jid = Jid.ofLocalAndDomain(this.username, this.domain);
                        } else if (this.domain != null) {
                            fixedUsername = false;
                            jid = Jid.ofLocalAndDomain(username, this.domain);
                        } else {
                            fixedUsername = false;
                            jid = Jid.ofLocalAndDomain(username, Config.MAGIC_CREATE_DOMAIN);
                        }
                        if (this.username == null && !username.matches("[A-Za-z0-9._-]{3,32}")) {
                            binding.usernameLayout.setError(getString(R.string.invalid_username));
                            binding.username.requestFocus();
                        } else if (password.length() < 10) {
                            binding.usernameLayout.setError(null);
                            binding.passwordLayout.setError(
                                    getString(R.string.operatt_password_too_short));
                            binding.password.requestFocus();
                        } else if (this.preAuth == null
                                && binding.registrationCode.getText().toString().trim().isEmpty()) {
                            binding.registrationCodeLayout.setError(
                                    getString(R.string.operatt_invite_required));
                            binding.registrationCode.requestFocus();
                        } else if (!password.equals(confirmPassword)) {
                            binding.passwordLayout.setError(null);
                            binding.confirmPasswordLayout.setError(
                                    getString(R.string.operatt_password_mismatch));
                            binding.confirmPassword.requestFocus();
                        } else {
                            binding.usernameLayout.setError(null);
                            binding.passwordLayout.setError(null);
                            binding.confirmPasswordLayout.setError(null);
                            binding.registrationCodeLayout.setError(null);
                            final String invitationToken = this.preAuth != null
                                    ? this.preAuth
                                    : binding.registrationCode.getText().toString().trim();
                            Account account = xmppConnectionService.findAccountByJid(jid);
                            if (account == null) {
                                account =
                                        new Account(jid, password);
                                account.setOption(Account.OPTION_REGISTER, true);
                                account.setOption(Account.OPTION_DISABLED, true);
                                account.setOption(Account.OPTION_MAGIC_CREATE, true);
                                account.setOption(Account.OPTION_FIXED_USERNAME, fixedUsername);
                                account.setKey(
                                        Account.KEY_PRE_AUTH_REGISTRATION_TOKEN, invitationToken);
                                xmppConnectionService.createAccount(account);
                            }
                            Intent intent =
                                    new Intent(MagicCreateActivity.this, EditAccountActivity.class);
                            intent.putExtra("jid", account.getJid().asBareJid().toString());
                            intent.putExtra("init", true);
                            intent.setFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK
                                            | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            StartConversationActivity.addInviteUri(intent, getIntent());
                            startActivity(intent);
                        }
                    } catch (final IllegalArgumentException e) {
                        binding.usernameLayout.setError(getString(R.string.invalid_username));
                        binding.username.requestFocus();
                    }
                });
        binding.username.addTextChangedListener(this);
    }

    @Override
    public void onDestroy() {
        InstallReferrerUtils.markInstallReferrerExecuted(this);
        super.onDestroy();
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {}

    @Override
    public void afterTextChanged(final Editable s) {
        updateFullJidInformation(s.toString());
    }

    private void updateFullJidInformation(final String username) {
        if (username.trim().isEmpty()) {
            binding.fullJid.setVisibility(View.INVISIBLE);
        } else {
            try {
                binding.fullJid.setVisibility(View.VISIBLE);
                final Jid jid;
                if (this.domain == null) {
                    jid = Jid.ofLocalAndDomain(username, Config.MAGIC_CREATE_DOMAIN);
                } else {
                    jid = Jid.ofLocalAndDomain(username, this.domain);
                }
                binding.fullJid.setText(getString(R.string.your_full_jid_will_be, jid.toString()));
                binding.usernameLayout.setError(null);
            } catch (final IllegalArgumentException e) {
                binding.fullJid.setVisibility(View.INVISIBLE);
            }
        }
    }
}
