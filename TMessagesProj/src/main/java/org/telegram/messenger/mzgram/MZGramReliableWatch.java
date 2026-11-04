/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Reliable delivery's watch: every 15 minutes, whether the service still
 * runs; if not, it is started again and the journal says so.
 */

package org.telegram.messenger.mzgram;

import android.app.job.JobParameters;
import android.app.job.JobService;

public class MZGramReliableWatch extends JobService {

    @Override
    public boolean onStartJob(JobParameters params) {
        MZGramReliableJournal.alive();
        if (!MZGramReliableService.isRunning()) {
            MZGramReliableJournal.event("watch: not running");
        }
        MZGramReliableService.apply(getApplicationContext(), MZGramReliableService.REASON_WATCH);
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}
