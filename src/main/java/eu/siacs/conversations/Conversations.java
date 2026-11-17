package eu.siacs.conversations;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.preference.PreferenceManager;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import eu.siacs.conversations.worker.ExportBackupWorker;
import java.util.concurrent.TimeUnit;
import android.util.Log;
import com.google.common.base.Stopwatch;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.Iterables;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.services.EmojiInitializationService;
import eu.siacs.conversations.ui.util.SettingsUtils;
import eu.siacs.conversations.utils.ExceptionHelper;
import java.security.Security;
import java.util.Collection;
import java.util.Objects;
import org.conscrypt.Conscrypt;

public class Conversations extends Application {

    @SuppressLint("StaticFieldLeak")
    private static Context CONTEXT;

    public static Context getContext() {
        return Conversations.CONTEXT;
    }

    private final Supplier<Collection<DatabaseBackend.AccountWithOptions>>
            accountWithOptionsSupplier =
                    () -> {
                        final var stopwatch = Stopwatch.createStarted();
                        final var accounts =
                                DatabaseBackend.getInstance(Conversations.this)
                                        .getAccountWithOptions();
                        Log.d(
                                Config.LOGTAG,
                                "fetching accounts from database in " + stopwatch.stop());
                        return accounts;
                    };
    private Supplier<Collection<DatabaseBackend.AccountWithOptions>> accountWithOptions =
            Suppliers.memoize(accountWithOptionsSupplier);

    @Override
    public void onCreate() {
        super.onCreate();
        installSecurityProvider();
        CONTEXT = this.getApplicationContext();
        EmojiInitializationService.execute(getApplicationContext());
        ExceptionHelper.init(getApplicationContext());
        SettingsUtils.applyThemeSettings(this);
        applyReducedBackgroundModeOnUpgrade();
        scheduleWeeklyBackupIfDefault();
    }

    /** Remove the old sticky foreground-service preference on the first v7 launch.
     * An existing install can inherit a true value from earlier builds even though
     * the current default is false. This one-time migration is intentionally
     * conservative: active call and transcoding services remain foreground.
     * Users can opt back into persistent connectivity in Connection & Battery.
     * A proper push gateway is still required for reliable instant delivery
     * after Android suspends background sockets.
     */
    private void applyReducedBackgroundModeOnUpgrade() {
        final var prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (!prefs.getBoolean("operatt_low_power_migration_v7", false)) {
            prefs.edit()
                    .putBoolean(eu.siacs.conversations.AppSettings.KEEP_FOREGROUND_SERVICE, false)
                    .putBoolean("operatt_low_power_migration_v7", true)
                    .apply();
        }
    }

    /** Initialize a seven-day backup only for installs that have not chosen another cadence.
     * WorkManager uses battery/storage constraints; Android chooses the execution time.
     */
    private void scheduleWeeklyBackupIfDefault() {
        final var prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (!prefs.contains("recurring_backup")) {
            prefs.edit().putString("recurring_backup", "604800").apply();
        }
        if (!"604800".equals(prefs.getString("recurring_backup", ""))) return;
        final Constraints constraints = new Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .build();
        final PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                        ExportBackupWorker.class, 7, TimeUnit.DAYS)
                .setConstraints(constraints)
                .setInputData(new Data.Builder().putBoolean("recurring_backup", true).build())
                .build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "recurring_backup", ExistingPeriodicWorkPolicy.KEEP, request);
    }

    private static void installSecurityProvider() {
        try {
            Security.insertProviderAt(Conscrypt.newProvider(), 1);
        } catch (final Throwable throwable) {
            Log.e(Config.LOGTAG, "could not install security provider", throwable);
        }
    }

    public static Conversations getInstance(final Context context) {
        if (context.getApplicationContext() instanceof Conversations c) {
            return c;
        }
        throw new IllegalStateException("Application is not Conversations");
    }

    public void resetAccounts() {
        this.accountWithOptions = Suppliers.memoize(accountWithOptionsSupplier);
    }

    public Collection<DatabaseBackend.AccountWithOptions> getAccounts() {
        return this.accountWithOptions.get();
    }

    public boolean hasEnabledAccount() {
        return Iterables.any(
                getAccounts(),
                a ->
                        !Objects.requireNonNull(a).isOptionSet(Account.OPTION_DISABLED)
                                && !a.isOptionSet(Account.OPTION_SOFT_DISABLED));
    }
}
