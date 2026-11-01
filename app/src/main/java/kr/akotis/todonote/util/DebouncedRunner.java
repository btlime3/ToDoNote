package kr.akotis.todonote.util;

import android.os.Handler;
import android.os.Looper;

/** 마지막 요청 후 일정 시간이 지나면 작업을 한 번만 실행한다. 메인 스레드에서만 사용한다. */
public final class DebouncedRunner {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final long delayMillis;
    private final Runnable task;

    public DebouncedRunner(long delayMillis, Runnable task) {
        this.delayMillis = delayMillis;
        this.task = task;
    }

    public void schedule() {
        handler.removeCallbacks(task);
        handler.postDelayed(task, delayMillis);
    }

    public void cancel() {
        handler.removeCallbacks(task);
    }
}
