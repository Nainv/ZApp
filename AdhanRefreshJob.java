package com.zikra.tv;

import android.app.job.JobService;
import android.app.job.JobParameters;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public final class AdhanRefreshJob extends JobService {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    @Override public boolean onStartJob(JobParameters params){worker.execute(()->{AdhanScheduler.refresh(this);jobFinished(params,false);});return true;}
    @Override public boolean onStopJob(JobParameters params){return true;}
    @Override public void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
